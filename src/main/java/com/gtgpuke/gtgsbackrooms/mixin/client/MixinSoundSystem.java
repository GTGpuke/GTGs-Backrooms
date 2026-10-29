package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;

import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;

/**
 * Chaque son que le moteur se met a jouer garde sa vraie source ({@link SoundAnchors}) : replace apres une traversee de
 * portail ; puis il recoit l'acoustique de la dimension ou il est emis ({@link AcousticRouter}). En fin de methode
 * seulement : un son non joue (moteur arrete, trop loin, inconnu) n'a pas de source, et avant la resolution de son jeu
 * de sons ({@code getSoundSet}) son volume n'est pas lisible.
 */
@Mixin(SoundSystem.class)
public abstract class MixinSoundSystem {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void backrooms$anchor(SoundInstance sound, CallbackInfo ci) {
        SoundAnchors.onPlay(sound);
        AcousticRouter.onPlay(sound);
    }
}
