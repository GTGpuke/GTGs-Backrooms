package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Developpement (option {@code gtgsbackrooms.look.checkGlState}) : verifie qu'une passe du moteur de shaders rend l'etat
 * GL tel qu'elle l'a trouve (specification des shaders, section 10) : stencil, profondeur, melange, elimination des
 * faces, ciseau, fenetre de rendu, masques de couleur, framebuffers lies, programme, unite de texture active, bloc
 * d'uniformes lie. L'etat est relu au pilote avant et apres la passe ; chaque ecart est journalise une fois par passe et
 * par element. Les masques du stencil se comparent sur ses huit bits (le pilote rend tous les bits a 1 par defaut, le
 * mod pose 0xFF : meme effet). Ne sont pas compares les tableaux et tampons de sommets ni la texture liee a l'unite
 * active : vanilla les repose avant chaque dessin et delie les textures d'un programme a sa fin
 * ({@code ShaderProgram.unbind}), par son suivi d'etat, que les passes tiennent a jour en dessinant par lui. Les
 * lectures attendent le pilote : jamais hors de cette option.
 */
public final class GlStateCheck {

    /** Verification active. */
    public static final boolean ENABLED = ModConfig.LOOK_CHECK_GL_STATE;
    /** Post masque du monde du joueur. */
    public static final int POST = 0;
    /** Look du monde du joueur sous un pack de shaders. */
    public static final int POST_UNDER_PACK = 1;
    /** Look d'une vue sous un pack de shaders. */
    public static final int POST_OF_VIEW = 2;
    /** Effacement du stencil au debut du rendu du monde. */
    public static final int STENCIL_CLEAR = 3;
    /** Mode de debogage du stencil. */
    public static final int STENCIL_DEBUG = 4;
    /** Envoi du bloc des neons. */
    public static final int NEON_UPLOAD = 5;
    private static final String[] PASSES = {"post", "post sous un pack", "post d'une vue sous un pack", "effacement du stencil",
        "debogage du stencil", "bloc des neons"};
    private static final String[] NAMES = {"GL_STENCIL_TEST", "GL_STENCIL_FUNC", "GL_STENCIL_REF", "GL_STENCIL_VALUE_MASK",
        "GL_STENCIL_WRITEMASK", "GL_STENCIL_FAIL", "GL_STENCIL_PASS_DEPTH_FAIL", "GL_STENCIL_PASS_DEPTH_PASS",
        "GL_STENCIL_CLEAR_VALUE", "GL_DEPTH_TEST", "GL_DEPTH_FUNC", "GL_DEPTH_WRITEMASK", "GL_BLEND", "GL_BLEND_SRC_RGB",
        "GL_BLEND_DST_RGB", "GL_CULL_FACE", "GL_SCISSOR_TEST", "GL_VIEWPORT x", "GL_VIEWPORT y", "GL_VIEWPORT largeur",
        "GL_VIEWPORT hauteur", "GL_COLOR_WRITEMASK r", "GL_COLOR_WRITEMASK g", "GL_COLOR_WRITEMASK b", "GL_COLOR_WRITEMASK a",
        "GL_DRAW_FRAMEBUFFER_BINDING", "GL_READ_FRAMEBUFFER_BINDING", "GL_CURRENT_PROGRAM", "GL_ACTIVE_TEXTURE",
        "GL_UNIFORM_BUFFER_BINDING"};
    /** Le stencil a huit bits : seuls ceux-la des masques comptent. */
    private static final int STENCIL_BITS = 0xFF;
    private static final int[] BEFORE = new int[NAMES.length];
    private static final int[] AFTER = new int[NAMES.length];
    private static final int[] FOUR = new int[4];
    private static final boolean[][] REPORTED = new boolean[PASSES.length][NAMES.length];
    private static int pending = -1;

    private GlStateCheck() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Avant une passe : l'etat qu'elle doit rendre. */
    public static void before(int pass) {
        read(BEFORE);
        pending = pass;
    }

    /** Apres la passe ouverte par {@link #before} : chaque ecart, journalise une fois. */
    public static void after(int pass) {
        if (pending != pass) {
            ModConstants.LOGGER.warn("[Look] Verification de l'etat GL : passe {} fermee sans avoir ete ouverte.", PASSES[pass]);
            pending = -1;
            return;
        }
        pending = -1;
        read(AFTER);
        for (int i = 0; i < NAMES.length; i++) {
            if (BEFORE[i] != AFTER[i] && !REPORTED[pass][i]) {
                REPORTED[pass][i] = true;
                ModConstants.LOGGER.warn("[Look] La passe {} ne rend pas l'etat GL tel quel : {} {} devient {}.", PASSES[pass], NAMES[i], BEFORE[i],
                        AFTER[i]);
            }
        }
    }

    private static void read(int[] state) {
        int i = 0;
        state[i++] = GL11.glIsEnabled(GL11.GL_STENCIL_TEST) ? 1 : 0;
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK) & STENCIL_BITS;
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK) & STENCIL_BITS;
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        state[i++] = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
        state[i++] = GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0;
        state[i++] = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        state[i++] = GL11.glGetInteger(GL11.GL_DEPTH_WRITEMASK);
        state[i++] = GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0;
        state[i++] = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        state[i++] = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        state[i++] = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0;
        state[i++] = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0;
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, FOUR);
        for (int k = 0; k < 4; k++) {
            state[i++] = FOUR[k];
        }
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, FOUR);
        for (int k = 0; k < 4; k++) {
            state[i++] = FOUR[k];
        }
        state[i++] = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        state[i++] = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        state[i++] = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        state[i++] = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        state[i] = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
    }
}
