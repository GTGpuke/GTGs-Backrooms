package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;

/**
 * Decide si un portail compte pour un observateur, selon deux criteres.
 *
 * <p>A portee ({@link #isInRange}), quelle que soit la direction du regard : le serveur precharge l'arrivee et
 * synchronise la fenetre de tout portail a portee de rendu, meme dans le dos, pour que le regard qui revient trouve
 * l'autre cote au lieu du brouillard (decision de l'auteur du 25 octobre 2026, {@code docs/DECISIONS.md}).
 * A portee et devant ({@link #isRelevant}) : le rendu, ou un portail dans le dos ne coute rien ; a portee
 * d'activation, la direction du regard ne compte pas non plus, on peut s'engager dans un portail a reculons ou de cote.
 */
public final class PortalRelevance {

    /** Distance en dessous de laquelle un portail compte quelle que soit la direction du regard : la distance d'activation. */
    public static final double ALWAYS_RELEVANT_DISTANCE = ModConfig.PORTAL_ACTIVATION_RADIUS_BLOCKS;
    /** Distance au-dela de laquelle aucun portail ne compte cote serveur : la plus grande de l'activation et du rendu. */
    public static final double MAX_DISTANCE = Math.max(ModConfig.PORTAL_ACTIVATION_RADIUS_BLOCKS, ModConfig.PORTAL_RENDER_DISTANCE_BLOCKS);

    private PortalRelevance() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} si le portail est a portee et devant l'observateur.
     *
     * @param eyePosition position des yeux de l'observateur
     * @param lookVector direction du regard, non nulle
     * @param portalPosition centre du portail
     * @param maxDistance distance maximale en blocs
     * @return {@code true} si le portail doit etre pris en compte
     */
    static boolean isRelevant(Vec3d eyePosition, Vec3d lookVector, Vec3d portalPosition, double maxDistance) {
        return isRelevant(eyePosition.x, eyePosition.y, eyePosition.z, lookVector.x, lookVector.y, lookVector.z, portalPosition, maxDistance);
    }

    /**
     * Meme critere pour un regard donne en angles (degres, convention Minecraft), sans vecteur de regard : le rendu le pose
     * a chaque image pour chaque portail.
     *
     * @param eyePosition position des yeux de l'observateur
     * @param pitch tangage du regard, en degres
     * @param yaw lacet du regard, en degres
     * @param portalPosition centre du portail
     * @param maxDistance distance maximale en blocs
     * @return {@code true} si le portail doit etre pris en compte
     */
    public static boolean isRelevant(Vec3d eyePosition, float pitch, float yaw, Vec3d portalPosition, double maxDistance) {
        return isRelevantFrom(eyePosition.x, eyePosition.y, eyePosition.z, pitch, yaw, portalPosition, maxDistance);
    }

    /**
     * {@code true} si le portail est a portee, quelle que soit la direction du regard.
     *
     * @param eyePosition position des yeux de l'observateur
     * @param portalPosition centre du portail
     * @param maxDistance distance maximale en blocs
     * @return {@code true} si le portail doit etre pris en compte
     */
    static boolean isInRange(Vec3d eyePosition, Vec3d portalPosition, double maxDistance) {
        return isInRange(eyePosition.x, eyePosition.y, eyePosition.z, portalPosition, maxDistance);
    }

    /**
     * Meme critere pour une entity et ses yeux du tick, sans le vecteur que vanilla creerait ({@code getEyePos}) : le
     * serveur le pose pour chaque joueur et chaque portail.
     *
     * @param observer entity qui observe
     * @param portalPosition centre du portail
     * @param maxDistance distance maximale en blocs
     * @return {@code true} si le portail doit etre pris en compte
     */
    public static boolean isInRange(Entity observer, Vec3d portalPosition, double maxDistance) {
        return isInRange(observer.getX(), observer.getEyeY(), observer.getZ(), portalPosition, maxDistance);
    }

    private static boolean isInRange(double eyeX, double eyeY, double eyeZ, Vec3d portalPosition, double maxDistance) {
        double dx = portalPosition.x - eyeX;
        double dy = portalPosition.y - eyeY;
        double dz = portalPosition.z - eyeZ;
        return dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance;
    }

    private static boolean isRelevantFrom(double eyeX, double eyeY, double eyeZ, float pitch, float yaw, Vec3d portalPosition, double maxDistance) {
        // Le regard comme Entity.getRotationVector, avec les memes tables de sinus.
        float pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
        float yawRad = -yaw * MathHelper.RADIANS_PER_DEGREE;
        float cosPitch = MathHelper.cos(pitchRad);
        return isRelevant(eyeX, eyeY, eyeZ, MathHelper.sin(yawRad) * cosPitch, -MathHelper.sin(pitchRad), MathHelper.cos(yawRad) * cosPitch,
                portalPosition, maxDistance);
    }

    private static boolean isRelevant(
            double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ, Vec3d portalPosition, double maxDistance) {
        // Appele a chaque image et pour chaque joueur : composantes a la main, sans vecteur intermediaire.
        double dx = portalPosition.x - eyeX;
        double dy = portalPosition.y - eyeY;
        double dz = portalPosition.z - eyeZ;
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared > maxDistance * maxDistance) {
            return false;
        }
        if (distanceSquared < ALWAYS_RELEVANT_DISTANCE * ALWAYS_RELEVANT_DISTANCE) {
            return true;
        }
        return lookX * dx + lookY * dy + lookZ * dz > 0.0;
    }
}
