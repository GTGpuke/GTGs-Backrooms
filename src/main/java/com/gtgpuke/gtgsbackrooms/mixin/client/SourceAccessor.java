package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.sound.Source;

/** Identifiant OpenAL d'une source du moteur de son : pour lui attacher la reverb et le filtre de sa dimension. */
@Mixin(Source.class)
public interface SourceAccessor {

    @Accessor("pointer")
    int backrooms$getPointer();
}
