package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.nio.IntBuffer;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sound.SoundEngine;

import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.audio.client.EfxBackend;
import com.gtgpuke.gtgsbackrooms.core.audio.client.NeonHum;

/**
 * Cycle de vie du contexte OpenAL : a sa creation, deux envois auxiliaires par source sont demandes (la reverb d'un son,
 * celle de la salle de l'auditeur), puis l'extension EFX et les envois obtenus sont relus et l'acoustique repart de
 * zero ; a sa fermeture (changement de peripherique, rechargement du moteur de son, F3+T), ses objets sont oublies, les
 * sons traites et les bourdonnements avec eux : le moteur arrete toutes ses sources avant de fermer son contexte.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Shadow
    private long devicePointer;

    @ModifyArg(method = "init", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J", remap = false),
            index = 1)
    private @Nullable IntBuffer backrooms$requestSends(@Nullable IntBuffer attributes) {
        return EfxBackend.contextAttributes(attributes);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void backrooms$engineStarted(String deviceSpecifier, boolean directionalAudio, CallbackInfo ci) {
        EfxBackend.onEngineStarted(this.devicePointer);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void backrooms$engineClosing(CallbackInfo ci) {
        EfxBackend.onEngineClosed();
        AcousticRouter.clear();
        CueTracker.clear();
        NeonHum.clear();
    }
}
