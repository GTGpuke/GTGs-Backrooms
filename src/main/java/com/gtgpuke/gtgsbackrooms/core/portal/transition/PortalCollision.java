package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Collision a travers les portails ouverts : une entity engagee dans un cadre heurte les blocs de l'autre cote, et plus
 * ceux de son cote qui sont derriere le plan.
 *
 * <p>Tant que son point de traversee (ses pieds, ses yeux pour un cadre couche) est devant le plan, en face de
 * l'ouverture, et que sa boite touche le cadre, l'entity y est engagee. Les blocs de l'autre cote, au-dela du plan
 * d'arrivee, sont alors ramenes de ce cote, derriere le plan : on ne s'engage pas la ou l'autre cote est un mur, et un sol
 * plus haut que le bas du cadre se monte comme une marche, personne ne ressort dans un bloc. Les blocs de son cote qui sont
 * tout entiers derriere le plan, dans l'ouverture, n'existent plus pour elle ({@link #tunnel}, {@link #hides}) : un cadre
 * pose contre un mur, un plafond ou un sol plein se traverse comme si ce bloc n'y etait pas, et un cadre au sol s'ouvre
 * sous qui s'y tient, comme une trappe. Celui qui contourne le portail en frolant son bord n'y entre pas : rien ne le gene
 * de l'autre cote, et les blocs autour de l'ouverture restent.
 *
 * <p>Du cote d'entree, qui ne peut pas traverser correctement, son point de traversee hors de l'ouverture (sur un bord),
 * bute sur le plan comme sur une paroi mince ({@link #walls}) : son corps ne passe pas a moitie dans le portail, meme
 * pour un cadre en l'air. De dos, rien ne l'arrete : on passe au travers du plan sans traverser.
 *
 * <p>Le client applique les blocs de l'autre cote a son joueur, qui decide de ses deplacements ; le serveur les applique
 * aux mobs, objets et projectiles, pas aux joueurs : il refuserait des pas qu'un client sans monde distant charge aurait
 * faits librement. Les blocs caches derriere le plan le sont des deux cotes, pour tous : le serveur accepte ainsi les pas
 * du joueur qui s'y engage. La paroi du plan suit la meme regle que les blocs de l'autre cote.
 */
public final class PortalCollision {

    /**
     * Portails d'un monde, monde ou chacun mene et cote qui suit ses entities : registre et mondes du serveur, ou index et
     * mondes distants du client.
     */
    public interface Worlds {

        /** Portails charges dans ce monde, ou plus largement ; liste vivante, lue par index. */
        List<BackroomsPortalEntity> portals(World world);

        /** Monde d'arrivee d'un portail, ou {@code null} s'il n'est pas disponible de ce cote. */
        @Nullable World destination(BackroomsPortalEntity portal);

        /** Cote qui suit les traversees des entities de ce monde. */
        Side side(World world);
    }

    /**
     * Suivi des traversees d'un cote, chacun sur son fil (le serveur sur le sien, le client sur celui du rendu) : dernier
     * engagement dans un cadre ({@link #tunnel}), par entity. Les entities de la table sont aussi dans une liste, que le
     * tick parcourt par index : ni iterateur ni entree allouee.
     */
    public enum Side {
        SERVER,
        CLIENT;

        final Map<Entity, Engagement> engaged = new IdentityHashMap<>();
        final List<Entity> engagedEntities = new ArrayList<>();
    }

    /** Cote serveur : le registre des portails charges, tous mondes confondus, et les mondes du serveur. */
    public static final Worlds SERVER_WORLDS = new Worlds() {
        @Override
        public List<BackroomsPortalEntity> portals(World world) {
            return BackroomsPortalEntityRegistry.all();
        }

        @Override
        public @Nullable World destination(BackroomsPortalEntity portal) {
            // Le registre ne tient que des portails serveur : leur monde connait le serveur.
            return portal.getServer().getWorld(portal.destinationDim());
        }

        @Override
        public Side side(World world) {
            return Side.SERVER;
        }
    };

    /** Epaisseur, derriere le plan, de l'ouverture fermee ({@link #closedOpening}) : la paroi d'un bord. */
    static final double CLOSED_DEPTH = 0.5;
    /**
     * Largeur, hors de l'ouverture, des bords d'un cadre pour un joueur : une petite paroi contre laquelle sa tete ou ses
     * epaules butent, et le long de laquelle il glisse jusqu'a s'aligner.
     */
    static final double EDGE_REACH = 0.5;
    /**
     * Ticks pendant lesquels une entity reste engagee apres l'avoir ete, son point de traversee passe derriere le plan : au
     * plus le delai que le serveur laisse au client pour annoncer sa traversee, et de la marge. {@link #tick} oublie
     * ensuite l'engagement.
     */
    public static final int CROSSING_TICKS = 10;
    /** Jeu des comparaisons au plan et aux bords du cadre : un bloc pose contre le plan, ou un bord de bloc sur un bord du cadre, compte. */
    private static final double EPSILON = 1.0e-6;

    private static Worlds worlds = SERVER_WORLDS;

    /** Portail ou une entity a ete engagee en dernier, et quand (heure de son monde) ; mis a jour sur place. */
    private static final class Engagement {
        private BackroomsPortalEntity portal;
        private long time;
    }

    private PortalCollision() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche les portails du client en plus de ceux du serveur (point d'entree client). */
    public static void setWorlds(Worlds clientAndServer) {
        worlds = clientAndServer;
    }

    /**
     * Ajoute aux collisions d'un deplacement les blocs de l'autre cote des portails ou l'entity est engagee, et le plan des
     * portails sur le bord desquels elle bute.
     *
     * @param entity entity qui se deplace ; un joueur cote serveur n'en recoit rien, son client decide de ses pas
     * @param world monde de l'entity
     * @param collisions collisions vanilla du deplacement
     * @param movingBox boite de l'entity etiree par son deplacement
     * @return les collisions recues, ou une nouvelle liste qui les complete
     */
    public static List<VoxelShape> withOtherSide(Entity entity, World world, List<VoxelShape> collisions, Box movingBox) {
        if (entity instanceof ServerPlayerEntity) {
            return collisions;
        }
        List<BackroomsPortalEntity> portals = worlds.portals(world);
        List<VoxelShape> result = collisions;
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.getWorld() != world) {
                continue;
            }
            if (engages(portal, entity, movingBox)) {
                World destination = worlds.destination(portal);
                if (destination != null) {
                    result = editable(result, collisions);
                    addOtherSide(portal, destination, movingBox, result);
                }
                if (entity instanceof PlayerEntity) {
                    result = withFrameEdges(portal, movingBox, result, collisions);
                }
            } else if (walls(portal, entity, movingBox)) {
                result = withClosedOpening(portal, movingBox, result, collisions);
            }
        }
        return result;
    }

    private static List<VoxelShape> editable(List<VoxelShape> result, List<VoxelShape> collisions) {
        return result == collisions ? new ArrayList<>(collisions) : result;
    }

    /**
     * Les quatre bords d'un cadre, pour un joueur engage dedans : de minces parois autour de l'ouverture, du plan a
     * {@value #CLOSED_DEPTH} bloc derriere comme l'ouverture fermee, d'{@value #EDGE_REACH} bloc hors de l'ouverture, coins
     * compris. Sa tete et ses epaules doivent passer l'ouverture : un cadre trop bas l'arrete au front, un cadre sureleve
     * se saute, un cadre etroit se passe aligne (decision de l'auteur du 25 octobre 2026). Les autres entities gardent
     * la seule ouverture fermee ({@link #walls}) : leur point de traversee decide seul.
     */
    static Box[] frameEdges(BackroomsPortalEntity portal) {
        PortalFacing facing = portal.facingSource();
        Vec3d center = portal.getPos();
        Vec3d depth = facing.normal.multiply(CLOSED_DEPTH);
        double halfWidth = portal.width() / 2.0;
        double halfHeight = portal.height() / 2.0;
        return new Box[] {
            edge(center, facing, depth, halfWidth, halfWidth + EDGE_REACH, -halfHeight - EDGE_REACH, halfHeight + EDGE_REACH),
            edge(center, facing, depth, -halfWidth - EDGE_REACH, -halfWidth, -halfHeight - EDGE_REACH, halfHeight + EDGE_REACH),
            edge(center, facing, depth, -halfWidth, halfWidth, halfHeight, halfHeight + EDGE_REACH),
            edge(center, facing, depth, -halfWidth, halfWidth, -halfHeight - EDGE_REACH, -halfHeight),
        };
    }

    /** Boite d'un bord : de {@code w1} a {@code w2} le long de la largeur, de {@code h1} a {@code h2} le long de la hauteur, du plan a {@code depth} derriere. */
    private static Box edge(Vec3d center, PortalFacing facing, Vec3d depth, double w1, double w2, double h1, double h2) {
        Vec3d near = center.add(facing.axisW.multiply(w1)).add(facing.axisH.multiply(h1));
        Vec3d far = center.add(facing.axisW.multiply(w2)).add(facing.axisH.multiply(h2)).subtract(depth);
        return new Box(near, far);
    }

    /**
     * Ajoute aux collisions les bords du cadre que la boite deplacee d'un joueur engage atteint. Comme l'autre cote et
     * l'ouverture fermee, cela alloue (bords, formes) : pour un joueur engage dans un cadre seulement, le temps qu'il le
     * touche.
     */
    private static List<VoxelShape> withFrameEdges(BackroomsPortalEntity portal, Box movingBox, List<VoxelShape> result, List<VoxelShape> collisions) {
        List<VoxelShape> edited = result;
        for (Box edge : frameEdges(portal)) {
            if (edge.intersects(movingBox)) {
                edited = editable(edited, collisions);
                edited.add(VoxelShapes.cuboid(edge));
            }
        }
        return edited;
    }

    /** Ajoute l'ouverture fermee du portail aux collisions, si la boite deplacee l'atteint. */
    private static List<VoxelShape> withClosedOpening(BackroomsPortalEntity portal, Box movingBox, List<VoxelShape> result,
            List<VoxelShape> collisions) {
        Box closed = closedOpening(portal);
        if (!closed.intersects(movingBox)) {
            return result;
        }
        List<VoxelShape> edited = editable(result, collisions);
        edited.add(VoxelShapes.cuboid(closed));
        return edited;
    }

    /**
     * Portail ou l'entity est engagee avec cette boite (la sienne, ou etiree par un deplacement) : les blocs de son cote que
     * ce portail cache ({@link #hides}) n'existent pas pour elle. Le premier trouve ; {@code null} si aucun.
     *
     * <p>Une entity engagee le reste {@value #CROSSING_TICKS} ticks apres son dernier engagement, jusqu'au {@link #tick} qui
     * l'oublie, tant qu'elle est en face de l'ouverture : son point de traversee vient de passer le plan, et la traversee
     * suit (le client la predit a l'image suivante, le serveur la recoit apres le pas qui l'y a mene, ou la mene lui-meme).
     * Entre-temps, ce pas ne la met pas dans un bloc cache : le serveur l'accepte, et rien ne l'etouffe ni ne la repousse.
     *
     * @param entity entity dont on cherche les collisions ; un portail ne s'engage dans aucun autre
     * @param box boite de la requete
     */
    public static @Nullable BackroomsPortalEntity tunnel(Entity entity, Box box) {
        if (entity instanceof BackroomsPortalEntity) {
            return null;
        }
        World world = entity.getWorld();
        Side side = worlds.side(world);
        BackroomsPortalEntity crossing = stillCrossing(entity, world, side.engaged);
        List<BackroomsPortalEntity> portals = worlds.portals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.getWorld() == world && engages(portal, entity, box) && !backFaceOf(portal, crossing)) {
                remember(entity, portal, world, side);
                return portal;
            }
        }
        return crossing;
    }

    /**
     * {@code true} si ce portail est la face arriere de celui que l'entity traverse : au meme centre, dos a dos. Son point
     * de traversee passe devant la face avant, elle est devant la face arriere, mais celle-ci cache le cote d'ou elle vient.
     */
    private static boolean backFaceOf(BackroomsPortalEntity portal, @Nullable BackroomsPortalEntity crossing) {
        return crossing != null && portal.centeredAt(crossing.getPos()) && portal.facingSource() == crossing.facingSource().opposite();
    }

    /** Garde le dernier engagement d'une entity, sans allocation une fois son suivi cree. */
    private static void remember(Entity entity, BackroomsPortalEntity portal, World world, Side side) {
        Engagement engagement = side.engaged.get(entity);
        if (engagement == null) {
            engagement = new Engagement();
            side.engaged.put(entity, engagement);
            side.engagedEntities.add(entity);
        }
        engagement.portal = portal;
        engagement.time = world.getTime();
    }

    /**
     * Portail ou l'entity etait engagee il y a peu (son engagement n'est pas encore oublie), si elle est encore en face de
     * son ouverture, dans le meme monde ; sinon {@code null}.
     */
    private static @Nullable BackroomsPortalEntity stillCrossing(Entity entity, World world, Map<Entity, Engagement> engaged) {
        Engagement engagement = engaged.get(entity);
        if (engagement == null) {
            return null;
        }
        BackroomsPortalEntity portal = engagement.portal;
        double y = entity.getY() + portal.crossingHeight(entity);
        return portal.getWorld() == world && !portal.isRemoved() && portal.teleportable() && portal.withinFrameWidth(entity.getX(), y, entity.getZ())
                ? portal : null;
    }

    /** Oublie les engagements passes depuis plus de {@value #CROSSING_TICKS} ticks, ou d'entities disparues, d'un cote. */
    public static void tick(Side side) {
        List<Entity> entities = side.engagedEntities;
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            if (entity.isRemoved() || entity.getWorld().getTime() - side.engaged.get(entity).time > CROSSING_TICKS) {
                side.engaged.remove(entity);
                entities.remove(i);
            }
        }
    }

    /** Oublie tous les engagements d'un cote (arret du serveur, deconnexion du client). */
    public static void clear(Side side) {
        side.engaged.clear();
        side.engagedEntities.clear();
    }

    /**
     * {@code true} si le bloc de cette position est tout entier derriere le plan du portail, dans son ouverture prolongee
     * vers l'arriere : pour l'entity engagee, il n'existe pas.
     */
    public static boolean hides(BackroomsPortalEntity portal, int x, int y, int z) {
        PortalFacing facing = portal.facingSource();
        Vec3d center = portal.getPos();
        double dx = x - center.x;
        double dy = y - center.y;
        double dz = z - center.z;
        return reach(facing.normal, dx, dy, dz, true) <= EPSILON
                && within(facing.axisW, dx, dy, dz, portal.width() / 2.0)
                && within(facing.axisH, dx, dy, dz, portal.height() / 2.0);
    }

    /**
     * Plus grande ({@code far}) ou plus petite composante, le long d'un axe du repere du monde (unitaire, positif ou
     * negatif), d'un bloc dont le coin bas est en (dx, dy, dz).
     */
    private static double reach(Vec3d axis, double dx, double dy, double dz, boolean far) {
        return axis.x * (axis.x > 0.0 == far ? dx + 1.0 : dx) + axis.y * (axis.y > 0.0 == far ? dy + 1.0 : dy)
                + axis.z * (axis.z > 0.0 == far ? dz + 1.0 : dz);
    }

    /** {@code true} si un bloc dont le coin bas est en (dx, dy, dz) tient, le long d'un axe, dans [-half, half]. */
    private static boolean within(Vec3d axis, double dx, double dy, double dz, double half) {
        return reach(axis, dx, dy, dz, true) <= half + EPSILON && reach(axis, dx, dy, dz, false) >= -half - EPSILON;
    }

    /**
     * {@code true} si le bloc de cette position est cache a l'entity par le portail ou elle est engagee ({@link #tunnel}) :
     * la tete qui approche d'un cadre contre un mur ou sous un plafond, ou y passe, n'y est pas dans un mur, ni etouffee,
     * ni aveuglee.
     */
    public static boolean hiddenFrom(Entity entity, BlockPos pos) {
        BackroomsPortalEntity tunnel = tunnel(entity, entity.getBoundingBox());
        return tunnel != null && hides(tunnel, pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Ouverture du cadre fermee, du plan a {@value #CLOSED_DEPTH} bloc derriere : la paroi sur laquelle bute qui ne traverse
     * pas correctement ({@link #walls}).
     */
    static Box closedOpening(BackroomsPortalEntity portal) {
        PortalFacing facing = portal.facingSource();
        Vec3d center = portal.getPos();
        Vec3d corner = center.add(facing.axisW.multiply(portal.width() / 2.0)).add(facing.axisH.multiply(portal.height() / 2.0));
        Vec3d opposite = center.subtract(facing.axisW.multiply(portal.width() / 2.0)).subtract(facing.axisH.multiply(portal.height() / 2.0))
                .subtract(facing.normal.multiply(CLOSED_DEPTH));
        return new Box(corner, opposite);
    }

    /**
     * L'entity a son point de traversee (les yeux pour un joueur ou un cadre couche, les pieds sinon) devant le plan d'un
     * portail ouvert, en face de l'ouverture, et sa boite deplacee touche le cadre. Une entity dont le cooldown de portail
     * court ne s'engage dans aucun cadre : elle ne pourrait pas le traverser, et passerait sous le plan d'un cadre au sol
     * (celui d'ou elle vient de sortir, retombee dedans aussitot) sans traverser.
     */
    static boolean engages(BackroomsPortalEntity portal, Entity entity, Box movingBox) {
        return portal.teleportable()
                && !entity.hasPortalCooldown()
                && portal.crossingPointFaces(entity, entity.getX(), entity.getY(), entity.getZ())
                && portal.detectionBox().intersects(movingBox);
    }

    /**
     * L'entity a son point de traversee devant le plan d'un portail ouvert, mais hors de son ouverture (sur un bord), et sa
     * boite deplacee touche le cadre : elle ne peut pas le traverser correctement, et le plan l'arrete.
     */
    static boolean walls(BackroomsPortalEntity portal, Entity entity, Box movingBox) {
        double y = entity.getY() + portal.crossingHeight(entity);
        return portal.teleportable()
                && portal.signedDistance(entity.getX(), y, entity.getZ()) > 0.0
                && !portal.withinOpening(entity, entity.getX(), y, entity.getZ())
                && portal.detectionBox().intersects(movingBox);
    }

    /** Blocs de l'autre cote que la boite deplacee atteindrait au-dela du plan d'arrivee, ramenes de ce cote. */
    private static void addOtherSide(BackroomsPortalEntity portal, World destination, Box movingBox, List<VoxelShape> shapes) {
        Vec3d exitNormal = portal.facingDestination().normal;
        Vec3d exitCenter = portal.destinationPos();
        for (VoxelShape shape : destination.getBlockCollisions(null, toDestination(portal, movingBox))) {
            for (Box box : shape.getBoundingBoxes()) {
                Box beyond = exitSide(box, exitNormal, exitCenter);
                if (beyond != null) {
                    shapes.add(VoxelShapes.cuboid(fromDestination(portal, beyond)));
                }
            }
        }
    }

    /** Image d'une boite de ce cote dans le monde d'arrivee : les orientations suivent les axes, l'image d'une boite en est une. */
    static Box toDestination(BackroomsPortalEntity portal, Box box) {
        return new Box(portal.toDestination(new Vec3d(box.minX, box.minY, box.minZ)), portal.toDestination(new Vec3d(box.maxX, box.maxY, box.maxZ)));
    }

    /** Image de ce cote d'une boite du monde d'arrivee. */
    static Box fromDestination(BackroomsPortalEntity portal, Box box) {
        return new Box(portal.fromDestination(new Vec3d(box.minX, box.minY, box.minZ)), portal.fromDestination(new Vec3d(box.maxX, box.maxY, box.maxZ)));
    }

    /**
     * Partie d'une boite situee du cote de sortie d'un plan aligne sur un axe.
     *
     * @param box boite a couper
     * @param normal normale du plan, vers la sortie
     * @param center point du plan
     * @return la partie du cote de sortie, ou {@code null} si la boite est entierement derriere le plan
     */
    static @Nullable Box exitSide(Box box, Vec3d normal, Vec3d center) {
        double minX = normal.x > 0.0 ? Math.max(box.minX, center.x) : box.minX;
        double minY = normal.y > 0.0 ? Math.max(box.minY, center.y) : box.minY;
        double minZ = normal.z > 0.0 ? Math.max(box.minZ, center.z) : box.minZ;
        double maxX = normal.x < 0.0 ? Math.min(box.maxX, center.x) : box.maxX;
        double maxY = normal.y < 0.0 ? Math.min(box.maxY, center.y) : box.maxY;
        double maxZ = normal.z < 0.0 ? Math.min(box.maxZ, center.z) : box.maxZ;
        return minX < maxX && minY < maxY && minZ < maxZ ? new Box(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }
}
