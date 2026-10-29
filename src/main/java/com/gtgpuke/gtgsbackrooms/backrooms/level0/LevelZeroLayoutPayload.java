package com.gtgpuke.gtgsbackrooms.backrooms.level0;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Serveur vers client : la graine du layout d'un Level 0 de ce serveur, envoyee a la connexion. Le client refait le
 * layout (sans la graine du monde, qu'il n'a pas) pour l'acoustique tiree de la generation ({@link LevelZeroRoomShape}).
 * La graine du layout est derivee de celle du monde par un hachage nomme : elle ne la revele pas.
 *
 * @param dimension dimension du Level 0
 * @param layoutSeed graine du layout de ce Level 0
 */
public record LevelZeroLayoutPayload(Identifier dimension, long layoutSeed) implements CustomPayload {

    public static final CustomPayload.Id<LevelZeroLayoutPayload> ID = new CustomPayload.Id<>(Identifier.of(ModConstants.MOD_ID, "level_zero_layout"));

    public static final PacketCodec<PacketByteBuf, LevelZeroLayoutPayload> CODEC =
            PacketCodec.of(LevelZeroLayoutPayload::write, LevelZeroLayoutPayload::read);

    private static LevelZeroLayoutPayload read(PacketByteBuf buf) {
        return new LevelZeroLayoutPayload(buf.readIdentifier(), buf.readLong());
    }

    private void write(PacketByteBuf buf) {
        buf.writeIdentifier(this.dimension);
        buf.writeLong(this.layoutSeed);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
