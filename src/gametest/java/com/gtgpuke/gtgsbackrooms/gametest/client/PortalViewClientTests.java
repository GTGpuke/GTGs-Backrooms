package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.SolidityIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.SectionGenerations;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.BuildBuffers;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Un portail de l'Overworld vers le Level 0, vu puis traverse par un vrai client : la vue du Level 0 dans le cadre, puis
 * l'aller et le retour a pied, sans couture (l'ecart entre l'image de la bascule et la precedente reste celui d'un pas).
 */
final class PortalViewClientTests {

    /** Images rendues apres la bascule, le joueur marchant encore : de quoi juger la couture et une image figee. */
    private static final int AFTER_SWITCH_FRAMES = 12;
    /** Images rendues apres un demi-tour : le temps que le graphe vanilla montre ce qui etait derriere et que cela se construise. */
    private static final int TURN_FRAMES = 20;
    /** Le joueur colle au cadre (plan a z = 4,5) : le cadre remplit l'ecran, sous la main comprise. */
    private static final double HAND_TEST_Z = 4.2;
    /** Ticks laisses a une section rendue a reconstruire pour l'etre. */
    private static final int REBUILD_TICKS = 200;
    /** Sections rendues a reconstruire, celle du renderer du joueur puis celle de la vue, suivies jusqu'a leur reconstruction. */
    private static final ChunkBuilder.BuiltChunk[] MARKED = new ChunkBuilder.BuiltChunk[2];
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
        // Le recul vient apres la liberation des mondes distants : il entre dans un Level 0 jamais construit hors du cadre.
        return List.of(portalViewAndSeamlessCrossings(), remoteWorldReleasedAndForgotten(), crossingBackwardsThroughAnUnseenFrame(),
                handKeepsThePlayersLook());
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

