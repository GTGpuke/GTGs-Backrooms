package com.gtgpuke.gtgsbackrooms.backrooms.level0.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroLayoutPayload;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroRoomShape;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;

/**
 * L'acoustique du Level 0 tiree de sa generation, cote client : a la graine du layout recue du serveur, la forme des
 * salles de ce Level 0 ({@link LevelZeroRoomShape}) est donnee au moteur audio pour sa dimension.
 */
public final class LevelZeroAcoustics {

    private LevelZeroAcoustics() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LevelZeroLayoutPayload.ID,
                (payload, context) -> GeneratedAcoustics.register(payload.dimension(), new LevelZeroRoomShape(payload.layoutSeed())));
    }
}
