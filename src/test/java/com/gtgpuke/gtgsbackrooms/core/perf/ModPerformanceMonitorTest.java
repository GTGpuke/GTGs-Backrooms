package com.gtgpuke.gtgsbackrooms.core.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Moniteur de performance")
class ModPerformanceMonitorTest {

    @AfterEach
    void disable() {
        ModPerformanceMonitor.setEnabled(false);
    }

    @Test
    @DisplayName("Desactive par defaut, le moniteur ne fait rien et n'alloue rien")
    void disabledIsNoOp() {
        assertFalse(ModPerformanceMonitor.isEnabled(), "Inactif par defaut.");
        ModPerformanceMonitor.Scope first = ModPerformanceMonitor.scope("test.section");
        ModPerformanceMonitor.Scope second = ModPerformanceMonitor.scope("other.section");
        assertSame(first, second, "Le scope inactif est une instance partagee.");
        first.close();

        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        ModPerformanceMonitor.onClientTick(60);
        ModPerformanceMonitor.onFrameRendered();
        ModPerformanceMonitor.onPayloadSent("portal_remote_chunk", 4096);
        assertEquals("aucun", ModPerformanceMonitor.networkSummary(), "Inactif, rien n'est compte sur le reseau.");
        List<String> hud = new ArrayList<>();
        ModPerformanceMonitor.appendDebugHudText(hud);
        assertTrue(hud.isEmpty(), "Rien n'est ajoute au F3 quand le moniteur est inactif.");
    }

    @Test
    @DisplayName("Le resume client donne l'image la plus longue, les images lentes et ses propres sections")
    void clientSummaryHasFramePeaksAndClientSections() throws InterruptedException {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.scope("client.test.render").close();
        ModPerformanceMonitor.scope("test.server").close();
        ModPerformanceMonitor.onFrameRendered();
        Thread.sleep(60L);
        ModPerformanceMonitor.onFrameRendered();
        ModPerformanceMonitor.onClientTick(90);
        String client = ModPerformanceMonitor.lastClientSummary();
        assertTrue(client.startsWith("client: fps=90 pic="), "Resume client avec l'image la plus longue : " + client);
        assertTrue(client.contains(" lentes=1 ") || client.contains(" lentes=2 "), "L'image de soixante millisecondes est lente : " + client);
        assertTrue(client.endsWith("top=client.test.render:1x/" + client.substring(client.indexOf("top=client.test.render:1x/") + 26)),
                "Les sections client sont dans le resume client : " + client);
        assertFalse(client.contains("test.server"), "Les sections serveur n'y sont pas : " + client);
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        String server = ModPerformanceMonitor.lastServerSummary();
        assertTrue(server.contains("test.server:1x/") && !server.contains("client.test.render"),
                "Les sections serveur sont dans le resume serveur, sans celles du client : " + server);
        // Le resume a remis les images a zero : le suivant repart de rien.
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onClientTick(91);
        assertTrue(ModPerformanceMonitor.lastClientSummary().startsWith("client: fps=91 pic=0.0ms lentes=0 "),
                "Images remises a zero : " + ModPerformanceMonitor.lastClientSummary());
    }

    @Test
    @DisplayName("Le resume serveur compte les paquets et les octets par type de payload, du plus lourd au plus leger")
    void serverSummaryCountsNetworkByPayload() {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.onPayloadSent("portal_remote_entity", 10);
        ModPerformanceMonitor.onPayloadSent("portal_remote_chunk", 2048);
        ModPerformanceMonitor.onPayloadSent("portal_remote_chunk", 1024);
        assertEquals("portal_remote_chunk:2x/3.0KiB | portal_remote_entity:1x/0.0KiB", ModPerformanceMonitor.networkSummary(),
                "Lecture sans remise a zero.");
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        String server = ModPerformanceMonitor.lastServerSummary();
        assertTrue(server.endsWith("reseau=portal_remote_chunk:2x/3.0KiB | portal_remote_entity:1x/0.0KiB"),
                "Le resume serveur donne le reseau : " + server);
        assertEquals("aucun", ModPerformanceMonitor.networkSummary(), "Le resume a remis les compteurs a zero.");
    }

