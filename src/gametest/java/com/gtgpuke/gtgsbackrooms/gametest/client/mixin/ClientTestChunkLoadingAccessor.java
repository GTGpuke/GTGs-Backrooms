package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;

/** Les chunks d'un monde du serveur integre, relus quand son arret ne finit pas, et son dechargement (tests du client). */
@Mixin(ServerChunkLoadingManager.class)
public interface ClientTestChunkLoadingAccessor {

    @Accessor("currentChunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> gtgsTest$getCurrentChunkHolders();

    @Accessor("chunksToUnload")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> gtgsTest$getChunksToUnload();

    @Invoker("tryUnloadChunk")
    void gtgsTest$tryUnloadChunk(long pos, ChunkHolder holder);
}
