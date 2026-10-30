package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.BlockBufferBuilderPool;
import net.minecraft.client.render.chunk.ChunkBuilder;

import com.gtgpuke.gtgsbackrooms.mixin.client.ChunkBuilderAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Tampons de construction des sections, par renderer : vanilla en donne un par processeur au renderer du joueur, une vue
 * n'en a que deux ({@link RemoteWorld}). Le renderer d'une vue devenu celui du joueur par une traversee construirait deux
 * par deux ce qui apparait ensuite : sa reserve grandit jusqu'au compte de vanilla, et celle du renderer qu'il remplace,
 * devenu une vue, redescend a deux.
 *
 * <p>Une reserve ne se touche que dans la file de son chunk builder, sur les fils de travail, la ou ses taches en cours
 * lui rendent leurs tampons : c'est la que des tampons se creent et que des tampons libres se ferment. Aucune reserve ne
 * change de chunk builder ni ne passe a un autre, et un tampon en cours d'usage ne se ferme jamais : une reserve ne se vide
 * jamais pour de bon, et son renderer construit toujours.
 */
public final class BuildBuffers {

    /** Compte de vanilla pour le renderer du joueur ({@code BlockBufferBuilderPool.allocate}) : un par processeur, borne par la memoire. */
    public static final int PLAYER_COUNT = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(),
            Math.max(1, (int) (Runtime.getRuntime().maxMemory() * 0.3) / BlockBufferAllocatorStorage.EXPECTED_TOTAL_SIZE)));
    /** Compte de chaque reserve deja vue, et si un ajustement y est en cours (fil de rendu seulement). */
    private static final Map<BlockBufferBuilderPool, Budget> BUDGETS = new IdentityHashMap<>();

    private BuildBuffers() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * A chaque tick : la reserve du renderer du joueur a le compte de vanilla, celle de chaque vue deux. Un ajustement en
     * cours se termine avant le suivant. Sans chunk builder, ou si un autre mod dessine le terrain (le chunk builder
     * vanilla ne sert pas), rien a ajuster : la reserve garde ce que vanilla lui a donne.
     */
    static void balance(MinecraftClient client, WorldRenderer renderer, int wanted) {
        ChunkBuilder builder = renderer.getChunkBuilder();
        if (builder == null || RemoteViewRenderer.foreignTerrain((WorldRendererAccessor) renderer)) {
            return;
        }
        ChunkBuilderAccessor accessor = (ChunkBuilderAccessor) builder;
        BlockBufferBuilderPool pool = accessor.backrooms$getBuffersPool();
        Budget budget = BUDGETS.get(pool);
        if (budget == null) {
            // Premiere fois : la reserve du jeu a le compte de vanilla, celle d'une vue le sien.
            budget = new Budget(pool == client.getBufferBuilders().getBlockBufferBuildersPool() ? PLAYER_COUNT : RemoteWorld.RENDER_BUFFER_COUNT);
            BUDGETS.put(pool, budget);
        }
        if (budget.adjusting || budget.count == wanted) {
            return;
        }
        budget.adjusting = true;
        Budget adjusted = budget;
        int count = budget.count;
        // Evenement rare (une traversee) : la tache s'alloue.
        accessor.backrooms$getMailbox().send(() -> {
            int added = Math.max(0, wanted - count);
            for (int i = 0; i < added; i++) {
                pool.release(new BlockBufferAllocatorStorage());
                accessor.backrooms$scheduleRunTasks();
            }
            // Seuls les tampons libres se ferment : ceux des taches en cours reviendront, et partiront a l'ajustement suivant.
            int closed = Math.min(Math.max(0, count - wanted), pool.getAvailableBuilderCount());
            for (int i = 0; i < closed; i++) {
                pool.acquire().close();
            }
            client.execute(() -> {
                adjusted.count = count + added - closed;
                adjusted.adjusting = false;
            });
        });
    }

    /** Nombre de tampons de construction de la reserve de ce renderer, ou -1 s'il n'en a pas encore ete tenu compte (diagnostic). */
    public static int count(WorldRenderer renderer) {
        ChunkBuilder builder = renderer.getChunkBuilder();
        Budget budget = builder == null ? null : BUDGETS.get(((ChunkBuilderAccessor) builder).backrooms$getBuffersPool());
        return budget == null ? -1 : budget.count;
    }

    /** Compte de tampons d'une reserve, tenu sur le fil de rendu. */
    private static final class Budget {

        private int count;
        private boolean adjusting;

        private Budget(int count) {
            this.count = count;
        }
    }
}
