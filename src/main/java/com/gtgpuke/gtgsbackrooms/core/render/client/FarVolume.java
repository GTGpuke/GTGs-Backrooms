package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import com.mojang.blaze3d.platform.GlStateManager;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;

/**
 * La texture 3D du loin que lisent les shaders du terrain : la lumiere cuite des surfaces jusqu'a {@value LightZone#FAR_RADIUS} sections de
 * l'ancrage, comme celle de la zone, une cellule pour deux blocs a l'horizontale et un a la verticale ; une tranche de {@value #ROWS}
 * rangees par face pour l'eclairement complet, puis autant pour sa part indirecte. Elle prend le relais de la texture de zone au-dela de
 * ce qu'elle couvre. {@value #WIDTH} cellules de cote en X et en Z, qui se repetent : une section garde sa place tant que l'ancrage
 * avance, seules celles qui entrent s'envoient, et une section sortie est effacee (les shaders retombent sur la piece moyenne).
 */
public final class FarVolume {

    /** Unite de texture du loin. */
    public static final int UNIT = 17;
    /** Echantillonneur du loin dans les shaders patches. */
    public static final String SAMPLER = "gtgs_FarVolume";
    /** Sections de cote de la texture : de quoi tenir le loin de part et d'autre de l'ancrage, sans qu'une section en recouvre une autre. */
    private static final int SLOTS_ACROSS = 2 * LightZone.FAR_RADIUS + 2;
    /** Cellules de cote de la texture, en X et en Z. */
    public static final int WIDTH = SLOTS_ACROSS * LightZone.FAR_CELLS;
    /** Rangees de la texture par tranche. */
    public static final int ROWS = LightZone.HEIGHT * LightZone.FAR_ROWS;
    /** Rangees de la texture : une tranche par face, pour l'eclairement complet puis pour sa part indirecte. */
    public static final int HEIGHT = ROWS * LightZone.FAR_LAYERS;
    private static final int SLOTS = SLOTS_ACROSS * SLOTS_ACROSS * LightZone.HEIGHT;
    private static final int FACE_VALUES = LightZone.FAR_FACE_VALUES;
    private static final int UPLOADS_PER_FRAME = 8;
    private static final int CLEARS_PER_FRAME = 8;
    private static final long NONE = Long.MIN_VALUE;
    private static final long[] OCCUPANT = new long[SLOTS];
    private static final IntBuffer VALUES = MemoryUtil.memAllocInt(LightZone.FAR_VALUES);
    private static final IntBuffer ZEROS = MemoryUtil.memCallocInt(FACE_VALUES);
    private static int texture;
    private static @Nullable LightZone zone;
    private static int anchorX;
    private static int anchorZ;
    private static int base;

    static {
        Arrays.fill(OCCUPANT, NONE);
    }

    private FarVolume() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Le loin a montrer (thread de rendu) : celui de la zone de {@link LightVolume}. Une autre zone ou une autre fenetre vident la
     * texture ; la zone republie alors ce qu'elle a deja cuit.
     *
     * @param next zone, {@code null} pour n'en montrer aucune
     * @param sectionX section d'ancrage
     * @param baseSection premiere section de la fenetre, a la verticale
     * @param sectionZ section d'ancrage
     */
    static void target(@Nullable LightZone next, int sectionX, int baseSection, int sectionZ) {
        if (next != zone || baseSection != base) {
            zone = next;
            base = baseSection;
            wipe();
        }
        anchorX = sectionX;
        anchorZ = sectionZ;
    }

