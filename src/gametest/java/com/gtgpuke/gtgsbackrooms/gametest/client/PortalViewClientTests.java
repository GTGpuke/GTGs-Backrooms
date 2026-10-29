package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.SolidityIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;

/**
 * Un portail de l'Overworld vers le Level 0, vu puis traverse par un vrai client : la vue du Level 0 dans le cadre, puis
 * l'aller et le retour a pied, sans couture (l'ecart entre l'image de la bascule et la precedente reste celui d'un pas).
 */
final class PortalViewClientTests {

    /** Images rendues apres la bascule, le joueur marchant encore : de quoi juger la couture et une image figee. */
    private static final int AFTER_SWITCH_FRAMES = 12;
    /**
     * Les deux images de la bascule ne doivent pas s'ecarter plus que ce multiple de l'ecart median des images voisines
     * (la marche)... Mesure du 29 octobre 2026 : 4,6 a 4,9 a la bascule pour des voisines a 4,1 ; une image noire ou de
     * l'autre monde vaut 20 a 80.
     */
    private static final double SEAM_FACTOR = 1.5;
    /** ... plus cet ecart absolu (0 a 255 par canal), pour une marche lente ou un decor uni. */
    private static final double SEAM_MARGIN = 1.0;
    /** Images voisines de la bascule prises de chaque cote pour la reference (la bascule et ses deux images exclues). */
    private static final int NEIGHBOURS = 6;
    /** Apres la bascule, en marchant encore, l'image change au moins de cette part de ce qu'elle changeait avant. */
    private static final double MOVING_SHARE = 0.25;
    /** Ticks laisses au serveur pour liberer un monde distant sans portail : son delai (cent ticks) et bien plus. */
    private static final int RELEASE_TICKS = 600;

