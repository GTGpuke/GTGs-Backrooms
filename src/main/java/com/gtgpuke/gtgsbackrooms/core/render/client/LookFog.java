package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.light.DimensionFog;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Brouillard du profil du monde rendu, quand il remplace celui de vanilla ({@link DimensionFog}) : ses distances, et sa
 * couleur telle que le profil la donne. Vanilla la melerait au ciel selon la distance de rendu du joueur et
 * l'assombrirait pres du bas du monde : un ciel noir (heure fixee a minuit) faisait tomber le brouillard du Level 0 a
 * 0,81 fois sa couleur a huit chunks, et presque au noir dans une vue dont la camera est loin derriere l'arrivee.
 */
public final class LookFog {

    private LookFog() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Brouillard a appliquer a la place de celui que vanilla vient de calculer, ou {@code null} pour garder celui-ci. Le
     * monde est celui de {@code MinecraftClient}, deja celui de la vue pendant le dessin d'un portail.
     *
     * @param camera camera du rendu en cours
     * @param type brouillard du terrain ou du ciel
     * @param thick {@code true} si vanilla impose un brouillard epais
     */
    public static LookProfile.@Nullable Fog override(Camera camera, BackgroundRenderer.FogType type, boolean thick) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        LookProfile.Fog fog = LookProfiles.of(world.getRegistryKey()).fog();
        return applies(fog, camera, type == BackgroundRenderer.FogType.FOG_TERRAIN, thick) ? fog : null;
    }

    /**
     * Couleur du brouillard (et du fond de l'image) de ce monde a la place de celle que vanilla vient de calculer, ou
     * {@code null} pour garder celle-ci : sans brouillard au profil, dans l'eau, la lave ou la neige poudreuse, sous
     * cecite ou obscurite, comme pour les distances (le brouillard epais, que les effets de dimension des looks ne
     * demandent jamais, ne compte que pour les distances).
     *
     * @param camera camera du rendu en cours
     * @param world monde rendu (celui de la vue pendant le dessin d'un portail)
     */
    public static @Nullable Vec3d color(Camera camera, ClientWorld world) {
        LookProfile.Fog fog = LookProfiles.of(world.getRegistryKey()).fog();
        return applies(fog, camera, true, false) ? fog.color() : null;
    }

    private static boolean applies(LookProfile.Fog fog, Camera camera, boolean terrain, boolean thick) {
        if (!fog.enabled()) {
            return false;
        }
        Entity focused = camera.getFocusedEntity();
        boolean impaired = focused instanceof LivingEntity living
                && (living.hasStatusEffect(StatusEffects.BLINDNESS) || living.hasStatusEffect(StatusEffects.DARKNESS));
        boolean submerged = camera.getSubmersionType() != CameraSubmersionType.NONE;
        return DimensionFog.applies(fog, terrain, submerged, impaired, thick);
    }
}
