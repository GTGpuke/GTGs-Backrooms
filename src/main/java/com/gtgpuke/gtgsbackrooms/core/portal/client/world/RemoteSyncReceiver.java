package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.EntityHandover;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockBreakingPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockUpdatePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkUnloadPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteDimensionReleasePayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteEntityPacketPayload;

/**
 * Recoit les payloads de synchronisation des portails (dimensions, chunks, blocs, paquets d'entities, fissures de minage)
 * et les applique aux mondes distants.
 */
public final class RemoteSyncReceiver {

    private RemoteSyncReceiver() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Enregistre les recepteurs ; a appeler une fois depuis le point d'entree client. Fabric les appelle deja sur le
     * thread de rendu, dans l'ordre des paquets : les differer avec {@code client.execute} les ferait passer apres les
     * paquets vanilla deja en file (un chunk du nouveau monde applique a l'ancien, par exemple).
     */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RemoteDimensionPayload.ID,
                (payload, context) -> onDimension(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteChunkPayload.ID,
                (payload, context) -> onChunk(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteChunkUnloadPayload.ID,
                (payload, context) -> onChunkUnload(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteDimensionReleasePayload.ID,
                (payload, context) -> onDimensionRelease(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteBlockUpdatePayload.ID,
                (payload, context) -> onBlockUpdate(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteEntityPacketPayload.ID,
                (payload, context) -> onEntityPacket(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(RemoteBlockBreakingPayload.ID,
                (payload, context) -> onBlockBreaking(context.client(), payload));
    }

    private static void onDimension(MinecraftClient client, RemoteDimensionPayload payload) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE) {
            return;
        }
        RemoteWorldRegistry.syncWithMainWorld(client.world);
        RemoteWorldRegistry.announce(worldKey(payload.dimension()), payload.dimensionType(), payload.index());
    }

    private static void onChunk(MinecraftClient client, RemoteChunkPayload payload) {
        ModPerformanceMonitor.onChunkReceived();
        RemoteWorld remote = remoteWorld(client, payload.dimension());
        if (remote != null) {
            remote.loadChunk(payload.chunkX(), payload.chunkZ(), payload.chunkData(), payload.lightData());
        }
    }

    private static void onDimensionRelease(MinecraftClient client, RemoteDimensionReleasePayload payload) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE) {
            return;
        }
        RemoteWorldRegistry.syncWithMainWorld(client.world);
        RemoteWorldRegistry.release(RemoteWorldRegistry.dimensionAt(payload.dimension()));
    }

    private static void onChunkUnload(MinecraftClient client, RemoteChunkUnloadPayload payload) {
        RemoteWorld remote = remoteWorld(client, RemoteWorldRegistry.dimensionAt(payload.dimension()));
        if (remote != null) {
            remote.unloadChunk(payload.chunkPos());
        }
    }

    private static void onBlockUpdate(MinecraftClient client, RemoteBlockUpdatePayload payload) {
        RemoteWorld remote = remoteWorld(client, RemoteWorldRegistry.dimensionAt(payload.dimension()));
        if (remote != null) {
            remote.updateBlock(payload.pos(), payload.state());
        }
    }

    private static void onEntityPacket(MinecraftClient client, RemoteEntityPacketPayload payload) {
        RegistryKey<World> dimension = RemoteWorldRegistry.dimensionAt(payload.dimension());
        if (dimension == null) {
            // Index jamais annonce : rien a quoi l'appliquer.
            return;
        }
        if (client.world != null && client.world.getRegistryKey().equals(dimension)) {
            // La dimension est devenue celle du joueur (traversee) : seules les destructions comptent, elles retirent
            // les copies distantes que vanilla ne connait pas ; le reste du monde est a vanilla, qui renvoie aussitot
            // celles qu'il suit (le portail de retour juste derriere le joueur) : retrait differe, sans trou.
            if (ModConfig.ENABLE_PORTAL_FEATURE && payload.packet() instanceof EntitiesDestroyS2CPacket destroy) {
                EntityHandover.deferRemoval(client.world, destroy.getEntityIds());
            }
            return;
        }
        RemoteWorld remote = remoteWorld(client, dimension);
        if (remote != null) {
            remote.applyEntityPacket(client, payload.packet());
        }
    }

    /** Fissures d'un bloc mine a travers un portail, sur la vue qui le montre (monde distant ou vue secondaire). */
    private static void onBlockBreaking(MinecraftClient client, RemoteBlockBreakingPayload payload) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE || client.player == null) {
            return;
        }
        RemoteWorldRegistry.syncWithMainWorld(client.world);
        RemoteWorld view = RemoteWorldRegistry.viewOf(worldKey(payload.dimension()));
        if (view != null) {
            view.renderer().setBlockBreakingInfo(client.player.getId(), payload.pos(), payload.stage());
        }
    }

    private static @Nullable RemoteWorld remoteWorld(MinecraftClient client, Identifier dimension) {
        return remoteWorld(client, worldKey(dimension));
    }

    /** Monde a nourrir pour cette dimension ; {@code null} sans portails, sans dimension (index jamais annonce) ou sans monde. */
    private static @Nullable RemoteWorld remoteWorld(MinecraftClient client, @Nullable RegistryKey<World> dimension) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE || dimension == null) {
            return null;
        }
        RemoteWorldRegistry.syncWithMainWorld(client.world);
        return RemoteWorldRegistry.syncTarget(client, dimension);
    }

    private static RegistryKey<World> worldKey(Identifier dimension) {
        return RegistryKey.of(RegistryKeys.WORLD, dimension);
    }
}
