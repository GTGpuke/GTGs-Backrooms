package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

import net.minecraft.client.gl.GlUniform;

import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Envoi du look courant ({@link LookState}) aux uniformes qu'un patch a ajoutes a un shader, a son activation. Les
 * emplacements sont resolus une fois par programme ({@link #resolve}) par {@code MixinShaderProgram}, qui n'appelle
 * {@link #upload} que si la version du look a change depuis son dernier envoi a ce programme.
 */
public final class LookUniformUploader {

    /** Identifiant du look, 0 pour vanilla : le shader rend alors la couleur telle quelle. */
    public static final String ID = "gtgs_LookId";
    /** Teinte (rgb) et force de l'etalonnage (a). */
    public static final String TINT = "gtgs_LookTint";
    /** Desaturation (x), grain (y), vacillement (z), temps (w). */
    public static final String PARAMS = "gtgs_LookParams";
    /** Nombre de neons dans le bloc d'uniformes de la vue courante. */
    public static final String NEON_COUNT = "gtgs_NeonCount";
    /** Bloc entier de la camera de la vue. */
    public static final String CAMERA_BLOCK = "gtgs_CameraBlock";
    /** Reste de la camera de la vue dans son bloc. */
    public static final String CAMERA_FRACT = "gtgs_CameraFract";
    /** Tick du monde de la vue dans la periode de l'horloge commune (x), graine des neons de sa dimension (y). */
    public static final String NEON_CLOCK = "gtgs_NeonClock";
    /** Rectangle de la texture du neon dans l'atlas, vide sans jumeau du calendrier. */
    public static final String NEON_FACE = "gtgs_NeonFace";
    /** Parts des neons qui vacillent et morts. */
    public static final String NEON_SHARES = "gtgs_NeonShares";
    /** Bloc d'uniformes des neons, lie au point {@link NeonLights#BINDING}. */
    public static final String NEON_BLOCK = "gtgs_NeonBlock";
    /** Emplacements a resoudre par programme, dans cet ordre. */
    public static final int LOCATIONS = 9;
    private static final String[] NAMES = {ID, TINT, PARAMS, NEON_COUNT, CAMERA_BLOCK, CAMERA_FRACT, NEON_CLOCK, NEON_FACE, NEON_SHARES};
    private static final int AT_ID = 0;
    private static final int AT_TINT = 1;
    private static final int AT_PARAMS = 2;
    private static final int AT_NEON_COUNT = 3;
    private static final int AT_CAMERA_BLOCK = 4;
    private static final int AT_CAMERA_FRACT = 5;
    private static final int AT_NEON_CLOCK = 6;
    private static final int AT_NEON_FACE = 7;
    private static final int AT_NEON_SHARES = 8;

    private LookUniformUploader() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Resout les emplacements des uniformes du look d'un programme, une fois, et lie son bloc des neons a son point.
     *
     * @param program programme GL
     * @param locations tableau de {@value #LOCATIONS} emplacements, rempli (negatif pour un uniforme absent)
     */
    public static void resolve(int program, int[] locations) {
        for (int i = 0; i < LOCATIONS; i++) {
            locations[i] = GlUniform.getUniformLocation(program, NAMES[i]);
        }
        int block = GL31.glGetUniformBlockIndex(program, NEON_BLOCK);
        if (block != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, block, NeonLights.BINDING);
        }
    }

    /** {@code true} si le programme est patche (il a l'identifiant du look). */
    public static boolean patched(int[] locations) {
        return locations[AT_ID] >= 0;
    }

    /**
     * Envoie le look courant au programme actif.
     *
     * @param locations emplacements resolus par {@link #resolve}
     */
    public static void upload(int[] locations) {
        LookProfile look = LookState.current();
        // Un mod qui dessine le terrain par ses propres shaders : le look part dans le post, les shaders patches restent vanilla.
        boolean carried = LookState.shadersCarryLook() && !look.vanilla();
        GL20.glUniform1i(locations[AT_ID], carried ? look.id() : 0);
        if (!carried) {
            return;
        }
        if (locations[AT_TINT] >= 0) {
            GL20.glUniform4f(locations[AT_TINT], look.tintRed(), look.tintGreen(), look.tintBlue(), look.tintStrength());
        }
        if (locations[AT_PARAMS] >= 0) {
            GL20.glUniform4f(locations[AT_PARAMS], look.desaturation(), look.grain(), look.flicker(), LookState.time());
        }
        if (locations[AT_NEON_COUNT] >= 0) {
            GL20.glUniform1i(locations[AT_NEON_COUNT], LookState.neonCount());
        }
        if (locations[AT_CAMERA_BLOCK] >= 0) {
            GL20.glUniform3i(locations[AT_CAMERA_BLOCK], LookState.cameraBlockX(), LookState.cameraBlockY(), LookState.cameraBlockZ());
        }
        if (locations[AT_CAMERA_FRACT] >= 0) {
            GL20.glUniform3f(locations[AT_CAMERA_FRACT], LookState.cameraFractX(), LookState.cameraFractY(), LookState.cameraFractZ());
        }
        if (locations[AT_NEON_CLOCK] >= 0) {
            GL20.glUniform2i(locations[AT_NEON_CLOCK], LookState.tick(), NeonSchedule.seed(look.dimension()));
        }
        if (locations[AT_NEON_FACE] >= 0) {
            float[] face = NeonFaces.rect(look);
            GL20.glUniform4f(locations[AT_NEON_FACE], face[0], face[1], face[2], face[3]);
        }
        if (locations[AT_NEON_SHARES] >= 0) {
            GL20.glUniform2f(locations[AT_NEON_SHARES], look.neons().flickering(), look.neons().dead());
        }
    }
}
