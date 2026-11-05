package com.gtgpuke.gtgsbackrooms.core.render.light.bake;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

/**
 * Les portails du monde d'une zone, pour la lumiere : le plan d'un cadre est a la fois un mur et un passage. Trois sortes
 * d'elements, chacun un rectangle (quatre coins, dans l'ordre de {@link AreaLight#blockFace}), la face de bloc qui donne sa normale
 * et une couleur, dans les unites des reglages de la zone :
 * <ul>
 * <li>un mur ({@link #WALL}) : le cadre d'un portail visible qui ne laisse pas passer la lumiere. La lumiere qui le traverse part de
 * l'autre cote : il arrete les rayons de son monde ;</li>
 * <li>un passage ({@link #PASSAGE}) : le cadre d'un portail qui la laisse passer, vers un monde a lumiere cuite. Il arrete la lumiere
 * de son monde, et un rayon qui le passe par sa face continue de l'autre cote, dans la zone qui y cuit la lumiere (la meme zone pour
 * un autre endroit de son monde) : il y ramene la lumiere cuite de la-bas, comme si les deux endroits se touchaient. Le passage porte
 * la transformation du portail et cette zone ;</li>
 * <li>un neon vu par un portail ({@link #VIRTUAL}) : un neon de l'autre cote, reporte de ce cote par la transformation du portail,
 * derriere son cadre. Il eclaire comme un neon, mais seulement par l'ouverture du cadre (son passage), et les blocs des deux cotes
 * font de l'ombre a ce faisceau.</li>
 * </ul>
 * Le thread de rendu pose une nouvelle liste quand ils changent ; le thread de cuisson lit celle du moment, qui ne change jamais
 * apres sa creation.
 */
public final class PortalOpenings {

    /** Un cadre qui ne laisse pas passer la lumiere. */
    public static final int WALL = 0;
    /** Un cadre qui la laisse passer : mur pour son monde, passage vers l'autre cote. */
    public static final int PASSAGE = 1;
    /** Un neon de l'autre cote, vu par le cadre d'un passage. */
    public static final int VIRTUAL = 2;
    /**
     * Valeurs d'un element : ses quatre coins, sa face, sa couleur, sa sorte, le rang du cadre qui lui sert de passage, puis, pour un
     * passage, la rotation du portail (neuf valeurs, ligne par ligne) et le centre de son arrivee.
     */
    public static final int VALUES = AreaLight.VALUES + 18;
    /** Aucun portail. */
    public static final PortalOpenings NONE = new PortalOpenings(new float[0], new LightZone[0]);
    private static final int FACE = AreaLight.VALUES;
    private static final int RED = FACE + 1;
    private static final int KIND = FACE + 4;
    private static final int THROUGH = FACE + 5;
    private static final int ROTATION = THROUGH + 1;
    private static final int ARRIVAL = ROTATION + 9;
    private static final int CORNERS = 4;
    /** La face d'un neon qui eclaire : celle du bas. */
    private static final int NEON_FACE = 3;
    /** Ecart au plan sous lequel un point est tenu pour dessus : un rayon qui y finit ne le traverse pas. */
    private static final float ON_PLANE = 1.0e-3f;
    /** Marge autour du rectangle d'un cadre, en blocs, ou un point compte encore dedans : un sol ou un mur pose au bord du cadre. */
    public static final float EDGE = 0.05f;

    private final float[] values;
    /** La zone de l'autre cote de chaque passage, {@code null} pour les autres elements ou sans zone la-bas. */
    private final LightZone[] zones;

    private PortalOpenings(float[] values, LightZone[] zones) {
        this.values = values;
        this.zones = zones;
    }

    /**
     * Une liste, copiee des premiers elements de deux tableaux.
     *
     * @param values elements ecrits par {@link #writePassage}, {@link #writeWall} et {@link #writeVirtual}
     * @param zones zone de l'autre cote de chaque element, {@code null} hors des passages
     * @param count nombre d'elements
     */
    public static PortalOpenings of(float[] values, LightZone[] zones, int count) {
        return count == 0 ? NONE : new PortalOpenings(Arrays.copyOf(values, count * VALUES), Arrays.copyOf(zones, count));
    }

    /** Nombre d'elements. */
    public int count() {
        return this.zones.length;
    }

