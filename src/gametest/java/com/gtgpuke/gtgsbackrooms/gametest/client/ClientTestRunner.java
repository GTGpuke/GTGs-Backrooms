package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.SortedArraySet;
import net.minecraft.util.math.ChunkPos;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticWorker;
import com.gtgpuke.gtgsbackrooms.core.audio.client.SolidityIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.gametest.client.mixin.ClientTestChunkLoadingAccessor;
import com.gtgpuke.gtgsbackrooms.gametest.client.mixin.ClientTestServerChunkManagerAccessor;
import com.gtgpuke.gtgsbackrooms.gametest.client.mixin.ClientTestTicketsAccessor;

/**
 * Joue les tests du client, tick apres tick : attend la fin du chargement, cree le monde de test, y entre, joue chaque
 * test etape par etape, quitte le monde (la fin de partie est verifiee comme un test), ecrit le rapport puis ferme le
 * client. Un chien de garde ecrit le rapport et quitte si plus rien n'avance (client bloque), pour qu'aucun lancement ne
 * reste suspendu.
 */
final class ClientTestRunner {

    /** Ticks laisses au client pour entrer dans le monde de test. */
    private static final int JOIN_TIMEOUT_TICKS = 2400;
    /** Ticks d'attente apres l'entree, le temps que le terrain autour du joueur se construise. */
    private static final int SETTLE_TICKS = 40;
    private static final long WATCHDOG_MILLIS = 5 * 60_000L;
    private static final long WATCHDOG_PERIOD_MILLIS = 5_000L;
    /** Delai de fermeture du client apres le dernier test. */
    private static final long SHUTDOWN_MILLIS = 3 * 60_000L;
    /** Ticks laisses au serveur integre pour s'arreter, puis a la partie pour tout rendre, apres la sortie du monde. */
    private static final int LEAVE_TIMEOUT_TICKS = 3600;
    /** Nom du test de la fin de partie dans le rapport. */
    private static final String END_OF_GAME = "end_of_game";

    private enum Phase {
        LOADING, JOINING, RUNNING, LEAVING, DONE
    }

    private final Path reportFile;
    private final List<ClientScenario> scenarios;
    private final ClientTestReport report = new ClientTestReport();
    /** Lue aussi par le chien de garde. */
    private volatile Phase phase = Phase.LOADING;
    private boolean glErrorsWatched;
    private int joinTicks;
    private int settleTicks;
    private int scenarioIndex;
    private int stepIndex;
    private boolean stepStarted;
    private int stepTicks;
    private long scenarioStart;
    private ClientTestContext context;
    /** Lu aussi par le chien de garde. */
    private volatile @Nullable IntegratedServer leftServer;
    private int leaveTicks;
    private volatile long lastProgress = System.currentTimeMillis();
    private volatile String lastStep = "chargement";

    ClientTestRunner(Path reportFile, List<ClientScenario> scenarios) {
        this.reportFile = reportFile;
        this.scenarios = scenarios;
    }

    void start() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        Thread watchdog = new Thread(this::watch, "Chien de garde des tests du client");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void tick(MinecraftClient client) {
        switch (phase) {
            case LOADING -> {
                if (!glErrorsWatched) {
                    glErrorsWatched = true;
                    ClientTestGlErrors.install();
                }
                if (client.getOverlay() == null && client.isFinishedLoading()) {
                    progress("creation du monde de test");
                    ClientTestWorld.create(client);
                    phase = Phase.JOINING;
                }
            }
            case JOINING -> join(client);
            case RUNNING -> runStep();
            case LEAVING -> checkEndOfGame(client);
            case DONE -> {
            }
        }
    }

