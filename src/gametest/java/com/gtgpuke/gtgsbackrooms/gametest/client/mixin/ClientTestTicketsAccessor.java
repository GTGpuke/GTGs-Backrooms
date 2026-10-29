package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.collection.SortedArraySet;

/** Les tickets d'un monde du serveur integre, relus quand son arret ne finit pas (diagnostic des tests du client). */
@Mixin(ChunkTicketManager.class)
public interface ClientTestTicketsAccessor {

    @Accessor("ticketsByPosition")
    Long2ObjectOpenHashMap<SortedArraySet<ChunkTicket<?>>> gtgsTest$getTicketsByPosition();
}
