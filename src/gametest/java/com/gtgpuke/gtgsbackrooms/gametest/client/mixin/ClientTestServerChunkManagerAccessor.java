package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerChunkManager;

/** Le gestionnaire de tickets d'un monde du serveur integre (diagnostic des tests du client). */
@Mixin(ServerChunkManager.class)
public interface ClientTestServerChunkManagerAccessor {

    @Accessor("ticketManager")
    ChunkTicketManager gtgsTest$getTicketManager();
}
