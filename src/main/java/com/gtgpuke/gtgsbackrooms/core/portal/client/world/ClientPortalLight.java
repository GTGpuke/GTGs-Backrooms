package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.light.PortalLightTransmission;

/**
 * Lumiere transmise par les portails, cote client : le moteur de lumiere du client recalcule seul les blocs qui changent,
 * dans son monde comme dans les mondes vus par les portails. Le meme calcul que le serveur tourne donc pour chacun, avec
 * les portails et les lumieres que le client connait.
 */
public final class ClientPortalLight {

    /** Section propre au client : en solo, la passe du serveur se compte a part. */
    private static final String PERF_LIGHT = "client.portal.light";
    private static final PortalLightTransmission TRANSMISSION = new PortalLightTransmission(PERF_LIGHT, (source, portal) -> RemoteWorldRegistry.worldOf(portal));
    private static int ticks;

    private ClientPortalLight() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Tick client, un monde principal charge et les portails actives : une passe tous les
     * {@link PortalLightTransmission#UPDATE_INTERVAL_TICKS} ticks.
     */
    public static void onClientTick(MinecraftClient client) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE || ++ticks % PortalLightTransmission.UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        ClientWorld main = client.world;
        // La lumiere passe par une sortie fugace que le joueur ne regarde pas : elle existe pour le serveur.
        TRANSMISSION.update(main, ClientPortalIndex.allPortals(main));
        for (int i = 0; i < RemoteWorldRegistry.remoteWorldCount(); i++) {
            ClientWorld remote = RemoteWorldRegistry.remoteWorld(i);
            TRANSMISSION.update(remote, ClientPortalIndex.allPortals(remote));
        }
    }
}