    @Test
    @DisplayName("Active, le moniteur mesure les sections et publie des resumes serveur et client")
    void enabledRecordsAndSummarizes() {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.resetLogTimers();
        assertTrue(ModPerformanceMonitor.isEnabled(), "Actif a chaud.");

        ModPerformanceMonitor.Scope scope = ModPerformanceMonitor.scope("test.generation");
        assertNotSame(ModPerformanceMonitor.scope("test.other"), scope, "Chaque scope actif est distinct.");
        scope.close();

        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        ModPerformanceMonitor.onClientTick(120);

        String server = ModPerformanceMonitor.lastServerSummary();
        assertTrue(server.startsWith("serveur: mspt="), "Resume serveur publie : " + server);
        assertTrue(server.contains("test.generation"), "La section mesuree apparait dans le top : " + server);
        assertTrue(server.contains("heap="), "La memoire est rapportee.");

        List<String> hud = new ArrayList<>();
        ModPerformanceMonitor.appendDebugHudText(hud);
        assertEquals(4, hud.size(), "Quatre lignes au F3.");
        assertEquals("Perf monitor : actif", hud.get(0), "Entete du F3.");
        assertTrue(hud.get(2).startsWith("client: fps=120"), "Resume client avec les FPS : " + hud.get(2));
    }

    @Test
    @DisplayName("Un resume est du la premiere fois, puis une fois l'intervalle ecoule")
    void summaryDueDates() {
        assertTrue(ModPerformanceMonitor.isDue(0L, 5L, 100L), "Jamais publie : du.");
        assertFalse(ModPerformanceMonitor.isDue(100L, 199L, 100L), "Dans l'intervalle : pas encore.");
        assertTrue(ModPerformanceMonitor.isDue(100L, 200L, 100L), "Intervalle ecoule : du.");
    }

    @Test
    @DisplayName("Un second resume dans l'intervalle est ignore")
    void summariesAreRateLimited() {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        String first = ModPerformanceMonitor.lastServerSummary();
        ModPerformanceMonitor.scope("test.late").close();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        assertEquals(first, ModPerformanceMonitor.lastServerSummary(), "Pas de nouveau resume avant l'intervalle.");
        assertFalse(first.contains("test.late"), "La section tardive attend le prochain resume.");
    }

    @Test
    @DisplayName("Sans section mesuree le resume le dit ; a plusieurs, elles sont classees et separees")
    void topSectionsFormatting() {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        assertTrue(ModPerformanceMonitor.lastServerSummary().contains(" top=aucune section reseau="),
                "Les sections ont ete videes par le resume precedent : " + ModPerformanceMonitor.lastServerSummary());
        ModPerformanceMonitor.scope("test.alpha").close();
        ModPerformanceMonitor.scope("test.beta").close();
        ModPerformanceMonitor.scope("test.beta").close();
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        String summary = ModPerformanceMonitor.lastServerSummary();
        assertTrue(summary.contains("test.alpha:1x/") && summary.contains("test.beta:2x/"), "Les deux sections et leurs appels : " + summary);
        assertTrue(summary.contains(" | "), "Les sections sont separees : " + summary);
        assertTrue(summary.contains("ms/alloc ") && summary.contains("KiB"), "Chaque section donne ce qu'elle a alloue : " + summary);
    }

