package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

/**
 * Les perturbations en cours : une liste courte de sons lies ({@link Cue}) qui jouent, chacun avec sa dimension, sa
 * position, son amplitude du moment (l'enveloppe lue a la position de lecture, ou 1) et ses reactions. Au plus
 * {@value #CAPACITY} ; la plus ancienne cede sa place. Chaque vue lit les perturbations de sa dimension seulement : un
 * cri du Level 0 ne fait jamais vaciller l'Overworld. La force du moment de chaque reaction suit l'amplitude par sa
 * courbe ({@link Cue.Reaction#force}). Rien n'est alloue apres la creation.
 */
public final class Disturbances {

    /** Perturbations a la fois. */
    public static final int CAPACITY = 4;

    private final Identifier[] dimensions = new Identifier[CAPACITY];
    private final double[] x = new double[CAPACITY];
    private final double[] y = new double[CAPACITY];
    private final double[] z = new double[CAPACITY];
    private final float[] amplitude = new float[CAPACITY];
    private final Cue.Reaction[] neon = new Cue.Reaction[CAPACITY];
    private final Cue.Reaction[] pulse = new Cue.Reaction[CAPACITY];
    private final Cue.Reaction[] lightmap = new Cue.Reaction[CAPACITY];
    private final Cue.Reaction[] shake = new Cue.Reaction[CAPACITY];
    private final long[] startedAt = new long[CAPACITY];
    private final boolean[] active = new boolean[CAPACITY];

    /**
     * Une perturbation commence.
     *
     * @param dimension dimension ou le son est emis
     * @param sx source
     * @param sy source
     * @param sz source
     * @param cue lien du son
     * @param now instant de depart (ticks), pour savoir laquelle est la plus ancienne
     * @return l'index de la perturbation
     */
    public int start(Identifier dimension, double sx, double sy, double sz, Cue cue, long now) {
        int slot = -1;
        long oldest = Long.MAX_VALUE;
        for (int i = 0; i < CAPACITY; i++) {
            if (!this.active[i]) {
                slot = i;
                break;
            }
            if (this.startedAt[i] < oldest) {
                oldest = this.startedAt[i];
                slot = i;
            }
        }
        this.active[slot] = true;
        this.dimensions[slot] = dimension;
        this.x[slot] = sx;
        this.y[slot] = sy;
        this.z[slot] = sz;
        this.amplitude[slot] = 1.0f;
        this.startedAt[slot] = now;
        this.neon[slot] = null;
        this.pulse[slot] = null;
        this.lightmap[slot] = null;
        this.shake[slot] = null;
        List<Cue.Reaction> reactions = cue.reactions();
        for (int i = 0; i < reactions.size(); i++) {
            Cue.Reaction reaction = reactions.get(i);
            if (reaction.strength() <= 0.0f) {
                continue;
            }
            Cue.Type type = reaction.type();
            if (type == Cue.Type.NEON_DISTURBANCE) {
                this.neon[slot] = reaction;
            } else if (type == Cue.Type.SCREEN_PULSE) {
                this.pulse[slot] = reaction;
            } else if (type == Cue.Type.LIGHTMAP_FLICKER) {
                this.lightmap[slot] = reaction;
            } else {
                this.shake[slot] = reaction;
            }
        }
        return slot;
    }

    /** Amplitude du moment d'une perturbation, de 0 a 1. */
    public void amplitude(int slot, float value) {
        this.amplitude[slot] = value;
    }

    /** La source d'une perturbation a bouge. */
    public void move(int slot, double sx, double sy, double sz) {
        this.x[slot] = sx;
        this.y[slot] = sy;
        this.z[slot] = sz;
    }

    /** Une perturbation finit. */
    public void end(int slot) {
        this.active[slot] = false;
        this.dimensions[slot] = null;
    }

    /** {@code true} tant que la perturbation est en cours. */
    public boolean active(int slot) {
        return this.active[slot];
    }

    /** Dimension d'une perturbation en cours, {@code null} sinon. */
    public @Nullable Identifier dimension(int slot) {
        return this.active[slot] ? this.dimensions[slot] : null;
    }

    /** Amplitude du son d'une perturbation, de 0 a 1. */
    public float currentAmplitude(int slot) {
        return this.amplitude[slot];
    }

