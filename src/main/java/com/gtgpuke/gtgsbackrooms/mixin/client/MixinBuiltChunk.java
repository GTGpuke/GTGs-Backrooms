package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.util.concurrent.atomic.AtomicReference;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.chunk.ChunkBuilder;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.SectionGenerations;

/**
 * Generations d'une section de terrain ({@link SectionGenerations}) : une par demande de reconstruction. La demande vient
 * du thread de rendu (bloc change, lumiere, voisin) ; une construction peut finir sur un thread de travail, d'ou la
 * generation construite volatile.
 */
@Mixin(ChunkBuilder.BuiltChunk.class)
public abstract class MixinBuiltChunk implements SectionGenerations {

    @Shadow
    @Final
    public AtomicReference<ChunkBuilder.ChunkData> data;

    @Unique
    private volatile int backrooms$requested;
    @Unique
    private volatile int backrooms$built;

    @Inject(method = "scheduleRebuild(Z)V", at = @At("HEAD"))
    private void backrooms$request(boolean important, CallbackInfo ci) {
        this.backrooms$requested++;
    }

    @Override
    public int backrooms$requested() {
        return this.backrooms$requested;
    }

    @Override
    public void backrooms$built(int generation) {
        this.backrooms$built = generation;
    }

    @Override
    public boolean backrooms$current() {
        // La generation construite d'abord : notee apres les donnees, elle ne precede jamais les donnees qu'elle date.
        return this.backrooms$built == this.backrooms$requested && this.data.get() != ChunkBuilder.ChunkData.EMPTY;
    }
}
