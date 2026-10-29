package com.gtgpuke.gtgsbackrooms.mixin.client;

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
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.client.LookFog;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Brouillard du profil du monde rendu a la place de celui de vanilla, pour le brouillard du terrain hors de l'eau et sans
 * effet de cecite ({@code LookFog}) : ses distances, et sa couleur, posee en fin de calcul (vanilla la melerait au ciel
 * et l'assombrirait), fond de l'image compris.
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
