package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Serveur vers client : annonce une dimension distante avant d'en envoyer les chunks.
 *
 * <p>Le client cree alors un monde local pour cette dimension avec le bon type
 * de dimension (hauteur, ciel, lumiere ambiante).
 *
 * @param dimension clef du monde distant : sa dimension, ou l'ailleurs du monde du joueur ({@link RemoteKeys})
 * @param dimensionType identifiant du type de dimension
 * @param index index de cette dimension chez le joueur, que les payloads frequents portent a la place de l'identifiant
 */
public record RemoteDimensionPayload(Identifier dimension, Identifier dimensionType, int index) implements CustomPayload {

    /** Nombre maximal de dimensions annoncees a un joueur dans une meme vue : bien plus que de dimensions dans un serveur ordinaire. */
    public static final int MAX_ANNOUNCED = 32;

    public static final CustomPayload.Id<RemoteDimensionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ModConstants.MOD_ID, "portal_remote_dimension"));

    public static final PacketCodec<ByteBuf, RemoteDimensionPayload> CODEC = PacketCodec.tuple(
            Identifier.PACKET_CODEC, RemoteDimensionPayload::dimension,
            Identifier.PACKET_CODEC, RemoteDimensionPayload::dimensionType,
            PacketCodecs.VAR_INT, RemoteDimensionPayload::index,
            RemoteDimensionPayload::new);

    /**
     * Annonce d'un monde serveur.
     *
     * @param key clef du monde distant annonce : la dimension du monde, ou son ailleurs ({@link RemoteKeys})
     * @param world monde annonce
     * @param index index de cette dimension chez le joueur, que les payloads frequents portent a la place de l'identifiant
     */
    public static RemoteDimensionPayload of(RegistryKey<World> key, ServerWorld world, int index) {
        return new RemoteDimensionPayload(key.getValue(), dimensionTypeId(world), index);
    }

    /** Identifiant du type de dimension d'un monde ; l'overworld si le type n'est pas enregistre sous une cle. */
    public static Identifier dimensionTypeId(ServerWorld world) {
        return world.getDimensionEntry().getKey().map(RegistryKey::getValue).orElse(DimensionTypes.OVERWORLD.getValue());
    }

    @Override
    public CustomPayload.Id<RemoteDimensionPayload> getId() {
        return ID;
    }
}
