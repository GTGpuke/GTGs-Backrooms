package com.gtgpuke.gtgsbackrooms.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;

/**
 * Le pipeline courant du gestionnaire d'Iris, et le compteur qui fait recharger au mod de terrain ses shaders de
 * sections quand le pipeline change.
 */
@Mixin(value = PipelineManager.class, remap = false)
public interface PipelineManagerAccessor {

    @Accessor("pipeline")
    void backrooms$setPipeline(WorldRenderingPipeline pipeline);

    @Accessor("versionCounterForSodiumShaderReload")
    int backrooms$getShaderReloadCounter();

    @Accessor("versionCounterForSodiumShaderReload")
    void backrooms$setShaderReloadCounter(int counter);
}
