package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La copie compacte d'un monde que lit la cuisson de la lumiere : un octet par bloc, l'indice de sa matiere dans une
 * palette (albedo, emission). Le thread de rendu pose les sections et les matieres, le thread de cuisson les lit : une
 * section posee n'est jamais modifiee en place, elle se remplace entiere, et la palette se republie entiere.
 */
public final class LightBlocks {

    /** Blocs d'une section. */
    public static final int SECTION_BLOCKS = 4096;
    /** Ce que la lumiere traverse : l'air et tout ce qui n'est pas un bloc plein et opaque. */
    public static final byte AIR = 0;
    /** Ce qu'on ne connait pas : opaque, d'albedo moyen. */
    public static final byte UNKNOWN = 1;
    /** Une section sans aucun bloc opaque, partagee. */
    public static final byte[] EMPTY = new byte[SECTION_BLOCKS];
    /** Une section d'une colonne que le client n'a pas : opaque, partagee ; elle ne fait attendre aucune cuisson. */
    public static final byte[] UNLOADED = new byte[SECTION_BLOCKS];
    private static final int CHANNELS = 3;
    private static final int MAX_MATERIALS = 256;
    private static final float UNKNOWN_ALBEDO = 0.5f;
    private static final int SECTION_SHIFT = 4;
    private static final int LAYER_SHIFT = 8;
    private static final int BLOCK_MASK = 15;

    private final ConcurrentHashMap<Long, byte[]> sections = new ConcurrentHashMap<>();
    static {
        Arrays.fill(UNLOADED, UNKNOWN);
    }

    private volatile float[] albedos = {0.0f, 0.0f, 0.0f, UNKNOWN_ALBEDO, UNKNOWN_ALBEDO, UNKNOWN_ALBEDO};
    private volatile boolean[] emissive = {false, false};

    /** Pose une section : un tableau neuf, ou {@link #EMPTY}, jamais modifie ensuite. */
    public void setSection(long key, byte[] blocks) {
        this.sections.put(key, blocks);
    }

    /** Les blocs d'une section, ou {@code null} si elle n'est pas copiee. */
    public byte @Nullable [] section(long key) {
        return this.sections.get(key);
    }

    /** Le bloc d'une position du monde : {@link #UNKNOWN} dans une section qui n'est pas copiee. */
    public byte block(int x, int y, int z) {
        byte[] section = this.sections.get(ChunkSectionPos.asLong(x >> SECTION_SHIFT, y >> SECTION_SHIFT, z >> SECTION_SHIFT));
        return section == null ? UNKNOWN : section[index(x, y, z)];
    }

    /** Rang d'une position du monde dans le tableau de sa section. */
    public static int index(int x, int y, int z) {
        return (z & BLOCK_MASK) << LAYER_SHIFT | (y & BLOCK_MASK) << SECTION_SHIFT | x & BLOCK_MASK;
    }

    /** Oublie une section. */
    public void removeSection(long key) {
        this.sections.remove(key);
    }

    /** Oublie les sections loin d'une section, au-dela d'un rayon, en sections, sur chaque axe. */
    public void retainAround(int sectionX, int sectionY, int sectionZ, int radius) {
        this.sections.keySet().removeIf(key -> Math.abs(ChunkSectionPos.unpackX(key) - sectionX) > radius
                || Math.abs(ChunkSectionPos.unpackY(key) - sectionY) > radius || Math.abs(ChunkSectionPos.unpackZ(key) - sectionZ) > radius);
    }

    /** Nombre de sections copiees. */
    public int sectionCount() {
        return this.sections.size();
    }

    /**
     * Ajoute une matiere, sur le thread de rendu.
     *
     * @param red albedo, de 0 a 1
     * @param green albedo
     * @param blue albedo
     * @param light {@code true} si le bloc emet (une dalle de neon)
     * @return son indice, a poser dans les sections ; au-dela de 256 matieres, {@link #UNKNOWN}
     */
    public byte addMaterial(float red, float green, float blue, boolean light) {
        int count = this.emissive.length;
        if (count == MAX_MATERIALS) {
            return UNKNOWN;
        }
        float[] nextAlbedos = Arrays.copyOf(this.albedos, (count + 1) * CHANNELS);
        boolean[] nextEmissive = Arrays.copyOf(this.emissive, count + 1);
        nextAlbedos[count * CHANNELS] = red;
        nextAlbedos[count * CHANNELS + 1] = green;
        nextAlbedos[count * CHANNELS + 2] = blue;
        nextEmissive[count] = light;
        this.albedos = nextAlbedos;
        this.emissive = nextEmissive;
        return (byte) count;
    }

    /** Les albedos des matieres, trois composantes par indice : a ne pas modifier. */
    public float[] albedos() {
        return this.albedos;
    }

    /** Les matieres qui emettent, par indice : a ne pas modifier. */
    public boolean[] emissive() {
        return this.emissive;
    }
}
