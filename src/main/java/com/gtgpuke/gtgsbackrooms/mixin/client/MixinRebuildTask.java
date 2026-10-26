package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.SectionGenerations;

/**
 * Construction d'une section : retient, a sa creation, la generation de la section dont elle copie les blocs, et la note
 * construite des que ses donnees remplacent celles de la section ({@link SectionGenerations}). La creation et la copie des
 * blocs se font sur le thread de rendu, ou arrivent aussi les changements de blocs : aucun ne se glisse entre les deux.
 */
@Mixin(targets = "net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
public abstract class MixinRebuildTask {

    @Unique
    private ChunkBuilder.BuiltChunk backrooms$section;
    @Unique
    private int backrooms$generation;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void backrooms$snapshot(ChunkBuilder.BuiltChunk section, double distance, ChunkRendererRegion region, boolean important,
            CallbackInfo ci) {
        this.backrooms$section = section;
        this.backrooms$generation = ((SectionGenerations) section).backrooms$requested();
    }

    @Inject(
            method = {"run", "method_23619"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;method_60908(Lnet/minecraft/client/render/chunk/ChunkBuilder$ChunkData;)V",
                    shift = At.Shift.AFTER))
    private void backrooms$built(CallbackInfoReturnable<?> cir) {
        ((SectionGenerations) this.backrooms$section).backrooms$built(this.backrooms$generation);
    }
}
