package com.gtgpuke.gtgsbackrooms.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;

/**
 * Acces au suivi vanilla des entities, pour y brancher les joueurs qui regardent une dimension a travers un portail, et
 * aux holders des chunks, dont les changements en attente partent avant qu'un joueur quitte leur monde.
 */
@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {

    /** Suivis par identifiant d'entity ; les valeurs implementent {@link EntityTrackerAccessor}. */
    @Accessor("entityTrackers")
    Int2ObjectMap<?> backrooms$getEntityTrackers();

    /** Holder d'un chunk, ou {@code null} s'il n'en a pas. */
    @Invoker("getChunkHolder")
    @Nullable ChunkHolder backrooms$getChunkHolder(long pos);
}
