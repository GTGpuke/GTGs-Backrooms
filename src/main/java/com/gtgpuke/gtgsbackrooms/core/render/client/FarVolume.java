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
 * avance, seules celles qui entrent s'envoient, et une section sortie est effacee (les shaders retombent sur la piece moyenne). Chaque
 * zone a la sienne ({@link ZoneTextures}).
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
    private static final IntBuffer VALUES = MemoryUtil.memAllocInt(LightZone.FAR_VALUES);
    private static final IntBuffer ZEROS = MemoryUtil.memCallocInt(FACE_VALUES);
    private final long[] occupant = new long[SLOTS];
    private int texture;
    private @Nullable LightZone zone;
    private int anchorX;
    private int anchorZ;
    private int base;

    FarVolume() {
        Arrays.fill(this.occupant, NONE);
    }

    /**
     * Le loin a montrer (thread de rendu) : celui de la zone de son emplacement. Une autre zone ou une autre fenetre vident la texture ;
     * la zone republie alors ce qu'elle a deja cuit.
     *
     * @param next zone, {@code null} pour n'en montrer aucune
     * @param sectionX section d'ancrage
     * @param baseSection premiere section de la fenetre, a la verticale
     * @param sectionZ section d'ancrage
     */
    void target(@Nullable LightZone next, int sectionX, int baseSection, int sectionZ) {
        if (next != this.zone || baseSection != this.base) {
            this.zone = next;
            this.base = baseSection;
            wipe();
        }
        this.anchorX = sectionX;
        this.anchorZ = sectionZ;
    }

    /** Vide toute la texture, d'un seul envoi : cela n'arrive qu'en changeant de monde ou de hauteur. */
    private void wipe() {
        Arrays.fill(this.occupant, NONE);
        if (this.texture == 0) {
            return;
        }
        ByteBuffer zeros = MemoryUtil.memCalloc(WIDTH * HEIGHT * WIDTH * Integer.BYTES);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        PixelUnpack.begin();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, zeros);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        MemoryUtil.memFree(zeros);
    }

    /** Debut du rendu du monde : efface ce qui est sorti et envoie ce qui est cuit, la texture liee a son unite. */
    void frame() {
        if (this.zone == null) {
            return;
        }
        if (this.texture == 0) {
            create();
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        LightZone.FarBaked baked = this.zone.pollFar();
        if (baked != null || hasStale()) {
            PixelUnpack.begin();
            clearStale();
            int uploads = 0;
            while (baked != null) {
                upload(baked);
                uploads++;
                baked = uploads < UPLOADS_PER_FRAME ? this.zone.pollFar() : null;
            }
            PixelUnpack.end();
        }
        GlStateManager._activeTexture(previousUnit);
    }

    /** Lie la texture a son unite, pour les dessins qui suivent. */
    void bind() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        GlStateManager._activeTexture(previousUnit);
    }

    /** Rend sa texture a la carte graphique : elle se recree, vide, a la prochaine image qui en a besoin. */
    void freeTexture() {
        Arrays.fill(this.occupant, NONE);
        if (this.texture != 0) {
            GL11.glDeleteTextures(this.texture);
            this.texture = 0;
        }
    }

    private void create() {
        this.texture = GL11.glGenTextures();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
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

    private boolean hasStale() {
        for (int i = 0; i < SLOTS; i++) {
            if (this.occupant[i] != NONE && !inFar(this.occupant[i])) {
                return true;
            }
        }
        return false;
    }

    private void clearStale() {
        int cleared = 0;
        for (int i = 0; i < SLOTS && cleared < CLEARS_PER_FRAME; i++) {
            if (this.occupant[i] != NONE && !inFar(this.occupant[i])) {
                clearSlot(i);
                if (this.zone != null) {
                    this.zone.forgetFar(this.occupant[i]);
                }
                this.occupant[i] = NONE;
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

    private void upload(LightZone.FarBaked baked) {
        long key = baked.key();
        int row = ChunkSectionPos.unpackY(key) - this.base;
        if (!inFar(key) || row < 0 || row >= LightZone.HEIGHT) {
            if (this.zone != null) {
                this.zone.forgetFar(key);
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
        if (this.occupant[slot] != NONE && this.occupant[slot] != key && this.zone != null) {
            this.zone.forgetFar(this.occupant[slot]);
        }
        this.occupant[slot] = key;
    }

    private boolean inFar(long key) {
        return Math.abs(ChunkSectionPos.unpackX(key) - this.anchorX) <= LightZone.FAR_RADIUS
                && Math.abs(ChunkSectionPos.unpackZ(key) - this.anchorZ) <= LightZone.FAR_RADIUS;
    }

    /** Texture, 0 avant le premier loin (tests du client). */
    int texture() {
        return this.texture;
    }

    /** La piece moyenne de la zone montree ({@link LightZone#room}), {@code null} sans zone ou avant sa premiere section. */
    float @Nullable [] room() {
        return this.zone == null ? null : this.zone.room();
    }
}
