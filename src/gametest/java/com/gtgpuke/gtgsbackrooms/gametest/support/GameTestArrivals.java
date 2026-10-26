package com.gtgpuke.gtgsbackrooms.gametest.support;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTestException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Arrivees calculees d'un trait pour les tests : la recherche chunk par chunk du jeu ({@link PortalTargets#resolve}),
 * relancee avec le meme tirage apres chaque chunk qu'elle demande, que le test genere aussitot sur le thread du serveur la
 * ou le bloc pose un ticket et attend. Rend l'arrivee que le bloc trouverait.
 */
public final class GameTestArrivals {

    private GameTestArrivals() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Destination d'un bloc portail, chaque chunk demande genere sur place.
     *
     * @param seed graine du tirage des X/Z de l'arrivee, la meme a chaque essai, comme pour le bloc
     * @return destination, ou {@code null} si la dimension cible est absente
     */
    public static @Nullable PortalTargets.Target resolve(MinecraftServer server, RegistryKey<World> dimension, BlockPos anchor,
            PortalFacing entryFacing, PortalSettings settings, long seed) {
        ServerWorld searched = server.getWorld(PortalTargets.searchDimension(dimension, settings));
        PortalTargets.Search search = PortalTargets.resolve(server, dimension, anchor, entryFacing, settings, Random.create(seed));
        ChunkPos previous = null;
        while (search.waits()) {
            ChunkPos missing = search.missingChunk();
            if (missing.equals(previous)) {
                throw new GameTestException("La recherche redemande le chunk qu'on vient de generer : " + missing + ".");
            }
            searched.getChunk(missing.x, missing.z);
            previous = missing;
            search = PortalTargets.resolve(server, dimension, anchor, entryFacing, settings, Random.create(seed));
        }
        return search.target();
    }

    /**
     * Arrivee dans un monde depuis une colonne : celle d'un portail de ce monde vers lui-meme, aux memes X/Z (echelle un),
     * sa face avant regardant a l'oppose de la sortie voulue (sortie droite). Les reglages ne tirent pas au sort et
     * n'imposent pas de sens de sortie.
     */
    public static PortalTargets.Target arrive(ServerWorld world, int x, int startY, int z, PortalFacing facing, PortalSettings settings) {
        return resolve(world.getServer(), world.getRegistryKey(), new BlockPos(x, startY, z), facing.opposite(),
                settings.withExit(Optional.of(world.getRegistryKey()), Optional.empty()), 0L);
    }
}
