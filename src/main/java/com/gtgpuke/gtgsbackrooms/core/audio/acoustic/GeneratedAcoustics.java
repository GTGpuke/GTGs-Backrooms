package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

/**
 * L'acoustique tiree de la generation d'une dimension ({@code "acoustics": "generated"}) : le contenu qui genere une
 * dimension sait la forme de ses salles sans lancer un rayon, et l'enregistre ici pour elle. Le thread de calcul s'en
 * sert la ou la copie du monde s'accorde avec la generation (aucun bloc pose ni casse autour de la source), et lance ses
 * rayons ailleurs. Thread de rendu pour l'enregistrement ; chaque forme n'est utilisee que par le thread de calcul.
 */
public final class GeneratedAcoustics {

    private static final Map<Identifier, Shape> SHAPES = new HashMap<>();

    /** La forme des salles d'une dimension, telle que sa generation la fait. */
    public interface Shape {

        /**
         * L'espace autour d'un point d'apres la generation : taille et ouverture, occlusion nulle.
         *
         * @return l'espace, ou {@code null} si la generation ne dit rien de ce point (hors de ses salles)
         */
        @Nullable AcousticResult estimate(int x, int y, int z);

        /**
         * {@code true} si la copie du monde autour d'un point s'accorde avec la generation : aucun bloc pose dans un passage
         * ni casse dans un mur ; un bloc pas encore copie s'accorde.
         *
         * @param window copie du monde autour du point
         */
        boolean matches(SolidityWindow window, int x, int y, int z);
    }

    private GeneratedAcoustics() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** La forme d'une dimension est connue (le serveur a donne de quoi la refaire). */
    public static void register(Identifier dimension, Shape shape) {
        SHAPES.put(dimension, shape);
    }

    /** La forme d'une dimension, ou {@code null} si elle n'est pas connue : ses sons se calculent par rayons. */
    public static @Nullable Shape of(@Nullable Identifier dimension) {
        return SHAPES.get(dimension);
    }

    /** Fin de partie : plus aucune forme. */
    public static void clear() {
        SHAPES.clear();
    }
}
