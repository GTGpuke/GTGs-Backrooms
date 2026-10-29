package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.HashSet;
import java.util.Set;

/**
 * Le fragment patche d'un programme lit des sorties que seul le vertex patche du meme programme ecrit (position de vue,
 * coordonnees, normale) : en GLSL 1.50, une entree lue sans sortie qui lui corresponde fait echouer l'edition de liens du
 * programme, qui ne dessine plus rien. Un fragment ne recoit donc son patch que si le vertex du meme nom a recu le sien
 * (vanilla charge le vertex d'un programme avant son fragment, et chaque programme vise porte le meme nom pour ses deux
 * etages) ; sinon il reste vanilla : le look manque a ce programme, le monde reste dessine. Thread de rendu seulement.
 */
public final class PatchedVertexStages {

    private static final Set<String> PATCHED = new HashSet<>();

    private PatchedVertexStages() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Le vertex de ce nom vient d'etre charge.
     *
     * @param name nom de l'etage
     * @param patched {@code true} s'il porte le patch du look (applique et accepte par le pilote)
     */
    public static void vertexLoaded(String name, boolean patched) {
        if (patched) {
            PATCHED.add(name);
        } else {
            PATCHED.remove(name);
        }
    }

    /** {@code true} si le fragment de ce nom peut recevoir le patch du look : le vertex du meme nom ecrit ses entrees. */
    public static boolean fragmentAllowed(String name) {
        return PATCHED.contains(name);
    }
}
