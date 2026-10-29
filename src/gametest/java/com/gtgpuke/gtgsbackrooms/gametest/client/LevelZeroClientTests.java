package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroArrival;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.audio.client.EfxBackend;
import com.gtgpuke.gtgsbackrooms.core.audio.client.SolidityIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonFaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonShaderCheck;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Le Level 0 vu et entendu par un vrai client : look du profil dans les shaders, neons envoyes, bourdonnement des tubes
 * route par le moteur audio, image teintee du profil.
 */
final class LevelZeroClientTests {

    /** Identifiant du bourdonnement des neons, tel que le diagnostic du moteur audio l'ecrit. */
    static final String HUM = "gtgs_backrooms:block.neon.hum";
    /** Ticks laisses a la generation et a l'envoi du Level 0 apres le changement de monde. */
    private static final int ARRIVAL_TICKS = 1200;

    private LevelZeroClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(levelZeroLooksAndSoundsLikeItsProfile());
    }

    /** Le joueur dans le Level 0, a l'arrivee d'un portail pres de l'origine. */
    static void enterLevelZero(ClientTestContext context) {
        context.onServer(server -> {
            ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            PortalTargets.Target target = LevelZeroArrival.find(levelZero, 0, 0, PortalFacing.NORTH, PortalSettings.DEFAULT);
            ClientTestContext.require(target != null, "Aucune arrivee trouvee dans le Level 0 pres de l'origine.");
            Vec3d feet = target.position().subtract(0.0, PortalSettings.DEFAULT.height() / 2.0, 0.0);
            ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
            player.getAbilities().flying = false;
            player.teleport(levelZero, feet.x, feet.y, feet.z, target.facing().direction.asRotation(), 0.0f);
        });
    }

    /** Le joueur dans l'Overworld, sur le sol du monde plat, regard vers le sud. */
    static void backToOverworld(ClientTestContext context) {
        context.onServer(server -> {
            ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
            player.teleport(ClientTestContext.serverWorld(server, World.OVERWORLD), 0.5, -60.0, 0.5, 0.0f, 0.0f);
        });
    }

    static boolean inLevelZero(ClientTestContext context) {
        return context.client().world != null && context.client().world.getRegistryKey() == BackroomsConstants.LEVEL_ZERO_WORLD
                && context.client().currentScreen == null;
    }

    private static ClientScenario levelZeroLooksAndSoundsLikeItsProfile() {
        return ClientScenario.named("level_zero_look_neons_and_hum")
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, ARRIVAL_TICKS)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 16, ARRIVAL_TICKS)
                .frames(20)
                .check("look du Level 0 dans les shaders et neons envoyes", context -> {
                    ClientTestContext.require(ClientTestPortals.levelZeroSeen(), "Le look du Level 0 n'est pas dans la derniere image.");
                    ClientTestContext.require(LookState.shadersCarryLook(), "L'etage 1 du look n'est pas dans les shaders.");
                    ClientTestContext.require(LookState.lastFrameNeons() > 0, "Aucun neon envoye dans le Level 0.");
                })
                .check("brouillard de la couleur du profil", context -> {
                    float[] fog = RenderSystem.getShaderFogColor();
                    Vec3d expected = LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).fog().color();
                    ClientTestContext.require(Math.abs(fog[0] - expected.x) < 0.02 && Math.abs(fog[1] - expected.y) < 0.02 && Math.abs(fog[2] - expected.z) < 0.02,
                            String.format(Locale.ROOT, "Brouillard (%.3f, %.3f, %.3f) au lieu de la couleur du profil (%.3f, %.3f, %.3f).",
                                    fog[0], fog[1], fog[2], expected.x, expected.y, expected.z));
                })
                .check("jumeau GLSL du calendrier des neons accorde a Java", context -> {
                    ClientTestContext.require(NeonShaderCheck.identicalVectors() > 0, "Le jumeau GLSL n'a pas ete verifie sur le processeur graphique (journal [Look]).");
                    ClientTestContext.require(!NeonFaces.disabled(), "Les faces des neons sont coupees : le shader et NeonSchedule divergent.");
                })
                .check("forme generee du Level 0 recue du serveur", context -> ClientTestContext.require(
                        GeneratedAcoustics.of(BackroomsConstants.LEVEL_ZERO_WORLD.getValue()) != null, "Le client n'a pas la graine du layout du Level 0."))
                .await("bourdonnement des tubes route par le moteur audio", context -> routed().contains(HUM), 400)
                .check("moteur audio EFX pret", context -> ClientTestContext.require(EfxBackend.ready(), "EFX n'est pas pret : mode " + EfxBackend.mode() + "."))
                .capture("image teintee du profil", image -> {
                    double[] mean = Images.mean(image, 0.0, 0.0, 1.0, 1.0);
                    ClientTestContext.require(Images.luminance(mean) > 20.0, "Image du Level 0 trop sombre : " + Images.describe(mean) + ".");
                    ClientTestContext.require(Images.yellowish(mean), "Image du Level 0 sans la teinte jaune du profil : " + Images.describe(mean) + ".");
                })
                .run("retour dans l'Overworld par un changement de monde vanilla", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD
                        && context.client().currentScreen == null, ARRIVAL_TICKS)
                .ticks(5)
                .check("le monde du Level 0 quitte n'est plus retenu", context -> {
                    ClientTestContext.require(NeonIndex.trackedWorlds() == 0, "L'index des neons tient encore " + NeonIndex.trackedWorlds() + " monde(s).");
                    ClientTestContext.require(SolidityIndex.trackedWorlds() == 0, "La copie du monde pour le son tient encore " + SolidityIndex.trackedWorlds()
                            + " monde(s).");
                })
                .cleanup(LevelZeroClientTests::backToOverworld);
    }

    /** Le diagnostic du moteur audio : chaque son traite, son identifiant et sa dimension d'emission. */
    static String routed() {
        StringBuilder out = new StringBuilder();
        AcousticRouter.describe(out);
        return out.toString();
    }
}
