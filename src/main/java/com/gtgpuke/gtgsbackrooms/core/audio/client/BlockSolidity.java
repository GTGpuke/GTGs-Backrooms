package com.gtgpuke.gtgsbackrooms.core.audio.client;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace;

/**
 * Ce qu'un bloc fait au son : rien (air, ou sans forme de collision : eau, herbe), un bloc partiel (dalle, escalier,
 * vitre, grille) ou un bloc plein et opaque. La meme regle pour le rayon lance sur le monde vivant et pour la copie
 * du monde ({@link SolidityIndex}).
 */
public final class BlockSolidity {

    private BlockSolidity() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Solidite d'un bloc : {@link OcclusionTrace#AIR}, {@link OcclusionTrace#PARTIAL} ou {@link OcclusionTrace#FULL}. */
    public static int of(BlockState state, BlockView world, BlockPos pos) {
        if (state.isAir()) {
            return OcclusionTrace.AIR;
        }
        if (state.isOpaqueFullCube(world, pos)) {
            return OcclusionTrace.FULL;
        }
        return state.getCollisionShape(world, pos).isEmpty() ? OcclusionTrace.AIR : OcclusionTrace.PARTIAL;
    }
}