    private void join(MinecraftClient client) {
        if (++joinTicks > JOIN_TIMEOUT_TICKS) {
            report.failed("entree dans le monde de test", 0, "Le client n'est pas entre dans le monde de test en " + JOIN_TIMEOUT_TICKS + " ticks.", null);
            finishAll(client);
            return;
        }
        if (client.world == null || client.player == null || client.currentScreen != null) {
            return;
        }
        client.options.hudHidden = true;
        if (++settleTicks < SETTLE_TICKS) {
            return;
        }
        context = new ClientTestContext(client);
        if (ClientTestGlErrors.count() > 0) {
            report.failed("chargement et entree dans le monde", 0, ClientTestGlErrors.describe(), null);
            ModConstants.LOGGER.error("[Test client] Chargement : {}", ClientTestGlErrors.describe());
        }
        ClientTestGlErrors.reset();
        phase = Phase.RUNNING;
        scenarioStart = System.currentTimeMillis();
        progress("debut des tests");
        ModConstants.LOGGER.info("[Test client] Monde de test pret : {} test(s) a jouer.", scenarios.size());
    }

    private void runStep() {
        MinecraftClient client = context.client();
        if (scenarioIndex >= scenarios.size()) {
            leave(client);
            return;
        }
        ClientScenario scenario = scenarios.get(scenarioIndex);
        if (stepIndex >= scenario.steps().size()) {
            finishScenario(scenario, null, null);
            return;
        }
        ClientScenario.Step step = scenario.steps().get(stepIndex);
        try {
            if (!stepStarted) {
                progress(scenario.name() + " : " + step.what());
                stepStarted = true;
                stepTicks = 0;
                step.action().accept(context);
            }
            if (step.until().test(context)) {
                stepIndex++;
                stepStarted = false;
                progress(scenario.name() + " : " + step.what() + " (fait)");
                return;
            }
            if (++stepTicks > step.timeoutTicks()) {
                throw new AssertionError("Attente expiree (" + step.timeoutTicks() + " ticks).");
            }
        } catch (Throwable failure) {
            finishScenario(scenario, step, failure);
        }
    }

    private void finishScenario(ClientScenario scenario, ClientScenario.Step failedStep, Throwable failure) {
        for (Consumer<ClientTestContext> cleanup : scenario.cleanups()) {
            try {
                cleanup.accept(context);
            } catch (Throwable cleanupFailure) {
                ModConstants.LOGGER.error("[Test client] Nettoyage de {} en echec.", scenario.name(), cleanupFailure);
            }
        }
        long millis = System.currentTimeMillis() - scenarioStart;
        String failedWhat = failedStep == null ? "" : failedStep.what();
        if (failure == null && ClientTestGlErrors.count() > 0) {
            failedWhat = "aucune erreur OpenGL";
            failure = new AssertionError(ClientTestGlErrors.describe());
        } else if (failure != null && ClientTestGlErrors.count() > 0) {
            // Les erreurs OpenGL disent souvent pourquoi le test a echoue : elles accompagnent l'echec.
            failure = new AssertionError(failure.getMessage() + System.lineSeparator() + "Et " + ClientTestGlErrors.describe(), failure);
        }
        ClientTestGlErrors.reset();
        if (failure == null) {
            report.passed(scenario.name(), millis);
            ModConstants.LOGGER.info("[Test client] {} : reussi.", scenario.name());
        } else {
            String message = "Etape \"" + failedWhat + "\" : " + failure.getMessage();
            report.failed(scenario.name(), millis, message, failure);
            ModConstants.LOGGER.error("[Test client] {} : ECHEC, {}", scenario.name(), message, failure);
        }
        scenarioIndex++;
        stepIndex = 0;
        stepStarted = false;
        scenarioStart = System.currentTimeMillis();
        // Ecrit apres chaque test : un client qui plante ensuite laisse les resultats deja joues.
        writeReport();
    }

