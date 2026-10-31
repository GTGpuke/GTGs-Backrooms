package com.gtgpuke.gtgsbackrooms.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.client.BiomeColors;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookFog;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Brouillard du profil du monde rendu a la place de celui de vanilla, pour le brouillard du terrain hors de l'eau et sans
 * effet de cecite ({@code LookFog}) : ses distances, et sa couleur, posee en fin de calcul (vanilla la melerait au ciel
 * et l'assombrirait), fond de l'image compris. La couleur des biomes que vanilla echantillonne autour de la camera se
 * calcule sans un vecteur par point ({@code BiomeColors}), au bit pres la meme.
 */
@Mixin(BackgroundRenderer.class)
public abstract class MixinBackgroundRenderer {

    @Shadow
    private static float red;

    @Shadow
    private static float green;

    @Shadow
    private static float blue;

    @Inject(method = "render", at = @At("RETURN"))
    private static void backrooms$lookFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        Vec3d color = LookFog.color(camera, world);
        if (color != null) {
            red = (float) color.x;
            green = (float) color.y;
            blue = (float) color.z;
            RenderSystem.clearColor(red, green, blue, 0.0f);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;sampleColor(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/CubicSampler$RgbFetcher;)Lnet/minecraft/util/math/Vec3d;"))
    private static Vec3d backrooms$fogBiomeColor(Vec3d pos, CubicSampler.RgbFetcher fetcher, Operation<Vec3d> original,
            @Local(argsOnly = true) ClientWorld world, @Local(argsOnly = true, ordinal = 0) float tickDelta) {
        Vec3d color = BiomeColors.fogColor(world, pos, tickDelta);
        return color != null ? color : original.call(pos, fetcher);
    }

    @ModifyArg(method = "applyFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogStart(F)V"))
    private static float backrooms$lookFogStart(float start, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) BackgroundRenderer.FogType fogType, @Local(argsOnly = true) boolean thickFog) {
        LookProfile.Fog fog = LookFog.override(camera, fogType, thickFog);
        return fog == null ? start : fog.start();
    }

    @ModifyArg(method = "applyFog", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogEnd(F)V"))
    private static float backrooms$lookFogEnd(float end, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) BackgroundRenderer.FogType fogType, @Local(argsOnly = true) boolean thickFog) {
        LookProfile.Fog fog = LookFog.override(camera, fogType, thickFog);
        return fog == null ? end : fog.end();
    }
}
