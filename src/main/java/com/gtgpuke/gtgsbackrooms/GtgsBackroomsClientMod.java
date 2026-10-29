package com.gtgpuke.gtgsbackrooms;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.client.LevelZeroAcoustics;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.client.LevelZeroEffects;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.client.model.LevelZeroWallpaperModelHandler;
import com.gtgpuke.gtgsbackrooms.compat.CompatClient;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticDebugRenderer;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticWorker;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AudioResources;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueResources;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.audio.client.NeonHum;
import com.gtgpuke.gtgsbackrooms.core.audio.client.SolidityIndex;
import com.gtgpuke.gtgsbackrooms.core.perf.client.ClientPerformanceMonitorHook;
import com.gtgpuke.gtgsbackrooms.core.perf.client.FrameStateCommand;
import com.gtgpuke.gtgsbackrooms.core.portal.client.entity.BackroomsPortalEntityRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.interaction.RemoteActions;
import com.gtgpuke.gtgsbackrooms.core.portal.client.interaction.RemoteTargeting;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.ClientPortalCrossing;
import com.gtgpuke.gtgsbackrooms.core.portal.client.transition.EntityHandover;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackPortalRenderer;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewLightmaps;
import com.gtgpuke.gtgsbackrooms.core.portal.client.wand.PortalWandScreen;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalLight;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.FrozenViews;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.PortalSightReporter;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteSyncReceiver;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.registry.ModEntities;
import com.gtgpuke.gtgsbackrooms.core.render.client.FramebufferRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.client.FullscreenTriangle;
import com.gtgpuke.gtgsbackrooms.core.render.client.GpuTimer;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookPrograms;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonFaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonLights;

/**
 * Point d'entree client du mod : rendu adaptatif du Level 0, ecran de la baguette, portails.
 */
public class GtgsBackroomsClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ModConstants.LOGGER.debug("Initialisation client de {}.", ModConstants.MOD_NAME);

        ClientTickEvents.END_CLIENT_TICK.register(ClientPerformanceMonitorHook::onEndClientTick);
        WorldRenderEvents.END.register(ClientPerformanceMonitorHook::onFrameRendered);
        // Diagnostic : /backrooms_state ecrit l'etat de l'image dans le journal.
        FrameStateCommand.register();
        LevelZeroWallpaperModelHandler.register();
        // Moteur de shaders : profils de look, patchs et framebuffers recharges avec les ressources, programmes propres au
        // mod charges avec les shaders vanilla ; effets de dimension du Level 0.
        LookResources.register();
        NeonFaces.register();
        LookPrograms.register();
        // Les neons de chaque monde client, releves a l'arrivee de ses chunks.
        NeonIndex.register();
        // Moteur audio : profils par dimension et liens son-image recharges avec les ressources ; copie du monde du calcul
        // par rayons.
        AudioResources.register();
        CueResources.register();
        SolidityIndex.register();
        AcousticDebugRenderer.register();
        ClientPortalCrossing.WORLD_SWITCHED.register((from, to) -> AcousticRouter.onWorldSwitched());
        LevelZeroEffects.register();
        LevelZeroAcoustics.register();
        // La baguette de portail se regle meme portails coupes.
        PortalWandScreen.register();

        // Le type d'entity portail est toujours enregistre : son renderer et ses payloads doivent l'etre aussi.
        EntityRendererRegistry.register(ModEntities.BACKROOMS_PORTAL, BackroomsPortalEntityRenderer::new);
        RemoteSyncReceiver.init();
        ClientPortalIndex.init();
        ClientPortalCrossing.init();
        EntityHandover.init();
        CompatClient.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                endPortalSession();
                return;
            }
            ClientPortalCrossing.onClientTick();
            ClientPortalIndex.tick();
            RemoteActions.onClientTick();
            RemoteWorldRegistry.tickRemoteWorlds(client);
            ViewLightmaps.onClientTick();
            // Apres le tick de tous les mondes : une arrivee se juge sur la premiere image a venir, partie d'avant ce tick.
            EntityHandover.onClientTick();
            ClientPortalLight.onClientTick(client);
            PortalSightReporter.onClientTick(client);
            // Copie du monde pour les rayons, acoustique des sons en cours (resultats appliques, occlusion revue) et bourdonnement
            // des neons proches.
            SolidityIndex.onClientTick(client);
            AcousticRouter.onClientTick(client);
            CueTracker.onClientTick(client);
            NeonHum.onClientTick(client);
            // Aussi hors du rendu des portails : apres un changement de dimension vers un monde sans portail, les images figees
            // de l'ancien seraient gardees, et le monde avec elles.
            FrozenViews.sweep();
        });
    }

    /**
     * Hors partie, les portails oublient tout : l'evenement de deconnexion de Fabric peut tourner sur le thread reseau,
     * or les mondes distants liberent des ressources de rendu. La fin de partie se traite donc ici, sur le thread de
     * rendu, une fois le monde quitte ; sans rien a oublier, ces remises a zero ne coutent rien.
     */
    private static void endPortalSession() {
        ClientPortalCrossing.clear();
        EntityHandover.clear();
        RemoteActions.clear();
        RemoteTargeting.clear();
        PortalSightReporter.clear();
        RemoteWorldRegistry.syncWithMainWorld(null);
        ClientPortalIndex.clear();
        ViewLightmaps.clear();
        ShaderPackPortalRenderer.release();
        FrozenViews.clear();
        FramebufferRegistry.close();
        NeonIndex.clear();
        NeonLights.close();
        FullscreenTriangle.close();
        GpuTimer.close();
        AcousticRouter.clear();
        AcousticWorker.clear();
        GeneratedAcoustics.clear();
        SolidityIndex.clear();
        CueTracker.clear();
        NeonHum.clear();
    }
}
