package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;
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
 * change remet a cuire sa section et ses voisines, plus le dernier rebond des voisines de celles-ci. Autour de la fenetre, le
 * loin cuit de la meme facon, une fois la fenetre au repos, et ne garde que des cellules de deux blocs ; de tout ce qui est publie se
 * mesure la piece moyenne de la dimension, qui eclaire au-dela. {@link #step} tourne sur le thread de cuisson ; ancre, changements et
 * resultats passent par des champs surs entre threads.
 */
public final class LightZone {

    /** Sections de chaque cote de la section d'ancrage, a l'horizontale. */
    public static final int RADIUS = 3;
    /**
     * Sections de chaque cote de la section d'ancrage dont la lumiere se cuit aussi pour le loin, a l'horizontale, comme celle de la
     * fenetre : la lightmap ne dit ni quel mur fait face aux dalles, ni ce que renvoient les murs de la piece.
     */
    public static final int FAR_RADIUS = 8;
    /** Cellules du loin d'une section, par axe horizontal : une pour deux blocs. */
    public static final int FAR_CELLS = 8;
    /** Cellules du loin d'une section a la verticale : une par bloc. */
    public static final int FAR_ROWS = 16;
    /** Tranches du loin publie d'une section : l'eclairement complet de chaque face, puis ce qui n'est pas la lumiere directe. */
    public static final int FAR_LAYERS = 2 * LightCell.FACES;
    /** Entiers d'une tranche du loin d'une section : X variant le plus vite, puis Y, puis Z. */
    public static final int FAR_FACE_VALUES = FAR_CELLS * FAR_ROWS * FAR_CELLS;
    /** Entiers du loin publie d'une section, tranche apres tranche. */
    public static final int FAR_VALUES = FAR_LAYERS * FAR_FACE_VALUES;
    /** Entiers d'un etat du loin garde par section, une tranche par face : sa lumiere directe, ou elle et son premier rebond. */
    static final int FAR_STATE_VALUES = LightCell.FACES * FAR_FACE_VALUES;
    /** Genres de surface de la piece moyenne : murs, sols, plafonds. */
    public static final int ROOM_KINDS = 3;
    /** Flottants de la piece moyenne : par genre, l'eclairement complet puis ce qui n'est pas la lumiere directe, en rouge, vert et bleu. */
    public static final int ROOM_VALUES = ROOM_KINDS * 6;
    /** Sections de la fenetre a la verticale, a partir de sa base. */
    public static final int HEIGHT = 2;
    /** Etats d'une section : rien, lumiere directe, un rebond, deux rebonds et l'estimation des suivants. */
    static final int FINAL_STAGE = 3;
    /** Etats du loin d'une section : lumiere directe, premier rebond, puis tout, publie. */
    private static final int FAR_DIRECT = 1;
    private static final int FAR_ONCE = 2;
    private static final int FAR_DONE = 3;
    /** Portee d'un changement de blocs sur le loin, en sections : un etat de moins par section d'ecart. */
    private static final int FAR_DEPENDS = 3;
    /** Sections publiees les plus recentes que garde la piece moyenne, par genre de surface. */
    private static final int ROOM_MEMORY = 256;
    /** Valeurs gardees par section dans la piece moyenne : l'eclairement puis sa part indirecte, en rouge, vert et bleu. */
    private static final int ROOM_SECTION_VALUES = 6;
    /** Ce qu'une surface doit recevoir au moins, en parts du fond, pour compter dans la piece moyenne : une piece eclairee. */
    private static final float ROOM_LIT = 3.0f;
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

    /**
     * L'eclairement d'une section pour le loin, a envoyer a la texture du loin.
     *
     * @param key clef de la section
     * @param values {@value #FAR_VALUES} entiers, au format de {@link #reduce} : l'eclairement complet, puis sa part indirecte
     */
    public record FarBaked(long key, int[] values) {
    }

    /** L'etat d'une section. */
    private static final class Section {

        private int stage;
        private int @Nullable [] direct;
        /** L'eclairement apres un rebond, garde une fois la section finie : ses voisines le relisent, jamais ses rebonds suivants. */
        private int @Nullable [] once;
        private int @Nullable [] values;
        private int @Nullable [] indirect;
        private int @Nullable [] surfaces;
        private int @Nullable [] lights;
        private boolean published;
    }

    /** L'etat d'une section du loin : sa lumiere directe, puis elle et son premier rebond, reduits ; puis publiee. */
    private static final class FarSection {

        private int stage;
        private int @Nullable [] direct;
        private int @Nullable [] once;

        /** L'etat a ce niveau, {@code null} s'il n'est pas (ou plus) connu. */
        private int @Nullable [] state(int level) {
            return level == FAR_DIRECT ? this.direct : this.once;
        }
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
    private final Queue<FarBaked> farBaked = new ConcurrentLinkedQueue<>();
    private final Queue<Long> farForgotten = new ConcurrentLinkedQueue<>();
    /** Le loin, section par section (thread de cuisson). */
    private final Long2ObjectMap<FarSection> far = new Long2ObjectOpenHashMap<>();
    /** Ce qu'une section du loin calcule a pleine resolution, le temps de le reduire (thread de cuisson). */
    private final int[] farDirect = new int[LightCell.VALUES];
    private final int[] farOnce = new int[LightCell.VALUES];
    private final int[] farValues = new int[LightCell.VALUES];
    private final int[] farIndirect = new int[LightCell.VALUES];
    /** Les voisines d'une section du loin, rendues a pleine resolution le temps d'un rebond (thread de cuisson). */
    private final int[][] farSources = new int[SectionBaker.neighbors()][];
    private final int[][] farExpanded = new int[SectionBaker.neighbors()][LightCell.VALUES];
    /** Sections comptees dans la piece moyenne, par genre de surface, et la moyenne des surfaces eclairees des plus recentes (thread de cuisson). */
    private final int[] roomSections = new int[ROOM_KINDS];
    private final float[][] roomRecent = new float[ROOM_KINDS][ROOM_MEMORY * ROOM_SECTION_VALUES];
    private final float[] roomLuma = new float[ROOM_MEMORY];
    private volatile float @Nullable [] room;
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
        this.baker.bounceColor(settings);
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
        return !this.working && this.changed.isEmpty() && this.baked.isEmpty() && this.forgotten.isEmpty() && this.farBaked.isEmpty()
                && this.farForgotten.isEmpty();
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

    /** Le prochain loin cuit, ou {@code null} (thread de rendu). */
    public @Nullable FarBaked pollFar() {
        return this.farBaked.poll();
    }

    /**
     * Le thread de rendu n'a plus le loin de cette section : il repartira si le loin revient sur elle.
     *
     * @param key section oubliee
     */
    public void forgetFar(long key) {
        this.farForgotten.add(key);
    }

    /**
     * La piece moyenne de la dimension, mesuree sur tout ce qui a ete publie : par genre de surface (murs, sols, plafonds), la moyenne
     * de l'eclairement complet puis de sa part indirecte des surfaces eclairees, en rouge, vert et bleu ; {@code null} avant la premiere
     * section (tout thread).
     */
    public float @Nullable [] room() {
        return this.room;
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
        long center = this.anchor;
        int centerX = ChunkSectionPos.unpackX(center);
        int centerZ = ChunkSectionPos.unpackZ(center);
        int base = this.baseSection;
        applyChanges();
        applyForgotten();
        if (this.republish) {
            this.republish = false;
            for (Section section : this.sections.values()) {
                section.published = false;
            }
            for (FarSection section : this.far.values()) {
                section.stage = Math.min(section.stage, FAR_ONCE);
            }
        }
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
            return advanceFar(centerX, base, centerZ);
        }
        int x = ChunkSectionPos.unpackX(best);
        int y = ChunkSectionPos.unpackY(best);
        int z = ChunkSectionPos.unpackZ(best);
        Section section = ensure(best, x, y, z, FINAL_STAGE, centerX, base, centerZ);
        section.published = true;
        this.baked.add(new Baked(best, section.values, section.indirect, section.surfaces, section.lights, stamp));
        // Le loin d'une section de la fenetre vient de son propre eclairement : rien a recuire, et il reste quand la fenetre s'en va.
        FarSection far = new FarSection();
        far.direct = reduced(section.direct);
        far.once = reduced(section.once);
        far.stage = FAR_DONE;
        this.far.put(best, far);
        publishFar(best, section.values, section.indirect);
        return true;
    }

    /**
     * La fenetre au repos, fait avancer d'un etat le loin d'une section hors d'elle, la plus proche de l'ancrage parmi celles qui le
     * peuvent, comme une section de la fenetre : sa lumiere directe, une fois ses blocs copies ; son premier rebond, une fois connue la
     * lumiere directe de ses voisines ; puis le second, les suivants estimes et le coin des surfaces, publies, une fois connu leur
     * premier rebond. Les voisines du loin se relisent dans leurs cellules de deux blocs.
     */
    private boolean advanceFar(int centerX, int base, int centerZ) {
        long best = 0L;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = base; y < base + HEIGHT; y++) {
            for (int z = centerZ - FAR_RADIUS; z <= centerZ + FAR_RADIUS; z++) {
                for (int x = centerX - FAR_RADIUS; x <= centerX + FAR_RADIUS; x++) {
                    int distance = (x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ);
                    if (distance < bestDistance && !inWindow(x, z, centerX, centerZ) && farCanAdvance(x, y, z, centerX, base, centerZ)) {
                        best = ChunkSectionPos.asLong(x, y, z);
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
        FarSection section = this.far.get(best);
        if (section == null) {
            section = new FarSection();
            this.far.put(best, section);
        }
        this.baker.direct(this.blocks, this.settings, x, y, z, this.farDirect);
        if (section.stage == 0) {
            section.direct = reduced(this.farDirect);
        } else if (section.stage == FAR_DIRECT) {
            farSources(x, y, z, FAR_DIRECT, this.farDirect, centerX, centerZ);
            this.baker.place(this.blocks, x, y, z);
            this.baker.bounce(this.blocks, x, y, z, this.farDirect, this.farSources, 0.0f, this.settings.bounce(), this.farOnce);
            section.once = reduced(this.farOnce);
        } else {
            expand(section.once, this.farOnce);
            farSources(x, y, z, FAR_ONCE, this.farOnce, centerX, centerZ);
            this.baker.place(this.blocks, x, y, z);
            this.baker.keepIndirect(this.farIndirect, this.settings);
            this.baker.keepFollowing(this.farOnce, this.settings.following());
            this.baker.bounce(this.blocks, x, y, z, this.farDirect, this.farSources, this.settings.occlusion(), this.settings.bounce(), this.farValues);
            publishFar(best, this.farValues, this.farIndirect);
        }
        section.stage++;
        return true;
    }

    /** {@code true} si cette section est dans la fenetre, a l'horizontale. */
    private static boolean inWindow(int x, int z, int centerX, int centerZ) {
        return Math.abs(x - centerX) <= RADIUS && Math.abs(z - centerZ) <= RADIUS;
    }

    /** {@code true} si cette section est du loin : hors de la fenetre, a sa portee, sur ses rangees. */
    private static boolean inFar(int x, int y, int z, int centerX, int base, int centerZ) {
        return y >= base && y < base + HEIGHT && Math.abs(x - centerX) <= FAR_RADIUS && Math.abs(z - centerZ) <= FAR_RADIUS
                && !inWindow(x, z, centerX, centerZ);
    }

    /**
     * {@code true} si le loin de cette section, dans le monde et pas encore publie, peut avancer d'un etat : pour sa lumiere directe,
     * les blocs de ses voisines sont copies ; pour un rebond, ses voisines du loin en sont au moins a son etat.
     */
    private boolean farCanAdvance(int x, int y, int z, int centerX, int base, int centerZ) {
        FarSection section = this.far.get(ChunkSectionPos.asLong(x, y, z));
        int stage = section == null ? 0 : section.stage;
        if (!inWorld(y) || stage == FAR_DONE) {
            return false;
        }
        for (int i = 0; i < SectionBaker.neighbors(); i++) {
            int nx = x - 1 + i % 3;
            int ny = y - 1 + i / 3 % 3;
            int nz = z - 1 + i / 9;
            if (!inWorld(ny)) {
                continue;
            }
            if (stage == 0) {
                if (this.blocks.section(ChunkSectionPos.asLong(nx, ny, nz)) == null) {
                    return false;
                }
            } else if (inFar(nx, ny, nz, centerX, base, centerZ)) {
                FarSection neighbor = this.far.get(ChunkSectionPos.asLong(nx, ny, nz));
                if (neighbor == null || neighbor.stage < stage) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Les sources d'un rebond du loin : la section elle-meme, et l'etat de ses voisines a ce niveau, celles de la fenetre telles
     * qu'elles sont, celles du loin rendues a pleine resolution.
     */
    private void farSources(int x, int y, int z, int level, int[] own, int centerX, int centerZ) {
        for (int i = 0; i < this.farSources.length; i++) {
            int nx = x - 1 + i % 3;
            int ny = y - 1 + i / 3 % 3;
            int nz = z - 1 + i / 9;
            long key = ChunkSectionPos.asLong(nx, ny, nz);
            if (nx == x && ny == y && nz == z) {
                this.farSources[i] = own;
            } else if (inWindow(nx, nz, centerX, centerZ)) {
                Section section = this.sections.get(key);
                this.farSources[i] = section == null ? null : at(section, level);
            } else {
                FarSection section = this.far.get(key);
                int[] state = section == null ? null : section.state(level);
                this.farSources[i] = state == null ? null : expand(state, this.farExpanded[i]);
            }
        }
    }

    /** Publie le loin d'une section, d'apres son eclairement et sa part indirecte, et le compte dans la piece moyenne. */
    private void publishFar(long key, int[] values, int[] indirect) {
        int[] out = new int[FAR_VALUES];
        reduce(values, out, 0);
        reduce(indirect, out, LightCell.FACES);
        this.farBaked.add(new FarBaked(key, out));
        measureRoom(out);
    }

    /**
     * Compte une section publiee dans la piece moyenne : par genre de surface, la moyenne de ses surfaces eclairees, gardee parmi les
     * {@value #ROOM_MEMORY} plus recentes ; puis la piece moyenne se refait sur elles.
     */
    private void measureRoom(int[] far) {
        float threshold = ROOM_LIT * luma(this.settings.ambientRed(), this.settings.ambientGreen(), this.settings.ambientBlue());
        float[] next = new float[ROOM_VALUES];
        double[] sums = new double[ROOM_SECTION_VALUES];
        for (int kind = 0; kind < ROOM_KINDS; kind++) {
            Arrays.fill(sums, 0.0);
            int count = 0;
            for (int face = 0; face < LightCell.FACES; face++) {
                if (kindOf(face) != kind) {
                    continue;
                }
                for (int i = face * FAR_FACE_VALUES; i < (face + 1) * FAR_FACE_VALUES; i++) {
                    int total = far[i];
                    if (!LightCell.valid(total) || luma(LightCell.red(total), LightCell.green(total), LightCell.blue(total)) <= threshold) {
                        continue;
                    }
                    int around = far[i + LightCell.FACES * FAR_FACE_VALUES];
                    sums[0] += LightCell.red(total);
                    sums[1] += LightCell.green(total);
                    sums[2] += LightCell.blue(total);
                    sums[3] += LightCell.red(around);
                    sums[4] += LightCell.green(around);
                    sums[5] += LightCell.blue(around);
                    count++;
                }
            }
            if (count > 0) {
                int slot = this.roomSections[kind] % ROOM_MEMORY * ROOM_SECTION_VALUES;
                for (int c = 0; c < ROOM_SECTION_VALUES; c++) {
                    this.roomRecent[kind][slot + c] = (float) (sums[c] / count);
                }
                this.roomSections[kind]++;
            }
            litRoom(kind, next);
        }
        this.room = next;
    }

    /**
     * La piece moyenne d'un genre de surface : la moyenne du quart le plus clair des sections recentes, une piece bien eclairee. Les plus
     * sombres sont des couloirs sans dalle ou des bords de piece, que la lightmap assombrit deja au shader : les compter ferait paraitre
     * sombre toute piece eclairee au loin (mesure : la moyenne de toutes valait 0,55 a 0,76 de la zone dans deux salles eclairees, le
     * quart le plus clair 0,69 a 1,17).
     */
    private void litRoom(int kind, float[] out) {
        int size = Math.min(this.roomSections[kind], ROOM_MEMORY);
        float[] recent = this.roomRecent[kind];
        for (int i = 0; i < size; i++) {
            int slot = i * ROOM_SECTION_VALUES;
            this.roomLuma[i] = luma(recent[slot], recent[slot + 1], recent[slot + 2]);
        }
        float[] sorted = Arrays.copyOf(this.roomLuma, size);
        Arrays.sort(sorted);
        float bright = size == 0 ? 0.0f : sorted[size * 3 / 4];
        int kept = 0;
        for (int i = 0; i < size; i++) {
            if (this.roomLuma[i] >= bright) {
                for (int c = 0; c < ROOM_SECTION_VALUES; c++) {
                    out[kind * ROOM_SECTION_VALUES + c] += recent[i * ROOM_SECTION_VALUES + c];
                }
                kept++;
            }
        }
        for (int c = 0; c < ROOM_SECTION_VALUES; c++) {
            out[kind * ROOM_SECTION_VALUES + c] /= Math.max(kept, 1);
        }
    }

    /** Genre de surface d'une face : 0 un mur, 1 un sol (normale vers le haut), 2 un plafond. */
    static int kindOf(int face) {
        int normal = LightCell.NORMAL_Y[face];
        return normal > 0 ? 1 : normal < 0 ? 2 : 0;
    }

    private static float luma(float red, float green, float blue) {
        return 0.299f * red + 0.587f * green + 0.114f * blue;
    }

    /** L'eclairement d'une section, reduit a un etat du loin, {@value #FAR_STATE_VALUES} entiers. */
    static int[] reduced(int[] values) {
        int[] out = new int[FAR_STATE_VALUES];
        reduce(values, out, 0);
        return out;
    }

    /**
     * L'eclairement d'une section reduit au loin, a partir de la tranche {@code firstLayer} : une cellule pour deux blocs a l'horizontale
     * et un a la verticale, la moyenne des surfaces de chaque face qu'elle contient ; une cellule sans surface reste a zero, et la
     * texture n'en tient pas compte.
     *
     * @param values eclairement de la section, {@value LightCell#VALUES} entiers
     * @param out loin a remplir
     * @param firstLayer premiere tranche ecrite, une par face
     */
    static void reduce(int[] values, int[] out, int firstLayer) {
        for (int face = 0; face < LightCell.FACES; face++) {
            for (int z = 0; z < FAR_CELLS; z++) {
                for (int y = 0; y < FAR_ROWS; y++) {
                    for (int x = 0; x < FAR_CELLS; x++) {
                        float red = 0.0f;
                        float green = 0.0f;
                        float blue = 0.0f;
                        int count = 0;
                        for (int i = 0; i < 4; i++) {
                            int value = values[LightCell.index(x * 2 + (i & 1), y, z * 2 + (i >> 1), face)];
                            if (LightCell.valid(value)) {
                                red += LightCell.red(value);
                                green += LightCell.green(value);
                                blue += LightCell.blue(value);
                                count++;
                            }
                        }
                        out[farIndex(x, y, z, firstLayer + face)] = count == 0 ? 0 : LightCell.pack(red / count, green / count, blue / count);
                    }
                }
            }
        }
    }

    /**
     * Un etat du loin rendu a la resolution d'une section : chaque cellule prend la valeur de la sienne.
     *
     * @return {@code out}
     */
    static int[] expand(int[] reduced, int[] out) {
        for (int face = 0; face < LightCell.FACES; face++) {
            for (int z = 0; z < SECTION_BLOCKS; z++) {
                for (int y = 0; y < SECTION_BLOCKS; y++) {
                    for (int x = 0; x < SECTION_BLOCKS; x++) {
                        out[LightCell.index(x, y, z, face)] = reduced[farIndex(x >> 1, y, z >> 1, face)];
                    }
                }
            }
        }
        return out;
    }

    /** Indice d'une cellule du loin d'une section, dans sa tranche. */
    static int farIndex(int x, int y, int z, int layer) {
        return ((layer * FAR_CELLS + z) * FAR_ROWS + y) * FAR_CELLS + x;
    }

    private void applyForgotten() {
        Long forgottenFar = this.farForgotten.poll();
        while (forgottenFar != null) {
            FarSection section = this.far.get(forgottenFar.longValue());
            if (section != null) {
                section.stage = Math.min(section.stage, FAR_ONCE);
            }
            forgottenFar = this.farForgotten.poll();
        }
        Long key = this.forgotten.poll();
        while (key != null) {
            Section section = this.sections.get(key.longValue());
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
            // La lumiere directe d'une section du loin lit les blocs de ses voisines, son premier rebond leur lumiere directe, et le reste
            // leur premier rebond : le changement la fait revenir d'un etat de moins par section d'ecart.
            for (int dz = -FAR_DEPENDS; dz <= FAR_DEPENDS; dz++) {
                for (int dy = -FAR_DEPENDS; dy <= FAR_DEPENDS; dy++) {
                    for (int dx = -FAR_DEPENDS; dx <= FAR_DEPENDS; dx++) {
                        FarSection section = this.far.get(ChunkSectionPos.asLong(x + dx, y + dy, z + dz));
                        if (section != null) {
                            int reach = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                            section.stage = Math.min(section.stage, Math.max(reach - 1, 0));
                            if (section.stage < FAR_DIRECT) {
                                section.direct = null;
                            }
                            if (section.stage < FAR_ONCE) {
                                section.once = null;
                            }
                        }
                    }
                }
            }
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
        int farReach = FAR_RADIUS + KEEP_MARGIN;
        this.far.keySet().removeIf(key -> Math.abs(ChunkSectionPos.unpackX(key) - centerX) > farReach
                || Math.abs(ChunkSectionPos.unpackZ(key) - centerZ) > farReach
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
                neighbors[i] = at(ensure(SectionBaker.neighborKey(i, x, y, z), nx, ny, nz, stage - 1, centerX, base, centerZ), stage - 1);
            }
        }
        int[] out = new int[LightCell.VALUES];
        this.baker.place(this.blocks, x, y, z);
        boolean last = stage == FINAL_STAGE;
        if (last) {
            section.indirect = new int[LightCell.VALUES];
            section.surfaces = this.baker.surfaces(section.direct, x, y, z);
            this.baker.keepIndirect(section.indirect, this.settings);
            if (this.settings.following() > 1.0f) {
                this.baker.keepFollowing(section.once, this.settings.following());
            }
        }
        this.baker.bounce(this.blocks, x, y, z, section.direct, neighbors, last ? this.settings.occlusion() : 0.0f, this.settings.bounce(), out);
        section.values = out;
        if (stage == FINAL_STAGE - 1) {
            section.once = out;
        }
        section.stage = stage;
    }

    /**
     * L'eclairement d'une section a un etat d'avant le dernier, meme si elle est allee plus loin : une voisine deja finie ne donne
     * pas ses rebonds suivants a celle qui n'en compte qu'un, sans quoi le bord d'une section se dessine plus clair que le reste.
     */
    private static int[] at(Section section, int stage) {
        return stage <= 1 ? section.direct : section.once;
    }
}
