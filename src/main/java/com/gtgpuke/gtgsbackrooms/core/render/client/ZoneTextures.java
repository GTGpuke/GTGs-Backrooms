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
 * Les textures d'une zone de lumiere cuite, a une place d'un monde : celle de la zone (l'eclairement des surfaces autour de l'ancrage,
 * {@value LightVolume#ROWS} blocs de haut par face, puis autant sans la lumiere directe) et celle de son loin. {@value LightVolume#WIDTH}
 * blocs de cote en X et en Z, qui se repetent : une section garde sa place tant que la zone avance, seules celles qui entrent s'envoient,
 * et une section sortie est effacee (les shaders retombent sur la lightmap). Envois limites par image. Une autre fenetre efface tout d'un
 * coup : ce qui y reste serait relu a la mauvaise hauteur. Chacune nourrit son cache de surfaces et ses rebonds : celle du joueur et
 * celle d'une vue de portail ont la meme qualite.
 */
final class ZoneTextures {

    private static final int SECTION = 16;
    private static final int SLOTS_ACROSS = LightVolume.WIDTH / SECTION;
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
    private static final IntBuffer SECTION_VALUES = MemoryUtil.memAllocInt(LightCell.VALUES);
    private static final IntBuffer ZEROS = MemoryUtil.memCallocInt(LightCell.SECTION_CELLS);
    /** Le cache de surfaces et les rebonds traces de cette zone. */
    private final LightSurfaces surfaces;
    private final FarVolume far = new FarVolume();
    private final long[] occupant = new long[SLOTS];
    private int texture;
    private @Nullable LightZone zone;
    private @Nullable Identifier dimension;
    private int anchorX;
    private int anchorZ;
    private int base;
    private int cameraY;
    /** Derniere image ou une zone etait montree, en millisecondes. */
    private long shownAt;

    /** {@code true} si le cache de surfaces de cette zone porte la lumiere directe du terrain. */
    boolean surfaces() {
        return this.surfaces.ready();
    }

    /** Le cache de surfaces de cette zone. */
    LightSurfaces cache() {
        return this.surfaces;
    }

    ZoneTextures(LightSurfaces surfaces) {
        this.surfaces = surfaces;
        Arrays.fill(this.occupant, NONE);
    }

    /**
     * La zone a montrer (thread de rendu). Un autre monde ou une autre fenetre vident la texture, et ce qui est deja cuit repart. Une
     * camera qui monte ou descend deplace seulement la bande des tuiles : ce qui y reste garde sa valeur.
     *
     * @param next zone, {@code null} pour n'en montrer aucune
     * @param world dimension de la zone
     * @param sectionX section d'ancrage
     * @param baseSection premiere section de la zone, a la verticale
     * @param sectionZ section d'ancrage
     * @param blockY Y de l'ancrage
     */
    void target(@Nullable LightZone next, @Nullable Identifier world, int sectionX, int baseSection, int sectionZ, int blockY) {
        if (next != this.zone || baseSection != this.base) {
            this.zone = next;
            this.base = baseSection;
            this.cameraY = blockY;
            this.surfaces.reset();
            wipe();
            if (next != null) {
                next.republish();
            }
        } else if (Math.abs(blockY - this.cameraY) > BAND) {
            this.cameraY = blockY;
            this.surfaces.rebanded(sectionX, sectionZ, blockY);
        }
        this.dimension = world;
        this.anchorX = sectionX;
        this.anchorZ = sectionZ;
        this.far.target(next, sectionX, baseSection, sectionZ);
    }

    /**
     * Vide toute la texture, d'un seul envoi : en changeant de monde ou de hauteur, et, pour celle d'une vue, quand sa zone apparait,
     * disparait ou suit un autre portail.
     */
    private void wipe() {
        Arrays.fill(this.occupant, NONE);
        if (this.texture == 0) {
            return;
        }
        ByteBuffer zeros = MemoryUtil.memCalloc(LightVolume.WIDTH * LightVolume.ROWS * LAYERS * LightVolume.WIDTH * Integer.BYTES);
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        PixelUnpack.begin();
        GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, LightVolume.WIDTH, LightVolume.ROWS * LAYERS, LightVolume.WIDTH, GL11.GL_RGBA,
                GL12.GL_UNSIGNED_INT_2_10_10_10_REV, zeros);
        PixelUnpack.end();
        GlStateManager._activeTexture(previousUnit);
        MemoryUtil.memFree(zeros);
    }

    /** Debut du rendu du monde : efface ce qui est sorti et envoie ce qui est cuit, puis fait avancer le cache de surfaces. */
    void frame() {
        if (this.zone == null) {
            return;
        }
        if (this.texture == 0) {
            create();
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        LightZone.Baked baked = this.zone.poll();
        if (baked != null || hasStale()) {
            PixelUnpack.begin();
            clearStale();
            int uploads = 0;
            while (baked != null) {
                upload(baked);
                uploads++;
                baked = uploads < UPLOADS_PER_FRAME ? this.zone.poll() : null;
            }
            PixelUnpack.end();
        }
        GlStateManager._activeTexture(previousUnit);
        this.far.frame();
        LightSurfaces.bindSprites();
        this.surfaces.frame(this.zone, LookProfiles.of(this.dimension), this.anchorX, this.base, this.anchorZ, this.cameraY);
    }

    /** Lie la texture de la zone, celle du loin, le cache de surfaces et les rebonds a leurs unites, pour les dessins qui suivent. */
    void bind() {
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        GlStateManager._activeTexture(previousUnit);
        this.far.bind();
        this.surfaces.bind();
    }

    /**
     * Rend a la carte graphique ce que tient cette zone : sa texture, celle du loin, son cache de surfaces et ses rebonds. Tout se
     * recree a la prochaine image qui en a besoin.
     */
    void freeTextures() {
        Arrays.fill(this.occupant, NONE);
        if (this.texture != 0) {
            GL11.glDeleteTextures(this.texture);
            this.texture = 0;
        }
        this.far.freeTexture();
        this.surfaces.freeTextures();
    }

    /**
     * Une fois par image : une zone que rien ne montre depuis {@link LightVolume#RELEASE_MS} rend tout ce qu'elle tient ; une zone
     * montree laisse son cache de surfaces juger du sien.
     *
     * @param now instant de l'image, en millisecondes
     */
    void releaseIdle(long now) {
        if (this.zone != null) {
            this.shownAt = now;
            this.surfaces.releaseIdle(now);
        } else if (allocated() && now - this.shownAt > LightVolume.RELEASE_MS) {
            freeTextures();
        }
    }

    /** {@code true} si la zone tient de la memoire graphique : sa texture, ou celle de son cache de surfaces. */
    boolean allocated() {
        return this.texture != 0 || this.surfaces.allocated();
    }

    private void create() {
        this.texture = GL11.glGenTextures();
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + LightVolume.UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.texture);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        PixelUnpack.begin();
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGB10_A2, LightVolume.WIDTH, LightVolume.ROWS * LAYERS, LightVolume.WIDTH, 0, GL11.GL_RGBA,
                GL12.GL_UNSIGNED_INT_2_10_10_10_REV, (ByteBuffer) null);
        // Rien de cuit au depart : validite nulle partout, les shaders lisent la lightmap.
        for (int z = 0; z < LightVolume.WIDTH; z += SECTION) {
            for (int y = 0; y < LightVolume.ROWS * LAYERS; y += SECTION) {
                for (int x = 0; x < LightVolume.WIDTH; x += SECTION) {
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

    private boolean hasStale() {
        for (int i = 0; i < SLOTS; i++) {
            if (this.occupant[i] != NONE && !inZone(this.occupant[i])) {
                return true;
            }
        }
        return false;
    }

    private void clearStale() {
        int cleared = 0;
        for (int i = 0; i < SLOTS && cleared < CLEARS_PER_FRAME; i++) {
            if (this.occupant[i] != NONE && !inZone(this.occupant[i])) {
                clearSlot(i);
                this.surfaces.release(this.occupant[i]);
                forget(this.occupant[i]);
                this.occupant[i] = NONE;
                cleared++;
            }
        }
    }

    /** La zone n'a plus a tenir cette section pour envoyee : elle repartira si la fenetre revient sur elle. */
    private void forget(long key) {
        if (this.zone != null) {
            this.zone.forget(key);
        }
    }

    private void clearSlot(int slot) {
        int x = (slot % SLOTS_ACROSS) * SECTION;
        int z = (slot / SLOTS_ACROSS % SLOTS_ACROSS) * SECTION;
        int row = (slot / (SLOTS_ACROSS * SLOTS_ACROSS)) * SECTION;
        for (int layer = 0; layer < LAYERS; layer++) {
            clearBlock(x, layer * LightVolume.ROWS + row, z);
        }
        this.surfaces.clearSlot(x / SECTION, row / SECTION, z / SECTION);
    }

    private void upload(LightZone.Baked baked) {
        long key = baked.key();
        int row = ChunkSectionPos.unpackY(key) - this.base;
        if (!inZone(key) || row < 0 || row >= LightZone.HEIGHT) {
            forget(key);
            return;
        }
        int slotX = ChunkSectionPos.unpackX(key) & SLOTS_ACROSS - 1;
        int slotZ = ChunkSectionPos.unpackZ(key) & SLOTS_ACROSS - 1;
        uploadLayers(baked.values(), 0, slotX, row, slotZ);
        uploadLayers(baked.indirect(), LightCell.FACES, slotX, row, slotZ);
        this.occupant[(row * SLOTS_ACROSS + slotZ) * SLOTS_ACROSS + slotX] = key;
        this.surfaces.accept(baked, this.anchorX, this.anchorZ, this.base, this.cameraY);
    }

    private static void uploadLayers(int[] values, int firstLayer, int slotX, int row, int slotZ) {
        SECTION_VALUES.clear();
        SECTION_VALUES.put(values).flip();
        for (int face = 0; face < LightCell.FACES; face++) {
            SECTION_VALUES.position(face * LightCell.SECTION_CELLS).limit((face + 1) * LightCell.SECTION_CELLS);
            GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, slotX * SECTION, (firstLayer + face) * LightVolume.ROWS + row * SECTION, slotZ * SECTION, SECTION,
                    SECTION, SECTION, GL11.GL_RGBA, GL12.GL_UNSIGNED_INT_2_10_10_10_REV, SECTION_VALUES);
        }
    }

    private boolean inZone(long key) {
        return Math.abs(ChunkSectionPos.unpackX(key) - this.anchorX) <= LightZone.RADIUS
                && Math.abs(ChunkSectionPos.unpackZ(key) - this.anchorZ) <= LightZone.RADIUS;
    }

    /** {@code true} si les fragments du monde d'un look, vus par une camera a cet endroit, lisent cette zone. */
    boolean covers(LookProfile look, int cameraX, int cameraY, int cameraZ) {
        return covers(look.dimension(), cameraX, cameraY, cameraZ);
    }

    /** {@code true} si une camera de cette dimension, a cet endroit, lit cette zone. */
    boolean covers(@Nullable Identifier world, int cameraX, int cameraY, int cameraZ) {
        return this.zone != null && world != null && world.equals(this.dimension)
                && Math.abs(cameraX - (this.anchorX << 4)) <= COVERED && Math.abs(cameraZ - (this.anchorZ << 4)) <= COVERED
                && cameraY >= this.base << 4 && cameraY < (this.base << 4) + LightVolume.ROWS;
    }

    /** Y du premier bloc de la zone. */
    int baseY() {
        return this.base << 4;
    }

    /** La zone montree, {@code null} sans zone. */
    @Nullable LightZone zone() {
        return this.zone;
    }

    /** Texture de la zone, 0 avant la premiere (tests du client). */
    int texture() {
        return this.texture;
    }

    /** Texture du loin, 0 avant le premier (tests du client). */
    int farTexture() {
        return this.far.texture();
    }

    /** La piece moyenne de la zone montree, {@code null} sans zone ou avant sa premiere section. */
    float @Nullable [] room() {
        return this.far.room();
    }
}
