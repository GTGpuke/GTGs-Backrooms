package com.gtgpuke.gtgsbackrooms.backrooms.level0.client;

import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookDimensionEffects;

/**
 * Effets de dimension du Level 0 : ceux d'un monde a look ({@code LookDimensionEffects}), pilotes par son profil, sous
 * l'identifiant que son type de dimension declare.
 */
public final class LevelZeroEffects {

    private LevelZeroEffects() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client. */
    public static void register() {
        DimensionRenderingRegistry.registerDimensionEffects(BackroomsConstants.LEVEL_ZERO_EFFECTS,
                new LookDimensionEffects(BackroomsConstants.LEVEL_ZERO_WORLD.getValue()));
    }
}
