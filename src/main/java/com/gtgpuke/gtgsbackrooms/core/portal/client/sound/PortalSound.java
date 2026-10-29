package com.gtgpuke.gtgsbackrooms.core.portal.client.sound;

import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;

/**
 * Regles du son qui traverse un portail : un son ressort au point du cadre, dans le monde de l'auditeur, le plus proche
 * de sa source vue par ce cadre (son image, derriere le plan), et le trajet de la source jusqu'au cadre lui coute du
 * volume. Le moteur de son attenue ensuite, comme pour tout son, du point de sortie a l'oreille. Quand aucun portail du
 * monde de l'auditeur ne mene au monde du son, le son peut passer deux cadres : un portail de l'auditeur vers un monde
 * intermediaire, puis, dans ce monde, un portail vers celui du son (un portail vu a travers un autre). Le moteur audio
 * s'en sert aussi pour faire bourdonner au cadre les neons de l'autre cote.
 */
public final class PortalSound {

    /** Portee d'un son par unite de volume, en blocs : celle de vanilla. */
    private static final double BLOCKS_PER_VOLUME = 16.0;
    /** Portee minimale d'un son, en blocs. */
    static final double MIN_RADIUS = 16.0;
    /** Portee maximale d'un son, en blocs. */
    static final double MAX_RADIUS = 64.0;
    /** Un son colle au cadre, juste de ce cote de son plan, passe encore ; un auditeur colle au cadre l'entend encore. */
    public static final double BEHIND_TOLERANCE = 0.5;

    /** Aucun monde intermediaire : un son ne passe qu'un cadre. */
    public static final Function<RegistryKey<World>, List<BackroomsPortalEntity>> NO_SECOND_FRAME = world -> List.of();

    /**
     * Point du cadre par ou un son ressort, distance de sa source (vue par ce cadre) a ce point, le portail traverse, et le
     * point par ou le son est entre dans le premier cadre, dans le monde du son.
     *
     * @param point point de sortie, dans le monde de l'auditeur
     * @param distance longueur du trajet de la source au point de sortie, par le ou les cadres
     * @param portal portail du monde de l'auditeur par lequel le son ressort
     * @param arrival point du premier cadre traverse, cote du monde du son
     */
    public record Exit(Vec3d point, double distance, BackroomsPortalEntity portal, Vec3d arrival) {
    }

    private PortalSound() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Portee d'un son de ce volume, bornee entre {@link #MIN_RADIUS} et {@link #MAX_RADIUS}. */
    public static double radius(float volume) {
        return Math.min(MAX_RADIUS, Math.max(BLOCKS_PER_VOLUME * volume, MIN_RADIUS));
    }

    /**
     * Volume qui ressort du portail : le trajet jusqu'au cadre en a consomme la part de la portee du son qu'il represente,
     * comme le moteur de son attenue tout son lineairement sur sa portee (seize blocs pour un son faible, plus pour un son
     * fort) : un son faible porte aussi loin a travers le cadre que sans lui.
     *
     * @param volume volume du son a sa source
     * @param distanceToFrame distance de la source au point du cadre le plus proche
     * @return volume au point de sortie, nul si le son s'est eteint en chemin
     */
    public static float volumeThroughPortal(float volume, double distanceToFrame) {
        return (float) Math.max(0.0, volume * (1.0 - distanceToFrame / radius(volume)));
    }

    /**
     * Cadre par lequel un son d'un autre monde parvient a l'auditeur : parmi les portails de son monde, ouverts et visibles,
     * qui menent au monde du son (qu'ils aient un retour ou non), celui dont le point de sortie est le plus pres de l'image de
     * la source. Un portail ferme ou invisible ne laisse passer aucun son.
     *
     * @param portals portails du monde de l'auditeur
     * @param soundWorld monde de la source
     * @param source position de la source dans son monde
     * @param listener position de l'auditeur
     * @param radius portee du son
     * @return point de sortie et distance parcourue jusqu'a lui, ou {@code null} si aucun cadre ne rapproche le son
     */
    public static @Nullable Exit bestExit(List<BackroomsPortalEntity> portals, RegistryKey<World> soundWorld, Vec3d source, Vec3d listener, double radius) {
        return bestExit(portals, NO_SECOND_FRAME, soundWorld, source, listener, radius);
    }

