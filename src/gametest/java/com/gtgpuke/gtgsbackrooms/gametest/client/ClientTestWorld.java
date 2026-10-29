package com.gtgpuke.gtgsbackrooms.gametest.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Le monde des tests du client : un monde plat neuf (graine fixe, creatif, commandes permises), sans cycle du jour ni
 * meteo, sans apparition de creatures ni tick aleatoire, pour des images qui ne dependent que du test. Le Level 0 y est,
 * comme dans tout monde avec le mod (dimension du datapack du mod).
 */
final class ClientTestWorld {

    /** Nom du dossier du monde, vide par Gradle avant chaque lancement. */
    static final String NAME = "clienttest";
    private static final long SEED = 0L;

    private ClientTestWorld() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Cree et ouvre le monde de test ; le client y entre quelques ticks plus tard. */
    static void create(MinecraftClient client) {
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        rules.get(GameRules.DO_FIRE_TICK).set(false, null);
        rules.get(GameRules.RANDOM_TICK_SPEED).set(0, null);
        LevelInfo info = new LevelInfo(NAME, GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(NAME, info, new GeneratorOptions(SEED, false, false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder(), null);
    }
}