    @Test
    @DisplayName("Sans compteur d'allocation par thread, les sections n'allouent rien")
    void allocationWithoutThreadBean() {
        java.lang.management.ThreadMXBean plain = (java.lang.management.ThreadMXBean) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {java.lang.management.ThreadMXBean.class}, (proxy, method, args) -> null);
        assertNull(ModPerformanceMonitor.threadAllocationBean(plain), "Un bean sans l'extension ne compte pas.");
        ThreadMXBean unsupported = (ThreadMXBean) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ThreadMXBean.class},
                (proxy, method, args) -> method.getName().equals("isThreadAllocatedMemorySupported") ? Boolean.FALSE : null);
        assertNull(ModPerformanceMonitor.threadAllocationBean(unsupported), "Un bean qui ne compte pas les allocations ne sert pas.");
        assertEquals(0L, ModPerformanceMonitor.allocatedBytes(null), "Sans compteur, zero.");
        assertTrue(ModPerformanceMonitor.allocatedBytes(ModPerformanceMonitor.threadAllocationBean(ManagementFactory.getThreadMXBean())) > 0L,
                "La JVM de test compte ce que son thread alloue.");
    }

    @Test
    @DisplayName("La charge CPU vient d'un echantillonneur a part : inconnue sans releve ou sans extension JVM, lue sans attendre ensuite")
    void cpuLoadComesFromTheSampler() throws InterruptedException {
        ModPerformanceMonitor.CpuSampler withoutBean = new ModPerformanceMonitor.CpuSampler(null);
        withoutBean.sample();
        ModPerformanceMonitor.Snapshot snapshot = ModPerformanceMonitor.Snapshot.capture(withoutBean);
        assertTrue(snapshot.heapMaxBytes() >= snapshot.heapUsedBytes(), "La memoire reste lue.");
        assertTrue(snapshot.format().endsWith("cpuProc=n/a cpuSys=n/a"), "Sans extension JVM, charge inconnue : " + snapshot.format());
        withoutBean.start(1_000_000_000L);
        assertFalse(withoutBean.running(), "Sans extension JVM, aucun thread ne tourne.");
        assertEquals("n/a", ModPerformanceMonitor.Snapshot.formatPercent(-1.0D), "Charge negative : inconnue.");
        assertEquals("50.0%", ModPerformanceMonitor.Snapshot.formatPercent(0.5D), "Charge connue en pourcentage.");

        OperatingSystemMXBean bean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        ModPerformanceMonitor.CpuSampler sampler = new ModPerformanceMonitor.CpuSampler(bean);
        assertTrue(ModPerformanceMonitor.Snapshot.capture(sampler).format().endsWith("cpuProc=n/a cpuSys=n/a"),
                "Avant tout releve, la charge est inconnue : le thread de jeu n'attend pas.");
        // La JVM rend une charge negative (inconnue) tant qu'elle n'a pas deux lectures : quelques releves peuvent y passer.
        long known = System.nanoTime() + 5_000_000_000L;
        sampler.sample();
        while ((sampler.processCpuLoad() < 0.0D || sampler.systemCpuLoad() < 0.0D) && System.nanoTime() < known) {
            Thread.sleep(10L);
            sampler.sample();
        }
        assertTrue(sampler.processCpuLoad() >= 0.0D && sampler.systemCpuLoad() >= 0.0D,
                "Un releve donne une charge connue : " + sampler.processCpuLoad() + " / " + sampler.systemCpuLoad() + ".");
        // Un intervalle d'une milliseconde : la boucle fait plusieurs tours avant l'arret.
        long before = sampler.samples();
        sampler.start(1_000_000L);
        assertTrue(sampler.running(), "L'echantillonneur tourne sur son thread.");
        sampler.start(1_000_000L);
        Thread thread = samplerThread();
        assertTrue(thread != null, "Son thread porte le nom du mod.");
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (sampler.samples() < before + 3 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertTrue(sampler.samples() >= before + 3, "La boucle releve a chaque intervalle : " + (sampler.samples() - before) + " releves.");
        sampler.stop();
        assertFalse(sampler.running() || thread.isAlive(), "Arrete, il ne tourne plus : son thread a fini avant de rendre la main.");
        assertTrue(ModPerformanceMonitor.Snapshot.capture(sampler).format().endsWith("cpuProc=n/a cpuSys=n/a"),
                "Arrete, ses valeurs sont oubliees.");
        sampler.stop();
        // Arrete depuis un thread deja interrompu : l'attente cede tout de suite et l'interruption est gardee.
        sampler.start(1_000_000_000L);
        Thread.currentThread().interrupt();
        sampler.stop();
        assertTrue(Thread.interrupted(), "L'interruption du thread appelant est gardee.");
        assertFalse(sampler.running(), "Arrete quand meme.");
        // La boucle de l'echantillonneur : un releve puis l'attente, qu'une interruption abrege et termine.
        assertTrue(sampler.sampleThenWait(1L), "Sans interruption, la boucle continue.");
        Thread.currentThread().interrupt();
        assertFalse(sampler.sampleThenWait(1_000_000_000L), "Interrompue pendant l'attente, la boucle s'arrete sans attendre.");
        assertTrue(Thread.interrupted(), "Et l'interruption est gardee.");
    }

    @Test
    @DisplayName("Activer le moniteur lance l'echantillonneur CPU, le couper l'arrete")
    void enablingStartsTheSampler() {
        ModPerformanceMonitor.setEnabled(true);
        Thread sampler = samplerThread();
        assertTrue(sampler != null && sampler.isDaemon(), "Active a chaud, le moniteur lance un thread demon qui echantillonne la charge CPU.");
        // Actif des le lancement (propriete systeme), l'echantillonneur pas encore lance : le demarrage du mod le lance.
        ModPerformanceMonitor.CPU_SAMPLER.stop();
        assertNull(samplerThread(), "Arrete, son thread a fini.");
        ModPerformanceMonitor.init();
        Thread started = samplerThread();
        assertTrue(started != null && started.isDaemon(), "Actif au lancement, le demarrage du mod lance l'echantillonneur.");
        ModPerformanceMonitor.setEnabled(false);
        assertFalse(started.isAlive(), "Coupe, l'echantillonneur s'arrete avant de rendre la main.");
        ModPerformanceMonitor.init();
        assertNull(samplerThread(), "Inactif au lancement, rien ne demarre.");
    }

    private static @Nullable Thread samplerThread() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals("gtgs-backrooms-cpu-sampler") && thread.isAlive()) {
                return thread;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Une fin de tick sans debut est ignoree, et un second echantillon client dans l'intervalle aussi")
    void endWithoutStartAndRateLimitedClient() {
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickStart();
        ModPerformanceMonitor.onServerTickEnd();
        String summary = ModPerformanceMonitor.lastServerSummary();
        ModPerformanceMonitor.resetLogTimers();
        ModPerformanceMonitor.onServerTickEnd();
        assertEquals(summary, ModPerformanceMonitor.lastServerSummary(), "Sans debut de tick, rien n'est mesure ni publie.");
        ModPerformanceMonitor.onClientTick(30);
        ModPerformanceMonitor.onClientTick(31);
        List<String> hud = new ArrayList<>();
        ModPerformanceMonitor.appendDebugHudText(hud);
        assertTrue(hud.get(2).startsWith("client: fps=30"), "Le second echantillon client, dans l'intervalle, est ignore : " + hud.get(2));
    }

    @Test
    @DisplayName("Une traversee mesure la bascule puis les images qui suivent, et compte les chunks recus")
    void crossingMeasurement() throws InterruptedException {
        ModPerformanceMonitor.setEnabled(true);
        String before = ModPerformanceMonitor.lastCrossingSummary();
        ModPerformanceMonitor.onFrameRendered();
        ModPerformanceMonitor.onChunkReceived();
        ModPerformanceMonitor.onCrossingSwitched(Identifier.ofVanilla("the_nether"));
        assertEquals(before, ModPerformanceMonitor.lastCrossingSummary(), "Sans debut de mesure, une bascule est ignoree.");
        ModPerformanceMonitor.onCrossingStart();
        ModPerformanceMonitor.onCrossingSwitched(Identifier.ofVanilla("the_nether"));
        ModPerformanceMonitor.onChunkReceived();
        ModPerformanceMonitor.onChunkReceived();
        ModPerformanceMonitor.onFrameRendered();
        Thread.sleep(60L);
        ModPerformanceMonitor.onFrameRendered();
        for (int frame = 2; frame < ModPerformanceMonitor.CROSSING_FRAMES; frame++) {
            ModPerformanceMonitor.onFrameRendered();
        }
        String summary = ModPerformanceMonitor.lastCrossingSummary();
        assertTrue(summary.startsWith("traversee: vers minecraft:the_nether bascule="), "Bilan publie : " + summary + ".");
        // Au moins l'image de soixante millisecondes : une pause de la JVM pendant la boucle en ajouterait d'autres.
        assertTrue(summary.contains("/" + (ModPerformanceMonitor.CROSSING_FRAMES - 1)) && !summary.contains("lentes=0/"),
                "L'image de soixante millisecondes est lente : " + summary + ".");
        assertTrue(summary.endsWith("chunks=2"), "Les chunks de la fenetre sont comptes : " + summary + ".");
        List<String> hud = new ArrayList<>();
        ModPerformanceMonitor.appendDebugHudText(hud);
        assertEquals(summary, hud.get(3), "Le bilan de traversee est au F3.");
    }

    @Test
    @DisplayName("Inactif, le moniteur ignore les traversees ; coupe en pleine fenetre, il l'abandonne")
    void crossingIgnoredWhenDisabled() {
        String before = ModPerformanceMonitor.lastCrossingSummary();
        ModPerformanceMonitor.onCrossingStart();
        ModPerformanceMonitor.onCrossingSwitched(Identifier.ofVanilla("the_end"));
        ModPerformanceMonitor.onFrameRendered();
        ModPerformanceMonitor.onChunkReceived();
        assertEquals(before, ModPerformanceMonitor.lastCrossingSummary(), "Inactif : rien n'est mesure.");
        ModPerformanceMonitor.setEnabled(true);
        ModPerformanceMonitor.onCrossingStart();
        ModPerformanceMonitor.onCrossingSwitched(Identifier.ofVanilla("the_end"));
        ModPerformanceMonitor.onFrameRendered();
        ModPerformanceMonitor.setEnabled(false);
        for (int frame = 0; frame <= ModPerformanceMonitor.CROSSING_FRAMES; frame++) {
            ModPerformanceMonitor.onFrameRendered();
        }
        assertEquals(before, ModPerformanceMonitor.lastCrossingSummary(), "Coupe en pleine fenetre, la mesure est abandonnee sans bilan.");
    }
}
