package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

/**
 * L'etat de lecture des pixels pendant un envoi de texture du mod. Vanilla pose le pas des lignes de chaque image qu'il
 * envoie, textures animees comprises, sans le remettre : un envoi en pleine partie lirait son tableau avec un autre pas,
 * au-dela de sa fin, et le pilote planterait. Un tampon de pixels lie ferait lire une adresse comme un decalage.
 * {@link #begin} remet tout au defaut, {@link #end} rend l'etat trouve. Sans allocation : deux envois ne s'imbriquent pas.
 */
final class PixelUnpack {

    private static final int[] SETTINGS = {GL11.GL_UNPACK_ROW_LENGTH, GL11.GL_UNPACK_SKIP_ROWS, GL11.GL_UNPACK_SKIP_PIXELS, GL11.GL_UNPACK_ALIGNMENT,
        GL12.GL_UNPACK_IMAGE_HEIGHT, GL12.GL_UNPACK_SKIP_IMAGES, GL11.GL_UNPACK_SWAP_BYTES};
    private static final int[] DEFAULTS = {0, 0, 0, 4, 0, 0, 0};
    private static final int[] SAVED = new int[SETTINGS.length];
    private static int buffer;

    private PixelUnpack() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Avant un envoi : garde l'etat trouve et pose les valeurs par defaut. */
    static void begin() {
        for (int i = 0; i < SETTINGS.length; i++) {
            SAVED[i] = GL11.glGetInteger(SETTINGS[i]);
            GL11.glPixelStorei(SETTINGS[i], DEFAULTS[i]);
        }
        buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
    }

    /** Apres l'envoi : rend l'etat trouve. */
    static void end() {
        for (int i = 0; i < SETTINGS.length; i++) {
            GL11.glPixelStorei(SETTINGS[i], SAVED[i]);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
    }
}
