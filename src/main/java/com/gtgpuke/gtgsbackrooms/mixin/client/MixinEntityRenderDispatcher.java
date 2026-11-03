package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldView;

import com.gtgpuke.gtgsbackrooms.core.render.client.EntityShadows;

/**
 * Le disque d'ombre de vanilla sous une entite : la ou les ombres du mod la remplacent (ombre de contact a tous les paliers a lumiere
 * cuite, ombres portees vers chaque dalle), il ne se dessine plus, sans quoi un cercle sombre se poserait sous ses pieds quelles que
 * soient les dalles autour.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher {

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void backrooms$replaceShadow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Entity entity, float opacity, float tickDelta,
            WorldView world, float radius, CallbackInfo ci) {
        if (EntityShadows.replacesVanilla(entity)) {
            ci.cancel();
        }
    }
}
