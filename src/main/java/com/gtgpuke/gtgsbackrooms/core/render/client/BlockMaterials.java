package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * La matiere des blocs au pixel de texture. A cote de la texture {@code x.png} d'un bloc du mod, {@code x_n.png} dit la pente de chaque
 * pixel (rouge vers la droite de la texture, vert vers son bas) et sa hauteur (alpha, opaque en haut), et {@code x_s.png} son lissage
 * (rouge, 0 pour mat). Les deux se recopient dans une texture de la taille de l'atlas des blocs, chaque pixel a la place du sien
 * dans l'atlas : un shader la lit aux memes coordonnees que sa texture, sans table, sans savoir quel bloc il dessine. Ce qui n'a pas de
 * fichier reste a zero : mat, et sans pente. Refaite a la premiere demande apres un rechargement des modeles : tous les paliers a lumiere
 * cuite la lisent (textures calmees).
 */
public final class BlockMaterials {

    /** Unite de texture de la matiere. */
    public static final int UNIT = 16;
    /** Echantillonneur de la matiere dans les shaders patches. */
    public static final String SAMPLER = "gtgs_BlockMaterial";
    private static final Identifier LISTENER_ID = Identifier.of(ModConstants.MOD_ID, "block_materials");
    private static final String FOLDER = "textures/block";
    private static final String SPRITE_FOLDER = "textures/";
    private static final String SLOPES = "_n.png";
    private static final String PROPERTIES = "_s.png";
    private static final int CHANNELS = 4;
    /** Le lissage dans un pixel de la matiere. */
    private static final int SMOOTHNESS_AT = 2;
    /** La hauteur dans un pixel de la matiere. */
    private static final int HEIGHT_AT = 3;
    private static int texture;
    private static boolean stale = true;

    private BlockMaterials() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : un rechargement des modeles replace les textures dans l'atlas. */
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
                stale = true;
                // Le relief est ecrit dans les tuiles du cache de surfaces.
                LightVolume.rebake();
            }
        });
    }

    /** La matiere a son unite : le terrain et le calcul des tuiles la lisent. */
    static void bindForShaders() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        if (stale) {
            stale = false;
            build();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager._activeTexture(previousUnit);
    }

    private static void build() {
        MinecraftClient client = MinecraftClient.getInstance();
        SpriteAtlasTexture atlas = client.getBakedModelManager().getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getGlId());
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        int levels = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
        ByteBuffer values = MemoryUtil.memCalloc(width * height * CHANNELS);
        try {
            Map<Identifier, Resource> files = client.getResourceManager().findResources(FOLDER, BlockMaterials::isMaterial);
            for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
                read(file.getKey(), file.getValue(), atlas, values, width);
            }
            if (texture == 0) {
                texture = GL11.glGenTextures();
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            PixelUnpack.begin();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, values);
            PixelUnpack.end();
            // L'atlas range ses textures sur la grille de ses niveaux : aucune moyenne ne deborde d'une texture sur sa voisine.
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            ModConstants.LOGGER.info("[Look] {} texture(s) de matiere, table de {} x {}.", files.size(), width, height);
        } finally {
            MemoryUtil.memFree(values);
        }
    }

    private static boolean isMaterial(Identifier id) {
        return id.getNamespace().equals(ModConstants.MOD_ID) && (id.getPath().endsWith(SLOPES) || id.getPath().endsWith(PROPERTIES));
    }

    /** Recopie un fichier a la place de sa texture dans l'atlas ; un fichier sans texture, ou d'une autre taille, ne compte pas. */
    private static void read(Identifier file, Resource resource, SpriteAtlasTexture atlas, ByteBuffer values, int width) {
        String path = file.getPath();
        boolean slopes = path.endsWith(SLOPES);
        Identifier id = file.withPath(path.substring(SPRITE_FOLDER.length(), path.length() - (slopes ? SLOPES : PROPERTIES).length()));
        Sprite sprite = atlas.getSprite(id);
        if (!sprite.getContents().getId().equals(id)) {
            ModConstants.LOGGER.warn("[Look] Matiere {} sans texture de bloc.", file);
            return;
        }
        try (InputStream in = resource.getInputStream(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() != sprite.getContents().getWidth() || image.getHeight() != sprite.getContents().getHeight()) {
                ModConstants.LOGGER.warn("[Look] Matiere {} d'une autre taille que sa texture.", file);
                return;
            }
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getColor(x, y);
                    int at = ((sprite.getY() + y) * width + sprite.getX() + x) * CHANNELS;
                    if (slopes) {
                        values.put(at, (byte) abgr).put(at + 1, (byte) (abgr >> 8)).put(at + HEIGHT_AT, (byte) (abgr >> 24));
                    } else {
                        values.put(at + SMOOTHNESS_AT, (byte) abgr);
                    }
                }
            }
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[Look] Matiere {} illisible : {}.", file, exception.getMessage());
        }
    }

    /** Largeur de la table, celle de l'atlas des blocs (tests du client). */
    public static int width() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        GlStateManager._activeTexture(previousUnit);
        return width;
    }

    /**
     * La table relue sur le processeur (tests du client).
     *
     * @return quatre octets par pixel de l'atlas des blocs
     */
    public static byte[] read() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * CHANNELS);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            GlStateManager._activeTexture(previousUnit);
            byte[] copy = new byte[pixels.remaining()];
            pixels.get(copy);
            return copy;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }
}
