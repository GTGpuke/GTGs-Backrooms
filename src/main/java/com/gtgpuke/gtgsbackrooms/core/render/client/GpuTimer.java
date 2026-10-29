package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackState;

/**
 * Temps GPU des passes du moteur de shaders, par requetes {@code GL_TIME_ELAPSED} : une requete ouverte autour d'une
 * passe, lue quelques images plus tard, quand le pilote a le resultat, jamais en attendant. Chaque section tient un
 * anneau de requetes ; une image dont l'anneau est plein n'est pas mesuree. Les durees vont au moniteur de performance
 * ({@code client.look.*.gpu}) et ne coutent rien quand il est inactif. Une seule requete de ce type peut etre ouverte a
 * la fois : les sections ne s'imbriquent jamais, et sous un pack de shaders (qui dessine ses vues au milieu des
 * notres) rien n'est mesure.
 */
public final class GpuTimer {

    /** Le rendu du monde du joueur, vues de portail comprises : le cout de l'etage 1 se lit en le comparant moteur coupe. */
    public static final Section WORLD = new Section("client.look.world.gpu");
    /** La copie de l'ecran du post. */
    public static final Section COPY = new Section("client.look.copy.gpu");
    /** Les passes du bloom. */
    public static final Section BLOOM = new Section("client.look.bloom.gpu");
    /** Les triangles du post, un par look. */
    public static final Section POST = new Section("client.look.post.gpu");

    private static final int RING = 4;
    private static final Section[] SECTIONS = {WORLD, COPY, BLOOM, POST};
    private static int supported = -1;
    /** La section dont la requete est ouverte : une seule a la fois, une seconde ouverte dedans ne serait pas mesuree. */
    private static @Nullable Section openSection;

    private GpuTimer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Ouvre la requete d'une passe ; rien si le moniteur est inactif, sous un pack, ou sans requetes de temps. */
    public static void begin(Section section) {
        if (!ModPerformanceMonitor.isEnabled() || ShaderPackState.inUse() || !supported() || openSection != null) {
            return;
        }
        if (section.queries == null) {
            section.queries = new int[RING];
            section.pending = new boolean[RING];
            GL15.glGenQueries(section.queries);
        }
        if (section.pending[section.write]) {
            // Anneau plein : le pilote est en retard de quatre images, cette passe n'est pas mesuree.
            return;
        }
        GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, section.queries[section.write]);
        section.open = true;
        openSection = section;
    }

    /** Ferme la requete ouverte par {@link #begin} et releve celles des images precedentes deja pretes. */
    /** Ferme la section restee ouverte, s'il y en a une : apres une exception au milieu d'une passe mesuree. */
    public static void endOpen() {
        Section section = openSection;
        if (section != null) {
            end(section);
        }
    }

    public static void end(Section section) {
        if (!section.open) {
            return;
        }
        GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
        section.open = false;
        openSection = null;
        section.pending[section.write] = true;
        section.write = (section.write + 1) % RING;
        while (section.pending[section.read]) {
            int query = section.queries[section.read];
            if (GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) {
                break;
            }
            ModPerformanceMonitor.record(section.name, GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT));
            section.pending[section.read] = false;
            section.read = (section.read + 1) % RING;
        }
    }

    /** Fin de partie : les requetes sont rendues au pilote. */
    public static void close() {
        for (Section section : SECTIONS) {
            if (section.queries != null) {
                GL15.glDeleteQueries(section.queries);
                section.queries = null;
                section.pending = null;
                section.write = 0;
                section.read = 0;
                section.open = false;
            }
        }
        openSection = null;
    }

    private static boolean supported() {
        if (supported < 0) {
            supported = GL.getCapabilities().GL_ARB_timer_query || GL.getCapabilities().OpenGL33 ? 1 : 0;
        }
        return supported == 1;
    }

    /** Une passe mesuree : son nom au moniteur et son anneau de requetes. */
    public static final class Section {

        private final String name;
        private int[] queries;
        private boolean[] pending;
        private int write;
        private int read;
        private boolean open;

        private Section(String name) {
            this.name = name;
        }
    }
}
