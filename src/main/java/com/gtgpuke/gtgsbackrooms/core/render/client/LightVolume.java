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

import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkSectionPos;

import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightCell;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * La texture 3D de zone que lisent les shaders du terrain : l'eclairement cuit des surfaces autour de l'ancrage, une
 * tranche de {@value #ROWS} blocs de haut par face de cellule, puis autant pour ce qui n'est pas la lumiere directe, que le
 * cache de surfaces ({@link LightSurfaces}) complete. {@value #WIDTH} blocs de cote en X et en Z, qui se repetent : une
 * section garde sa place tant que la zone avance, et seules les sections qui entrent s'envoient. Une section sortie
 * de la zone est effacee (les shaders retombent sur la lightmap). Envois limites par image : une section ne coute qu'un
 * envoi par face. Une autre fenetre efface tout d'un coup : ce qui y reste serait relu a la mauvaise hauteur.
 */
public final class LightVolume {

    /** Unite de texture de la zone. */
    public static final int UNIT = 8;
    /** Echantillonneur de la zone dans les shaders patches. */
    public static final String SAMPLER = "gtgs_LightVolume";
    /** Blocs de cote de la zone, en X et en Z, dans la texture. */
    public static final int WIDTH = 128;
    /** Blocs de haut de la zone. */
    public static final int ROWS = LightZone.HEIGHT * 16;
    private static final int SECTION = 16;
    private static final int SLOTS_ACROSS = WIDTH / SECTION;
    private static final int SLOTS = SLOTS_ACROSS * SLOTS_ACROSS * LightZone.HEIGHT;
    private static final int UPLOADS_PER_FRAME = 4;
    /** Tranches de la texture : six pour la lumiere complete, six sans la lumiere directe. */
    private static final int LAYERS = LightCell.FACES * 2;
    /** Blocs dont la camera peut monter ou descendre avant que les tuiles du cache de surfaces se redistribuent. */
    private static final int BAND = 2;
    private static final int CLEARS_PER_FRAME = 4;
    private static final long NONE = Long.MIN_VALUE;
    /** Distance a l'ancrage, en blocs, au-dela de laquelle une camera ne lit plus la zone : ce qu'elle verrait a une autre place. */
    private static final int COVERED = 48;
    private static final long[] OCCUPANT = new long[SLOTS];
    private static final IntBuffer SECTION_VALUES = MemoryUtil.memAllocInt(LightCell.VALUES);
    private static final IntBuffer ZEROS = MemoryUtil.memCallocInt(LightCell.SECTION_CELLS);
    private static int texture;
    private static @Nullable LightZone zone;
    private static @Nullable Identifier dimension;
    private static int anchorX;
    private static int anchorZ;
    private static int base;
    private static int cameraY;

    static {
        Arrays.fill(OCCUPANT, NONE);
    }

    private LightVolume() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * La zone a montrer (thread de rendu). Un autre monde ou une autre fenetre vident la texture, et ce qui est deja cuit
     * repart. Une camera qui monte ou descend deplace seulement la bande des tuiles : ce qui y reste garde sa valeur.
     *
     * @param next zone, {@code null} pour n'en montrer aucune
     * @param world dimension de la zone
     * @param sectionX section d'ancrage
     * @param baseSection premiere section de la zone, a la verticale
     * @param sectionZ section d'ancrage
     * @param blockY Y de l'ancrage
     */
    public static void target(@Nullable LightZone next, @Nullable Identifier world, int sectionX, int baseSection, int sectionZ, int blockY) {
        if (next != zone || baseSection != base) {
            zone = next;
            base = baseSection;
            cameraY = blockY;
            LightSurfaces.reset();
            wipe();
            if (next != null) {
                next.republish();
            }
        } else if (Math.abs(blockY - cameraY) > BAND) {
            cameraY = blockY;
            LightSurfaces.rebanded(sectionX, sectionZ, blockY);
        }
        dimension = world;
        anchorX = sectionX;
        anchorZ = sectionZ;
        FarVolume.target(next, sectionX, baseSection, sectionZ);
    }

    /** Vide toute la texture, d'un seul envoi : cela n'arrive qu'en changeant de monde ou de hauteur. */
    private static void wipe() {
        Arrays.fill(OCCUPANT, NONE);
        if (texture == 0) {
            return;
        }
        ByteBuffer zeros = MemoryUtil.memCalloc(WIDTH * ROWS * LAYERS * WIDTH * Integer.BYTES);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        PixelUnpack.begin();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, WIDTH, ROWS * LAYERS, WIDTH, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, zeros);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        MemoryUtil.memFree(zeros);
    }

    /** Debut du rendu du monde : efface ce qui est sorti, envoie ce qui est cuit, et lie la texture a son unite. */
    public static void frame() {
        if (zone == null) {
            return;
        }
        if (texture == 0) {
            create();
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        LightZone.Baked baked = zone.poll();
        boolean work = baked != null || hasStale();
        if (work) {
            PixelUnpack.begin();
            clearStale();
            int uploads = 0;
            while (baked != null) {
                upload(baked);
                uploads++;
                baked = uploads < UPLOADS_PER_FRAME ? zone.poll() : null;
            }
            PixelUnpack.end();
        }
        GlStateManager._activeTexture(previousUnit);
        FarVolume.frame();
        LightSurfaces.bindSprites();
        LightSurfaces.frame(zone, LookProfiles.of(dimension), anchorX, base, anchorZ, cameraY);
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
        PixelUnpack.begin();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGB10_A2, WIDTH, ROWS * LAYERS, WIDTH, 0, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV,
                (ByteBuffer) null);
        // Rien de cuit au depart : validite nulle partout, les shaders lisent la lightmap.
        for (int z = 0; z < WIDTH; z += SECTION) {
            for (int y = 0; y < ROWS * LAYERS; y += SECTION) {
                for (int x = 0; x < WIDTH; x += SECTION) {
                    clearBlock(x, y, z);
                }
            }
        }
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
    }

    private static void clearBlock(int x, int y, int z) {
        ZEROS.position(0).limit(LightCell.SECTION_CELLS);
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, x, y, z, SECTION, SECTION, SECTION, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, ZEROS);
    }

    private static boolean hasStale() {
        for (int i = 0; i < SLOTS; i++) {
            if (OCCUPANT[i] != NONE && !inZone(OCCUPANT[i])) {
                return true;
            }
        }
        return false;
    }

    private static void clearStale() {
        int cleared = 0;
        for (int i = 0; i < SLOTS && cleared < CLEARS_PER_FRAME; i++) {
            if (OCCUPANT[i] != NONE && !inZone(OCCUPANT[i])) {
                clearSlot(i);
                LightSurfaces.release(OCCUPANT[i]);
                forget(OCCUPANT[i]);
                OCCUPANT[i] = NONE;
                cleared++;
            }
        }
    }

    /** La zone n'a plus a tenir cette section pour envoyee : elle repartira si la fenetre revient sur elle. */
    private static void forget(long key) {
        if (zone != null) {
            zone.forget(key);
        }
    }

    private static void clearSlot(int slot) {
        int x = (slot % SLOTS_ACROSS) * SECTION;
        int z = (slot / SLOTS_ACROSS % SLOTS_ACROSS) * SECTION;
        int row = (slot / (SLOTS_ACROSS * SLOTS_ACROSS)) * SECTION;
        for (int layer = 0; layer < LAYERS; layer++) {
            clearBlock(x, layer * ROWS + row, z);
        }
        LightSurfaces.clearSlot(x / SECTION, row / SECTION, z / SECTION);
    }

    private static void upload(LightZone.Baked baked) {
        long key = baked.key();
        int row = ChunkSectionPos.unpackY(key) - base;
        if (!inZone(key) || row < 0 || row >= LightZone.HEIGHT) {
            forget(key);
            return;
        }
        int slotX = ChunkSectionPos.unpackX(key) & SLOTS_ACROSS - 1;
        int slotZ = ChunkSectionPos.unpackZ(key) & SLOTS_ACROSS - 1;
        uploadLayers(baked.values(), 0, slotX, row, slotZ);
        uploadLayers(baked.indirect(), LightCell.FACES, slotX, row, slotZ);
        OCCUPANT[(row * SLOTS_ACROSS + slotZ) * SLOTS_ACROSS + slotX] = key;
        LightSurfaces.accept(baked, anchorX, anchorZ, base, cameraY);
    }

    private static void uploadLayers(int[] values, int firstLayer, int slotX, int row, int slotZ) {
        SECTION_VALUES.clear();
        SECTION_VALUES.put(values).flip();
        for (int face = 0; face < LightCell.FACES; face++) {
            SECTION_VALUES.position(face * LightCell.SECTION_CELLS).limit((face + 1) * LightCell.SECTION_CELLS);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, slotX * SECTION, (firstLayer + face) * ROWS + row * SECTION, slotZ * SECTION, SECTION, SECTION, SECTION,
                    GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, SECTION_VALUES);
        }
    }

    private static boolean inZone(long key) {
        return Math.abs(ChunkSectionPos.unpackX(key) - anchorX) <= LightZone.RADIUS && Math.abs(ChunkSectionPos.unpackZ(key) - anchorZ) <= LightZone.RADIUS;
    }

    /**
     * {@code true} si les fragments du monde d'un look, vus par une camera a cet endroit, lisent cette zone.
     *
     * @param look look de la vue
     * @param cameraX bloc de la camera
     * @param cameraY bloc de la camera
     * @param cameraZ bloc de la camera
     */
    public static boolean covers(LookProfile look, int cameraX, int cameraY, int cameraZ) {
        return zone != null && look.dimension() != null && look.dimension().equals(dimension)
                && Math.abs(cameraX - (anchorX << 4)) <= COVERED && Math.abs(cameraZ - (anchorZ << 4)) <= COVERED
                && cameraY >= base << 4 && cameraY < (base << 4) + ROWS;
    }

    /** Y du premier bloc de la zone. */
    public static int baseY() {
        return base << 4;
    }

    /** Les reglages changent la lumiere du cache de surfaces : ses tuiles se recalculent, depuis ce qui est deja cuit. */
    public static void rebake() {
        LightSurfaces.reset();
        if (zone != null) {
            zone.republish();
        }
    }

    /** Texture, 0 avant la premiere zone (tests du client). */
    public static int texture() {
        return texture;
    }

    /** Fin de partie, ou rechargement des profils : plus de zone ; la texture reste, videe a sa prochaine utilisation. */
    public static void clear() {
        target(null, null, 0, 0, 0, 0);
    }
}
