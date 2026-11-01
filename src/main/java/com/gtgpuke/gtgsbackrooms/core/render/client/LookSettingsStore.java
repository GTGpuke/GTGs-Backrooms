package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * Les reglages du menu sur le disque, cote client : {@code config/gtgs_backrooms.json}. Un fichier absent ou illisible laisse
 * les valeurs de depart, une ecriture ratee laisse les reglages en vigueur jusqu'a la fin de la partie.
 */
public final class LookSettingsStore {

    private static final String FILE = "gtgs_backrooms.json";

    private LookSettingsStore() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE);
    }

    /** Lit les reglages, a l'initialisation du client. */
    public static void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }
        try {
            LookSettings.set(LookSettings.fromJson(JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject(), LookSettings.defaults()));
        } catch (IOException | JsonParseException | IllegalStateException | ClassCastException | UnsupportedOperationException exception) {
            ModConstants.LOGGER.warn("[Look] Reglages illisibles ({}) : valeurs de depart.", exception.getMessage());
        }
    }

    /** Ecrit les reglages. */
    static void save(LookSettings settings) {
        try {
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(settings.toJson()), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[Look] Reglages non sauvegardes ({}).", exception.getMessage());
        }
    }
}
