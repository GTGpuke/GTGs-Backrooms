package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundContainer;
import net.minecraft.client.sound.WeightedSoundSet;

/** Les variantes d'un jeu de sons ({@code sounds.json}) : le moteur audio en precalcule les enveloppes. */
@Mixin(WeightedSoundSet.class)
public interface WeightedSoundSetAccessor {

    @Accessor("sounds")
    List<SoundContainer<Sound>> backrooms$getSounds();
}
