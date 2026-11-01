package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La cuisson de la lumiere d'un monde autour d'un point d'ancrage : une fenetre de sections, la plus proche d'abord,
 * chacune passant par trois etats : lumiere directe, puis deux rebonds, le second lisant le resultat du premier chez ses
 * voisines. Une section ne cuit que lorsque tout ce dont elle depend est copie (deux sections autour d'elle), et un bloc
 * change remet a cuire sa section et ses voisines, plus le dernier rebond des voisines de celles-ci. {@link #step} tourne
 * sur le thread de cuisson ; ancre, changements et resultats passent par des champs surs entre threads.
 */
public final class LightZone {

    /** Sections de chaque cote de la section d'ancrage, a l'horizontale. */
    public static final int RADIUS = 3;
    /** Sections de la fenetre a la verticale, a partir de sa base. */
    public static final int HEIGHT = 2;
    /** Etats d'une section : rien, lumiere directe, un rebond, deux rebonds. */
    static final int FINAL_STAGE = 3;
    /** Sections copiees au-dela de la fenetre : les rayons et les rebonds les lisent. */
    public static final int COPY_MARGIN = 1;
    /** Portee de ce dont depend une section, en sections : la lumiere directe, puis les deux rebonds. */
    private static final int DEPENDS = 2;
    /** Sections gardees au-dela de la fenetre : un joueur qui fait demi-tour ne les recuit pas. */
    private static final int KEEP_MARGIN = 2;
    /** Blocs a garder entre la camera et le haut ou le bas de la fenetre : plus pres, la fenetre se deplace. */
    private static final int BASE_MARGIN = 4;
    private static final int SECTION_BLOCKS = 16;
    private static final int NO_BASE = Integer.MIN_VALUE;

    /**
     * Une section cuite, a envoyer a la texture de zone et au cache de surfaces.
     *
     * @param key clef de la section
     * @param values eclairement complet de ses surfaces
     * @param indirect ce qui n'est pas la lumiere directe : le fond et les rebonds
     * @param surfaces ses surfaces, au format de {@link SurfaceAtlas#surface}
     * @param lights neons a sa portee : quatre entiers chacun, position du bloc et face qui eclaire
     * @param stamp nombre de changements connus quand sa cuisson a commence : plus vieille qu'un bloc change, elle est perimee
     */
    public record Baked(long key, int[] values, int[] indirect, int[] surfaces, int[] lights, long stamp) {
    }

    /** L'etat d'une section. */
    private static final class Section {

        private int stage;
        private int @Nullable [] direct;
        private int @Nullable [] values;
        private int @Nullable [] indirect;
        private int @Nullable [] surfaces;
        private int @Nullable [] lights;
        private boolean published;
    }

    private final LightBlocks blocks;
    private final LightBakeSettings settings;
    private final int bottomSection;
    private final int topSection;
    private final SectionBaker baker = new SectionBaker();
    private final Long2ObjectMap<Section> sections = new Long2ObjectOpenHashMap<>();
    private final Queue<Long> changed = new ConcurrentLinkedQueue<>();
    private final Queue<Baked> baked = new ConcurrentLinkedQueue<>();
    private final Queue<Long> forgotten = new ConcurrentLinkedQueue<>();
    private volatile long anchor;
    private volatile int baseSection;
    private volatile boolean republish;
    private volatile long changes;
    private volatile boolean working;

    /**
     * La premiere section de la fenetre pour une camera : la meme tant que la camera reste loin de ses bords, pour qu'un saut
     * au mauvais endroit ne deplace pas toute la fenetre.
     *
     * @param previous base precedente, {@link Integer#MIN_VALUE} sans base
     * @param blockY Y de la camera
     */
    public static int baseFor(int previous, int blockY) {
        if (previous != NO_BASE) {
            int low = previous * SECTION_BLOCKS;
            if (blockY >= low + BASE_MARGIN && blockY < low + HEIGHT * SECTION_BLOCKS - BASE_MARGIN) {
                return previous;
            }
        }
        // La zone couvre deux sections de haut : la base garde au moins huit blocs de part et d'autre de la camera.
        return Math.floorDiv(blockY - SECTION_BLOCKS / 2, SECTION_BLOCKS);
    }

    /**
     * @param blocks copie du monde
     * @param settings reglages de la cuisson
     * @param bottomSection premiere section du monde
     * @param topSection section juste au-dessus du monde
     */
    public LightZone(LightBlocks blocks, LightBakeSettings settings, int bottomSection, int topSection) {
        this.blocks = blocks;
        this.settings = settings;
        this.bottomSection = bottomSection;
        this.topSection = topSection;
    }

    /** Reglages de la cuisson de cette zone. */
    public LightBakeSettings settings() {
        return this.settings;
    }

    /** Copie du monde de cette zone. */
    public LightBlocks blocks() {
        return this.blocks;
    }

    /**
     * Pose l'ancrage (thread de rendu).
     *
     * @param sectionX section de la camera
     * @param base premiere section de la fenetre, a la verticale
     * @param sectionZ section de la camera
     */
    public void anchor(int sectionX, int base, int sectionZ) {
        this.anchor = ChunkSectionPos.asLong(sectionX, 0, sectionZ);
        this.baseSection = base;
    }

    /**
     * Un bloc ou une section a change : la section et ses voisines sont a recuire (thread de rendu, la copie deja a jour).
     *
     * @return le numero de ce changement : une section cuite avant lui a un numero plus petit
     */
    public long onChanged(long sectionKey) {
        long stamp = this.changes + 1L;
        this.changes = stamp;
        this.changed.add(sectionKey);
        return stamp;
    }

    /** {@code true} si plus rien n'est a cuire ni a prendre : le thread de cuisson est au repos (tests du client). */
    public boolean idle() {
        return !this.working && this.changed.isEmpty() && this.baked.isEmpty() && this.forgotten.isEmpty();
    }

    /** {@code true} si cette section existe dans le monde. */
    public boolean inWorld(int sectionY) {
        return sectionY >= this.bottomSection && sectionY < this.topSection;
    }

    /** Toutes les sections deja cuites repartiront vers la texture, a vider (tout thread). */
    public void republish() {
        this.republish = true;
    }

    /**
     * Le thread de rendu n'a plus cette section : elle est sortie de la fenetre et sa place est effacee, ou elle en etait deja sortie
     * a son arrivee. Sa cuisson reste bonne, mais elle repartira si la fenetre revient sur elle.
     *
     * @param key section oubliee
     */
    public void forget(long key) {
        this.forgotten.add(key);
    }

    /** La prochaine section cuite, ou {@code null} (thread de rendu). */
    public @Nullable Baked poll() {
        return this.baked.poll();
    }

    /**
     * Fait avancer la cuisson d'une section, la plus proche de l'ancrage parmi celles qui attendent.
     *
     * @return {@code true} si une section a cuit : il y en a peut-etre d'autres
     */
    public boolean step() {
        this.working = true;
        boolean baking = advance();
        this.working = baking;
        return baking;
    }

    private boolean advance() {
        long stamp = this.changes;
        applyChanges();
        applyForgotten();
        if (this.republish) {
            this.republish = false;
            for (Section section : this.sections.values()) {
                section.published = false;
            }
        }
        long center = this.anchor;
        int centerX = ChunkSectionPos.unpackX(center);
        int centerZ = ChunkSectionPos.unpackZ(center);
        int base = this.baseSection;
        retain(centerX, base, centerZ);
        long best = 0L;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = base; y < base + HEIGHT; y++) {
            for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
                for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
                    int distance = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                    long key = ChunkSectionPos.asLong(x, y, z);
                    if (distance < bestDistance && waiting(key, x, y, z, centerX, base, centerZ)) {
                        best = key;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (bestDistance == Integer.MAX_VALUE) {
            return false;
        }
        int x = ChunkSectionPos.unpackX(best);
        int y = ChunkSectionPos.unpackY(best);
        int z = ChunkSectionPos.unpackZ(best);
        Section section = ensure(best, x, y, z, FINAL_STAGE, centerX, base, centerZ);
        section.published = true;
        this.baked.add(new Baked(best, section.values, section.indirect, section.surfaces, section.lights, stamp));
        return true;
    }

    private void applyForgotten() {
        Long key = this.forgotten.poll();
        while (key != null) {
            Section section = this.sections.get(key);
            if (section != null) {
                section.published = false;
            }
            key = this.forgotten.poll();
        }
    }

    private void applyChanges() {
        Long key = this.changed.poll();
        while (key != null) {
            int x = ChunkSectionPos.unpackX(key);
            int y = ChunkSectionPos.unpackY(key);
            int z = ChunkSectionPos.unpackZ(key);
            for (int dz = -DEPENDS; dz <= DEPENDS; dz++) {
                for (int dy = -DEPENDS; dy <= DEPENDS; dy++) {
                    for (int dx = -DEPENDS; dx <= DEPENDS; dx++) {
                        Section section = this.sections.get(ChunkSectionPos.asLong(x + dx, y + dy, z + dz));
                        if (section != null) {
                            boolean near = Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1;
                            section.stage = near ? 0 : Math.min(section.stage, FINAL_STAGE - 1);
                            section.published = false;
                        }
                    }
                }
            }
            key = this.changed.poll();
        }
    }

    /** Oublie les sections loin de la fenetre. */
    private void retain(int centerX, int base, int centerZ) {
        int reach = RADIUS + KEEP_MARGIN;
        this.sections.keySet().removeIf(key -> Math.abs(ChunkSectionPos.unpackX(key) - centerX) > reach
                || Math.abs(ChunkSectionPos.unpackZ(key) - centerZ) > reach
                || ChunkSectionPos.unpackY(key) < base - KEEP_MARGIN || ChunkSectionPos.unpackY(key) >= base + HEIGHT + KEEP_MARGIN);
    }

    /** {@code true} si cette section, dans le monde, reste a cuire ou a publier, et que tout ce dont elle depend est copie. */
    private boolean waiting(long key, int x, int y, int z, int centerX, int base, int centerZ) {
        Section section = this.sections.get(key);
        if (y < this.bottomSection || y >= this.topSection || section != null && section.published) {
            return false;
        }
        for (int sz = z - DEPENDS; sz <= z + DEPENDS; sz++) {
            for (int sy = y - DEPENDS; sy <= y + DEPENDS; sy++) {
                for (int sx = x - DEPENDS; sx <= x + DEPENDS; sx++) {
                    if (copied(sx, sy, sz, centerX, base, centerZ) && this.blocks.section(ChunkSectionPos.asLong(sx, sy, sz)) == null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** {@code true} si cette section fait partie de ce que le thread de rendu copie autour de l'ancrage, dans le monde. */
    private boolean copied(int x, int y, int z, int centerX, int base, int centerZ) {
        return y >= this.bottomSection && y < this.topSection && Math.abs(x - centerX) <= RADIUS + COPY_MARGIN && Math.abs(z - centerZ) <= RADIUS + COPY_MARGIN
                && y >= base - COPY_MARGIN && y < base + HEIGHT + COPY_MARGIN;
    }

    /** Amene une section a un etat, ses voisines de la fenetre d'abord a l'etat d'avant. */
    private Section ensure(long key, int x, int y, int z, int stage, int centerX, int base, int centerZ) {
        Section section = this.sections.get(key);
        if (section == null) {
            section = new Section();
            this.sections.put(key, section);
        }
        if (section.stage == 0) {
            int[] direct = new int[LightCell.VALUES];
            this.baker.direct(this.blocks, this.settings, x, y, z, direct);
            section.direct = direct;
            section.values = direct;
            section.lights = this.baker.nearLights(x, y, z);
            section.stage = 1;
        }
        while (section.stage < stage) {
            bounce(section, x, y, z, section.stage + 1, centerX, base, centerZ);
        }
        return section;
    }

    private void bounce(Section section, int x, int y, int z, int stage, int centerX, int base, int centerZ) {
        int[][] neighbors = new int[SectionBaker.neighbors()][];
        for (int i = 0; i < neighbors.length; i++) {
            int nx = x - 1 + i % 3;
            int ny = y - 1 + i / 3 % 3;
            int nz = z - 1 + i / 9;
            if (copied(nx, ny, nz, centerX, base, centerZ)) {
                neighbors[i] = ensure(SectionBaker.neighborKey(i, x, y, z), nx, ny, nz, stage - 1, centerX, base, centerZ).values;
            }
        }
        int[] out = new int[LightCell.VALUES];
        this.baker.place(this.blocks, x, y, z);
        boolean last = stage == FINAL_STAGE;
        if (last) {
            section.indirect = new int[LightCell.VALUES];
            section.surfaces = this.baker.surfaces(section.direct, x, y, z);
            this.baker.keepIndirect(section.indirect, this.settings);
        }
        this.baker.bounce(this.blocks, x, y, z, section.direct, neighbors, last ? this.settings.occlusion() : 0.0f, this.settings.bounce(), out);
        section.values = out;
        section.stage = stage;
    }
}
