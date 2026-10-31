package com.gtgpuke.gtgsbackrooms.core.portal.interaction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.OperatorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.gtgpuke.gtgsbackrooms.core.portal.interaction.RemoteInteractionHandler.RemoteReach;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalNetwork;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockBreakingPayload;

/**
 * Minage d'un bloc a travers un portail, cote serveur et sans prediction client.
 *
 * <p>Vanilla laisse le client mesurer sa progression et ne fait que la verifier. Ici le serveur fait seul
 * le compte : a chaque tick, la vitesse de casse du bloc pour ce joueur s'ajoute a la progression, les
 * fissures sont envoyees au joueur (pour son rendu a travers le portail) et aux joueurs de la dimension
 * d'arrivee, et le bloc casse a cent pour cent comme sous {@code ServerPlayerInteractionManager}.
 * Le minage s'arrete si le client l'abandonne, si le bloc disparait ou si le joueur n'est plus a portee.
 */
final class RemoteMiningTracker {

    /** Nombre d'etapes de fissure vanilla. */
    private static final float BREAKING_STAGES = 10.0f;

    /** Bloc en cours de minage par un joueur. */
    private static final class Mining {
        final UUID portal;
        final RegistryKey<World> dimension;
        final BlockPos pos;
        float progress;
        int stage = -1;

        Mining(UUID portal, RegistryKey<World> dimension, BlockPos pos) {
            this.portal = portal;
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private static final Map<UUID, Mining> MINING = new HashMap<>();

    private RemoteMiningTracker() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche le tick et le nettoyage ; appele une fois, par {@link RemoteInteractionHandler#init()}. */
    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RemoteMiningTracker::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MINING.clear());
    }

    /** Le joueur commence a miner : casse immediate en creatif, progression sinon. */
    static void start(ServerPlayerEntity player, RemoteReach reach, BlockPos pos) {
        abort(player);
        ServerWorld world = reach.destination();
        if (world.getBlockState(pos).isAir()) {
            return;
        }
        if (player.isCreative()) {
            breakBlock(player, world, pos);
            return;
        }
        MINING.put(player.getUuid(), new Mining(reach.portal().getUuid(), world.getRegistryKey(), pos.toImmutable()));
    }

    /**
     * Le joueur s'en va en plein minage : ses fissures s'effacent chez les autres, sans plus rien lui envoyer (meme s'il
     * n'en a pas encore pose : effacer ce qui n'est pas la ne coute qu'un paquet).
     */
    private static void forget(ServerPlayerEntity player) {
        Mining mining = MINING.remove(player.getUuid());
        if (mining != null) {
            player.getServer().getWorld(mining.dimension).setBlockBreakingInfo(player.getId(), mining.pos, -1);
        }
    }

    /** Le joueur relache le bouton ou vise ailleurs : les fissures s'effacent. */
    static void abort(ServerPlayerEntity player) {
        Mining mining = MINING.remove(player.getUuid());
        if (mining != null) {
            clearProgress(player, mining);
        }
    }

    private static void tick(MinecraftServer server) {
        if (MINING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Mining>> iterator = MINING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Mining> entry = iterator.next();
            // Toujours present : la deconnexion retire le minage avant que le joueur ne quitte la liste.
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Mining mining = entry.getValue();
            RemoteReach reach = RemoteInteractionHandler.resolve(player, mining.portal, mining.dimension.getValue());
            BlockState state = reach == null ? null : reach.destination().getBlockState(mining.pos);
            // Mort, il ne mine plus : vanilla arrete aussi le minage d'un joueur mort.
            if (state == null || state.isAir() || !player.isAlive() || !reach.canReachBlock(player, mining.pos)) {
                iterator.remove();
                clearProgress(player, mining);
                continue;
            }
            ServerWorld world = reach.destination();
            mining.progress += state.calcBlockBreakingDelta(player, world, mining.pos);
            if (mining.progress >= 1.0f) {
                iterator.remove();
                clearProgress(player, mining);
                breakBlock(player, world, mining.pos);
                continue;
            }
            int stage = (int) (mining.progress * BREAKING_STAGES);
            if (stage != mining.stage) {
                mining.stage = stage;
                sendProgress(player, world, mining, stage);
            }
        }
    }

    private static void clearProgress(ServerPlayerEntity player, Mining mining) {
        if (mining.stage < 0) {
            return;
        }
        // La dimension vient d'une portee resolue : son monde existe.
        sendProgress(player, player.getServer().getWorld(mining.dimension), mining, -1);
    }

    private static void sendProgress(ServerPlayerEntity player, ServerWorld world, Mining mining, int stage) {
        world.setBlockBreakingInfo(player.getId(), mining.pos, stage);
        PortalNetwork.send(player, new RemoteBlockBreakingPayload(mining.dimension.getValue(), mining.pos, stage));
    }

    /**
     * Casse un bloc comme {@code ServerPlayerInteractionManager.tryBreakBlock}, evenements Fabric compris ; le son et les
     * eclats de la casse parviennent au mineur ({@link RemoteInteractionHandler#actThroughFrame}).
     */
    static void breakBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        RemoteInteractionHandler.actThroughFrame(player, () -> tryBreakBlock(player, world, pos));
    }

    private static void tryBreakBlock(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!player.getMainHandStack().getItem().canMine(state, world, pos, player)) {
            return;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        Block block = state.getBlock();
        if (block instanceof OperatorBlock && !player.isCreativeLevelTwoOp()) {
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
            return;
        }
        if (player.isBlockBreakingRestricted(world, pos, player.interactionManager.getGameMode())) {
            return;
        }
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state, blockEntity)) {
            PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(world, player, pos, state, blockEntity);
            return;
        }
        BlockState broken = block.onBreak(world, pos, state, player);
        // Le bloc n'etait pas de l'air (verifie en amont) : vanilla le retire toujours.
        world.removeBlock(pos, false);
        block.onBroken(world, pos, broken);
        if (!player.isCreative()) {
            ItemStack tool = player.getMainHandStack();
            ItemStack toolBefore = tool.copy();
            boolean canHarvest = player.canHarvest(broken);
            tool.postMine(world, broken, pos, player);
            if (canHarvest) {
                block.afterBreak(world, player, pos, broken, blockEntity, toolBefore);
            }
        }
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(world, player, pos, state, blockEntity);
    }
}
