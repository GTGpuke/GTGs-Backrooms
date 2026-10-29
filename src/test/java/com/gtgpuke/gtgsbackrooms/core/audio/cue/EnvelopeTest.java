package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Enveloppe d'amplitude d'un son")
class EnvelopeTest {

    private static final float EPSILON = 1.0e-3f;
    private static final int RATE = 1000;

    @Test
    @DisplayName("Le silence donne une enveloppe nulle, une impulsion une seule fenetre pleine")
    void silenceAndImpulse() {
        Envelope silence = Envelope.bake(new short[RATE], 1, RATE);
        assertEquals(50, silence.windows(), "Une seconde a mille echantillons : cinquante fenetres de vingt millisecondes.");
        assertEquals(1.0f, silence.durationSeconds(), EPSILON, "Une seconde.");
        assertEquals(0.0f, silence.at(0.5f), EPSILON, "Rien.");
        short[] impulse = new short[RATE];
        impulse[600] = 20000;
        Envelope envelope = Envelope.bake(impulse, 1, RATE);
        assertEquals(1.0f, envelope.at(0.6f), EPSILON, "La fenetre de l'impulsion est la plus forte : 1.");
        assertEquals(0.0f, envelope.at(0.1f), EPSILON, "Avant : rien.");
        assertEquals(0.0f, envelope.at(0.9f), EPSILON, "Apres : rien.");
        assertEquals(0.0f, envelope.at(-0.1f), EPSILON, "Avant le debut : rien.");
        assertEquals(0.0f, envelope.at(1.0f), EPSILON, "Apres la fin : rien.");
        assertEquals(0.0f, envelope.at(5.0f), EPSILON, "Longtemps apres : rien.");
    }

    @Test
    @DisplayName("Une rampe monte fenetre apres fenetre, interpolee entre deux fenetres, les voies entrelacees comptees ensemble")
    void rampIsInterpolated() {
        short[] stereo = new short[2 * RATE];
        for (int i = 0; i < RATE; i++) {
            short value = (short) (i * 30);
            stereo[2 * i] = value;
            stereo[2 * i + 1] = (short) -value;
        }
        Envelope envelope = Envelope.bake(stereo, 2, RATE);
        assertEquals(50, envelope.windows(), "Deux voies : toujours cinquante fenetres.");
        assertTrue(envelope.at(0.2f) < envelope.at(0.5f) && envelope.at(0.5f) < envelope.at(0.9f), "Elle monte.");
        assertEquals(1.0f, envelope.at(0.99f), EPSILON, "La derniere fenetre est la plus forte.");
        float start = envelope.at(0.40f);
        float middle = envelope.at(0.41f);
        float end = envelope.at(0.42f);
        assertTrue(start < middle && middle < end, "Entre deux fenetres, la valeur glisse de l'une a l'autre.");
        assertEquals((start + end) / 2.0f, middle, 0.01f, "Au milieu, la moyenne des deux.");
    }

    @Test
    @DisplayName("Un son plus court qu'une fenetre, ou a tres bas debit, tient dans une fenetre d'au moins un echantillon")
    void tinySoundsStillBake() {
        Envelope tiny = Envelope.bake(new short[] {1000, -1000, 500}, 1, RATE);
        assertEquals(1, tiny.windows(), "Trois echantillons : une fenetre.");
        assertEquals(1.0f, tiny.at(0.0f), EPSILON, "Pleine.");
        Envelope slow = Envelope.bake(new short[] {1000, 0, 0, 2000}, 1, 10);
        assertEquals(4, slow.windows(), "A dix echantillons par seconde, un echantillon par fenetre.");
        assertEquals(0.5f, slow.at(0.0f), 1.0f / 255.0f, "La moitie du plus fort, a un octet pres.");
    }

    @Test
    @DisplayName("Une enveloppe gardee sur disque se relit a l'identique ; un autre format ou un autre fichier ne se relit pas")
    void envelopesRoundTripOnDisk() {
        short[] samples = new short[44100];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 2000 * 10);
        }
        Envelope envelope = Envelope.bake(samples, 1, 44100);
        byte[] stored = envelope.toBytes();
        Envelope read = Envelope.fromBytes(stored);
        assertNotNull(read, "Relue.");
        assertEquals(envelope.windows(), read.windows(), "Meme nombre de fenetres.");
        for (int i = 0; i < 50; i++) {
            assertEquals(envelope.at(i * 0.02f), read.at(i * 0.02f), 1.0e-6f, "Meme amplitude a la fenetre " + i + ".");
        }
        assertNull(Envelope.fromBytes(new byte[] {'G', 'T'}), "Trop court.");
        byte[] other = stored.clone();
        other[0] = 'X';
        assertNull(Envelope.fromBytes(other), "Pas une enveloppe.");
        byte[] newer = stored.clone();
        newer[4] = (byte) (Envelope.FORMAT + 1);
        assertNull(Envelope.fromBytes(newer), "Un autre format se recalcule.");
        byte[] window = stored.clone();
        window[5] = 10;
        assertNull(Envelope.fromBytes(window), "Une autre duree de fenetre aussi.");
        String name = Envelope.cacheName(new byte[] {1, 2, 3});
        assertEquals("7037807198c22a7d2b0807371d763779a84fdfcf.env", name, "Empreinte SHA-1 du contenu.");
        assertNotEquals(name, Envelope.cacheName(new byte[] {1, 2, 4}), "Un autre contenu, une autre empreinte.");
        assertThrows(IllegalStateException.class, () -> Envelope.cacheName(new byte[0], "absent"),
                "Un algorithme absent de la machine Java est une erreur.");
    }
}