    /** {@code true} si ces elements sont ceux de la liste, zones comprises : rien n'a change. */
    public boolean sameAs(float[] values, LightZone[] zones, int count) {
        if (count != this.zones.length || !Arrays.equals(this.values, 0, this.values.length, values, 0, count * VALUES)) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (this.zones[i] != zones[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code true} si la geometrie de ces elements est celle de la liste, couleurs et zones mises a part : ce que le cache de surfaces,
     * qui ne compte que la lumiere directe des neons, doit suivre.
     */
    public boolean sameShapeAs(PortalOpenings other) {
        if (other.values.length != this.values.length) {
            return false;
        }
        for (int i = 0; i < this.values.length; i++) {
            int offset = i % VALUES;
            if ((offset < RED || offset > RED + 2) && this.values[i] != other.values[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Ecrit un passage dans un tableau : un rectangle centre en un point, de deux demi-axes dans son plan, dont la face regarde le cote
     * d'ou l'on entre, et la transformation qui mene de l'autre cote.
     *
     * @param out tableau a remplir, {@value #VALUES} valeurs par element
     * @param index rang de l'element
     * @param center centre du rectangle : x, y, z
     * @param halfWidth demi-largeur, dans le plan : x, y, z
     * @param halfHeight demi-hauteur, dans le plan : x, y, z
     * @param face face de bloc qui donne la normale du cadre, du cote d'ou l'on entre
     * @param rotation ce que devient un vecteur de l'autre cote, ligne par ligne : neuf valeurs
     * @param arrival centre de l'arrivee : x, y, z
     */
    public static void writePassage(float[] out, int index, double[] center, double[] halfWidth, double[] halfHeight, int face, double[] rotation,
            double[] arrival) {
        frame(out, index, center, halfWidth, halfHeight, face, PASSAGE);
        int at = index * VALUES;
        for (int i = 0; i < 9; i++) {
            out[at + ROTATION + i] = (float) rotation[i];
        }
        for (int axis = 0; axis < 3; axis++) {
            out[at + ARRIVAL + axis] = (float) arrival[axis];
        }
    }

    /** Ecrit un mur, aux memes conventions que {@link #writePassage}, sans transformation. */
    public static void writeWall(float[] out, int index, double[] center, double[] halfWidth, double[] halfHeight, int face) {
        frame(out, index, center, halfWidth, halfHeight, face, WALL);
    }

    private static void frame(float[] out, int index, double[] center, double[] halfWidth, double[] halfHeight, int face, int kind) {
        int at = index * VALUES;
        Arrays.fill(out, at, at + VALUES, 0.0f);
        for (int i = 0; i < CORNERS; i++) {
            double su = i == 0 || i == 3 ? -1.0 : 1.0;
            double sv = i < 2 ? 1.0 : -1.0;
            for (int axis = 0; axis < 3; axis++) {
                out[at + i * 3 + axis] = (float) (center[axis] + su * halfWidth[axis] + sv * halfHeight[axis]);
            }
        }
        out[at + FACE] = face;
        out[at + KIND] = kind;
        out[at + THROUGH] = index;
    }

    /**
     * Ecrit un neon vu par un portail.
     *
     * @param out tableau a remplir
     * @param index rang de l'element
     * @param corners les quatre coins de la face du neon, reportee de ce cote, dans l'ordre de {@link AreaLight#blockFace}
     * @param face face de bloc par laquelle il eclaire, reportee de ce cote
     * @param color couleur
     * @param passage rang de l'ouverture par laquelle il eclaire
     */
    public static void writeVirtual(float[] out, int index, float[] corners, int face, float[] color, int passage) {
        int at = index * VALUES;
        Arrays.fill(out, at, at + VALUES, 0.0f);
        System.arraycopy(corners, 0, out, at, AreaLight.VALUES);
        out[at + FACE] = face;
        for (int channel = 0; channel < 3; channel++) {
            out[at + RED + channel] = color[channel];
        }
        out[at + KIND] = VIRTUAL;
        out[at + THROUGH] = passage;
    }

    /**
     * Les quatre coins de la face d'un neon qui eclaire, celle du bas, comme la cuisson la prend : a reporter de l'autre cote d'un portail.
     *
     * @param out les douze coordonnees des coins
     * @param x bloc du neon
     * @param y bloc du neon
     * @param z bloc du neon
     * @param size cote du rectangle qui eclaire, en blocs
     */
    public static void neonFace(float[] out, int x, int y, int z, float size) {
        AreaLight.blockFace(out, 0, x, y, z, NEON_FACE, size);
    }

    /** La face de bloc de cette normale, alignee sur un axe ; {@code -1} pour une autre. */
    public static int face(double normalX, double normalY, double normalZ) {
        for (int face = 0; face < LightCell.FACES; face++) {
            if (LightCell.NORMAL_X[face] == normalX && LightCell.NORMAL_Y[face] == normalY && LightCell.NORMAL_Z[face] == normalZ) {
                return face;
            }
        }
        return -1;
    }

    /** Les valeurs d'un element, dans l'ordre ou elles sont ecrites (tests). */
    float value(int index, int offset) {
        return this.values[index * VALUES + offset];
    }

    /** La sorte d'un element : {@link #WALL}, {@link #PASSAGE} ou {@link #VIRTUAL}. */
    public int kind(int index) {
        return (int) this.values[index * VALUES + KIND];
    }

    /** Le rang du passage par lequel eclaire un neon vu par un portail ; le sien pour un cadre. */
    public int passage(int index) {
        return (int) this.values[index * VALUES + THROUGH];
    }

    /** {@code true} si l'element est un cadre : un mur, ou un passage. */
    public boolean isFrame(int index) {
        return kind(index) != VIRTUAL;
    }

    /**
     * {@code true} si ce point est sur le plan d'un cadre, a {@value #ON_PLANE} pres, et dans son rectangle, a {@link #EDGE} pres : le point
     * d'une face de cellule que le plan coupe. Cuit tel quel, il ne saurait de quel cote du cadre il est.
     */
    public boolean onPlane(int index, float x, float y, float z) {
        int at = index * VALUES;
        int face = (int) this.values[at + FACE];
        float cx = (this.values[at] + this.values[at + 6]) * 0.5f;
        float cy = (this.values[at + 1] + this.values[at + 7]) * 0.5f;
        float cz = (this.values[at + 2] + this.values[at + 8]) * 0.5f;
        float dx = x - cx;
        float dy = y - cy;
        float dz = z - cz;
        if (Math.abs(dx * LightCell.NORMAL_X[face] + dy * LightCell.NORMAL_Y[face] + dz * LightCell.NORMAL_Z[face]) > ON_PLANE) {
            return false;
        }
        float wx = this.values[at + 3] - this.values[at];
        float wy = this.values[at + 4] - this.values[at + 1];
        float wz = this.values[at + 5] - this.values[at + 2];
        float hx = this.values[at + 6] - this.values[at + 3];
        float hy = this.values[at + 7] - this.values[at + 4];
        float hz = this.values[at + 8] - this.values[at + 5];
        float w2 = wx * wx + wy * wy + wz * wz;
        float h2 = hx * hx + hy * hy + hz * hz;
        return Math.abs(dx * wx + dy * wy + dz * wz) <= w2 * 0.5f + EDGE * (float) Math.sqrt(w2)
                && Math.abs(dx * hx + dy * hy + dz * hz) <= h2 * 0.5f + EDGE * (float) Math.sqrt(h2);
    }

    /** Copie les coins d'un element dans des rectangles, au format de {@link AreaLight}. */
    void corners(int index, float[] rects, int at) {
        System.arraycopy(this.values, index * VALUES, rects, at, AreaLight.VALUES);
    }

    /** La face par laquelle eclaire un element ; pour un cadre, celle qui donne sa normale, du cote ou il s'ouvre. */
    int faceOf(int index) {
        return (int) this.values[index * VALUES + FACE];
    }

    /** La face d'un element, en flottant pour les shaders. */
    public float face(int index) {
        return this.values[index * VALUES + FACE];
    }

    /** Une composante de la couleur d'un element : 0 rouge, 1 vert, 2 bleu. */
    public float color(int index, int channel) {
        return this.values[index * VALUES + RED + channel];
    }

    /** Un coin d'un element, sur un axe : 0 a 3 dans l'ordre de {@link AreaLight#blockFace}. */
    public float corner(int index, int corner, int axis) {
        return this.values[index * VALUES + corner * 3 + axis];
    }

    /**
     * {@code true} si un element est a moins de cette distance d'une boite : la distance entre la boite et celle de ses coins.
     *
     * @param min coin bas de la boite : x, y, z
     * @param max coin haut de la boite : x, y, z
     * @param reach distance, en blocs
     */
    boolean near(int index, float[] min, float[] max, float reach) {
        int at = index * VALUES;
        float distance2 = 0.0f;
        for (int axis = 0; axis < 3; axis++) {
            float low = Math.min(Math.min(this.values[at + axis], this.values[at + 3 + axis]), Math.min(this.values[at + 6 + axis], this.values[at + 9 + axis]));
            float high = Math.max(Math.max(this.values[at + axis], this.values[at + 3 + axis]), Math.max(this.values[at + 6 + axis], this.values[at + 9 + axis]));
            float gap = Math.max(Math.max(min[axis] - high, low - max[axis]), 0.0f);
            distance2 += gap * gap;
        }
        return distance2 < reach * reach;
    }

    /** Le centre d'un element, sur un axe : le milieu de deux coins opposes. */
    public float center(int index, int axis) {
        int at = index * VALUES;
        return (this.values[at + axis] + this.values[at + 6 + axis]) * 0.5f;
    }

    /** La zone de l'autre cote d'un passage, {@code null} sans zone la-bas ou pour un autre element. */
    public @Nullable LightZone zone(int index) {
        return this.zones[index];
    }

    /** Fait passer un point de l'autre cote d'un passage, en place : x, y, z. */
    void toArrival(int index, float[] point) {
        int at = index * VALUES;
        float x = point[0] - (this.values[at] + this.values[at + 6]) * 0.5f;
        float y = point[1] - (this.values[at + 1] + this.values[at + 7]) * 0.5f;
        float z = point[2] - (this.values[at + 2] + this.values[at + 8]) * 0.5f;
        for (int axis = 0; axis < 3; axis++) {
            int row = at + ROTATION + axis * 3;
            point[axis] = this.values[at + ARRIVAL + axis] + this.values[row] * x + this.values[row + 1] * y + this.values[row + 2] * z;
        }
    }

    /** Fait passer une direction de l'autre cote d'un passage, en place : x, y, z. */
    void turn(int index, float[] vector) {
        int at = index * VALUES;
        float x = vector[0];
        float y = vector[1];
        float z = vector[2];
        for (int axis = 0; axis < 3; axis++) {
            int row = at + ROTATION + axis * 3;
            vector[axis] = this.values[row] * x + this.values[row + 1] * y + this.values[row + 2] * z;
        }
    }

    /**
     * Ou un segment passe le plan d'un cadre, s'il le passe dans le cadre : un rayon qui le traverse part de l'autre cote. Un bout du
     * segment pose sur le plan ne le traverse pas.
     *
     * @param index rang du cadre
     * @param from depart du segment : x, y, z
     * @param to arrivee du segment : x, y, z
     * @return la part du segment ou il passe le cadre, de 0 a 1, ou {@code -1} s'il ne le passe pas
     */
    float crossing(int index, float[] from, float[] to) {
        int at = index * VALUES;
        int face = (int) this.values[at + FACE];
        float nx = LightCell.NORMAL_X[face];
        float ny = LightCell.NORMAL_Y[face];
        float nz = LightCell.NORMAL_Z[face];
        float cx = (this.values[at] + this.values[at + 6]) * 0.5f;
        float cy = (this.values[at + 1] + this.values[at + 7]) * 0.5f;
        float cz = (this.values[at + 2] + this.values[at + 8]) * 0.5f;
        float start = (from[0] - cx) * nx + (from[1] - cy) * ny + (from[2] - cz) * nz;
        float end = (to[0] - cx) * nx + (to[1] - cy) * ny + (to[2] - cz) * nz;
        if (start > -ON_PLANE && end > -ON_PLANE || start < ON_PLANE && end < ON_PLANE) {
            return -1.0f;
        }
        float t = start / (start - end);
        // Les deux axes du cadre : du premier coin au deuxieme, du deuxieme au troisieme.
        float wx = this.values[at + 3] - this.values[at];
        float wy = this.values[at + 4] - this.values[at + 1];
        float wz = this.values[at + 5] - this.values[at + 2];
        float hx = this.values[at + 6] - this.values[at + 3];
        float hy = this.values[at + 7] - this.values[at + 4];
        float hz = this.values[at + 8] - this.values[at + 5];
        float dx = from[0] + (to[0] - from[0]) * t - cx;
        float dy = from[1] + (to[1] - from[1]) * t - cy;
        float dz = from[2] + (to[2] - from[2]) * t - cz;
        float alongW = dx * wx + dy * wy + dz * wz;
        float alongH = dx * hx + dy * hy + dz * hz;
        boolean inside = Math.abs(alongW) <= (wx * wx + wy * wy + wz * wz) * 0.5f && Math.abs(alongH) <= (hx * hx + hy * hy + hz * hz) * 0.5f;
        return inside ? t : -1.0f;
    }
}
