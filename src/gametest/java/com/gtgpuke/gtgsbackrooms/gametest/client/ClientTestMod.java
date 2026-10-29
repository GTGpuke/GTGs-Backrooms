package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ClientModInitializer;

/**
 * Point d'entree des tests du client, lances par {@code ./gradlew runClientTest} : un vrai client, fenetre cachee, entre
 * dans un monde de test et y joue chaque test ({@link ClientScenario}) sous l'agent de couverture. Inerte sans la
 * propriete {@code gtgsbackrooms.clientTest.report} (chemin du rapport), que seul ce lancement pose.
 */
public final class ClientTestMod implements ClientModInitializer {

    /** Propriete du chemin du rapport ; son absence laisse le mod de test inerte. */
    public static final String REPORT_PROPERTY = "gtgsbackrooms.clientTest.report";

    @Override
    public void onInitializeClient() {
        String report = System.getProperty(REPORT_PROPERTY);
        if (report == null) {
            return;
        }
        List<ClientScenario> scenarios = new ArrayList<>();
        scenarios.addAll(LevelZeroClientTests.scenarios());
        scenarios.addAll(EngineRulesClientTests.scenarios());
        scenarios.addAll(PortalViewClientTests.scenarios());
        scenarios.addAll(PortalFeaturesClientTests.scenarios());
        // Mise au point : -PclientTestOnly=nom1,nom2 ne joue que ces tests.
        String only = System.getProperty("gtgsbackrooms.clientTest.only");
        if (only != null && !only.isBlank()) {
            List<String> names = List.of(only.split(","));
            scenarios.removeIf(scenario -> !names.contains(scenario.name()));
        }
        new ClientTestRunner(Path.of(report), scenarios).start();
    }
}
