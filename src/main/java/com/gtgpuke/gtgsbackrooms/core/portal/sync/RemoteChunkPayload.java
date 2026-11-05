package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Serveur vers client : un chunk complet d'une dimension distante, blocs et lumiere compris.
 *
 * <p>Le contenu reutilise le format vanilla ({@link ChunkData}, {@link LightData}) : le client le
 * charge dans son monde distant exactement comme il chargerait un chunk de son propre monde.
 *
 * @param dimension clef du monde distant : sa dimension, ou l'ailleurs du monde du joueur ({@link RemoteKeys})
 * @param chunkX coordonnee X du chunk
 * @param chunkZ coordonnee Z du chunk
 * @param chunkData sections, heightmaps et block entities
 * @param lightData lumiere du ciel et des blocs
 */
public record RemoteChunkPayload(Identifier dimension, int chunkX, int chunkZ, ChunkData chunkData, LightData lightData)
        implements CustomPayload {

    public static final CustomPayload.Id<RemoteChunkPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ModConstants.MOD_ID, "portal_remote_chunk"));

    public static final PacketCodec<RegistryByteBuf, RemoteChunkPayload> CODEC =
            PacketCodec.of(RemoteChunkPayload::write, RemoteChunkPayload::read);

    /**
     * Payload d'un chunk serveur charge, avec toute sa lumiere.
     *
     * @param key clef du monde distant qui le recoit : la dimension du chunk, ou son ailleurs ({@link RemoteKeys})
     * @param world monde du chunk
     * @param chunk chunk complet
     * @return payload pret a envoyer
     */
    public static RemoteChunkPayload of(RegistryKey<World> key, ServerWorld world, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        return new RemoteChunkPayload(
                key.getValue(),
                pos.x,
                pos.z,
                new ChunkData(chunk),
                new LightData(pos, world.getLightingProvider(), null, null));
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(this.dimension);
        buf.writeInt(this.chunkX);
        buf.writeInt(this.chunkZ);
        this.chunkData.write(buf);
        this.lightData.write(buf);
    }

    private static RemoteChunkPayload read(RegistryByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        int chunkX = buf.readInt();
        int chunkZ = buf.readInt();
        return new RemoteChunkPayload(dimension, chunkX, chunkZ, new ChunkData(buf, chunkX, chunkZ), new LightData(buf, chunkX, chunkZ));
    }

    @Override
    public CustomPayload.Id<RemoteChunkPayload> getId() {
        return ID;
    }
}
