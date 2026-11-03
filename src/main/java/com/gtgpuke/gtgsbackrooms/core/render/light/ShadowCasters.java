package com.gtgpuke.gtgsbackrooms.core.render.light;

/**
 * Les corps qui portent une ombre sur le terrain, en coordonnees relatives au bloc d'une camera, sans rien allouer apres la creation :
 * une liste bornee de corps, chacun une enveloppe (un cylindre debout qui le contient, pour ecarter vite les rayons qui passent loin) et
 * ses boites, les cubes du modele qui le dessine, poses et animes comme lui : un centre et trois demi-axes. Un corps sans modele connu
 * prend la boite de sa collision, tournee selon son corps.
 */
public final class ShadowCasters {

    /** Corps gardes, au plus : ce que declare {@code look.glsl}. */
    public static final int MAX_BODIES = 8;
    /** Boites d'un corps, au plus : au-dela, les plus petites cedent leur place. */
    public static final int MAX_BOXES = 16;
    /** Valeurs de l'enveloppe d'un corps, deux groupes de quatre : centre du bas et rayon, puis haut et nombre de boites. */
    public static final int BOUNDS_VALUES = 8;
    /**
     * Valeurs d'une boite, quatre groupes de quatre : son centre et le rayon de la sphere qui la resume pour l'ombre de contact, puis ses
     * trois demi-axes, le plus long d'abord.
     */
    public static final int BOX_VALUES = 16;
    /** Valeurs d'un corps : son enveloppe, puis la place de toutes ses boites. */
    public static final int BODY_VALUES = BOUNDS_VALUES + MAX_BOXES * BOX_VALUES;
    /** Valeurs de la liste. */
    public static final int VALUES = MAX_BODIES * BODY_VALUES;
    /** Demi-axe en dessous duquel une boite n'a pas d'epaisseur : le shader ne saurait pas y lancer un rayon. */
    private static final double FLAT = 1.0e-6;
    /** Ce qu'une boite peut deborder d'une autre et y tenir encore, en blocs : les calques de la peau reprennent les cubes du corps. */
    private static final double SAME = 1.0e-4;
    private static final int AXES = 3;

    private final float[] values = new float[VALUES];
    private final double[] volumes = new double[MAX_BOXES];
    private final double[] axis = new double[AXES * AXES];
    private final double[] length = new double[AXES];
    private final float[] box = new float[BOX_VALUES];
    private int bodies;
    private int boxes;
    private boolean open;
    private double footX;
    private double footY;
    private double footZ;

    /** Vide la liste. */
    public void clear() {
        this.bodies = 0;
        this.open = false;
    }

    /**
     * Commence un corps, s'il reste de la place.
     *
     * @param x centre des pieds
     * @param y pieds
     * @param z centre des pieds
     * @return {@code false} si la liste est pleine : ses boites seront ignorees
     */
    public boolean begin(double x, double y, double z) {
        this.open = this.bodies < MAX_BODIES;
        this.boxes = 0;
        this.footX = x;
        this.footY = y;
        this.footZ = z;
        return this.open;
    }

    /**
     * Ajoute une boite au corps commence : son centre et ses trois demi-axes, perpendiculaires. Une boite sans epaisseur est ignoree, comme
     * une boite qui tient dans une autre (un calque de la peau reprend le cube qu'il recouvre) ; une boite qui en contient d'autres les
     * remplace, sans quoi leur ombre de contact compterait deux fois. Corps plein, elle prend la place de la plus petite si elle est plus
     * grande.
     */
    public void box(double cx, double cy, double cz, double ux, double uy, double uz, double vx, double vy, double vz, double wx, double wy, double wz) {
        if (!this.open) {
            return;
        }
        setAxis(0, ux, uy, uz);
        setAxis(1, vx, vy, vz);
        setAxis(2, wx, wy, wz);
        if (Math.min(this.length[0], Math.min(this.length[1], this.length[2])) < FLAT) {
            return;
        }
        int longest = this.length[0] >= this.length[1] && this.length[0] >= this.length[2] ? 0 : (this.length[1] >= this.length[2] ? 1 : 2);
        int second = longest == 0 ? 1 : 0;
        int third = 3 - longest - second;
        this.box[0] = (float) cx;
        this.box[1] = (float) cy;
        this.box[2] = (float) cz;
        this.box[3] = (float) Math.sqrt(this.length[second] * this.length[third]);
        copyAxis(longest, 4);
        copyAxis(second, 8);
        copyAxis(third, 12);
        int base = this.bodies * BODY_VALUES + BOUNDS_VALUES;
        for (int k = 0; k < this.boxes; k++) {
            if (inside(this.box, 0, this.values, base + k * BOX_VALUES)) {
                return;
            }
        }
        for (int k = this.boxes - 1; k >= 0; k--) {
            if (inside(this.values, base + k * BOX_VALUES, this.box, 0)) {
                remove(base, k);
            }
        }
        double volume = this.length[0] * this.length[1] * this.length[2];
        int at = this.boxes;
        if (at == MAX_BOXES) {
            at = 0;
            for (int k = 1; k < MAX_BOXES; k++) {
                if (this.volumes[k] < this.volumes[at]) {
                    at = k;
                }
            }
            if (this.volumes[at] >= volume) {
                return;
            }
        } else {
            this.boxes++;
        }
        this.volumes[at] = volume;
        System.arraycopy(this.box, 0, this.values, base + at * BOX_VALUES, BOX_VALUES);
    }