    /** Nombre de perturbations en cours. */
    public int count() {
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (this.active[i]) {
                count++;
            }
        }
        return count;
    }

    /** Toutes finies. */
    public void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            end(i);
        }
    }

    /** {@code true} si la perturbation est en cours dans cette dimension et agit sur ses neons. */
    public boolean disturbsNeons(int slot, @Nullable Identifier dimension) {
        return in(slot, dimension) && this.neon[slot] != null;
    }

    /** Source d'une perturbation, axe x. */
    public double x(int slot) {
        return this.x[slot];
    }

    /** Source d'une perturbation, axe y. */
    public double y(int slot) {
        return this.y[slot];
    }

    /** Source d'une perturbation, axe z. */
    public double z(int slot) {
        return this.z[slot];
    }

    /** Portee d'une perturbation sur les neons ; 0 si elle n'en a pas. */
    public float neonRadius(int slot) {
        return this.neon[slot] == null ? 0.0f : this.neon[slot].radius();
    }

    /** Ce qu'une perturbation retire au plus a un neon a sa source : sa force du moment ; 0 si elle n'agit pas sur eux. */
    public float neonForce(int slot) {
        return this.neon[slot] == null ? 0.0f : this.neon[slot].force(this.amplitude[slot]);
    }

    /**
     * Ce qu'il reste a un neon de son intensite, de 0 a 1 : 1 hors de toute perturbation, moins pres d'une source, au
     * rythme de son amplitude.
     *
     * @param dimension dimension du neon
     * @param nx neon
     * @param ny neon
     * @param nz neon
     */
    public float neonFactor(@Nullable Identifier dimension, double nx, double ny, double nz) {
        float drop = 0.0f;
        for (int i = 0; i < CAPACITY; i++) {
            if (!disturbsNeons(i, dimension)) {
                continue;
            }
            double distance = distance(i, nx, ny, nz);
            float radius = this.neon[i].radius();
            if (distance >= radius) {
                continue;
            }
            drop = Math.max(drop, this.neon[i].force(this.amplitude[i]) * (float) (1.0 - distance / radius));
        }
        return 1.0f - drop;
    }

    /**
     * Pulsation de l'ecran d'une dimension, de 0 a 1 : la plus forte des perturbations a portee de l'auditeur.
     *
     * @param dimension dimension vue
     * @param lx auditeur
     * @param ly auditeur
     * @param lz auditeur
     */
    public float screenPulse(@Nullable Identifier dimension, double lx, double ly, double lz) {
        return nearListener(this.pulse, dimension, lx, ly, lz);
    }

    /**
     * Tremblement de camera dans une dimension, de 0 a 1 : le plus fort des sons a portee de l'auditeur.
     *
     * @param dimension dimension de l'auditeur
     * @param lx auditeur
     * @param ly auditeur
     * @param lz auditeur
     */
    public float cameraShake(@Nullable Identifier dimension, double lx, double ly, double lz) {
        return nearListener(this.shake, dimension, lx, ly, lz);
    }

    /** Baisse de la lumiere d'une dimension, de 0 a 1 : la plus forte des perturbations qui la demandent. */
    public float lightmapFlicker(@Nullable Identifier dimension) {
        float drop = 0.0f;
        for (int i = 0; i < CAPACITY; i++) {
            if (in(i, dimension) && this.lightmap[i] != null) {
                drop = Math.max(drop, this.lightmap[i].force(this.amplitude[i]));
            }
        }
        return drop;
    }

    /**
     * La variation de lumiere que les perturbations d'une dimension demandent a ses pixels, de 0 a 1 : la somme de leurs
     * reactions lumineuses du moment (neons, pulsation, lumiere ; le tremblement n'en est pas une), bornee. C'est ce que
     * le plafond de clignotement compte pour une vue de cette dimension.
     */
    public float load(@Nullable Identifier dimension) {
        float load = 0.0f;
        for (int i = 0; i < CAPACITY; i++) {
            if (in(i, dimension)) {
                load += force(this.neon[i], i) + force(this.pulse[i], i) + force(this.lightmap[i], i);
            }
        }
        return Math.min(1.0f, load);
    }

    private float nearListener(Cue.Reaction[] reactions, @Nullable Identifier dimension, double lx, double ly, double lz) {
        float strongest = 0.0f;
        for (int i = 0; i < CAPACITY; i++) {
            if (!in(i, dimension) || reactions[i] == null || distance(i, lx, ly, lz) > reactions[i].maxDistance()) {
                continue;
            }
            strongest = Math.max(strongest, reactions[i].force(this.amplitude[i]));
        }
        return strongest;
    }

    private float force(@Nullable Cue.Reaction reaction, int slot) {
        return reaction == null ? 0.0f : reaction.force(this.amplitude[slot]);
    }

    private boolean in(int slot, @Nullable Identifier dimension) {
        return this.active[slot] && this.dimensions[slot].equals(dimension);
    }

    private double distance(int slot, double px, double py, double pz) {
        double dx = px - this.x[slot];
        double dy = py - this.y[slot];
        double dz = pz - this.z[slot];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
