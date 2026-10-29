package com.gtgpuke.gtgsbackrooms.core.render.light;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Etat de chaque neon, calculable par n'importe quel client a partir de sa position, de la graine de sa dimension et du
 * temps du monde : la meme fonction pour la lumiere qu'il projette, sa face, et son bourdonnement. Chaque neon tire son
 * caractere de sa position : la plupart brillent sans faillir, une part vacille (des coupures de quatre ticks, un
 * cinquieme de seconde, jamais plus de cinq par seconde, voisins decorreles), une part est morte.
 *
 * <p>La fonction existe deux fois, ici et en GLSL ({@code assets/gtgs_backrooms/shaders/include/neon_schedule.glsl}), et
 * les deux donnent exactement le meme resultat : arithmetique entiere sur 32 bits (multiplications modulo 2^32, decalages
 * non signes, ou exclusif ; un {@code int} Java a les memes bits qu'un {@code uint} GLSL), aucun flottant dans la
 * decision, le temps passe en ticks entiers dans la periode de l'horloge commune. Les deux sont validees contre les memes
 * vecteurs ({@code src/test/resources/neon_schedule_vectors.json}).
 */
public final class NeonSchedule {

    /** Intensite d'un neon mort ou d'un neon coupe : jamais noir, le tube reste visible. */
    public static final float OFF = 0.12f;
    /** Un neon qui brille sans faillir. */
    public static final int STEADY = 0;
    /** Un neon qui vacille. */
    public static final int FLICKERING = 1;
    /** Un neon mort. */
    public static final int DEAD = 2;
    /** Ticks d'une coupure : le plus court qui se voit. */
    static final int SLOT_TICKS = 4;
    /** Le caractere d'un neon range de 0 a 1023 : la part qui vacille, puis la part morte. */
    static final int LOTS = 1024;
    private static final int LOT_SHIFT = 22;
    private static final int ROLL_SHIFT = 29;
    private static final int SLOT_SHIFT = 2;
    private static final int X_SALT = 0x9E3779B1;
    private static final int Y_SALT = 0x85EBCA77;
    private static final int Z_SALT = 0xC2B2AE3D;
    private static final int SLOT_SALT = 0x27D4EB2F;
    private static final int MIX_A = 0x7FEB352D;
    private static final int MIX_B = 0x846CA68B;
    private static final int MIX_SHIFT_A = 16;
    private static final int MIX_SHIFT_B = 15;

    private NeonSchedule() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * La graine des neons d'une dimension : deux dimensions n'ont pas le meme calendrier. Le hachage de son identifiant
     * (celui des chaines de son espace de noms et de son chemin, que Java fixe et met en cache) : rien d'alloue.
     */
    public static int seed(Identifier dimension) {
        return dimension.hashCode();
    }

    /**
     * Intensite d'un neon, de {@value #OFF} a 1.
     *
     * @param x position du bloc
     * @param y position du bloc
     * @param z position du bloc
     * @param seed graine de sa dimension ({@link #seed})
     * @param tick tick dans la periode de l'horloge commune, positif ({@code CueClock.tick})
     * @param flickering part des neons qui vacillent, de 0 a 1
     * @param dead part des neons morts, de 0 a 1 (pris apres ceux qui vacillent)
     */
    public static float intensity(int x, int y, int z, int seed, int tick, float flickering, float dead) {
        int character = character(x, y, z, seed);
        int kind = kind(character, flickering, dead);
        if (kind == FLICKERING) {
            return flickers(character, tick) ? OFF : 1.0f;
        }
        return kind == DEAD ? OFF : 1.0f;
    }

    /** {@link #intensity(int, int, int, int, int, float, float)} pour une position empaquetee ({@code BlockPos.asLong}). */
    public static float intensity(long packed, int seed, int tick, float flickering, float dead) {
        return intensity(BlockPos.unpackLongX(packed), BlockPos.unpackLongY(packed), BlockPos.unpackLongZ(packed), seed, tick, flickering, dead);
    }

    /**
     * Le caractere d'un neon : {@link #STEADY}, {@link #FLICKERING} ou {@link #DEAD}, d'apres un entier de 0 a 1023 tire de
     * sa position, compare aux parts du profil sans flottant dans la decision (les parts multipliees par 1024 et tronquees,
     * exactes pour un flottant).
     */
    public static int kind(int character, float flickering, float dead) {
        int lot = character >>> LOT_SHIFT;
        if (lot < (int) (flickering * LOTS)) {
            return FLICKERING;
        }
        if (lot < (int) ((flickering + dead) * LOTS)) {
            return DEAD;
        }
        return STEADY;
    }

    /** L'entier qui fait le caractere d'un neon, tire de sa position et de la graine de sa dimension. */
    public static int character(int x, int y, int z, int seed) {
        return mix(x * X_SALT ^ mix(y * Y_SALT ^ mix(z * Z_SALT ^ seed)));
    }

    /** {@code true} pendant une coupure : un creneau de {@value #SLOT_TICKS} ticks sur huit en moyenne, tires au sort. */
    static boolean flickers(int character, int tick) {
        int slot = tick >>> SLOT_SHIFT;
        return mix(character ^ slot * SLOT_SALT) >>> ROLL_SHIFT == 0;
    }

    /** Melange d'un entier sur 32 bits, identique en Java et en GLSL. */
    static int mix(int value) {
        int z = value;
        z = (z ^ (z >>> MIX_SHIFT_A)) * MIX_A;
        z = (z ^ (z >>> MIX_SHIFT_B)) * MIX_B;
        return z ^ (z >>> MIX_SHIFT_A);
    }
}
