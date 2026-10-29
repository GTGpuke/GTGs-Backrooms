package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.world.BlockView;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.CrossingRoll;

/**
 * Apres son orientation par vanilla, la camera du joueur prend ce qui reste du roulis d'une traversee
 * ({@link CrossingRoll}), puis, si l'option l'autorise, le tremblement d'un cri proche dans sa dimension ({@link CueTracker}).
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Inject(method = "update", at = @At("TAIL"))
    private void backrooms$crossingRoll(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta,
            CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (self != client.gameRenderer.getCamera()) {
            return;
        }
        if (CrossingRoll.active()) {
            CrossingRoll.apply(self, Util.getMeasuringTimeMs());
        }
        if (ModConfig.AUDIO_CAMERA_SHAKE && client.world != null) {
            CueTracker.shakeCamera(self, client.world, tickDelta, client.options.getDistortionEffectScale().getValue());
        }
    }
}