    /**
     * Apres le dernier test, le client quitte le monde comme "Sauvegarder et quitter" : la fin de partie est un test de
     * plus ({@link #END_OF_GAME}), joue une fois le serveur integre arrete.
     */
    private void leave(MinecraftClient client) {
        if (ClientTestGlErrors.count() > 0) {
            // Erreurs apres le dernier nettoyage : imputees a la fin des tests, pas perdues.
            report.failed("fin des tests", 0, ClientTestGlErrors.describe(), null);
            ClientTestGlErrors.reset();
        }
        writeReport();
        progress("fin de partie : sortie du monde de test");
        leftServer = client.getServer();
        leaveTicks = 0;
        scenarioStart = System.currentTimeMillis();
        phase = Phase.LEAVING;
        if (client.world != null) {
            client.world.disconnect();
        }
        client.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
    }

    /**
     * Fin de partie : le serveur integre s'arrete, puis tout ce que la partie tenait est rendu (mondes distants, index des
     * neons et du son, fenetres du thread de calcul du son), sans erreur OpenGL.
     */
    private void checkEndOfGame(MinecraftClient client) {
        leaveTicks++;
        IntegratedServer server = leftServer;
        if (server != null && !server.isStopped()) {
            if (leaveTicks > LEAVE_TIMEOUT_TICKS) {
                ModConstants.LOGGER.error("[Test client] Le serveur integre ne s'arrete pas apres la sortie du monde.");
                logServerChunks(server);
                logThreads();
                endOfGame(client, "Le serveur integre ne s'est pas arrete en " + LEAVE_TIMEOUT_TICKS + " ticks apres la sortie du monde.");
            }
            return;
        }
        leftServer = null;
        String retained = retained(client);
        if (retained != null && leaveTicks <= LEAVE_TIMEOUT_TICKS) {
            return;
        }
        endOfGame(client, retained);
    }

    /** Ce que la partie quittee tient encore, ou {@code null} : tout est rendu. */
    private static @Nullable String retained(MinecraftClient client) {
        if (client.world != null) {
            return "Le client est encore dans un monde.";
        }
        if (RemoteWorldRegistry.remoteWorldCount() > 0) {
            return RemoteWorldRegistry.remoteWorldCount() + " monde(s) distant(s) encore tenu(s).";
        }
        if (NeonIndex.trackedWorlds() > 0 || SolidityIndex.trackedWorlds() > 0) {
            return "Index des neons ou copie du monde pour le son encore tenus (" + NeonIndex.trackedWorlds() + ", " + SolidityIndex.trackedWorlds() + ").";
        }
        if (AcousticWorker.holdsWindows()) {
            return "Le thread de calcul du son tient encore les sections de la partie dans ses fenetres.";
        }
        return null;
    }

    private void endOfGame(MinecraftClient client, @Nullable String failure) {
        long millis = System.currentTimeMillis() - scenarioStart;
        String message = failure;
        if (ClientTestGlErrors.count() > 0) {
            message = (message == null ? "" : message + System.lineSeparator() + "Et ") + ClientTestGlErrors.describe();
            ClientTestGlErrors.reset();
        }
        if (message == null) {
            report.passed(END_OF_GAME, millis);
            ModConstants.LOGGER.info("[Test client] {} : reussi.", END_OF_GAME);
        } else {
            report.failed(END_OF_GAME, millis, message, null);
            ModConstants.LOGGER.error("[Test client] {} : ECHEC, {}", END_OF_GAME, message);
        }
        finishAll(client);
    }

    private void finishAll(MinecraftClient client) {
        phase = Phase.DONE;
        writeReport();
        ModConstants.LOGGER.info("[Test client] {} test(s), {} echec(s).", report.size(), report.failures());
        progress("arret du client");
        client.scheduleStop();
    }

    private synchronized void writeReport() {
        try {
            report.write(reportFile);
        } catch (IOException exception) {
            ModConstants.LOGGER.error("[Test client] Rapport impossible a ecrire : {}.", reportFile, exception);
        }
    }

    private void progress(String step) {
        lastStep = step;
        lastProgress = System.currentTimeMillis();
    }

