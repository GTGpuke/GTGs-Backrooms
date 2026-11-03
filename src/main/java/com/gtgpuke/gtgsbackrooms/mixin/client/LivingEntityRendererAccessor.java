package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

/** Ce que le rendu d'une creature fait avant de dessiner son modele : l'ombre d'un corps le pose de la meme facon. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @Invoker("getHandSwingProgress")
    float backrooms$getHandSwingProgress(LivingEntity entity, float tickDelta);

    @Invoker("getAnimationProgress")
    float backrooms$getAnimationProgress(LivingEntity entity, float tickDelta);

    @Invoker("setupTransforms")
    void backrooms$setupTransforms(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, float scale);

    @Invoker("scale")
    void backrooms$scale(LivingEntity entity, MatrixStack matrices, float amount);
}