    /**
     * Ajoute au corps commence la boite de sa collision, tournee selon son corps : la forme d'un corps sans modele connu.
     *
     * @param width largeur
     * @param height hauteur
     * @param yaw orientation du corps, en degres
     */
    public void boxOf(double width, double height, float yaw) {
        double angle = Math.toRadians(yaw);
        double half = width * 0.5;
        box(this.footX, this.footY + height * 0.5, this.footZ, Math.cos(angle) * half, 0.0, Math.sin(angle) * half, 0.0, height * 0.5, 0.0,
                -Math.sin(angle) * half, 0.0, Math.cos(angle) * half);
    }

    /** Ferme le corps commence : son enveloppe contient toutes ses boites. Un corps sans boite n'est pas garde. */
    public void end() {
        if (!this.open) {
            return;
        }
        this.open = false;
        if (this.boxes == 0) {
            return;
        }
        int base = this.bodies * BODY_VALUES;
        double bottom = Double.MAX_VALUE;
        double top = -Double.MAX_VALUE;
        double reach = 0.0;
        for (int k = 0; k < this.boxes; k++) {
            int at = base + BOUNDS_VALUES + k * BOX_VALUES;
            double up = 0.0;
            double across = Math.hypot(this.values[at] - this.footX, this.values[at + 2] - this.footZ);
            for (int a = 4; a < BOX_VALUES; a += 4) {
                up += Math.abs(this.values[at + a + 1]);
                across += Math.hypot(this.values[at + a], this.values[at + a + 2]);
            }
            bottom = Math.min(bottom, this.values[at + 1] - up);
            top = Math.max(top, this.values[at + 1] + up);
            reach = Math.max(reach, across);
        }
        this.values[base] = (float) this.footX;
        this.values[base + 1] = (float) bottom;
        this.values[base + 2] = (float) this.footZ;
        this.values[base + 3] = (float) reach;
        this.values[base + 4] = (float) top;
        this.values[base + 5] = this.boxes;
        this.bodies++;
    }

    private void setAxis(int index, double x, double y, double z) {
        this.axis[index * AXES] = x;
        this.axis[index * AXES + 1] = y;
        this.axis[index * AXES + 2] = z;
        this.length[index] = Math.sqrt(x * x + y * y + z * z);
    }

    private void copyAxis(int index, int at) {
        this.box[at] = (float) this.axis[index * AXES];
        this.box[at + 1] = (float) this.axis[index * AXES + 1];
        this.box[at + 2] = (float) this.axis[index * AXES + 2];
        this.box[at + 3] = 0.0f;
    }

    /** Retire une boite du corps commence : la derniere prend sa place. */
    private void remove(int base, int k) {
        int last = this.boxes - 1;
        if (k != last) {
            System.arraycopy(this.values, base + last * BOX_VALUES, this.values, base + k * BOX_VALUES, BOX_VALUES);
            this.volumes[k] = this.volumes[last];
        }
        this.boxes = last;
    }

    /**
     * {@code true} si une boite tient dans une autre : sur chaque axe de la seconde, la premiere s'etend de l'ecart de leurs centres plus
     * la projection de ses trois demi-axes, pas plus loin que le demi-axe.
     */
    private static boolean inside(float[] inner, int innerAt, float[] outer, int outerAt) {
        for (int a = 4; a < BOX_VALUES; a += 4) {
            double x = outer[outerAt + a];
            double y = outer[outerAt + a + 1];
            double z = outer[outerAt + a + 2];
            double half = Math.sqrt(x * x + y * y + z * z);
            double reach = Math.abs((inner[innerAt] - outer[outerAt]) * x + (inner[innerAt + 1] - outer[outerAt + 1]) * y
                    + (inner[innerAt + 2] - outer[outerAt + 2]) * z);
            for (int b = 4; b < BOX_VALUES; b += 4) {
                reach += Math.abs(inner[innerAt + b] * x + inner[innerAt + b + 1] * y + inner[innerAt + b + 2] * z);
            }
            if (reach > half * (half + SAME)) {
                return false;
            }
        }
        return true;
    }

    /** Nombre de corps. */
    public int bodies() {
        return this.bodies;
    }

    /** Les corps, {@value #BODY_VALUES} valeurs chacun : a ne pas modifier. */
    public float[] values() {
        return this.values;
    }
}