    /** Vide toute la texture, d'un seul envoi : cela n'arrive qu'en changeant de monde ou de hauteur. */
    private static void wipe() {
        Arrays.fill(OCCUPANT, NONE);
        if (texture == 0) {
            return;
        }
        ByteBuffer zeros = MemoryUtil.memCalloc(WIDTH * HEIGHT * WIDTH * Integer.BYTES);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        PixelUnpack.begin();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, zeros);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        MemoryUtil.memFree(zeros);
    }

    /** Debut du rendu du monde : efface ce qui est sorti, envoie ce qui est cuit, et lie la texture a son unite. */
    static void frame() {
        if (zone == null) {
            return;
        }
        if (texture == 0) {
            create();
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        LightZone.FarBaked baked = zone.pollFar();
        if (baked != null || hasStale()) {
            PixelUnpack.begin();
            clearStale();
            int uploads = 0;
            while (baked != null) {
                upload(baked);
                uploads++;
                baked = uploads < UPLOADS_PER_FRAME ? zone.pollFar() : null;
            }
            PixelUnpack.end();
        }
        GlStateManager._activeTexture(previousUnit);
    }

    private static void create() {
        texture = GL11.glGenTextures();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        // Rien de cuit au depart : validite nulle partout, les shaders lisent la piece moyenne.
        ByteBuffer zeros = MemoryUtil.memCalloc(WIDTH * HEIGHT * WIDTH * Integer.BYTES);
        PixelUnpack.begin();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGB10_A2, WIDTH, HEIGHT, WIDTH, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, zeros);
        PixelUnpack.end();
        MemoryUtil.memFree(zeros);
        GlStateManager._activeTexture(previousUnit);
    }

    private static boolean hasStale() {
        for (int i = 0; i < SLOTS; i++) {
            if (OCCUPANT[i] != NONE && !inFar(OCCUPANT[i])) {
                return true;
            }
        }
        return false;
    }

    private static void clearStale() {
        int cleared = 0;
        for (int i = 0; i < SLOTS && cleared < CLEARS_PER_FRAME; i++) {
            if (OCCUPANT[i] != NONE && !inFar(OCCUPANT[i])) {
                clearSlot(i);
                if (zone != null) {
                    zone.forgetFar(OCCUPANT[i]);
                }
                OCCUPANT[i] = NONE;
                cleared++;
            }
        }
    }

    private static void clearSlot(int slot) {
        int x = (slot % SLOTS_ACROSS) * LightZone.FAR_CELLS;
        int z = (slot / SLOTS_ACROSS % SLOTS_ACROSS) * LightZone.FAR_CELLS;
        int row = (slot / (SLOTS_ACROSS * SLOTS_ACROSS)) * LightZone.FAR_ROWS;
        for (int layer = 0; layer < LightZone.FAR_LAYERS; layer++) {
            ZEROS.position(0).limit(FACE_VALUES);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, x, layer * ROWS + row, z, LightZone.FAR_CELLS, LightZone.FAR_ROWS, LightZone.FAR_CELLS, GL11.GL_RGBA,
                    GL12.GL_UNSIGNED_INT_2_10_10_10_REV, ZEROS);
        }
    }

    private static void upload(LightZone.FarBaked baked) {
        long key = baked.key();
        int row = ChunkSectionPos.unpackY(key) - base;
        if (!inFar(key) || row < 0 || row >= LightZone.HEIGHT) {
            if (zone != null) {
                zone.forgetFar(key);
            }
            return;
        }
        int slotX = Math.floorMod(ChunkSectionPos.unpackX(key), SLOTS_ACROSS);
        int slotZ = Math.floorMod(ChunkSectionPos.unpackZ(key), SLOTS_ACROSS);
        VALUES.clear();
        VALUES.put(baked.values()).flip();
        for (int layer = 0; layer < LightZone.FAR_LAYERS; layer++) {
            VALUES.position(layer * FACE_VALUES).limit((layer + 1) * FACE_VALUES);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, slotX * LightZone.FAR_CELLS, layer * ROWS + row * LightZone.FAR_ROWS, slotZ * LightZone.FAR_CELLS,
                    LightZone.FAR_CELLS, LightZone.FAR_ROWS, LightZone.FAR_CELLS, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, VALUES);
        }
        int slot = (row * SLOTS_ACROSS + slotZ) * SLOTS_ACROSS + slotX;
        // Une section pas encore effacee a cette place : la zone l'oublie, sinon elle ne la renverrait pas si le loin revient sur elle.
        if (OCCUPANT[slot] != NONE && OCCUPANT[slot] != key && zone != null) {
            zone.forgetFar(OCCUPANT[slot]);
        }
        OCCUPANT[slot] = key;
    }

    private static boolean inFar(long key) {
        return Math.abs(ChunkSectionPos.unpackX(key) - anchorX) <= LightZone.FAR_RADIUS && Math.abs(ChunkSectionPos.unpackZ(key) - anchorZ) <= LightZone.FAR_RADIUS;
    }

    /** Texture, 0 avant le premier loin (tests du client). */
    public static int texture() {
        return texture;
    }

    /** La piece moyenne de la zone montree ({@link LightZone#room}), {@code null} sans zone ou avant sa premiere section. */
    static float @Nullable [] room() {
        return zone == null ? null : zone.room();
    }
}
