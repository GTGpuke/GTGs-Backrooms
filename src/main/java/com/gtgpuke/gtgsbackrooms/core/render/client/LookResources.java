package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.fbo.FramebufferSpec;
import com.gtgpuke.gtgsbackrooms.core.render.patch.ShaderPatchRegistry;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;

/**
 * Ressources du moteur de shaders : les profils de look ({@code look_profiles/*.json}), les patchs de shaders
 * ({@code shader_patches/*.json}) et leurs bibliotheques GLSL, les framebuffers ({@code framebuffers/*.json}). Chargees depuis
 * le gestionnaire de ressources derriere la fabrique avec laquelle vanilla charge ses shaders, au premier shader de
 * chaque rechargement (le rechargement des shaders vanilla passe avant les ecouteurs des mods), puis une seule fois par
 * gestionnaire ; rechargees avec F3+T. Sans le moteur (option
 * {@code gtgsbackrooms.look.enabled}), rien n'est charge : tout reste vanilla.
 */
public final class LookResources {

    private static final String PROFILES = "look_profiles";
    private static final String PATCHES = "shader_patches";
    private static final String FRAMEBUFFERS = "framebuffers";
    private static final String JSON = ".json";
    private static final Identifier LISTENER_ID = Identifier.of(ModConstants.MOD_ID, "look");
    private static @Nullable ResourceFactory loadedFrom;
    private static ShaderPatchRegistry patches = ShaderPatchRegistry.EMPTY;
    private static @Nullable LookQuality quality;

    private LookResources() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Ecouteur de rechargement des ressources du client : apres un F3+T, profils et patchs sont relus. */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public void reload(ResourceManager manager) {
                // Les programmes vanilla sont deja recharges (leur rechargement passe avant les ecouteurs des mods) : le
                // bilan du pilote sur les etages patches se journalise ici.
                ensure(manager);
                if (ModConfig.ENABLE_LOOK) {
                    ModConstants.LOGGER.info("[Look] {} etage(s) de shaders patche(s) compile(s) par le pilote, {} refuse(s).",
                            ShaderCompileCheck.accepted(), ShaderCompileCheck.refused());
                }
            }
        });
    }

    /**
     * Charge profils, patchs et framebuffers depuis le gestionnaire de ressources derriere cette fabrique, si ce n'est
     * deja fait avec lui. A chaque rechargement, vanilla charge ses programmes par une fabrique a cache
     * ({@link GameRenderer.CachedResourceFactory}, remplie pendant la preparation) posee sur le gestionnaire du
     * rechargement : c'est lui qui enumere nos fichiers, avant le premier etage patche.
     */
    public static void ensure(ResourceFactory factory) {
        ResourceFactory source = factory instanceof GameRenderer.CachedResourceFactory cached ? cached.original() : factory;
        if (source == loadedFrom) {
            return;
        }
        loadedFrom = source;
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        if (!(source instanceof ResourceManager manager)) {
            // Les shaders de l'ecran de chargement, lus avant les ressources par la fabrique du pack vanilla : ils ne sont
            // pas patches (interface), et le rechargement des ressources recharge tous les programmes.
            ModConstants.LOGGER.debug("[Look] Fabrique de ressources sans enumeration : profils et patchs attendus au rechargement.");
            return;
        }
        load(manager);
    }

    /** Patchs de shaders charges. */
    public static ShaderPatchRegistry patches() {
        return patches;
    }

    /** Qualite du moteur : celle de l'option, ou choisie d'apres la carte, relevee une fois (chaine du pilote). */
    public static LookQuality quality() {
        if (quality == null) {
            quality = LookQuality.choose(ModConfig.LOOK_QUALITY, GL11.glGetString(GL11.GL_RENDERER));
            ModConstants.LOGGER.info("[Look] Qualite {} ({}).", quality, ModConfig.LOOK_QUALITY);
        }
        return quality;
    }

    private static void load(ResourceManager manager) {
        ShaderCompileCheck.resetCounts();
        List<LookProfile> profiles = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(PROFILES, LookResources::isModJson).entrySet()) {
            try {
                LookProfile profile = LookProfile.fromJson(JsonParser.parseString(read(entry.getValue())).getAsJsonObject());
                profiles.add(profile.comfort(ModConfig.REDUCE_FLASHES, ModConfig.REDUCE_VISUAL_NOISE));
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                ModConstants.LOGGER.error("[Look] Profil {} refuse ({}).", entry.getKey(), exception.getMessage());
            }
        }
        LookProfiles.load(profiles);
        Map<String, String> patchFiles = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(PATCHES, LookResources::isModJson).entrySet()) {
            try {
                patchFiles.put(entry.getKey().getPath(), read(entry.getValue()));
            } catch (IOException exception) {
                ModConstants.LOGGER.error("[Look] Patch {} illisible ({}).", entry.getKey(), exception.getMessage());
            }
        }
        patches = ShaderPatchRegistry.of(patchFiles, include -> readInclude(manager, include));
        List<FramebufferSpec> framebuffers = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(FRAMEBUFFERS, LookResources::isModJson).entrySet()) {
            try {
                framebuffers.add(FramebufferSpec.fromJson(JsonParser.parseString(read(entry.getValue())).getAsJsonObject()));
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                ModConstants.LOGGER.error("[Look] Framebuffer {} refuse ({}).", entry.getKey(), exception.getMessage());
            }
        }
        FramebufferRegistry.load(framebuffers);
        ModConstants.LOGGER.info("[Look] {} profil(s) de look, {} patch(s) de shaders, {} framebuffer(s).", LookProfiles.size(), patches.size(),
                framebuffers.size());
    }

    private static boolean isModJson(Identifier id) {
        return id.getNamespace().equals(ModConstants.MOD_ID) && id.getPath().endsWith(JSON);
    }

    private static @Nullable String readInclude(ResourceManager manager, Identifier include) {
        try {
            Resource resource = manager.getResource(include).orElse(null);
            return resource == null ? null : read(resource);
        } catch (IOException exception) {
            ModConstants.LOGGER.error("[Look] Bibliotheque {} illisible ({}).", include, exception.getMessage());
            return null;
        }
    }

    private static String read(Resource resource) throws IOException {
        try (Reader reader = resource.getReader()) {
            return IOUtils.toString(reader);
        }
    }
}
