package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La cuisson d'une section, sur le thread de cuisson. D'abord la lumiere directe de chaque surface : le facteur de forme
 * de chaque dalle a portee ({@link AreaLight}), multiplie par la part de la dalle que la surface voit (quelques rayons
 * vers des points de la dalle, d'ou des ombres douces). Puis le rebond : des rayons repartis sur la demi-sphere de la
 * surface vont chercher l'eclairement des surfaces qu'ils touchent, teinte par leur albedo, d'ou le mur jaune qui colore
 * ce qui lui fait face. Le dernier rebond assombrit aussi ce que des blocs tout proches cachent, d'ou les coins sombres.
 * Seules les faces d'une cellule d'air posees contre un bloc opaque portent une valeur.
 */
final class SectionBaker {

    /** Rayons vers une dalle. */
    static final int LIGHT_SAMPLES = 4;
    /** Rayons du rebond, par surface. */
    static final int BOUNCE_RAYS = 24;
    /** Longueur des rayons du rebond, en blocs : ils restent dans les sections voisines. */
    static final float BOUNCE_LENGTH = 14.0f;
    /** Distance sous laquelle un bloc touche par un rayon du rebond cache la surface, en blocs. */
    private static final float NEAR = 1.5f;
    /** Part de sa lumiere sous laquelle une dalle ne compte plus. */
    private static final float MIN_SHARE = 1.0e-4f;
    /** La seule face d'un bloc qui eclaire : une dalle de plafond eclaire vers le bas, jamais par ses cotes ni vers le haut. */
    private static final int EMITTING_FACE = 3;
    /** Ecart a la surface d'ou partent les rayons, pour ne pas partir dans le bloc. */
    private static final float SURFACE_OFFSET = 0.02f;
    /**
     * Ce que valent tous les rebonds a partir du second, en parts du second : chacun garde environ la moitie du precedent, comme pour
     * les rebonds traces ({@code bounce.fsh}). Sans elle, la zone, qui ne compte que deux rebonds, est plus sombre que les tuiles, et la
     * lumiere change quand une surface prend sa tuile ; sans lumiere tracee, les paliers ont ainsi les memes rebonds que l'Ultra.
     */
    static final float FOLLOWING = 2.0f;
    /** Moitie de l'ecart entre les points vises sur une dalle d'un bloc. */
    private static final float SAMPLE_SPREAD = 0.25f;
    private static final int SECTION = 16;
    private static final int MASK = SECTION - 1;
    private static final int NEIGHBORS = 27;
    private static final int INITIAL_LIGHTS = 64;
    /** Entiers par neon rendu au cache de surfaces : position du bloc, face. */
    static final int LIGHT_INTS = 4;
    /** Neons rendus au cache de surfaces, les plus proches de la section : chaque tuile y prend les siens. */
    static final int MAX_NEAR_LIGHTS = 192;
    private static final float[][] BOUNCE_DIRECTIONS = bounceDirections();

    private final LightWindow window = new LightWindow();
    private final BlockRay ray = new BlockRay();
    private float[] rects = new float[INITIAL_LIGHTS * AreaLight.VALUES];
    private int[] lightFaces = new int[INITIAL_LIGHTS];
    private int[] lightBlocks = new int[INITIAL_LIGHTS * 3];
    private int lights;
    private int @Nullable [] indirect;
    private int @Nullable [] firstBounce;
    private float followingShare;
    private float ambientRed;
    private float ambientGreen;
    private float ambientBlue;
    private float bouncedRed;
    private float bouncedGreen;
    private float bouncedBlue;
    private float colorRed = 1.0f;
    private float colorGreen = 1.0f;
    private float colorBlue = 1.0f;
    private float seenShare;

    /** Les rayons du rebond de chaque face : repartis selon le cosinus de la normale, leur moyenne suffit. */
    private static float[][] bounceDirections() {
        float[][] directions = new float[LightCell.FACES][BOUNCE_RAYS * 3];
        for (int face = 0; face < LightCell.FACES; face++) {
            int nx = LightCell.NORMAL_X[face];
            int ny = LightCell.NORMAL_Y[face];
            int nz = LightCell.NORMAL_Z[face];
            int ux = nz != 0 ? 1 : 0;
            int uz = 1 - ux;
            int vx = ny * uz;
            int vy = nz * ux - nx * uz;
            int vz = -ny * ux;
            for (int i = 0; i < BOUNCE_RAYS; i++) {
                double u = (i + 0.5) / BOUNCE_RAYS;
                double phi = 2.0 * Math.PI * vanDerCorput(i);
                double radius = Math.sqrt(u);
                double a = radius * Math.cos(phi);
                double b = radius * Math.sin(phi);
                double c = Math.sqrt(1.0 - u);
                directions[face][i * 3] = (float) (a * ux + b * vx + c * nx);
                directions[face][i * 3 + 1] = (float) (b * vy + c * ny);
                directions[face][i * 3 + 2] = (float) (a * uz + b * vz + c * nz);
            }
        }
        return directions;
    }

    /** Suite a faible discrepance : les bits de l'indice en miroir derriere la virgule. */
    private static double vanDerCorput(int index) {
        return (Integer.reverse(index) & 0xFFFFFFFFL) / 4294967296.0;
    }

    /**
     * Lumiere directe d'une section.
     *
     * @param blocks copie du monde
     * @param settings reglages de la cuisson
     * @param sectionX section
     * @param sectionY section
     * @param sectionZ section
     * @param out section cuite, {@value LightCell#VALUES} valeurs
     */
    void direct(LightBlocks blocks, LightBakeSettings settings, int sectionX, int sectionY, int sectionZ, int[] out) {
        this.window.fill(blocks, sectionX, sectionY, sectionZ);
        collectLights(blocks.emissive(), settings);
        int baseX = sectionX << 4;
        int baseY = sectionY << 4;
        int baseZ = sectionZ << 4;
        for (int z = 0; z < SECTION; z++) {
            for (int y = 0; y < SECTION; y++) {
                for (int x = 0; x < SECTION; x++) {
                    if (this.window.block(baseX + x, baseY + y, baseZ + z) != LightBlocks.AIR) {
                        for (int face = 0; face < LightCell.FACES; face++) {
                            out[LightCell.index(x, y, z, face)] = 0;
                        }
                        continue;
                    }
                    for (int face = 0; face < LightCell.FACES; face++) {
                        out[LightCell.index(x, y, z, face)] = surface(baseX + x, baseY + y, baseZ + z, face) ? lit(baseX + x, baseY + y, baseZ + z, face, settings) : 0;
                    }
                }
            }
        }
    }

    /** {@code true} si cette face de la cellule d'air est posee contre un bloc opaque. */
    private boolean surface(int x, int y, int z, int face) {
        return this.window.block(x - LightCell.NORMAL_X[face], y - LightCell.NORMAL_Y[face], z - LightCell.NORMAL_Z[face]) != LightBlocks.AIR;
    }

    /** La face du bas des blocs qui emettent, si elle est libre, a portee de la section, hors neons morts du calendrier. */
    private void collectLights(boolean[] emissive, LightBakeSettings settings) {
        this.lights = 0;
        int originX = this.window.originX();
        int originY = this.window.originY();
        int originZ = this.window.originZ();
        for (int z = 0; z < LightWindow.SIZE; z++) {
            for (int y = 0; y < LightWindow.SIZE; y++) {
                for (int x = 0; x < LightWindow.SIZE; x++) {
                    int material = this.window.block(originX + x, originY + y, originZ + z) & 0xFF;
                    if (emissive[material] && settings.lit(originX + x, originY + y, originZ + z)) {
                        addLight(originX + x, originY + y, originZ + z, settings.size());
                    }
                }
            }
        }
    }

    private void addLight(int x, int y, int z, float size) {
        if (this.window.block(x + LightCell.NORMAL_X[EMITTING_FACE], y + LightCell.NORMAL_Y[EMITTING_FACE], z + LightCell.NORMAL_Z[EMITTING_FACE]) != LightBlocks.AIR) {
            return;
        }
        if (this.lights == this.lightFaces.length) {
            this.rects = Arrays.copyOf(this.rects, this.rects.length * 2);
            this.lightFaces = Arrays.copyOf(this.lightFaces, this.lightFaces.length * 2);
            this.lightBlocks = Arrays.copyOf(this.lightBlocks, this.lightBlocks.length * 2);
        }
        AreaLight.blockFace(this.rects, this.lights * AreaLight.VALUES, x, y, z, EMITTING_FACE, size);
        this.lightBlocks[this.lights * 3] = x;
        this.lightBlocks[this.lights * 3 + 1] = y;
        this.lightBlocks[this.lights * 3 + 2] = z;
        this.lightFaces[this.lights++] = EMITTING_FACE;
    }

    /**
     * Les neons trouves par la derniere lumiere directe, les plus proches du centre de la section d'abord.
     *
     * @param sectionX section
     * @param sectionY section
     * @param sectionZ section
     * @return {@value #LIGHT_INTS} entiers par neon : position du bloc et face qui eclaire, {@value #MAX_NEAR_LIGHTS} neons au plus
     */
    int[] nearLights(int sectionX, int sectionY, int sectionZ) {
        int centerX = (sectionX << 4) + SECTION / 2;
        int centerY = (sectionY << 4) + SECTION / 2;
        int centerZ = (sectionZ << 4) + SECTION / 2;
        long[] order = new long[this.lights];
        for (int i = 0; i < this.lights; i++) {
            int dx = this.lightBlocks[i * 3] - centerX;
            int dy = this.lightBlocks[i * 3 + 1] - centerY;
            int dz = this.lightBlocks[i * 3 + 2] - centerZ;
            order[i] = (long) (dx * dx + dy * dy + dz * dz) << 32 | i;
        }
        Arrays.sort(order);
        int kept = Math.min(this.lights, MAX_NEAR_LIGHTS);
        // Le tri par distance au centre de la section garde ce qui compte pour toutes ses surfaces : une dalle hors de portee d'un
        // coin de la section reste utile a un autre.
        int[] near = new int[kept * LIGHT_INTS];
        for (int k = 0; k < kept; k++) {
            int i = (int) order[k];
            near[k * LIGHT_INTS] = this.lightBlocks[i * 3];
            near[k * LIGHT_INTS + 1] = this.lightBlocks[i * 3 + 1];
            near[k * LIGHT_INTS + 2] = this.lightBlocks[i * 3 + 2];
            near[k * LIGHT_INTS + 3] = this.lightFaces[i];
        }
        return near;
    }

    /**
     * Les surfaces d'une section, pour le cache de surfaces. La fenetre de blocs est celle de la section.
     *
     * @param direct lumiere directe de la section
     * @param sectionX section
     * @param sectionY section
     * @param sectionZ section
     * @return les surfaces, au format de {@link SurfaceAtlas#surface}
     */
    int[] surfaces(int[] direct, int sectionX, int sectionY, int sectionZ) {
        int[] found = new int[LightCell.VALUES];
        int count = 0;
        for (int i = 0; i < LightCell.VALUES; i++) {
            if (LightCell.valid(direct[i])) {
                int face = i / LightCell.SECTION_CELLS;
                int cell = i & LightCell.SECTION_CELLS - 1;
                byte material = this.window.block((sectionX << 4) + (cell & MASK) - LightCell.NORMAL_X[face], (sectionY << 4) + (cell >> 4 & MASK) - LightCell.NORMAL_Y[face],
                        (sectionZ << 4) + (cell >> 8) - LightCell.NORMAL_Z[face]);
                found[count++] = SurfaceAtlas.surface(cell, face, material & 0xFF);
            }
        }
        return Arrays.copyOf(found, count);
    }

    /** L'eclairement direct d'une surface, lumiere de fond comprise. */
    private int lit(int x, int y, int z, int face, LightBakeSettings settings) {
        float nx = LightCell.NORMAL_X[face];
        float ny = LightCell.NORMAL_Y[face];
        float nz = LightCell.NORMAL_Z[face];
        float px = x + 0.5f - nx * 0.5f;
        float py = y + 0.5f - ny * 0.5f;
        float pz = z + 0.5f - nz * 0.5f;
        float sum = 0.0f;
        for (int i = 0; i < this.lights; i++) {
            int at = i * AreaLight.VALUES;
            float share = AreaLight.received(this.rects, at, this.lightFaces[i], px, py, pz, nx, ny, nz, settings.reach(), settings.cone());
            if (share >= MIN_SHARE) {
                float cx = (this.rects[at] + this.rects[at + 6]) * 0.5f;
                float cy = (this.rects[at + 1] + this.rects[at + 7]) * 0.5f;
                float cz = (this.rects[at + 2] + this.rects[at + 8]) * 0.5f;
                sum += share * seen(px + nx * SURFACE_OFFSET, py + ny * SURFACE_OFFSET, pz + nz * SURFACE_OFFSET, i, cx, cy, cz);
            }
        }
        return LightCell.pack(settings.ambientRed() + sum * settings.red(), settings.ambientGreen() + sum * settings.green(),
                settings.ambientBlue() + sum * settings.blue());
    }

    /** La part d'une dalle que voit un point : quelques rayons vers des points repartis sur elle, juste devant elle. */
    private float seen(float x, float y, float z, int light, float cx, float cy, float cz) {
        int at = light * AreaLight.VALUES;
        float ux = (this.rects[at + 3] - this.rects[at]) * SAMPLE_SPREAD;
        float uy = (this.rects[at + 4] - this.rects[at + 1]) * SAMPLE_SPREAD;
        float uz = (this.rects[at + 5] - this.rects[at + 2]) * SAMPLE_SPREAD;
        float vx = (this.rects[at + 6] - this.rects[at + 3]) * SAMPLE_SPREAD;
        float vy = (this.rects[at + 7] - this.rects[at + 4]) * SAMPLE_SPREAD;
        float vz = (this.rects[at + 8] - this.rects[at + 5]) * SAMPLE_SPREAD;
        int face = this.lightFaces[light];
        float frontX = cx + LightCell.NORMAL_X[face] * SURFACE_OFFSET;
        float frontY = cy + LightCell.NORMAL_Y[face] * SURFACE_OFFSET;
        float frontZ = cz + LightCell.NORMAL_Z[face] * SURFACE_OFFSET;
        int free = 0;
        for (int i = 0; i < LIGHT_SAMPLES; i++) {
            float su = i < 2 ? -1.0f : 1.0f;
            float sv = (i & 1) == 0 ? -1.0f : 1.0f;
            if (this.ray.clear(this.window, x, y, z, frontX + su * ux + sv * vx, frontY + su * uy + sv * vy, frontZ + su * uz + sv * vz)) {
                free++;
            }
        }
        return free / (float) LIGHT_SAMPLES;
    }

    /**
     * Un rebond : chaque surface recoit sa lumiere directe plus ce que renvoient les surfaces que ses rayons touchent.
     * La fenetre de blocs est celle de la section, posee par {@link #direct} ou {@link #place}.
     *
     * @param blocks copie du monde
     * @param sectionX section
     * @param sectionY section
     * @param sectionZ section
     * @param direct lumiere directe de la section
     * @param sources eclairement du tour precedent des vingt-sept sections autour, {@code null} sans donnees
     * @param occlusion ce qu'une surface perd quand tous ses rayons touchent un bloc tout proche, de 0 a 1
     * @param share part de la lumiere que renvoient les surfaces
     * @param out section cuite
     */
    void bounce(LightBlocks blocks, int sectionX, int sectionY, int sectionZ, int[] direct, int[][] sources, float occlusion, float share, int[] out) {
        float[] albedos = blocks.albedos();
        int baseX = sectionX << 4;
        int baseY = sectionY << 4;
        int baseZ = sectionZ << 4;
        for (int i = 0; i < LightCell.VALUES; i++) {
            int value = direct[i];
            if (!LightCell.valid(value)) {
                out[i] = 0;
                continue;
            }
            int face = i / LightCell.SECTION_CELLS;
            int cell = i & LightCell.SECTION_CELLS - 1;
            gather(baseX + (cell & MASK), baseY + (cell >> 4 & MASK), baseZ + (cell >> 8), face, albedos, sources, occlusion, sectionX, sectionY, sectionZ);
            float seen = this.seenShare;
            float red = this.bouncedRed * share * this.colorRed;
            float green = this.bouncedGreen * share * this.colorGreen;
            float blue = this.bouncedBlue * share * this.colorBlue;
            if (this.firstBounce != null) {
                int first = this.firstBounce[i];
                red = following(red, LightCell.red(first) - LightCell.red(value));
                green = following(green, LightCell.green(first) - LightCell.green(value));
                blue = following(blue, LightCell.blue(first) - LightCell.blue(value));
            }
            out[i] = LightCell.pack((LightCell.red(value) + red) * seen, (LightCell.green(value) + green) * seen, (LightCell.blue(value) + blue) * seen);
            if (this.indirect != null) {
                this.indirect[i] = LightCell.pack((this.ambientRed + red) * seen, (this.ambientGreen + green) * seen, (this.ambientBlue + blue) * seen);
            }
        }
        this.indirect = null;
        this.firstBounce = null;
    }

    /** Ce que rapporte un rebond qui en compte deux, quand le premier seul rapportait {@code first} : les suivants a part du second. */
    private float following(float bounced, float first) {
        float kept = Math.max(first, 0.0f);
        return kept + Math.max(bounced - kept, 0.0f) * this.followingShare;
    }

    /**
     * Le prochain rebond est le dernier : il estime aussi tous ceux qui le suivent, d'apres l'eclairement du tour precedent, qui
     * n'avait qu'un rebond.
     *
     * @param previous eclairement de la section au tour precedent, lumiere directe et premier rebond
     * @param following ce que valent tous les rebonds a partir du second, en parts du second
     */
    void keepFollowing(int[] previous, float following) {
        this.firstBounce = previous;
        this.followingShare = following;
    }

    /**
     * Le prochain rebond ecrit aussi, a part, tout ce qui n'est pas la lumiere directe : le fond et les rebonds, que le
     * cache de surfaces complete avec sa propre lumiere directe.
     *
     * @param out section a remplir
     * @param settings reglages de la cuisson
     */
    void keepIndirect(int[] out, LightBakeSettings settings) {
        this.indirect = out;
        this.ambientRed = settings.ambientRed();
        this.ambientGreen = settings.ambientGreen();
        this.ambientBlue = settings.ambientBlue();
    }

    /** Ce que les rebonds suivants ajoutent a la couleur des blocs. */
    void bounceColor(LightBakeSettings settings) {
        this.colorRed = settings.bounceRed();
        this.colorGreen = settings.bounceGreen();
        this.colorBlue = settings.bounceBlue();
    }

    /** Pose la fenetre de blocs d'une section, sans lumiere directe. */
    void place(LightBlocks blocks, int sectionX, int sectionY, int sectionZ) {
        this.window.fill(blocks, sectionX, sectionY, sectionZ);
    }

    /** Ce que les rayons d'une surface ramenent, et la part d'elle que cachent des blocs proches : dans les champs. */
    private void gather(int x, int y, int z, int face, float[] albedos, int[][] sources, float occlusion, int sectionX, int sectionY, int sectionZ) {
        float[] directions = BOUNCE_DIRECTIONS[face];
        float nx = LightCell.NORMAL_X[face];
        float ny = LightCell.NORMAL_Y[face];
        float nz = LightCell.NORMAL_Z[face];
        float px = x + 0.5f - nx * (0.5f - SURFACE_OFFSET);
        float py = y + 0.5f - ny * (0.5f - SURFACE_OFFSET);
        float pz = z + 0.5f - nz * (0.5f - SURFACE_OFFSET);
        float red = 0.0f;
        float green = 0.0f;
        float blue = 0.0f;
        int near = 0;
        for (int i = 0; i < BOUNCE_RAYS; i++) {
            if (!this.ray.march(this.window, px, py, pz, directions[i * 3], directions[i * 3 + 1], directions[i * 3 + 2], BOUNCE_LENGTH)) {
                continue;
            }
            if (this.ray.distance() < NEAR) {
                near++;
            }
            int[] source = sources[neighbor(this.ray.cellX() >> 4, this.ray.cellY() >> 4, this.ray.cellZ() >> 4, sectionX, sectionY, sectionZ)];
            int value = source == null ? 0
                    : source[LightCell.index(this.ray.cellX() & MASK, this.ray.cellY() & MASK, this.ray.cellZ() & MASK, this.ray.face())];
            if (LightCell.valid(value)) {
                int material = (this.ray.block() & 0xFF) * 3;
                red += LightCell.red(value) * albedos[material];
                green += LightCell.green(value) * albedos[material + 1];
                blue += LightCell.blue(value) * albedos[material + 2];
            }
        }
        this.bouncedRed = red / BOUNCE_RAYS;
        this.bouncedGreen = green / BOUNCE_RAYS;
        this.bouncedBlue = blue / BOUNCE_RAYS;
        this.seenShare = 1.0f - occlusion * near / BOUNCE_RAYS;
    }

    /** Indice d'une section parmi les vingt-sept autour d'une autre, centre a {@code 13}. */
    static int neighbor(int sectionX, int sectionY, int sectionZ, int centerX, int centerY, int centerZ) {
        return (sectionZ - centerZ + 1) * 9 + (sectionY - centerY + 1) * 3 + sectionX - centerX + 1;
    }

    /** Clef d'une section voisine, d'apres son indice parmi les {@value #NEIGHBORS}. */
    static long neighborKey(int index, int centerX, int centerY, int centerZ) {
        return ChunkSectionPos.asLong(centerX - 1 + index % 3, centerY - 1 + index / 3 % 3, centerZ - 1 + index / 9);
    }

    /** Sections voisines d'une section, elle comprise. */
    static int neighbors() {
        return NEIGHBORS;
    }
}