    private PortalViewClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(portalViewAndSeamlessCrossings(), remoteWorldReleasedAndForgotten());
    }

    /**
     * Le monde distant d'une vue s'en va de deux facons : libere par le serveur (plus aucun portail a portee depuis
     * {@code remoteWorldReleaseSeconds}, cinq secondes pour les tests du client), ou jete avec les autres quand vanilla
     * change le monde du joueur. Dans les deux cas, l'index des neons et la copie du monde pour le son l'oublient, et son
     * renderer se ferme sans erreur OpenGL (ses tampons jamais remplis compris, que le pilote ne connait pas).
     */
    private static ClientScenario remoteWorldReleasedAndForgotten() {
        return ClientScenario.named("remote_world_released_and_forgotten")
                .run("joueur et portail dans l'Overworld", context -> {
                    ClientTestPortals.place(context, PortalSettings.DEFAULT);
                    ClientTestPortals.standAt(context, 0.5, 0.5, 0.0f, 0.0f);
                })
                .await("portail recu par le client", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 prete dans le cadre", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .await("monde distant du Level 0 indexe pour les neons et le son", context -> trackedWorlds() == 1, 200)
                .run("portail retire", ClientTestPortals::remove)
                .await("monde distant du Level 0 libere par le serveur", context -> RemoteWorldRegistry.get(BackroomsConstants.LEVEL_ZERO_WORLD) == null,
                        RELEASE_TICKS)
                .check("monde libere oublie", context -> requireForgotten("Apres la liberation"))
                .run("portail repose", context -> ClientTestPortals.place(context, PortalSettings.DEFAULT))
                .await("portail recu de nouveau", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 de nouveau prete", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .await("monde distant du Level 0 de nouveau indexe", context -> trackedWorlds() == 1, 200)
                .run("changement de monde vanilla vers le Nether", context -> context.onServer(server -> {
                    ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
                    // Sur le toit de bedrock : ni bloc autour du joueur, ni chute.
                    player.teleport(ClientTestContext.serverWorld(server, World.NETHER), 0.5, 128.0, 0.5, 0.0f, 0.0f);
                }))
                .await("Nether cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.NETHER
                        && context.client().currentScreen == null, ClientTestPortals.PORTAL_TICKS)
                .check("monde distant jete et oublie au changement de monde", context -> {
                    ClientTestContext.require(RemoteWorldRegistry.get(BackroomsConstants.LEVEL_ZERO_WORLD) == null,
                            "Le monde distant du Level 0 survit au changement de monde vanilla.");
                    requireForgotten("Apres le changement de monde");
                })
                .cleanup(context -> {
                    ClientTestPortals.remove(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Mondes que l'index des neons ou la copie du monde pour le son retiennent (le plus grand des deux). */
    private static int trackedWorlds() {
        return Math.max(NeonIndex.trackedWorlds(), SolidityIndex.trackedWorlds());
    }

    private static void requireForgotten(String after) {
        ClientTestContext.require(NeonIndex.trackedWorlds() == 0, after + ", l'index des neons tient encore " + NeonIndex.trackedWorlds()
                + " monde(s).");
        ClientTestContext.require(SolidityIndex.trackedWorlds() == 0, after + ", la copie du monde pour le son tient encore "
                + SolidityIndex.trackedWorlds() + " monde(s).");
    }

    private static ClientScenario portalViewAndSeamlessCrossings() {
        return ClientScenario.named("portal_view_and_seamless_crossings")
                .run("joueur et portail dans l'Overworld", context -> {
                    ClientTestPortals.place(context, PortalSettings.DEFAULT);
                    ClientTestPortals.standAt(context, 0.5, 0.5, 0.0f, 0.0f);
                })
                .await("portail recu par le client", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 prete dans le cadre", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .frames(60)
                .capture("Level 0 dans le cadre, ciel autour", image -> {
                    double[] frame = Images.mean(image, 0.47, 0.50, 0.53, 0.70);
                    double[] sky = Images.mean(image, 0.0, 0.0, 1.0, 0.15);
                    ClientTestContext.require(Images.yellowish(frame), "Le cadre ne montre pas le Level 0 (teinte jaune attendue) : " + Images.describe(frame) + ".");
                    ClientTestContext.require(sky[2] > sky[0], "Le ciel de l'Overworld n'est pas autour du cadre : " + Images.describe(sky) + ".");
                })
                .run("marche vers le cadre", context -> {
                    ClientTestFrames.startRecording();
                    context.client().options.forwardKey.setPressed(true);
                })
                .await("bascule dans le Level 0", LevelZeroClientTests::inLevelZero, 200)
                // La marche continue apres la bascule : une image figee ensuite se verrait, meme en rendu logiciel lent.
                .frames(AFTER_SWITCH_FRAMES)
                .run("arret", context -> context.client().options.forwardKey.setPressed(false))
                .check("aller sans couture", context -> requireSeamless(ClientTestFrames.stopRecording(), "aller"))
                .ticks(10)
                .run("marche arriere vers le cadre de retour", context -> {
                    ClientTestFrames.startRecording();
                    context.client().options.backKey.setPressed(true);
                })
                .await("bascule dans l'Overworld", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 200)
                .frames(AFTER_SWITCH_FRAMES)
                .run("arret", context -> context.client().options.backKey.setPressed(false))
                .check("retour sans couture", context -> requireSeamless(ClientTestFrames.stopRecording(), "retour"))
                .cleanup(context -> {
                    context.client().options.forwardKey.setPressed(false);
                    context.client().options.backKey.setPressed(false);
                    ClientTestFrames.stopRecording();
                    ClientTestPortals.remove(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * La bascule (premiere image dans l'autre monde, et la suivante) ne saute pas plus qu'un pas : leur ecart a l'image
     * precedente reste sous {@link #SEAM_FACTOR} fois l'ecart median des images voisines, plus {@link #SEAM_MARGIN} ; et
     * l'image continue de changer apres elle (le joueur marche encore), ce qu'un rendu fige ne fait pas.
     */
    private static void requireSeamless(List<ClientTestFrames.FrameStep> steps, String crossing) {
        int seam = -1;
        for (int i = 1; i < steps.size(); i++) {
            if (!steps.get(i).world().equals(steps.get(i - 1).world())) {
                seam = i;
                break;
            }
        }
        ClientTestContext.require(seam > 0 && seam + 2 < steps.size(), "Aucune bascule enregistree au milieu du " + crossing + " (" + steps.size() + " images).");
        List<Double> before = new ArrayList<>();
        List<Double> after = new ArrayList<>();
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            double difference = steps.get(i).difference();
            trace.append(String.format(Locale.ROOT, " %.2f%s", difference, i == seam ? "*" : ""));
            if (i < seam - 1 && i >= seam - 1 - NEIGHBOURS) {
                before.add(difference);
            } else if (i > seam + 1 && i <= seam + 1 + NEIGHBOURS) {
                after.add(difference);
            }
        }
        ModConstants.LOGGER.info("[Test client] Ecarts d'une image a l'autre pendant le {} (* = bascule) :{}", crossing, trace);
        List<Double> neighbours = new ArrayList<>(before);
        neighbours.addAll(after);
        double reference = median(neighbours);
        double limit = SEAM_FACTOR * reference + SEAM_MARGIN;
        double atSeam = Math.max(steps.get(seam).difference(), steps.get(seam + 1).difference());
        ClientTestContext.require(atSeam <= limit, String.format(Locale.ROOT,
                "Couture a la bascule du %s : ecart %.2f pour une limite de %.2f (voisines %.2f).", crossing, atSeam, limit, reference));
        ClientTestContext.require(median(after) >= MOVING_SHARE * median(before), String.format(Locale.ROOT,
                "Image figee apres la bascule du %s : ecart %.2f apres, %.2f avant.", crossing, median(after), median(before)));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        return sorted.get(sorted.size() / 2);
    }
}
