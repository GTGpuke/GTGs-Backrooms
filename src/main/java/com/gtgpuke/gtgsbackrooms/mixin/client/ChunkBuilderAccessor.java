package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.render.chunk.BlockBufferBuilderPool;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.thread.TaskExecutor;

/**
 * Reserve de tampons de construction d'un chunk builder et sa file : la reserve ne se touche que dans la file, sur les
 * fils de travail, la ou les taches en cours rendent leurs tampons.
 */
@Mixin(ChunkBuilder.class)
public interface ChunkBuilderAccessor {

    /** File du chunk builder, ou passent ses taches et le retour de leurs tampons. */
    @Accessor("mailbox")
    TaskExecutor<Runnable> backrooms$getMailbox();

    /** Reserve de tampons de construction, celle des tampons du renderer d'ou il a ete cree. */
    @Accessor("buffersPool")
    BlockBufferBuilderPool backrooms$getBuffersPool();

    /** Lance une tache en attente si la reserve a un tampon libre ; dans la file seulement. */
    @Invoker("scheduleRunTasks")
    void backrooms$scheduleRunTasks();
}
