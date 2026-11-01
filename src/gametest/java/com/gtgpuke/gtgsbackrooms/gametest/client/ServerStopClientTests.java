package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.gametest.client.mixin.ClientTestChunkLoadingAccessor;

/**
 * La fermeture du serveur integre, dans un vrai client : un chunk encore retenu au moment de quitter remet sa tache de dechargement
 * dans la file a chaque passage, et seul le fil du serveur peut le lacher. Vanilla ne lui rend alors jamais la main, et la partie reste
 * figee sur la sauvegarde. Ce test se joue en dernier : ce qui le juge est la fin de partie, que le chien de garde arrete si elle bloque.
 */
final class ServerStopClientTests {

    private ServerStopClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(stopsWithAChunkHeld());
    }

    private static ClientScenario stopsWithAChunkHeld() {
        return ClientScenario.named("server_stops_with_a_chunk_held")
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .run("un chunk retenu jusqu'a l'arret", context -> {
                    ChunkPos pos = context.player().getChunkPos();
                    context.onServer(server -> hold(server, pos));
                })
                .cleanup(LevelZeroClientTests::backToOverworld);
    }

    /** Le chunk du joueur, retenu comme par un chargement en cours, avec sa tache de dechargement dans la file. */
    private static void hold(IntegratedServer server, ChunkPos pos) {
        ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
        ClientTestChunkLoadingAccessor loading = (ClientTestChunkLoadingAccessor) world.getChunkManager().chunkLoadingManager;
        ChunkHolder holder = loading.gtgsTest$getCurrentChunkHolders().get(pos.toLong());
        holder.incrementRefCount();
        loading.gtgsTest$tryUnloadChunk(pos.toLong(), holder);
        releaseAtStop(server, holder);
    }

    /** Le fil du serveur lache le chunk une fois l'arret commence, comme le ferait le chargement qui l'attend. */
    private static void releaseAtStop(MinecraftServer server, ChunkHolder holder) {
        server.send(new ServerTask(server.getTicks(), () -> {
            if (server.isRunning()) {
                releaseAtStop(server, holder);
            } else {
                holder.decrementRefCount();
            }
        }));
    }
}
