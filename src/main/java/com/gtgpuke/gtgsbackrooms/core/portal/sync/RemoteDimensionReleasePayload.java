package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Serveur vers client : plus aucun portail a portee de ce joueur ne montre cette dimension depuis un moment, le client
 * peut jeter son monde distant (chunks, entites, renderer). La dimension garde son index ; le serveur l'annoncera de
 * nouveau avant d'en renvoyer quoi que ce soit.
 *
 * @param dimension index de la dimension distante chez le joueur, celui de son annonce
 */
public record RemoteDimensionReleasePayload(int dimension) implements CustomPayload {

    public static final CustomPayload.Id<RemoteDimensionReleasePayload> ID =
            new CustomPayload.Id<>(Identifier.of(ModConstants.MOD_ID, "portal_remote_dimension_release"));

    public static final PacketCodec<ByteBuf, RemoteDimensionReleasePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, RemoteDimensionReleasePayload::dimension,
            RemoteDimensionReleasePayload::new);

    @Override
    public CustomPayload.Id<RemoteDimensionReleasePayload> getId() {
        return ID;
    }
}