    /**
     * Cadre par lequel un son d'un autre monde parvient a l'auditeur, par un cadre ou par deux : un portail de l'auditeur
     * vers un monde intermediaire, puis un portail de ce monde vers celui du son. Le trajet a deux cadres additionne les
     * deux segments (de la source au second cadre, puis de ce cadre au premier), sous la portee du son ; le trajet le plus
     * court l'emporte.
     *
     * @param portals portails du monde de l'auditeur
     * @param portalsOf portails d'un monde intermediaire, tel que le client le tient (vide s'il ne le tient pas)
     * @param soundWorld monde de la source
     * @param source position de la source dans son monde
     * @param listener position de l'auditeur
     * @param radius portee du son
     * @return point de sortie et trajet, ou {@code null} si aucun chemin ne rapproche le son
     */
    public static @Nullable Exit bestExit(List<BackroomsPortalEntity> portals, Function<RegistryKey<World>, List<BackroomsPortalEntity>> portalsOf,
            RegistryKey<World> soundWorld, Vec3d source, Vec3d listener, double radius) {
        Exit best = null;
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (!portal.visible() || !portal.teleportable() || !portal.destinationDim().equals(soundWorld)) {
                continue;
            }
            Vec3d image = portal.fromDestination(source);
            Vec3d exit = exitPoint(portal, image, listener, radius);
            if (exit == null) {
                continue;
            }
            double distance = Math.sqrt(exit.squaredDistanceTo(image));
            if (best == null || distance < best.distance()) {
                best = new Exit(exit, distance, portal, portal.toDestination(exit));
            }
        }
        // Un portail vu a travers un autre peut rapprocher le son davantage qu'un cadre direct : le plus court l'emporte.
        Exit throughTwo = bestThroughTwo(portals, portalsOf, soundWorld, source, listener, radius);
        return throughTwo != null && (best == null || throughTwo.distance() < best.distance()) ? throughTwo : best;
    }

    /** Le meilleur trajet par deux cadres, s'il y en a un : un portail vu a travers un autre. */
    private static @Nullable Exit bestThroughTwo(List<BackroomsPortalEntity> portals, Function<RegistryKey<World>, List<BackroomsPortalEntity>> portalsOf,
            RegistryKey<World> soundWorld, Vec3d source, Vec3d listener, double radius) {
        Exit best = null;
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity first = portals.get(i);
            if (!first.visible() || !first.teleportable()) {
                continue;
            }
            // L'auditeur vu dans le monde intermediaire, par le premier cadre.
            Vec3d listenerThere = first.toDestination(listener);
            List<BackroomsPortalEntity> inner = portalsOf.apply(first.destinationDim());
            for (int j = 0; j < inner.size(); j++) {
                BackroomsPortalEntity second = inner.get(j);
                if (!second.visible() || !second.teleportable() || !second.destinationDim().equals(soundWorld)) {
                    continue;
                }
                Vec3d imageThere = second.fromDestination(source);
                // Le second cadre se voit a travers le premier : l'auditeur doit etre devant lui, a n'importe quelle distance (le
                // trajet entier, plus bas, tient la portee du son).
                Vec3d innerExit = exitPoint(second, imageThere, listenerThere, radius, Double.POSITIVE_INFINITY);
                if (innerExit == null) {
                    continue;
                }
                double innerDistance = Math.sqrt(innerExit.squaredDistanceTo(imageThere));
                Vec3d image = first.fromDestination(innerExit);
                Vec3d exit = exitPoint(first, image, listener, radius);
                if (exit == null) {
                    continue;
                }
                double distance = innerDistance + Math.sqrt(exit.squaredDistanceTo(image));
                if (distance <= radius && (best == null || distance < best.distance())) {
                    best = new Exit(exit, distance, first, second.toDestination(innerExit));
                }
            }
        }
        return best;
    }

    /**
     * Point du cadre par ou un son ressort d'un portail du monde de l'auditeur, celui par lequel il voit l'autre cote.
     * La source est prise par son image, la ou elle semble etre vue par le cadre, derriere son plan ; le son ressort au
     * point du cadre le plus proche de cette image. Il passe si la source est bien de l'autre cote, l'auditeur devant la
     * face, et l'un comme l'autre a portee du cadre.
     *
     * @param portal portail du monde de l'auditeur, vers le monde du son
     * @param image image de la source par ce portail ({@link BackroomsPortalEntity#fromDestination(Vec3d)})
     * @param listener position de l'auditeur
     * @param radius portee du son
     * @return point de sortie sur le cadre, ou {@code null} si ce portail ne laisse pas passer le son
     */
    public static @Nullable Vec3d exitPoint(BackroomsPortalEntity portal, Vec3d image, Vec3d listener, double radius) {
        return exitPoint(portal, image, listener, radius, radius);
    }

    /** {@link #exitPoint(BackroomsPortalEntity, Vec3d, Vec3d, double)} avec une portee a part pour l'auditeur. */
    private static @Nullable Vec3d exitPoint(BackroomsPortalEntity portal, Vec3d image, Vec3d listener, double radius, double listenerRadius) {
        if (portal.signedDistance(image) > BEHIND_TOLERANCE || portal.signedDistance(listener) < -BEHIND_TOLERANCE) {
            return null;
        }
        Vec3d exit = portal.nearestFramePoint(image.x, image.y, image.z);
        return exit.squaredDistanceTo(image) > radius * radius || exit.squaredDistanceTo(listener) > listenerRadius * listenerRadius ? null : exit;
    }
}
