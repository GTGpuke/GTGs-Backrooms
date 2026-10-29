package com.gtgpuke.gtgsbackrooms.compat.iris;

import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.irisshaders.iris.shaderpack.materialmap.BlockRenderType;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;

/**
 * Les reglages globaux qu'un pipeline d'Iris pose a sa creation (format des maillages du terrain, ombrage, occlusion
 * ambiante, identifiants des blocs, entites et objets pour le pack) : un pipeline repris du cache ne les repose pas.
 * Gardes a chaque bascule entre le pipeline du pack et le pipeline vanilla, et reposes au retour.
 *
 * @param blockStateIds identifiants des etats de blocs
 * @param blockTypeIds types de rendu des blocs
 * @param entityIds identifiants des entites
 * @param itemIds identifiants des objets
 * @param ambientOcclusion niveau d'occlusion ambiante
 * @param noDirectionalShading ombrage directionnel coupe
 * @param separateAo occlusion ambiante separee
 * @param separateEntityDraws dessins d'entites separes
 * @param voxelizeLightBlocks blocs lumineux voxelises
 * @param vertexFormat format des maillages du terrain
 */
record PipelineSettings(
        Object2IntMap<BlockState> blockStateIds,
        Map<Block, BlockRenderType> blockTypeIds,
        Object2IntFunction<NamespacedId> entityIds,
        Object2IntFunction<NamespacedId> itemIds,
        float ambientOcclusion,
        boolean noDirectionalShading,
        boolean separateAo,
        boolean separateEntityDraws,
        boolean voxelizeLightBlocks,
        ChunkVertexType vertexFormat) {

    /** Les reglages poses en ce moment. */
    static PipelineSettings capture() {
        WorldRenderingSettings settings = WorldRenderingSettings.INSTANCE;
        return new PipelineSettings(settings.getBlockStateIds(), settings.getBlockTypeIds(), settings.getEntityIds(), settings.getItemIds(),
                settings.getAmbientOcclusionLevel(), settings.shouldDisableDirectionalShading(), settings.shouldUseSeparateAo(),
                settings.shouldSeparateEntityDraws(), settings.shouldVoxelizeLightBlocks(), settings.getVertexFormat());
    }

    /** Repose ces reglages. */
    void apply() {
        WorldRenderingSettings settings = WorldRenderingSettings.INSTANCE;
        settings.setBlockStateIds(this.blockStateIds);
        settings.setBlockTypeIds(this.blockTypeIds);
        settings.setEntityIds(this.entityIds);
        settings.setItemIds(this.itemIds);
        settings.setAmbientOcclusionLevel(this.ambientOcclusion);
        settings.setDisableDirectionalShading(this.noDirectionalShading);
        settings.setUseSeparateAo(this.separateAo);
        settings.setSeparateEntityDraws(this.separateEntityDraws);
        settings.setVoxelizeLightBlocks(this.voxelizeLightBlocks);
        settings.setVertexFormat(this.vertexFormat);
    }
}
