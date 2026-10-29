package com.gtgpuke.gtgsbackrooms.core.audio.client;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles;

/**
 * Ressources du moteur audio : les profils ({@code audio_profiles/*.json}), charges avec les ressources du client et
 * recharges avec F3+T pour regler a l'oreille sans relancer. Sans le moteur (option {@code gtgsbackrooms.audio.enabled}),
 * rien n'est charge : tous les sons restent vanilla.
 */
public final class AudioResources {

    private static final String PROFILES = "audio_profiles";
    private static final String JSON = ".json";
    private static final Identifier LISTENER_ID = Identifier.of(ModConstants.MOD_ID, "audio");

    private AudioResources() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client. */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public void reload(ResourceManager manager) {
                if (ModConfig.ENABLE_AUDIO) {
                    load(manager);
                }
            }
        });
    }

    private static void load(ResourceManager manager) {
        List<AudioProfile> profiles = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(PROFILES, AudioResources::isModJson).entrySet()) {
            try (Reader reader = entry.getValue().getReader()) {
                profiles.add(AudioProfile.fromJson(JsonParser.parseString(IOUtils.toString(reader)).getAsJsonObject()));
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                ModConstants.LOGGER.error("[Audio] Profil {} refuse : {}", entry.getKey(), exception.getMessage());
            }
        }
        AudioProfiles.load(profiles);
        ModConstants.LOGGER.info("[Audio] {} profil(s) audio.", AudioProfiles.size());
        EfxBackend.requestPrewarm();
    }

    private static boolean isModJson(Identifier id) {
        return id.getNamespace().equals(ModConstants.MOD_ID) && id.getPath().endsWith(JSON);
    }
}
