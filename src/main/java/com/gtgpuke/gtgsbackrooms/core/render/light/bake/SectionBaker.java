package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.math.ChunkSectionPos;

/**
 * La cuisson d'une section, sur le thread de cuisson. D'abord la lumiere directe de chaque surface : le facteur de forme
 * de chaque dalle a portee ({@link AreaLight}), multiplie par la part de la dalle que la surface voit (quelques rayons
 * vers des points de la dalle, d'ou des ombres douces). Puis le rebond : des rayons repartis sur la demi-sphere de la surface
 * vont chercher l'eclairement des surfaces qu'ils touchent, teinte par leur albedo, d'ou le mur jaune qui colore ce qui lui
 * fait face. Le dernier rebond assombrit aussi ce que des blocs tout proches cachent, d'ou les coins sombres. Seules les faces
 * d'une cellule d'air posees contre un bloc opaque portent une valeur.
 *
 * <p>Les portails ({@link PortalOpenings}) : leur cadre arrete les rayons de son monde qui le traversent. Celui d'un passage les
 * laisse continuer de l'autre cote, comme si les deux endroits se touchaient : un rayon de rebond qui entre par sa face repart de
 * l'arrivee, dans les blocs de la zone de la-bas, et en ramene l'eclairement cuit (l'etat qu'aurait lu une voisine) ; les neons de
 * l'autre cote, reportes de ce cote, eclairent par l'ouverture, et les blocs des deux cotes font de l'ombre a leur faisceau. Les
 * blocs de l'autre cote se lisent dans une fenetre a part, posee autour de l'arrivee, quelques passages par section.
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
    /** Passages vers une zone suivis par section, au plus : chacun a sa fenetre de blocs de l'autre cote. */
    static final int CROSSINGS = 4;
    /** Ecart au plan d'arrivee d'ou repart un rayon qui passe un cadre, pour partir de la bonne cellule. */
    private static final float BEYOND = 1.0e-3f;
    /** Ce que donne un rayon qui passe un cadre : rien, l'eclairement de l'autre cote, ou rien de connu la-bas (il continue de ce cote). */
    private static final int CROSS_MISS = 0;
    private static final int CROSS_HIT = 1;
    private static final int CROSS_UNKNOWN = 2;
    /** Ecart entre deux passages d'un segment sous lequel deux cadres sont dans le meme plan, en parts du segment. */
    private static final float SAME_PLANE = 1.0e-4f;
    /**
     * Directions de la lumiere qui sort d'un passage, mesuree pour les rebonds traces : autant que leurs rayons, reparties comme eux
     * ({@code bounce.fsh}), et un rayon d'une surface face au cadre retrouve exactement la sienne.
     */
    static final int PASSAGE_DIRECTIONS = 64;
    /**
     * Points du cadre par cote d'ou part chaque direction : le milieu de chaque case d'une grille reguliere, ici de chaque quart. Un rayon
     * lit les quatre qui entourent son entree. Au banc de la couture, quatre par cote rapprochent du bord les rayons rasants et ameliorent
     * le fond vu par le cadre, mais leur font lire les cellules que coupe le plan d'arrivee, d'un bloc entier, et eclaircissent le sol
     * colle au plan : deux, ou aucune vue n'est moins bonne qu'avant la mesure par point.
     */
    static final int PASSAGE_GRID = 2;
    /** Points du cadre d'ou part chaque direction. */
    static final int PASSAGE_POINTS = PASSAGE_GRID * PASSAGE_GRID;
    /** Valeurs par direction et par point : la lumiere directe, puis elle et un rebond, en rouge, vert et bleu. */
    static final int PASSAGE_SAMPLE = 6;
    /** Longueur de la marche de la mesure, en blocs : celle des rayons traces ({@code RAY_LENGTH} de {@code bounce.fsh}). */
    static final float TRACED_LENGTH = 24.0f;
    /** Sections par cote de la fenetre de la mesure : la marche de {@link #TRACED_LENGTH} blocs y reste depuis toute arrivee. */
    private static final int PASSAGE_WINDOW = 5;
    /** Ecart entre deux directions successives, autour de l'axe du cadre : l'angle d'or, comme les rayons des shaders. */
    private static final double GOLDEN_ANGLE = 2.3999632;
    private static final int SECTION = 16;
    private static final int MASK = SECTION - 1;
    private static final int NEIGHBORS = 27;
    private static final int INITIAL_LIGHTS = 64;
    private static final int INITIAL_WALLS = 8;
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
    /** Couleur de chaque neon vu par un portail parmi les lumieres, a la suite des neons. */
    private float[] lightColors = new float[INITIAL_LIGHTS * 3];
    private int lights;
    /** Les neons sont les premieres lumieres, les neons vus par les portails les suivantes. */
    private int neons;
    /** Le cadre par lequel eclaire chaque neon vu par un portail, rang dans la liste des portails. */
    private int[] lightPassages = new int[INITIAL_LIGHTS];
    /** Les cadres a portee de la section, rangs dans la liste des portails : ils arretent les rayons qui les traversent. */
    private int[] walls = new int[INITIAL_WALLS];
    private int wallCount;
    private final float[] rayFrom = new float[3];
    private final float[] rayTo = new float[3];
    private final float[] point = new float[3];
    private PortalOpenings openings = PortalOpenings.NONE;
    private final float[] boxMin = new float[3];
    private final float[] boxMax = new float[3];
    /** Les blocs de l'autre cote des passages a portee de la section, et le rang de chacun dans la liste des portails. */
    private final LightWindow[] crossWindows = windows();
    private final int[] crossFrames = new int[CROSSINGS];
    private int crossCount;
    private final BlockRay crossRay = new BlockRay();
    /** Les blocs autour de l'arrivee d'un passage mesure : assez pour toute la marche de la mesure. */
    private final LightWindow passageWindow = new LightWindow(PASSAGE_WINDOW);
    private final float[] crossPoint = new float[3];
    private final float[] crossDirection = new float[3];
    /** L'etat de l'autre cote que lisent les rayons de rebond qui passent un cadre ; 0, ils n'y lisent rien. */
    private int crossStage;
    /** La part du dernier segment ou il passe le cadre le plus proche. */
    private float crossT;
    /** Ce que ramene le dernier rayon passe de l'autre cote, et s'il y touche un bloc tout proche. */
    private float crossRed;
    private float crossGreen;
    private float crossBlue;
    private boolean crossNear;
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

    private static LightWindow[] windows() {
        LightWindow[] windows = new LightWindow[CROSSINGS];
        for (int i = 0; i < CROSSINGS; i++) {
            windows[i] = new LightWindow();
        }
        return windows;
    }

    /** Suite a faible discrepance : les bits de l'indice en miroir derriere la virgule. */
    private static double vanDerCorput(int index) {
        return (Integer.reverse(index) & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** Les portails du monde de la zone, pour les prochaines cuissons. */
    void openings(PortalOpenings next) {
        this.openings = next;
    }

    /**
     * L'etat de l'autre cote que liront les prochains rebonds : celui qu'ils lisent chez leurs voisines (la lumiere directe, ou elle et
     * le premier rebond), 0 pour n'y rien lire.
     */
    void crossStage(int stage) {
        this.crossStage = stage;
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
        collectOpenings(sectionX, sectionY, sectionZ, settings.reach());
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

    /** Les portails a portee de la section : les neons vus par eux a la suite des neons, et les cadres a part. */
    private void collectOpenings(int sectionX, int sectionY, int sectionZ, float reach) {
        this.neons = this.lights;
        collectWalls(sectionX, sectionY, sectionZ, reach);
        addVirtual(this.openings, reach);
    }

    /** Les cadres a cette distance de la section : seuls ceux-la coupent un rayon qui part d'elle. */
    private void collectWalls(int sectionX, int sectionY, int sectionZ, float reach) {
        this.wallCount = 0;
        PortalOpenings current = this.openings;
        this.boxMin[0] = sectionX << 4;
        this.boxMin[1] = sectionY << 4;
        this.boxMin[2] = sectionZ << 4;
        for (int axis = 0; axis < 3; axis++) {
            this.boxMax[axis] = this.boxMin[axis] + SECTION;
        }
        this.crossCount = 0;
        for (int i = 0; i < current.count(); i++) {
            if (current.isFrame(i) && current.near(i, this.boxMin, this.boxMax, reach)) {
                if (this.wallCount == this.walls.length) {
                    this.walls = Arrays.copyOf(this.walls, this.walls.length * 2);
                }
                this.walls[this.wallCount++] = i;
                LightZone beyond = current.zone(i);
                if (beyond != null && this.crossCount < CROSSINGS) {
                    // Les blocs autour de l'arrivee : les vingt-sept sections autour de la sienne.
                    for (int axis = 0; axis < 3; axis++) {
                        this.crossPoint[axis] = current.center(i, axis);
                    }
                    current.toArrival(i, this.crossPoint);
                    this.crossWindows[this.crossCount].fill(beyond.blocks(), ChunkSectionPos.getSectionCoord(this.crossPoint[0]),
                            ChunkSectionPos.getSectionCoord(this.crossPoint[1]), ChunkSectionPos.getSectionCoord(this.crossPoint[2]));
                    this.crossFrames[this.crossCount++] = i;
                }
            }
        }
    }

    /** La fenetre des blocs de l'autre cote d'un passage, -1 s'il n'en a pas. */
    private int crossSlot(int frame) {
        for (int slot = 0; slot < this.crossCount; slot++) {
            if (this.crossFrames[slot] == frame) {
                return slot;
            }
        }
        return -1;
    }

    private void addVirtual(PortalOpenings current, float reach) {
        for (int i = 0; i < current.count(); i++) {
            if (current.kind(i) == PortalOpenings.VIRTUAL && current.near(i, this.boxMin, this.boxMax, reach)) {
                grow();
                current.corners(i, this.rects, this.lights * AreaLight.VALUES);
                for (int channel = 0; channel < 3; channel++) {
                    this.lightColors[this.lights * 3 + channel] = current.color(i, channel);
                }
                this.lightPassages[this.lights] = current.passage(i);
                this.lightFaces[this.lights++] = current.faceOf(i);
            }
        }
    }

    /**
     * Le point d'une face de cellule, dans {@link #point} ; sur le plan d'un cadre (la face que coupe un plan au milieu d'un bloc), il passe
     * d'un cheveu du cote ou le cadre s'ouvre : cuit sur le plan meme, il ne saurait de quel cote il est, et ne verrait pas le portail,
     * alors que le terrain lit cette cellule du cote du cadre, jusqu'au plan.
     */
    private void offPlane(float x, float y, float z) {
        this.point[0] = x;
        this.point[1] = y;
        this.point[2] = z;
        for (int i = 0; i < this.wallCount; i++) {
            int frame = this.walls[i];
            if (this.openings.onPlane(frame, x, y, z)) {
                int face = this.openings.faceOf(frame);
                this.point[0] += LightCell.NORMAL_X[face] * SURFACE_OFFSET;
                this.point[1] += LightCell.NORMAL_Y[face] * SURFACE_OFFSET;
                this.point[2] += LightCell.NORMAL_Z[face] * SURFACE_OFFSET;
                return;
            }
        }
    }

    /** {@code true} si un cadre a portee coupe le segment : la lumiere qui le suit part de l'autre cote du portail. */
    private boolean walled(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (this.wallCount == 0) {
            return false;
        }
        this.rayFrom[0] = x0;
        this.rayFrom[1] = y0;
        this.rayFrom[2] = z0;
        this.rayTo[0] = x1;
        this.rayTo[1] = y1;
        this.rayTo[2] = z1;
        for (int i = 0; i < this.wallCount; i++) {
            if (this.openings.crossing(this.walls[i], this.rayFrom, this.rayTo) >= 0.0f) {
                return true;
            }
        }
        return false;
    }

    /**
     * Le cadre a portee que le segment passe en premier, -1 s'il n'en passe aucun ; la part du segment ou il le passe, dans {@link #crossT}.
     * Les deux faces d'un portail double sont dans le meme plan : a egalite, celle par laquelle le segment entre l'emporte, l'autre le
     * prenant de dos.
     */
    private int nearestFrame(float x0, float y0, float z0, float x1, float y1, float z1) {
        this.rayFrom[0] = x0;
        this.rayFrom[1] = y0;
        this.rayFrom[2] = z0;
        this.rayTo[0] = x1;
        this.rayTo[1] = y1;
        this.rayTo[2] = z1;
        int nearest = -1;
        this.crossT = 2.0f;
        for (int i = 0; i < this.wallCount; i++) {
            int frame = this.walls[i];
            float t = this.openings.crossing(frame, this.rayFrom, this.rayTo);
            boolean tie = nearest >= 0 && Math.abs(t - this.crossT) < SAME_PLANE;
            if (t >= 0.0f && (tie ? entering(frame, x1 - x0, y1 - y0, z1 - z0) : t < this.crossT)) {
                this.crossT = Math.min(t, this.crossT);
                nearest = frame;
            }
        }
        return nearest;
    }

    /**
     * Fait passer un point et une direction de l'autre cote d'un passage, dans {@link #crossPoint} et {@link #crossDirection} : le
     * point juste au-dela du plan d'arrivee.
     */
    private void cross(int frame, float x, float y, float z, float dx, float dy, float dz) {
        this.crossPoint[0] = x;
        this.crossPoint[1] = y;
        this.crossPoint[2] = z;
        this.openings.toArrival(frame, this.crossPoint);
        this.crossDirection[0] = dx;
        this.crossDirection[1] = dy;
        this.crossDirection[2] = dz;
        this.openings.turn(frame, this.crossDirection);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        for (int axis = 0; axis < 3; axis++) {
            this.crossPoint[axis] += this.crossDirection[axis] / length * BEYOND;
        }
    }

    /** {@code true} si la direction entre dans le cadre par sa face : du cote d'ou l'on passe. */
    private boolean entering(int frame, float dx, float dy, float dz) {
        int face = this.openings.faceOf(frame);
        return dx * LightCell.NORMAL_X[face] + dy * LightCell.NORMAL_Y[face] + dz * LightCell.NORMAL_Z[face] < 0.0f;
    }

    private void grow() {
        if (this.lights == this.lightFaces.length) {
            this.rects = Arrays.copyOf(this.rects, this.rects.length * 2);
            this.lightFaces = Arrays.copyOf(this.lightFaces, this.lightFaces.length * 2);
            this.lightBlocks = Arrays.copyOf(this.lightBlocks, this.lightBlocks.length * 2);
            this.lightColors = Arrays.copyOf(this.lightColors, this.lightColors.length * 2);
            this.lightPassages = Arrays.copyOf(this.lightPassages, this.lightPassages.length * 2);
        }
    }

    private void addLight(int x, int y, int z, float size) {
        if (this.window.block(x + LightCell.NORMAL_X[EMITTING_FACE], y + LightCell.NORMAL_Y[EMITTING_FACE], z + LightCell.NORMAL_Z[EMITTING_FACE]) != LightBlocks.AIR) {
            return;
        }
        grow();
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
        long[] order = new long[this.neons];
        for (int i = 0; i < this.neons; i++) {
            int dx = this.lightBlocks[i * 3] - centerX;
            int dy = this.lightBlocks[i * 3 + 1] - centerY;
            int dz = this.lightBlocks[i * 3 + 2] - centerZ;
            order[i] = (long) (dx * dx + dy * dy + dz * dz) << 32 | i;
        }
        Arrays.sort(order);
        int kept = Math.min(this.neons, MAX_NEAR_LIGHTS);
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
        offPlane(x + 0.5f - nx * 0.5f, y + 0.5f - ny * 0.5f, z + 0.5f - nz * 0.5f);
        float px = this.point[0];
        float py = this.point[1];
        float pz = this.point[2];
        float sum = 0.0f;
        for (int i = 0; i < this.neons; i++) {
            int at = i * AreaLight.VALUES;
            float share = AreaLight.received(this.rects, at, this.lightFaces[i], px, py, pz, nx, ny, nz, settings.reach(), settings.cone());
            if (share >= MIN_SHARE) {
                float cx = (this.rects[at] + this.rects[at + 6]) * 0.5f;
                float cy = (this.rects[at + 1] + this.rects[at + 7]) * 0.5f;
                float cz = (this.rects[at + 2] + this.rects[at + 8]) * 0.5f;
                sum += share * seen(px + nx * SURFACE_OFFSET, py + ny * SURFACE_OFFSET, pz + nz * SURFACE_OFFSET, i, cx, cy, cz);
            }
        }
        if (this.lights == this.neons) {
            return LightCell.pack(settings.ambientRed() + sum * settings.red(), settings.ambientGreen() + sum * settings.green(),
                    settings.ambientBlue() + sum * settings.blue());
        }
        // Un neon de l'autre cote eclaire comme un neon, du meme calcul, reporte derriere le cadre : par l'ouverture seulement.
        float beamRed = 0.0f;
        float beamGreen = 0.0f;
        float beamBlue = 0.0f;
        for (int i = this.neons; i < this.lights; i++) {
            int at = i * AreaLight.VALUES;
            float cx = (this.rects[at] + this.rects[at + 6]) * 0.5f;
            float cy = (this.rects[at + 1] + this.rects[at + 7]) * 0.5f;
            float cz = (this.rects[at + 2] + this.rects[at + 8]) * 0.5f;
            float share = AreaLight.received(this.rects, at, this.lightFaces[i], px, py, pz, nx, ny, nz, settings.reach(), settings.cone());
            if (share >= MIN_SHARE) {
                float received = share * passing(px + nx * SURFACE_OFFSET, py + ny * SURFACE_OFFSET, pz + nz * SURFACE_OFFSET, i, cx, cy, cz);
                beamRed += received * this.lightColors[i * 3];
                beamGreen += received * this.lightColors[i * 3 + 1];
                beamBlue += received * this.lightColors[i * 3 + 2];
            }
        }
        return LightCell.pack(settings.ambientRed() + sum * settings.red() + beamRed, settings.ambientGreen() + sum * settings.green() + beamGreen,
                settings.ambientBlue() + sum * settings.blue() + beamBlue);
    }

    /**
     * La part d'un neon vu par un portail que voit un point : des rayons vers des points repartis sur lui, qui comptent s'ils passent
     * par l'ouverture et que rien ne les arrete de ce cote, jusqu'au cadre, puis de l'autre, jusqu'au neon, quand la zone de la-bas a
     * ses blocs.
     */
    private float passing(float x, float y, float z, int light, float cx, float cy, float cz) {
        int at = light * AreaLight.VALUES;
        float ux = (this.rects[at + 3] - this.rects[at]) * SAMPLE_SPREAD;
        float uy = (this.rects[at + 4] - this.rects[at + 1]) * SAMPLE_SPREAD;
        float uz = (this.rects[at + 5] - this.rects[at + 2]) * SAMPLE_SPREAD;
        float vx = (this.rects[at + 6] - this.rects[at + 3]) * SAMPLE_SPREAD;
        float vy = (this.rects[at + 7] - this.rects[at + 4]) * SAMPLE_SPREAD;
        float vz = (this.rects[at + 8] - this.rects[at + 5]) * SAMPLE_SPREAD;
        int passage = this.lightPassages[light];
        int free = 0;
        for (int i = 0; i < LIGHT_SAMPLES; i++) {
            float su = i < 2 ? -1.0f : 1.0f;
            float sv = (i & 1) == 0 ? -1.0f : 1.0f;
            this.rayFrom[0] = x;
            this.rayFrom[1] = y;
            this.rayFrom[2] = z;
            this.rayTo[0] = cx + su * ux + sv * vx;
            this.rayTo[1] = cy + su * uy + sv * vy;
            this.rayTo[2] = cz + su * uz + sv * vz;
            float t = this.openings.crossing(passage, this.rayFrom, this.rayTo);
            if (t < 0.0f) {
                continue;
            }
            float tx = this.rayTo[0];
            float ty = this.rayTo[1];
            float tz = this.rayTo[2];
            float hx = x + (tx - x) * t;
            float hy = y + (ty - y) * t;
            float hz = z + (tz - z) * t;
            if (this.ray.clear(this.window, x, y, z, hx, hy, hz) && !walled(x, y, z, hx, hy, hz) && clearBeyond(passage, light, hx, hy, hz, tx, ty, tz)) {
                free++;
            }
        }
        return free / (float) LIGHT_SAMPLES;
    }

    /** {@code true} si rien n'arrete, de l'autre cote d'un passage, le rayon qui va du cadre au neon ; sans ses blocs, rien. */
    private boolean clearBeyond(int passage, int light, float hx, float hy, float hz, float tx, float ty, float tz) {
        int slot = crossSlot(passage);
        if (slot < 0) {
            return true;
        }
        // Le neon de l'autre cote, juste devant sa face, pour ne pas s'arreter sur lui.
        int face = this.lightFaces[light];
        cross(passage, tx + LightCell.NORMAL_X[face] * SURFACE_OFFSET, ty + LightCell.NORMAL_Y[face] * SURFACE_OFFSET,
                tz + LightCell.NORMAL_Z[face] * SURFACE_OFFSET, tx - hx, ty - hy, tz - hz);
        float neonX = this.crossPoint[0];
        float neonY = this.crossPoint[1];
        float neonZ = this.crossPoint[2];
        cross(passage, hx, hy, hz, tx - hx, ty - hy, tz - hz);
        return !inBlock(this.crossWindows[slot]) && this.crossRay.clear(this.crossWindows[slot], this.crossPoint[0], this.crossPoint[1], this.crossPoint[2], neonX, neonY,
                neonZ);
    }

    /** {@code true} si le point passe de l'autre cote ({@link #crossPoint}) est dans un bloc : une marche n'y regarde pas sa premiere cellule. */
    private boolean inBlock(LightWindow beyond) {
        return beyond.block((int) Math.floor(this.crossPoint[0]), (int) Math.floor(this.crossPoint[1]), (int) Math.floor(this.crossPoint[2])) != LightBlocks.AIR;
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
            float tx = frontX + su * ux + sv * vx;
            float ty = frontY + su * uy + sv * vy;
            float tz = frontZ + su * uz + sv * vz;
            if (this.ray.clear(this.window, x, y, z, tx, ty, tz) && !walled(x, y, z, tx, ty, tz)) {
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
     * Le prochain rebond ecrit aussi, a part, tout ce qui n'est pas la lumiere directe des neons : le fond et les rebonds, ceux qui
     * passent les portails compris, que le cache de surfaces complete avec sa propre lumiere directe.
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

    /** Pose la fenetre de blocs d'une section, sans lumiere directe, et les cadres que ses rayons de rebond peuvent passer. */
    void place(LightBlocks blocks, int sectionX, int sectionY, int sectionZ) {
        this.window.fill(blocks, sectionX, sectionY, sectionZ);
        collectWalls(sectionX, sectionY, sectionZ, BOUNCE_LENGTH);
    }

    /** Ce que les rayons d'une surface ramenent, et la part d'elle que cachent des blocs proches : dans les champs. */
    private void gather(int x, int y, int z, int face, float[] albedos, int[][] sources, float occlusion, int sectionX, int sectionY, int sectionZ) {
        float[] directions = BOUNCE_DIRECTIONS[face];
        float nx = LightCell.NORMAL_X[face];
        float ny = LightCell.NORMAL_Y[face];
        float nz = LightCell.NORMAL_Z[face];
        offPlane(x + 0.5f - nx * (0.5f - SURFACE_OFFSET), y + 0.5f - ny * (0.5f - SURFACE_OFFSET), z + 0.5f - nz * (0.5f - SURFACE_OFFSET));
        float px = this.point[0];
        float py = this.point[1];
        float pz = this.point[2];
        float red = 0.0f;
        float green = 0.0f;
        float blue = 0.0f;
        int near = 0;
        for (int i = 0; i < BOUNCE_RAYS; i++) {
            float dx = directions[i * 3];
            float dy = directions[i * 3 + 1];
            float dz = directions[i * 3 + 2];
            boolean hit = this.ray.march(this.window, px, py, pz, dx, dy, dz, BOUNCE_LENGTH);
            float reached = hit ? this.ray.distance() : BOUNCE_LENGTH;
            int frame = this.wallCount == 0 ? -1 : nearestFrame(px, py, pz, px + dx * reached, py + dy * reached, pz + dz * reached);
            if (frame >= 0) {
                // Un cadre arrete le rayon dans son monde ; celui d'un passage, entre par sa face, le laisse repartir de l'autre cote. Tant
                // que l'autre cote n'est pas cuit la ou il va, le rayon continue de ce cote, comme sans cadre : un mur sombre serait pire.
                int slot = crossSlot(frame);
                boolean passes = this.openings.kind(frame) == PortalOpenings.PASSAGE && entering(frame, dx, dy, dz);
                int crossed = !passes ? CROSS_MISS : slot < 0 || this.crossStage == 0 ? CROSS_UNKNOWN : crossGather(frame, slot, px, py, pz, dx, dy, dz, reached * this.crossT);
                if (crossed == CROSS_HIT) {
                    near += this.crossNear ? 1 : 0;
                    red += this.crossRed;
                    green += this.crossGreen;
                    blue += this.crossBlue;
                }
                if (crossed != CROSS_UNKNOWN) {
                    continue;
                }
            }
            if (!hit) {
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

    /**
     * Un rayon de rebond qui passe un cadre par sa face repart de l'arrivee, dans les blocs de l'autre cote, et ramene l'eclairement de
     * la surface qu'il y touche, teinte par son albedo, dans les champs.
     *
     * @return {@link #CROSS_HIT} s'il touche une surface de la-bas, {@link #CROSS_MISS} s'il n'en touche aucune, {@link #CROSS_UNKNOWN} si
     *     la section qu'il touche n'y est pas encore cuite
     */
    private int crossGather(int frame, int slot, float px, float py, float pz, float dx, float dy, float dz, float travelled) {
        cross(frame, px + dx * travelled, py + dy * travelled, pz + dz * travelled, dx, dy, dz);
        BlockRay beyond = this.crossRay;
        if (inBlock(this.crossWindows[slot])) {
            // L'arrivee est contre un bloc : le rayon s'y arrete aussitot, sans rien en ramener.
            this.crossNear = travelled < NEAR;
            this.crossRed = 0.0f;
            this.crossGreen = 0.0f;
            this.crossBlue = 0.0f;
            return CROSS_HIT;
        }
        if (!beyond.march(this.crossWindows[slot], this.crossPoint[0], this.crossPoint[1], this.crossPoint[2], this.crossDirection[0], this.crossDirection[1],
                this.crossDirection[2], BOUNCE_LENGTH - travelled)) {
            return CROSS_MISS;
        }
        LightZone zone = this.openings.zone(frame);
        int[] state = zone.peek(ChunkSectionPos.asLong(beyond.cellX() >> 4, beyond.cellY() >> 4, beyond.cellZ() >> 4), this.crossStage);
        if (state == null) {
            return CROSS_UNKNOWN;
        }
        int value = state[LightCell.index(beyond.cellX() & MASK, beyond.cellY() & MASK, beyond.cellZ() & MASK, beyond.face())];
        this.crossNear = travelled + beyond.distance() < NEAR;
        float[] albedos = zone.blocks().albedos();
        int material = (beyond.block() & 0xFF) * 3;
        // Une cellule sans surface vaut 0 : ses couleurs aussi, et le rayon n'en ramene rien.
        this.crossRed = LightCell.red(value) * albedos[material];
        this.crossGreen = LightCell.green(value) * albedos[material + 1];
        this.crossBlue = LightCell.blue(value) * albedos[material + 2];
        return CROSS_HIT;
    }

    /**
     * La lumiere qui sort d'un passage, pour les rebonds traces, qui ne marchent que dans les blocs de leur monde : pour chaque direction
     * qui entre dans le cadre par sa face, et pour chaque point d'une grille sur le cadre ({@link #PASSAGE_GRID} par cote), ce que ramene
     * de l'autre cote un rayon parti de ce point, sur la longueur des rayons traces, teint par l'albedo de la surface touchee. Six valeurs
     * par direction et par point : la lumiere directe, puis elle et un premier rebond, en rouge, vert et bleu. Les directions sont
     * reparties selon le cosinus autour de l'axe du cadre, d'un angle d'or a la suivante, comme les rayons des shaders : un rayon reprend
     * la plus proche, entre les points qui entourent son entree. Ce qu'il a deja parcouru avant le cadre ne compte pas : plus une surface
     * est loin du cadre, plus elle le voit petit, et ses rayons qui le passent pesent peu (au banc de la couture, les couper au reste de
     * leur longueur ne change rien au centieme, fond du couloir de 7,5 ou de 15,5 blocs). Comme ce
     * qu'un rayon trace lit sur une surface de son monde (sa tuile), la lumiere mesuree n'a pas la lumiere de fond, que le terrain ajoute
     * a part : avec elle, chaque rayon qui passe un cadre ramenait le fond de la-bas en plus.
     *
     * <p>Ce qu'un corps en recoit, comme d'une fenetre, garde sa definition : la moyenne, sur toutes les directions et tous les points,
     * de ce que renvoie avec un rebond, fond compris, une surface touchee a portee des rebonds de la cuisson ({@link #BOUNCE_LENGTH}).
     *
     * @param frame rang du passage, qui a sa zone de l'autre cote
     * @param out tableau a remplir
     * @param at premiere valeur a ecrire
     * @param window ce qu'un corps recoit, a remplir : rouge, vert et bleu
     * @param windowAt premiere valeur a ecrire dans {@code window}
     */
    void measurePassage(int frame, float[] out, int at, float[] window, int windowAt) {
        PortalOpenings current = this.openings;
        LightZone zone = current.zone(frame);
        LightBakeSettings settings = zone.settings();
        float sampleShare = 1.0f / (PASSAGE_DIRECTIONS * PASSAGE_POINTS);
        for (int axis = 0; axis < 3; axis++) {
            this.crossPoint[axis] = current.center(frame, axis);
        }
        current.toArrival(frame, this.crossPoint);
        LightWindow beyond = this.passageWindow;
        beyond.fill(zone.blocks(), ChunkSectionPos.getSectionCoord(this.crossPoint[0]), ChunkSectionPos.getSectionCoord(this.crossPoint[1]),
                ChunkSectionPos.getSectionCoord(this.crossPoint[2]));
        float[] albedos = zone.blocks().albedos();
        int face = current.faceOf(frame);
        float wx = current.corner(frame, 1, 0) - current.corner(frame, 0, 0);
        float wy = current.corner(frame, 1, 1) - current.corner(frame, 0, 1);
        float wz = current.corner(frame, 1, 2) - current.corner(frame, 0, 2);
        float hx = current.corner(frame, 2, 0) - current.corner(frame, 1, 0);
        float hy = current.corner(frame, 2, 1) - current.corner(frame, 1, 1);
        float hz = current.corner(frame, 2, 2) - current.corner(frame, 1, 2);
        float width = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        float height = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        for (int k = 0; k < PASSAGE_DIRECTIONS; k++) {
            double share = (k + 0.5) / PASSAGE_DIRECTIONS;
            double radius = Math.sqrt(share);
            float along = (float) (Math.cos(k * GOLDEN_ANGLE) * radius);
            float across = (float) (Math.sin(k * GOLDEN_ANGLE) * radius);
            float back = (float) -Math.sqrt(1.0 - share);
            float dx = LightCell.NORMAL_X[face] * back + wx / width * along + hx / height * across;
            float dy = LightCell.NORMAL_Y[face] * back + wy / width * along + hy / height * across;
            float dz = LightCell.NORMAL_Z[face] * back + wz / width * along + hz / height * across;
            for (int point = 0; point < PASSAGE_POINTS; point++) {
                float su = (point / PASSAGE_GRID + 0.5f) / PASSAGE_GRID - 0.5f;
                float sv = (point % PASSAGE_GRID + 0.5f) / PASSAGE_GRID - 0.5f;
                int to = at + (k * PASSAGE_POINTS + point) * PASSAGE_SAMPLE;
                cross(frame, current.center(frame, 0) + wx * su + hx * sv, current.center(frame, 1) + wy * su + hy * sv, current.center(frame, 2) + wz * su + hz * sv,
                        dx, dy, dz);
                if (!inBlock(beyond) && this.crossRay.march(beyond, this.crossPoint[0], this.crossPoint[1], this.crossPoint[2], this.crossDirection[0], this.crossDirection[1],
                        this.crossDirection[2], TRACED_LENGTH)) {
                    long key = ChunkSectionPos.asLong(this.crossRay.cellX() >> 4, this.crossRay.cellY() >> 4, this.crossRay.cellZ() >> 4);
                    int index = LightCell.index(this.crossRay.cellX() & MASK, this.crossRay.cellY() & MASK, this.crossRay.cellZ() & MASK, this.crossRay.face());
                    int material = (this.crossRay.block() & 0xFF) * 3;
                    int[] once = zone.peek(key, 2);
                    store(out, to, zone.peek(key, 1), index, albedos, material, settings);
                    store(out, to + 3, once, index, albedos, material, settings);
                    if (this.crossRay.distance() <= BOUNCE_LENGTH) {
                        int value = once == null ? 0 : once[index];
                        window[windowAt] += LightCell.red(value) * albedos[material] * sampleShare;
                        window[windowAt + 1] += LightCell.green(value) * albedos[material + 1] * sampleShare;
                        window[windowAt + 2] += LightCell.blue(value) * albedos[material + 2] * sampleShare;
                    }
                }
            }
        }
    }

    /**
     * Ce que renvoie une surface touchee, sans la lumiere de fond, teint par son albedo. Une cellule sans surface, ou d'une section pas
     * encore cuite, vaut 0 : ses couleurs aussi.
     */
    private static void store(float[] out, int at, int @Nullable [] state, int index, float[] albedos, int material, LightBakeSettings settings) {
        int value = state == null ? 0 : state[index];
        out[at] = Math.max(LightCell.red(value) - settings.ambientRed(), 0.0f) * albedos[material];
        out[at + 1] = Math.max(LightCell.green(value) - settings.ambientGreen(), 0.0f) * albedos[material + 1];
        out[at + 2] = Math.max(LightCell.blue(value) - settings.ambientBlue(), 0.0f) * albedos[material + 2];
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
