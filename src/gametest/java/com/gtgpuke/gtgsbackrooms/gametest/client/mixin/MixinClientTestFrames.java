package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;

import com.gtgpuke.gtgsbackrooms.gametest.client.ClientTestFrames;

/** Fin de chaque image du client de test : compte des images et capture demandee par un test. */
@Mixin(MinecraftClient.class)
public abstract class MixinClientTestFrames {

    @Inject(method = "render", at = @At("TAIL"))
    private void gtgsTest$frameEnd(boolean tick, CallbackInfo ci) {
        ClientTestFrames.onFrameEnd((MinecraftClient) (Object) this);
    }
}
