package com.gtgpuke.gtgsbackrooms.compat.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtgpuke.gtgsbackrooms.compat.iris.IrisCompat;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

/**
 * Le pipeline qu'Iris prepare au debut de chaque rendu du monde : dans une dimension a look, celui que choisit
 * {@link IrisCompat} (son pipeline vanilla, que les shaders patches du mod dessinent). Quand Iris detruit ses pipelines
 * (pack change, recharge, shaders coupes), celui du mod part avec eux.
 */
@Mixin(value = PipelineManager.class, remap = false)
public abstract class MixinPipelineManager {

    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true)
    private void backrooms$replaceInBackrooms(NamespacedId dimension, CallbackInfoReturnable<WorldRenderingPipeline> cir) {
        WorldRenderingPipeline replaced = IrisCompat.override((PipelineManager) (Object) this);
        if (replaced != null) {
            cir.setReturnValue(replaced);
        }
    }

    @Inject(method = "destroyPipeline", at = @At("HEAD"))
    private void backrooms$forgetReplacement(CallbackInfo ci) {
        IrisCompat.onPipelinesDestroyed();
    }
}
