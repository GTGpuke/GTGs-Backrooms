package com.gtgpuke.gtgsbackrooms.backrooms.level0;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.LevelZeroChunkGenerator;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.LevelZeroSeedResolver;

/**
 * A la connexion d'un joueur, le serveur lui donne la graine du layout de chacun de ses Level 0
 * ({@link LevelZeroLayoutPayload}) : le client en tire l'acoustique de la generation. Une dimension dont le generateur
 * n'est pas celui du Level 0 n'envoie rien.
 */
public final class LevelZeroLayoutSync {

    private LevelZeroLayoutSync() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler une fois depuis le point d'entree commun. */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendAll(handler.player, server));
    }

    /** La graine de chaque Level 0 du serveur, au joueur. */
    static void sendAll(ServerPlayerEntity player, MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            send(player, world.getRegistryKey(), world.getChunkManager().getChunkGenerator(), world.getChunkManager().getNoiseConfig());
        }
    }

    /**
     * La graine du layout d'une dimension, au joueur, si son generateur est celui du Level 0.
     *
     * @param player joueur
     * @param dimension dimension
     * @param generator generateur de la dimension
     * @param noiseConfig configuration de bruit de la dimension
     * @return {@code true} si une graine est partie
     */
    public static boolean send(ServerPlayerEntity player, RegistryKey<World> dimension, ChunkGenerator generator, NoiseConfig noiseConfig) {
        if (!(generator instanceof LevelZeroChunkGenerator)) {
            return false;
        }
        ServerPlayNetworking.send(player, new LevelZeroLayoutPayload(dimension.getValue(), LevelZeroSeedResolver.resolveLayoutSeed(noiseConfig)));
        return true;
    }
}
