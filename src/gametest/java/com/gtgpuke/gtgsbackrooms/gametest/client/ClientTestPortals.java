package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModItems;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Le portail des tests du client : un bloc portail sur le sol du monde plat de l'Overworld, face avant au nord, vers le
 * Level 0 (sa destination par defaut), et le joueur place devant lui.
 */
final class ClientTestPortals {

    /** Bloc portail, sur le sol du monde plat, face avant vers le nord (vers le joueur). */
    static final BlockPos PORTAL = new BlockPos(0, -60, 4);
    /** Plan du cadre d'un bloc portail : au milieu du bloc. */
    static final double PLANE_Z = 4.5;
    /** Hauteur des pieds sur le sol du monde plat. */
    static final double FLOOR_Y = -60.0;
    /** Ticks laisses a la recherche de l'arrivee dans le Level 0 et a l'envoi de sa fenetre. */
    static final int PORTAL_TICKS = 1200;
    /** Chunks du Level 0 recus avant de tenir une vue pour prete (la fenetre autour de l'arrivee en compte bien plus). */
    private static final int MIN_VIEW_CHUNKS = 9;

    private ClientTestPortals() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Pose le bloc portail aux reglages donnes (comme la baguette), cote serveur. */
    static void place(ClientTestContext context, PortalSettings settings) {
        context.onServer(server -> {
            ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
            overworld.setBlockState(PORTAL, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
            if (overworld.getBlockEntity(PORTAL) instanceof BackroomsPortalBlockEntity block) {
                block.readComponents(wand(settings));
            }
        });
    }

    /** Retire le bloc portail (et son portail). */
    static void remove(ClientTestContext context) {
        context.onServer(server -> ClientTestContext.serverWorld(server, World.OVERWORLD).setBlockState(PORTAL, Blocks.AIR.getDefaultState()));
    }

    /** Une baguette portant ces reglages. */
    static ItemStack wand(PortalSettings settings) {
        ItemStack wand = new ItemStack(ModItems.PORTAL_WAND);
        wand.set(ModDataComponents.PORTAL_SETTINGS, settings);
        return wand;
    }

    /** Le joueur dans l'Overworld, pieds sur le sol, a cette position et ce regard, sans voler. */
    static void standAt(ClientTestContext context, double x, double z, float yaw, float pitch) {
        context.onServer(server -> {
            ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
            player.teleport(ClientTestContext.serverWorld(server, World.OVERWORLD), x, FLOOR_Y, z, yaw, pitch);
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        });
    }

    /** Le client est dans l'Overworld et connait le portail. */
    static boolean received(ClientTestContext context) {
        return context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD
                && front(ClientPortalIndex.portals(context.world())) != null;
    }

    /**
     * La vue du Level 0 est prete : dessinee a la derniere image (son look y est), et son monde distant a recu le terrain
     * autour de l'arrivee.
     */
    static boolean viewReady(ClientTestContext context) {
        RemoteWorld remote = RemoteWorldRegistry.get(BackroomsConstants.LEVEL_ZERO_WORLD);
        return levelZeroSeen() && remote != null && remote.world().getChunkManager().getLoadedChunkCount() >= MIN_VIEW_CHUNKS;
    }

    /**
     * Le look du Level 0 est dans la derniere image : son bit, pas seulement un look (le profil vanilla, d'identifiant 0,
     * pose toujours le sien).
     */
    static boolean levelZeroSeen() {
        return (LookState.lastFrameLooks() & 1 << LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).id()) != 0;
    }

    /** La face avant du portail du bloc (celle qui regarde le joueur, au nord), cote client. */
    static BackroomsPortalEntity clientPortal(ClientTestContext context) {
        BackroomsPortalEntity front = front(ClientPortalIndex.portals(context.world()));
        ClientTestContext.require(front != null, "Aucune face avant de portail cote client.");
        return front;
    }

    /** La face avant (au nord) parmi les portails d'un monde, ou {@code null} : le bloc pose aussi une face arriere. */
    static @Nullable BackroomsPortalEntity front(List<BackroomsPortalEntity> portals) {
        for (BackroomsPortalEntity portal : portals) {
            if (portal.facingSource() == PortalFacing.NORTH) {
                return portal;
            }
        }
        return null;
    }

    /** Point de l'arrivee, a {@code distance} blocs du plan de sortie dans le sens de la marche, a hauteur des yeux. */
    static Vec3d pastExit(BackroomsPortalEntity portal, double distance) {
        Vec3d out = Vec3d.of(portal.facingDestination().direction.getVector());
        return portal.destinationPos().add(out.multiply(distance));
    }

    /** Lit l'entier qui suit {@code prefix} dans un diagnostic du mod. */
    static int readCount(CharSequence diagnostic, String prefix) {
        String text = diagnostic.toString();
        int start = text.indexOf(prefix);
        ClientTestContext.require(start >= 0, "Diagnostic sans \"" + prefix + "\" : " + text + ".");
        start += prefix.length();
        int end = start;
        while (end < text.length() && Character.isDigit(text.charAt(end))) {
            end++;
        }
        return Integer.parseInt(text.substring(start, end));
    }
}
