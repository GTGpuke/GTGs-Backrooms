package com.gtgpuke.gtgsbackrooms.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;

import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.render.light.DimensionLightmap;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Chaque point de la lightmap passe par la courbe du profil du monde pour lequel elle se calcule : celui de
 * {@code MinecraftClient}, deja le monde d'une vue de portail quand sa lightmap se calcule ({@code ViewLightmaps}).
 * Sans profil, le point ressort tel quel.
 */
@Mixin(LightmapTextureManager.class)
public abstract class MixinLightmapTextureManager {

    @Shadow
    @Final
    private MinecraftClient client;

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"), index = 2)
    private int backrooms$lookCurve(int color) {
        ClientWorld world = this.client.world;
        if (world == null) {
            return color;
        }
        // La baisse du moment : un cri lie a l'image, dans cette dimension seulement (plafond de clignotement compris).
        return DimensionLightmap.apply(LookProfiles.of(world.getRegistryKey()).lightmap(), color, CueTracker.lightmapFlicker(world.getRegistryKey().getValue()));
    }

    // Le vacillement de torche de vanilla, tire a chaque tick : un monde a courbe n'en a pas, sans quoi ce que la lightmap eclaire au loin tremble.
    @ModifyExpressionValue(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/LightmapTextureManager;flickerIntensity:F"))
    private float backrooms$steadyBlockLight(float flicker) {
        ClientWorld world = this.client.world;
        return world == null ? flicker : DimensionLightmap.blockFlicker(LookProfiles.of(world.getRegistryKey()).lightmap(), flicker);
    }
}
