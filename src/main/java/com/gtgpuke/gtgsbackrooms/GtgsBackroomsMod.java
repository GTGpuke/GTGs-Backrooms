package com.gtgpuke.gtgsbackrooms;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroLayout;
import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.debug.DebugPortalCommand;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.interaction.RemoteInteractionHandler;
import com.gtgpuke.gtgsbackrooms.core.portal.item.PortalWandItem;
import com.gtgpuke.gtgsbackrooms.core.portal.light.PortalLightTransmission;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalSight;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.DestinationLoader;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlockEntities;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModChunkGenerators;
import com.gtgpuke.gtgsbackrooms.core.registry.ModCreativeTab;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModEntities;
import com.gtgpuke.gtgsbackrooms.core.registry.ModItems;
import com.gtgpuke.gtgsbackrooms.core.registry.ModNetworking;
import com.gtgpuke.gtgsbackrooms.core.registry.ModSounds;

/**
 * Point d'entree commun (client et serveur) du mod.
 */
public class GtgsBackroomsMod implements ModInitializer {

    @Override
    public void onInitialize() {
        String version = FabricLoader.getInstance().getModContainer(ModConstants.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        ModConstants.LOGGER.info("Initialisation de {} v{}.", ModConstants.MOD_NAME, version);

        registerContent();
        registerServerHooks();
        initPortals(ModConfig.ENABLE_PORTAL_FEATURE, FabricLoader.getInstance().isDevelopmentEnvironment());

        ModConstants.LOGGER.info("Initialisation de {} terminee.", ModConstants.MOD_NAME);
    }

    /** Enregistre tout le contenu : blocs avant items, block entities apres blocs, composants et sons avant items. */
    private static void registerContent() {
        ModChunkGenerators.register();
        ModDataComponents.register();
        ModSounds.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModEntities.register();
        ModItems.register();
        ModCreativeTab.register();
        ModNetworking.register();
        // La baguette se regle meme portails coupes : elle pose alors des blocs portail inertes.
        PortalWandItem.init();
    }

    private static void registerServerHooks() {
        ModPerformanceMonitor.init();
        ServerTickEvents.START_SERVER_TICK.register(server -> ModPerformanceMonitor.onServerTickStart());
        ServerTickEvents.END_SERVER_TICK.register(server -> ModPerformanceMonitor.onServerTickEnd());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LevelZeroLayout.clearCache());
    }

    /**
     * Portails actives ou non par configuration ; sans portails, le bloc portail reste inerte. Le registre des portails
     * tourne dans les deux cas : il retire les portails dont le bloc a disparu.
     *
     * @param enabled {@code true} si les portails sont actives
     * @param developmentEnvironment {@code true} hors production : la commande de debug des portails est enregistree
     */
    static void initPortals(boolean enabled, boolean developmentEnvironment) {
        BackroomsPortalBlockEntity.setPortalsEnabled(enabled);
        BackroomsPortalEntityRegistry.init();
        if (enabled) {
            initPortalRuntime(developmentEnvironment);
        } else {
            ModConstants.LOGGER.info("[Portal] Portails desactives par configuration : le bloc portail reste inerte.");
        }
    }

    /** Portails : prechargement, synchronisation client, teleportation, interaction et lumiere a travers le cadre. */
    private static void initPortalRuntime(boolean developmentEnvironment) {
        DestinationLoader.init();
        PortalViewTracker.init();
        TeleportExecutor.init();
        PortalSight.init();
        RemoteInteractionHandler.init();
        PortalLightTransmission.init();
        if (developmentEnvironment) {
            DebugPortalCommand.register();
            ModConstants.LOGGER.info("[Portal] Commande /backrooms portal enregistree (developpement).");
        }
        ModConstants.LOGGER.info("[Portal] Portails actifs.");
    }
}
