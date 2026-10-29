package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * La face des neons de chaque look, telle que le jumeau GLSL du calendrier la reconnait : le rectangle de la texture du
 * bloc de neon du profil dans l'atlas des blocs (la texture de particules de son modele, la face d'un cube a une seule
 * texture). Calcule a la premiere demande apres chaque rechargement des modeles, garde par identifiant de look. Le
 * jumeau se coupe pour la session si la verification du client de developpement trouve un ecart entre Java et GLSL
 * ({@link #disable}) : la face des neons proches suit alors l'etat envoye dans le bloc des neons.
 */
public final class NeonFaces {

    private static final float[] NONE = new float[4];
    private static final float[][] RECTS = new float[StencilLookMask.MAX_LOOK + 1][];
    private static final Identifier LISTENER_ID = Identifier.of(ModConstants.MOD_ID, "neon_faces");
    private static boolean disabled;

    private NeonFaces() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * A appeler a l'initialisation du client : les rectangles sont oublies apres chaque rechargement des modeles (et le
     * jumeau verifie au premier, si l'option le demande).
     */
    public static void register() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return LISTENER_ID;
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return Set.of(ResourceReloadListenerKeys.MODELS);
            }

            @Override
            public void reload(ResourceManager manager) {
                Arrays.fill(RECTS, null);
                NeonShaderCheck.runOnce(manager);
            }
        });
    }

    /**
     * Le rectangle (u0, v0, u1, v1) de la face des neons d'un look, vide sans neons, sans bloc connu, ou jumeau coupe.
     *
     * @param look profil du look
     */
    public static float[] rect(LookProfile look) {
        Identifier block = look.neons().block();
        if (disabled || block == null || !look.neons().any()) {
            return NONE;
        }
        float[] rect = RECTS[look.id()];
        if (rect == null) {
            rect = compute(block);
            RECTS[look.id()] = rect;
        }
        return rect;
    }

    /** Ecart trouve entre Java et GLSL : le jumeau est coupe pour la session. */
    public static void disable() {
        disabled = true;
    }

    /** {@code true} si le jumeau du calendrier est coupe. */
    public static boolean disabled() {
        return disabled;
    }

    private static float[] compute(Identifier id) {
        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR) {
            return NONE;
        }
        Sprite sprite = MinecraftClient.getInstance().getBlockRenderManager().getModels().getModelParticleSprite(block.getDefaultState());
        return new float[] {sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV()};
    }
}