    /**
     * Entrer a reculons dans un cadre qu'on ne regarde pas : sa vue ne se dessine pas, mais pres du cadre elle s'entretient
     * et le Level 0, jamais vu, est deja construit a la bascule. Rien n'y manque, aucun chunk n'y arrive ensuite. L'image,
     * elle, change d'un monde a l'autre : on ne voit pas, dos au cadre, ce qu'il y a de l'autre cote.
     */
    private static ClientScenario crossingBackwardsThroughAnUnseenFrame() {
        return ClientScenario.named("crossing_backwards_through_an_unseen_frame")
                .run("joueur dos au portail de l'Overworld", context -> {
                    ClientTestPortals.place(context, PortalSettings.DEFAULT);
                    ClientTestPortals.standAt(context, 0.5, 0.5, 180.0f, 0.0f);
                })
                .await("portail recu par le client", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("arrivee envoyee jusqu'a la distance de vue", PortalViewClientTests::arrivalWindowReceived, ClientTestPortals.PORTAL_TICKS)
                .run("marche a reculons vers le cadre", context -> {
                    ClientTestFrames.startRecording();
                    context.client().options.backKey.setPressed(true);
                })
                .await("bascule dans le Level 0", LevelZeroClientTests::inLevelZero, 200)
                .frames(AFTER_SWITCH_FRAMES)
                .run("arret", context -> context.client().options.backKey.setPressed(false))
                .check("Level 0 deja construit a la bascule", context -> {
                    List<ClientTestFrames.FrameStep> steps = ClientTestFrames.stopRecording();
                    requireArrivalReady(steps, seamOf(steps, "recul"), "recul");
                })
                .cleanup(context -> {
                    context.client().options.backKey.setPressed(false);
                    ClientTestFrames.stopRecording();
                    ClientTestPortals.remove(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * La main du joueur est de son monde, meme devant un cadre : ses pixels prennent le look du monde du joueur, et le
     * post-traitement du monde que montre le cadre ne la touche pas (sinon la silhouette du cadre se voit sur la main).
     * Colle au cadre, qui remplit l'ecran, le joueur voit moins de pixels du Level 0 avec sa main qu'en la cachant (F1). Les
     * tests du client cachent l'interface, main comprise : elle ne s'affiche que le temps de la premiere mesure.
     */
    private static ClientScenario handKeepsThePlayersLook() {
        int[] pixels = new int[2];
        return ClientScenario.named("hand_keeps_the_players_look")
                .run("joueur et portail dans l'Overworld, main vide", context -> {
                    ClientTestPortals.place(context, PortalSettings.DEFAULT);
                    ClientTestPortals.standAt(context, 0.5, 0.5, 0.0f, 0.0f);
                })
                .await("portail recu par le client", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 prete dans le cadre", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .run("joueur colle au cadre, main affichee", context -> {
                    ClientTestPortals.standAt(context, 0.5, HAND_TEST_Z, 0.0f, 0.0f);
                    context.client().options.hudHidden = false;
                })
                .frames(10)
                .run("pixels du Level 0, main dessinee", context -> pixels[0] = levelZeroPixels(context))
                .run("main cachee", context -> context.client().options.hudHidden = true)
                .frames(5)
                .run("pixels du Level 0, sans la main", context -> pixels[1] = levelZeroPixels(context))
                .check("la main garde le look du monde du joueur", context -> {
                    ClientTestContext.require(pixels[1] > 0, "Le cadre ne montre pas le Level 0 a l'ecran.");
                    ClientTestContext.require(pixels[0] < pixels[1], "Les pixels de la main gardent le look du Level 0 que montre le cadre : "
                            + pixels[0] + " avec la main, " + pixels[1] + " sans.");
                })
                .cleanup(context -> {
                    context.client().options.hudHidden = true;
                    ClientTestPortals.remove(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Pixels de la derniere image au look du Level 0 ; le profil, charge avec les ressources, se lit au moment de compter. */
    private static int levelZeroPixels(ClientTestContext context) {
        return ClientTestStencil.pixelsWithLook(context.client(), LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).id());
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
                // Pres du cadre, le serveur envoie l'arrivee jusqu'a la distance de vue : elle est complete avant le passage.
                .await("arrivee envoyee jusqu'a la distance de vue", PortalViewClientTests::arrivalWindowReceived, ClientTestPortals.PORTAL_TICKS)
                .run("marche vers le cadre", context -> {
                    ClientTestFrames.startRecording();
                    context.client().options.forwardKey.setPressed(true);
                })
                .await("bascule dans le Level 0", LevelZeroClientTests::inLevelZero, 200)
                // La marche continue apres la bascule : une image figee ensuite se verrait, meme en rendu logiciel lent.
                .frames(AFTER_SWITCH_FRAMES)
                .run("arret", context -> context.client().options.forwardKey.setPressed(false))
                .check("aller sans couture", context -> requireSeamless(ClientTestFrames.stopRecording(), "aller"))
                .await("tampons de construction du jeu au renderer du Level 0, deux a la vue de l'Overworld",
                        context -> buildBuffersFollowThePlayer(context, World.OVERWORLD), REBUILD_TICKS)
                // Sitot passe, le joueur se retourne vers le cadre d'ou il vient : ce qui entourait l'arrivee est deja construit.
                .run("demi-tour vers le cadre", context -> {
                    ClientTestFrames.startRecording();
                    turnAround(context);
                })
                .frames(TURN_FRAMES)
                .check("rien ne manque derriere soi", context -> requireNothingMissingBehind(ClientTestFrames.stopRecording()))
                .run("une section du Level 0 et une de la vue de l'Overworld a reconstruire", context -> markSections(context, World.OVERWORLD))
                .await("les deux renderers reconstruisent encore apres l'aller", PortalViewClientTests::sectionsRebuilt, REBUILD_TICKS)
                .run("de nouveau dos au cadre", PortalViewClientTests::turnAround)
                .ticks(10)
                .run("marche arriere vers le cadre de retour", context -> {
                    ClientTestFrames.startRecording();
                    context.client().options.backKey.setPressed(true);
                })
                .await("bascule dans l'Overworld", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 200)
                .frames(AFTER_SWITCH_FRAMES)
                .run("arret", context -> context.client().options.backKey.setPressed(false))
                .check("retour sans couture", context -> requireSeamless(ClientTestFrames.stopRecording(), "retour"))
                .await("tampons de construction du jeu au renderer de l'Overworld, deux a la vue du Level 0",
                        context -> buildBuffersFollowThePlayer(context, BackroomsConstants.LEVEL_ZERO_WORLD), REBUILD_TICKS)
                // Face au cadre qui s'eloigne : la vue du Level 0 se dessine.
                .run("une section de l'Overworld et une de la vue du Level 0 a reconstruire",
                        context -> markSections(context, BackroomsConstants.LEVEL_ZERO_WORLD))
                .await("les deux renderers reconstruisent encore apres le retour", PortalViewClientTests::sectionsRebuilt, REBUILD_TICKS)
                .cleanup(context -> {
                    context.client().options.forwardKey.setPressed(false);
                    context.client().options.backKey.setPressed(false);
                    ClientTestFrames.stopRecording();
                    ClientTestPortals.remove(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Le joueur fait demi-tour d'un coup, d'une image a la suivante. */
    private static void turnAround(ClientTestContext context) {
        ClientPlayerEntity player = context.client().player;
        player.setYaw(player.getYaw() + 180.0f);
        player.prevYaw = player.getYaw();
    }

    /**
     * Apres un demi-tour juste apres la bascule, aucune section que le brouillard du Level 0 laisse voir ne manque : les
     * anneaux construits d'avance autour de l'arrivee couvrent ce qui etait derriere le joueur.
     */
    private static void requireNothingMissingBehind(List<ClientTestFrames.FrameStep> steps) {
        int nearHoles = 0;
        for (ClientTestFrames.FrameStep step : steps) {
            nearHoles = Math.max(nearHoles, step.nearHoles());
        }
        ClientTestContext.require(!steps.isEmpty(), "Aucune image enregistree apres le demi-tour.");
        ClientTestContext.require(nearHoles == 0, "Section proche pas encore construite apres un demi-tour juste apres la bascule ("
                + nearHoles + " au plus).");
    }

    /**
     * Les tampons de construction suivent le joueur : son renderer, celui d'une vue promu par la traversee, a le compte de
     * vanilla (un par processeur), et la vue de {@code viewed}, son ancien renderer, redescend aux deux d'une vue.
     */
    private static boolean buildBuffersFollowThePlayer(ClientTestContext context, RegistryKey<World> viewed) {
        RemoteWorld remote = RemoteWorldRegistry.get(viewed);
        return remote != null && BuildBuffers.count(context.client().worldRenderer) == BuildBuffers.PLAYER_COUNT
                && BuildBuffers.count(remote.renderer()) == RemoteWorld.RENDER_BUFFER_COUNT;
    }

    /**
     * Rend a reconstruire une section deja construite, avec des blocs, que dessine le renderer du joueur, et une que dessine
     * la vue de {@code viewed} : chacun doit pouvoir encore construire apres une bascule, un bloc change de l'autre cote
     * comme sous les pieds du joueur.
     */
    private static void markSections(ClientTestContext context, RegistryKey<World> viewed) {
        RemoteWorld remote = RemoteWorldRegistry.get(viewed);
        MARKED[0] = mark(context.client().worldRenderer, context.client().world);
        MARKED[1] = remote == null ? null : mark(remote.renderer(), remote.world());
        ClientTestContext.require(MARKED[0] != null, "Aucune section construite a reconstruire dans le renderer du joueur.");
        ClientTestContext.require(MARKED[1] != null, "Aucune section construite a reconstruire dans la vue de " + viewed.getValue() + ".");
    }

    private static @Nullable ChunkBuilder.BuiltChunk mark(WorldRenderer renderer, ClientWorld world) {
        for (ChunkBuilder.BuiltChunk section : ((WorldRendererAccessor) renderer).backrooms$getBuiltChunks()) {
            BlockPos origin = section.getOrigin();
            WorldChunk chunk = world.getChunkManager().getWorldChunk(origin.getX() >> 4, origin.getZ() >> 4);
            int index = chunk == null ? -1 : chunk.getSectionIndex(origin.getY());
            if (section.getData() != ChunkBuilder.ChunkData.EMPTY && index >= 0 && index < chunk.getSectionArray().length
                    && !chunk.getSectionArray()[index].isEmpty()) {
                section.scheduleRebuild(false);
                return section;
            }
        }
        return null;
    }

    /** Les deux sections rendues a reconstruire l'ont ete. */
    private static boolean sectionsRebuilt(ClientTestContext context) {
        return ((SectionGenerations) MARKED[0]).backrooms$current() && ((SectionGenerations) MARKED[1]).backrooms$current();
    }

    /**
     * La bascule (premiere image dans l'autre monde, et la suivante) ne saute pas plus qu'un pas : leur ecart a l'image
     * precedente reste sous {@link #SEAM_FACTOR} fois l'ecart median des images voisines, plus {@link #SEAM_MARGIN} ; et
     * l'image continue de changer apres elle (le joueur marche encore), ce qu'un rendu fige ne fait pas.
     */
    private static void requireSeamless(List<ClientTestFrames.FrameStep> steps, String crossing) {
        int seam = seamOf(steps, crossing);
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
        requireArrivalReady(steps, seam, crossing);
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

    /** Premiere image enregistree dans l'autre monde, au milieu de l'enregistrement. */
    private static int seamOf(List<ClientTestFrames.FrameStep> steps, String crossing) {
        int seam = -1;
        for (int i = 1; i < steps.size(); i++) {
            if (!steps.get(i).world().equals(steps.get(i - 1).world())) {
                seam = i;
                break;
            }
        }
        ClientTestContext.require(seam > 0 && seam + 2 < steps.size(), "Aucune bascule enregistree au milieu du " + crossing + " (" + steps.size() + " images).");
        return seam;
    }

    /**
     * Images autour de la bascule, journalisees ; apres elle, aucun chunk ne doit arriver dans le monde du joueur et aucune
     * section que le joueur peut voir ne doit manquer : a moins de 48 blocs, la fin du brouillard du Level 0. Plus loin, le
     * renderer d'une vue devenu celui du joueur finit sa file avec ses deux tampons de construction, cache par le brouillard.
     */
    private static void requireArrivalReady(List<ClientTestFrames.FrameStep> steps, int seam, String crossing) {
        StringBuilder detail = new StringBuilder();
        int loadedAfter = 0;
        int nearHoles = 0;
        for (int i = Math.max(0, seam - 5); i < steps.size(); i++) {
            ClientTestFrames.FrameStep step = steps.get(i);
            detail.append(String.format(Locale.ROOT, "%n  %3d%s ecart %5.2f, %5.1f ms, sections %3d, trous %3d (proches %d), chunks %3d, +%d -%d, distants +%d,"
                    + " gc %d, %s", i - seam, i == seam ? "*" : " ", step.difference(), step.frameMillis(), step.builtSections(), step.holes(), step.nearHoles(),
                    step.loadedChunks(), step.chunkLoads(), step.chunkUnloads(), step.remoteChunkLoads(), step.collections(), step.world().getPath()));
            if (i >= seam) {
                loadedAfter += step.chunkLoads();
                nearHoles = Math.max(nearHoles, step.nearHoles());
            }
        }
        ModConstants.LOGGER.info("[Test client] Images autour de la bascule du {} :{}", crossing, detail);
        // Le client tient deja chaque chunk du monde d'arrivee (fenetre envoyee pres du cadre, ou monde quitte garde) : aucun
        // ne doit arriver en rafale apres la bascule, ni se reconstruire sous ses yeux. Et ce que le joueur voit en sortant
        // est deja construit (construction d'avance pres du cadre).
        ClientTestContext.require(loadedAfter == 0, "Chunks recus dans le monde du joueur apres la bascule du " + crossing + " : " + loadedAfter + ".");
        ClientTestContext.require(nearHoles == 0, "Section proche pas encore construite apres la bascule du " + crossing + " (" + nearHoles + " au plus).");
    }

    /**
     * Le monde distant du Level 0 tient la fenetre carree de l'arrivee : la distance de vue du joueur et l'anneau qui la
     * borde, ce que vanilla lui enverrait.
     */
    private static boolean arrivalWindowReceived(ClientTestContext context) {
        RemoteWorld remote = RemoteWorldRegistry.get(BackroomsConstants.LEVEL_ZERO_WORLD);
        int side = 2 * (context.client().options.getClampedViewDistance() + 1) + 1;
        return remote != null && remote.world().getChunkManager().getLoadedChunkCount() >= side * side;
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
