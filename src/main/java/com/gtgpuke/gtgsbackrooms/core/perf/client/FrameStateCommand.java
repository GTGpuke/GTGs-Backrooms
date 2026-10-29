package com.gtgpuke.gtgsbackrooms.core.perf.client;

import java.util.List;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Commande de diagnostic du client, {@code /backrooms_state} : l'etat de l'image en cours ecrit dans le journal, pour
 * accompagner un rapport (monde du joueur et son look, looks vus et neons envoyes, pack de shaders, portails du monde et
 * palier de leur vue, mondes distants tenus, sons traites par le moteur audio et leur routage, perturbations des sons
 * lies). Rien n'est mesure en continu : l'etat est lu a la commande.
 */
public final class FrameStateCommand {

    private FrameStateCommand() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Enregistre la commande du client. */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("backrooms_state").executes(FrameStateCommand::export)));
    }

    private static int export(CommandContext<FabricClientCommandSource> context) {
        MinecraftClient client = context.getSource().getClient();
        ClientWorld world = client.world;
        if (world == null) {
            return 0;
        }
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        StringBuilder out = new StringBuilder("[Etat] Image du client\n");
        out.append("Monde du joueur : ").append(world.getRegistryKey().getValue()).append(", look ").append(LookProfiles.of(world.getRegistryKey()).id())
                .append(", camera ").append(camera).append('\n');
        out.append("Rendu (derniere image) : qualite ").append(LookResources.quality()).append(", looks vus (bits) ")
                .append(Integer.toBinaryString(LookState.lastFrameLooks())).append(", neons du monde du joueur ").append(LookState.lastFrameNeons())
                .append(", etage 1 dans les shaders ").append(LookState.shadersCarryLook())
                .append(", pack de shaders ").append(ShaderPackState.inUse()).append('\n');
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        out.append("Portails du monde : ").append(portals.size()).append('\n');
        for (BackroomsPortalEntity portal : portals) {
            out.append("  ").append(portal.getPos()).append(' ').append(portal.facingSource()).append(" -> ").append(portal.destinationDim().getValue())
                    .append(' ').append(portal.destinationPos()).append(' ').append(portal.facingDestination()).append(", a ")
                    .append(Math.sqrt(portal.squaredDistanceToFrame(camera.x, camera.y, camera.z))).append(" blocs, palier ").append(portal.detailTier())
                    .append(portal.visible() ? "" : ", invisible").append(portal.teleportable() ? "" : ", ferme").append('\n');
        }
        out.append("Mondes distants : ").append(RemoteWorldRegistry.remoteWorldCount()).append('\n');
        for (int i = 0; i < RemoteWorldRegistry.remoteWorldCount(); i++) {
            out.append("  ").append(RemoteWorldRegistry.remoteWorld(i).getRegistryKey().getValue()).append('\n');
        }
        AcousticRouter.describe(out);
        CueTracker.describe(out);
        out.append("Fin de l'etat de l'image.");
        ModConstants.LOGGER.info(out.toString());
        context.getSource().sendFeedback(Text.translatable("commands.gtgs_backrooms.state.written"));
        return 1;
    }
}
