package com.gtgpuke.gtgsbackrooms.backrooms;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Constantes partagees de la feature Backrooms.
 */
public final class BackroomsConstants {

    /** Identifiant (path) de la dimension du Level 0. */
    public static final String LEVEL_ZERO_DIMENSION = "backrooms_level_0";

    /** Cle de registre de la dimension du Level 0. */
    public static final RegistryKey<World> LEVEL_ZERO_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ModConstants.MOD_ID, LEVEL_ZERO_DIMENSION));

    /** Effets de dimension du Level 0 (ciel, nuages, brouillard), declares par son type de dimension. */
    public static final Identifier LEVEL_ZERO_EFFECTS = Identifier.of(ModConstants.MOD_ID, LEVEL_ZERO_DIMENSION);

    /** Identifiant (path) du generateur de chunk du Level 0. */
    public static final String LEVEL_ZERO_CHUNK_GENERATOR = "backrooms_level_zero";

    /** Cle du random principal du layout, derivee du seed monde par Minecraft. */
    public static final Identifier LEVEL_ZERO_LAYOUT_RANDOM =
            Identifier.of(ModConstants.MOD_ID, "backrooms_level_zero_layout");

    /** Taille en blocs d'une cellule logique du labyrinthe. */
    public static final int LEVEL_ZERO_CELL_SCALE = 3;

    private BackroomsConstants() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }
}
