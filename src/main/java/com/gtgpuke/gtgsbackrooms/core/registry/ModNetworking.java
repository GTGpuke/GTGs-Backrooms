package com.gtgpuke.gtgsbackrooms.core.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalBlockActionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalCrossPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalEntityActionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalNetwork;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalSightPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalTeleportPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalWandSettingsPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockBreakingPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockUpdatePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkUnloadPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionReleasePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteEntityPacketPayload;

/**
 * Enregistrement des payloads reseau du mod (cote commun) : synchronisation, traversee et interaction a travers les portails,
 * reglages de la baguette.
 */
public final class ModNetworking {

    private ModNetworking() {
        throw new UnsupportedOperationException("Classe de registre.");
    }

    /** Enregistre les types de payloads ; a appeler une fois depuis le point d'entree commun. */
    public static void register() {
        // Les payloads des portails passent par PortalNetwork, qui garde leur codec pour mesurer ce qui part.
        PortalNetwork.registerS2C(RemoteDimensionPayload.ID, RemoteDimensionPayload.CODEC);
        PortalNetwork.registerS2C(RemoteChunkPayload.ID, RemoteChunkPayload.CODEC);
        PortalNetwork.registerS2C(RemoteChunkUnloadPayload.ID, RemoteChunkUnloadPayload.CODEC);
        PortalNetwork.registerS2C(RemoteDimensionReleasePayload.ID, RemoteDimensionReleasePayload.CODEC);
        PortalNetwork.registerS2C(RemoteBlockUpdatePayload.ID, RemoteBlockUpdatePayload.CODEC);
        PortalNetwork.registerS2C(RemoteEntityPacketPayload.ID, RemoteEntityPacketPayload.CODEC);
        PortalNetwork.registerS2C(RemoteBlockBreakingPayload.ID, RemoteBlockBreakingPayload.CODEC);
        PortalNetwork.registerS2C(PortalTeleportPayload.ID, PortalTeleportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PortalCrossPayload.ID, PortalCrossPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PortalEntityActionPayload.ID, PortalEntityActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PortalBlockActionPayload.ID, PortalBlockActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PortalSightPayload.ID, PortalSightPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PortalWandSettingsPayload.ID, PortalWandSettingsPayload.CODEC);
        ModConstants.LOGGER.debug("Payloads reseau enregistres.");
    }
}
