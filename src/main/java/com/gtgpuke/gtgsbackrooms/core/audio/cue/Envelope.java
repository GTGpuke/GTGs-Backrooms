package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

/**
 * L'enveloppe d'amplitude d'un son, precalculee au chargement des ressources : l'energie (moyenne quadratique) de
 * chaque fenetre de {@value #WINDOW_MS} ms, ramenee entre 0 et 1 par la fenetre la plus forte, un octet par fenetre.
 * Lue a la position de lecture reelle du son, elle fait vaciller les neons au rythme d'un cri. Elle se garde sur disque
 * sous l'empreinte du fichier son ({@link #cacheName}) : un fichier change, une autre empreinte, l'ancienne n'est plus lue.
 */
public final class Envelope {

    /** Duree d'une fenetre. */
    public static final int WINDOW_MS = 20;
    /** Version du format sur disque : une autre version n'est pas relue (l'enveloppe est recalculee). */
    static final int FORMAT = 1;
    private static final byte[] MAGIC = {'G', 'T', 'G', 'E'};
    private static final int HEADER = MAGIC.length + 2;
    private static final float SCALE = 255.0f;
    private static final float MS_PER_SECOND = 1000.0f;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final int NIBBLE = 4;
    private static final int LOW_NIBBLE = 0x0F;

    private final byte[] windows;

    private Envelope(byte[] windows) {
        this.windows = windows;
    }

    /**
     * Calcule l'enveloppe d'un son decode.
     *
     * @param samples echantillons sur 16 bits, voies entrelacees
     * @param channels nombre de voies, au moins une
     * @param sampleRate echantillons par seconde et par voie
     */
    public static Envelope bake(short[] samples, int channels, int sampleRate) {
        int perWindow = Math.max(1, sampleRate * WINDOW_MS / 1000) * channels;
        int count = (samples.length + perWindow - 1) / perWindow;
        float[] energy = new float[count];
        float strongest = 0.0f;
        for (int window = 0; window < count; window++) {
            int from = window * perWindow;
            int to = Math.min(samples.length, from + perWindow);
            double sum = 0.0;
            for (int i = from; i < to; i++) {
                double sample = samples[i] / 32768.0;
                sum += sample * sample;
            }
            energy[window] = (float) Math.sqrt(sum / (to - from));
            strongest = Math.max(strongest, energy[window]);
        }
        byte[] windows = new byte[count];
        if (strongest > 0.0f) {
            for (int window = 0; window < count; window++) {
                windows[window] = (byte) Math.round(energy[window] / strongest * SCALE);
            }
        }
        return new Envelope(windows);
    }

    /**
     * Le nom du fichier ou garder l'enveloppe d'un son : l'empreinte SHA-1 de son contenu, en hexadecimal.
     *
     * @param sound contenu du fichier son
     */
    public static String cacheName(byte[] sound) {
        return cacheName(sound, "SHA-1");
    }

    /** {@link #cacheName(byte[])} par un algorithme d'empreinte donne ; un algorithme absent est une erreur de la machine Java. */
    static String cacheName(byte[] sound, String algorithm) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(sound);
            char[] name = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                name[2 * i] = HEX[(digest[i] >> NIBBLE) & LOW_NIBBLE];
                name[2 * i + 1] = HEX[digest[i] & LOW_NIBBLE];
            }
            return new String(name) + ".env";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Algorithme d'empreinte absent de la machine Java : " + algorithm + ".", exception);
        }
    }

    /** L'enveloppe telle qu'elle se garde sur disque : une signature, le format, la duree d'une fenetre, puis les fenetres. */
    public byte[] toBytes() {
        byte[] out = new byte[HEADER + this.windows.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        out[MAGIC.length] = (byte) FORMAT;
        out[MAGIC.length + 1] = (byte) WINDOW_MS;
        System.arraycopy(this.windows, 0, out, HEADER, this.windows.length);
        return out;
    }

    /**
     * Relit une enveloppe gardee sur disque.
     *
     * @param data contenu du fichier
     * @return l'enveloppe, ou {@code null} si le fichier n'en est pas une, ou d'un autre format (elle se recalcule)
     */
    public static @Nullable Envelope fromBytes(byte[] data) {
        if (data.length < HEADER || !Arrays.equals(data, 0, MAGIC.length, MAGIC, 0, MAGIC.length) || data[MAGIC.length] != FORMAT
                || data[MAGIC.length + 1] != WINDOW_MS) {
            return null;
        }
        return new Envelope(Arrays.copyOfRange(data, HEADER, data.length));
    }

    /** Nombre de fenetres. */
    public int windows() {
        return this.windows.length;
    }

    /** Duree du son, en secondes. */
    public float durationSeconds() {
        return this.windows.length * WINDOW_MS / MS_PER_SECOND;
    }

    /**
     * Amplitude a une position de lecture, de 0 a 1, interpolee entre deux fenetres ; nulle avant le debut et apres la
     * fin.
     *
     * @param seconds position de lecture, en secondes
     */
    public float at(float seconds) {
        float position = seconds * MS_PER_SECOND / WINDOW_MS;
        if (position < 0.0f || position >= this.windows.length) {
            return 0.0f;
        }
        int index = (int) position;
        float current = (this.windows[index] & 0xFF) / SCALE;
        if (index + 1 >= this.windows.length) {
            return current;
        }
        float next = (this.windows[index + 1] & 0xFF) / SCALE;
        return current + (next - current) * (position - index);
    }
}
