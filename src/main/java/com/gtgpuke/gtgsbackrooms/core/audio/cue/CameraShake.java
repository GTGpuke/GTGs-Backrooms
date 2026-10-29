package com.gtgpuke.gtgsbackrooms.core.audio.cue;

/**
 * Le micro-tremblement de camera d'un cri ({@code camera_shake}) : un petit ecart de lacet et de tangage, deux
 * oscillations melangees sur l'horloge commune (le meme tremblement pour tous les joueurs au meme instant), plafonne a
 * {@value #MAX_DEGREES} degres a l'amplitude 1. Jamais dans une dimension vanilla, coupe par defaut et par l'option de
 * confort : c'est l'appelant qui en decide. Logique pure.
 */
public final class CameraShake {

    /** Ecart maximal de lacet, en degres, a l'amplitude 1. */
    public static final float MAX_DEGREES = 1.5f;
    /** Le tangage tremble un peu moins que le lacet. */
    private static final float PITCH_SHARE = 0.6f;
    private static final double YAW_A = 31.0;
    private static final double YAW_B = 17.3;
    private static final double YAW_PHASE = 0.7;
    private static final double PITCH_A = 27.1;
    private static final double PITCH_B = 13.7;
    private static final double PITCH_PHASE = 1.3;

    private CameraShake() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Ecart de lacet, en degres.
     *
     * @param seconds temps de l'horloge commune, en secondes
     * @param amplitude force du tremblement, de 0 a 1
     */
    public static float yaw(float seconds, float amplitude) {
        return amplitude * MAX_DEGREES * (float) (Math.sin(seconds * YAW_A) * Math.sin(seconds * YAW_B + YAW_PHASE));
    }

    /**
     * Ecart de tangage, en degres.
     *
     * @param seconds temps de l'horloge commune, en secondes
     * @param amplitude force du tremblement, de 0 a 1
     */
    public static float pitch(float seconds, float amplitude) {
        return amplitude * MAX_DEGREES * PITCH_SHARE * (float) (Math.sin(seconds * PITCH_A + PITCH_PHASE) * Math.sin(seconds * PITCH_B));
    }
}