    private void watch() {
        while (true) {
            try {
                Thread.sleep(WATCHDOG_PERIOD_MILLIS);
            } catch (InterruptedException interrupted) {
                return;
            }
            long idle = System.currentTimeMillis() - lastProgress;
            if (phase != Phase.DONE && idle > WATCHDOG_MILLIS) {
                report.failed("blocage", 0, "Plus rien n'avance depuis " + WATCHDOG_MILLIS / 1000 + " s ; derniere etape : " + lastStep + ".", null);
                writeReport();
                ModConstants.LOGGER.error("[Test client] Blocage apres l'etape : {}. Arret.", lastStep);
                logServerChunks(leftServer);
                logThreads();
                System.exit(3);
            }
            if (phase == Phase.DONE && idle > SHUTDOWN_MILLIS) {
                // Le client ne se ferme pas (serveur integre qui ne finit pas de decharger ses mondes) : un echec, pas une attente sans fin.
                report.failed("arret du client", 0, "Le client ne s'est pas ferme en " + SHUTDOWN_MILLIS / 1000 + " s apres les tests.", null);
                writeReport();
                ModConstants.LOGGER.error("[Test client] Le client ne se ferme pas apres les tests. Arret force.");
                logServerChunks(MinecraftClient.getInstance().getServer());
                logThreads();
                Runtime.getRuntime().halt(4);
            }
        }
    }

    /**
     * Les chunks que chaque monde du serveur integre tient encore (a decharger, tickets par type), quand son arret ne
     * finit pas. Lu depuis un autre thread que celui du serveur : un instantane approximatif, assez pour un diagnostic.
     */
    private static void logServerChunks(@Nullable IntegratedServer server) {
        if (server == null) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            try {
                ClientTestChunkLoadingAccessor loading = (ClientTestChunkLoadingAccessor) world.getChunkManager().chunkLoadingManager;
                ChunkTicketManager tickets = ((ClientTestServerChunkManagerAccessor) world.getChunkManager()).gtgsTest$getTicketManager();
                Map<String, Integer> byType = new TreeMap<>();
                for (SortedArraySet<ChunkTicket<?>> set : ((ClientTestTicketsAccessor) tickets).gtgsTest$getTicketsByPosition().values()) {
                    for (ChunkTicket<?> ticket : set) {
                        byType.merge(ticket.getType().toString(), 1, Integer::sum);
                    }
                }
                StringBuilder unloading = new StringBuilder();
                int shown = 0;
                for (long pos : loading.gtgsTest$getChunksToUnload().keySet()) {
                    if (shown++ == 8) {
                        break;
                    }
                    unloading.append(' ').append(new ChunkPos(pos)).append(" (tickets ")
                            .append(((ClientTestTicketsAccessor) tickets).gtgsTest$getTicketsByPosition().get(pos)).append(')');
                }
                ModConstants.LOGGER.error("[Test client] Monde {} : arret retarde {}, {} chunk(s) tenu(s), {} a decharger :{} ; tickets {}.",
                        world.getRegistryKey().getValue(), world.getChunkManager().chunkLoadingManager.shouldDelayShutdown(),
                        loading.gtgsTest$getCurrentChunkHolders().size(), loading.gtgsTest$getChunksToUnload().size(), unloading, byType);
            } catch (RuntimeException exception) {
                ModConstants.LOGGER.error("[Test client] Monde {} : etat des chunks illisible.", world.getRegistryKey().getValue(), exception);
            }
        }
    }

    /** La pile de chaque thread dans le journal, avant un arret force : de quoi savoir ce qui tournait encore. */
    private static void logThreads() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            StringBuilder stack = new StringBuilder();
            for (StackTraceElement element : entry.getValue()) {
                stack.append("\n    at ").append(element);
            }
            ModConstants.LOGGER.error("[Test client] Thread {} ({}) :{}", entry.getKey().getName(), entry.getKey().getState(), stack);
        }
    }
}
