package com.gtgpuke.gtgsbackrooms.backrooms.level0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

@DisplayName("Graine du layout du Level 0 envoyee au client")
class LevelZeroLayoutPayloadTest {

    @Test
    @DisplayName("La graine et sa dimension passent le reseau a l'identique")
    void roundTrip() {
        LevelZeroLayoutPayload payload = new LevelZeroLayoutPayload(Identifier.of("gtgs_backrooms", "backrooms_level_0"), -1234567890123L);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        LevelZeroLayoutPayload.CODEC.encode(buf, payload);
        assertEquals(payload, LevelZeroLayoutPayload.CODEC.decode(buf), "Relu a l'identique.");
        assertEquals(LevelZeroLayoutPayload.ID, payload.getId(), "Son identifiant.");
    }
}
