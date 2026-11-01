package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Les programmes de shaders propres au mod, charges avec les shaders vanilla depuis
 * {@code assets/gtgs_backrooms/shaders/core/} (enregistres aupres du chargeur de l'API Fabric) et recharges avec les
 * ressources. Un programme qui ne compile pas fait echouer le rechargement des shaders, comme un programme vanilla :
 * le journal du pilote dit lequel et pourquoi.
 */
public final class LookPrograms {

    /** Post-traitement masque : bloom, vignette et aberration chromatique sur les pixels d'un look ; look entier sous un pack. */
    public static final Identifier POST = Identifier.of(ModConstants.MOD_ID, "look_post");
    /** Bloom, premiere passe : les pixels lumineux de la copie de l'ecran, a demi-resolution. */
    public static final Identifier BLOOM_EXTRACT = Identifier.of(ModConstants.MOD_ID, "look_bloom_extract");
    /** Bloom, flou separable a quart de resolution, une passe par axe. */
    public static final Identifier BLUR = Identifier.of(ModConstants.MOD_ID, "look_blur");
    /** Uniforme du post : x vignette, y aberration, z 1 pour appliquer aussi le look de l'etage 1, w force du bloom. */
    public static final String POST_PARAMS = "gtgs_PostParams";
    /** Identifiant (x), grain (y), vacillement (z), temps (w) du look de l'etage 1, quand les shaders ne le portent pas. */
    public static final String POST_LOOK = "gtgs_PostLook";
    /** Style du preset visuel : bande usee, bandes noires. */
    public static final String POST_STYLE = "gtgs_PostStyle";
    /** Seuil de luminosite du bloom. */
    public static final String BLOOM_THRESHOLD = "gtgs_BloomThreshold";
    /** Pas du flou : un texel le long de l'axe de la passe. */
    public static final String BLUR_STEP = "gtgs_BlurStep";

    private static @Nullable ShaderProgram post;
    private static @Nullable GlUniform postParams;
    private static @Nullable GlUniform postLook;
    private static @Nullable GlUniform postStyle;
    /** {@code true} une fois l'echantillonneur de la LUT du post lie a son unite, pour le programme charge. */
    private static boolean postLutBound;
    private static @Nullable ShaderProgram bloomExtract;
    private static @Nullable GlUniform bloomThreshold;
    private static @Nullable ShaderProgram blur;
    private static @Nullable GlUniform blurStep;

    private LookPrograms() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : les programmes se chargent a chaque rechargement des shaders. */
    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(POST, VertexFormats.POSITION_TEXTURE, program -> {
                post = program;
                postParams = program.getUniform(POST_PARAMS);
                postLook = program.getUniform(POST_LOOK);
                postStyle = program.getUniform(POST_STYLE);
                postLutBound = false;
            });
            context.register(BLOOM_EXTRACT, VertexFormats.POSITION_TEXTURE, program -> {
                bloomExtract = program;
                bloomThreshold = program.getUniform(BLOOM_THRESHOLD);
            });
            context.register(BLUR, VertexFormats.POSITION_TEXTURE, program -> {
                blur = program;
                blurStep = program.getUniform(BLUR_STEP);
            });
        });
    }

    /** Programme du post, ou {@code null} tant qu'il n'est pas charge. */
    public static @Nullable ShaderProgram post() {
        return post;
    }

    /** Uniforme des parametres du post, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform postParams() {
        return postParams;
    }

    /**
     * Lie l'echantillonneur de la LUT du post a son unite ({@link LookLuts#UNIT}), une fois par programme charge : vanilla
     * ne lie que les echantillonneurs 2D de son fichier, et la valeur reste dans le programme.
     */
    public static void bindLutSampler() {
        if (post == null || postLutBound) {
            return;
        }
        post.bind();
        int location = GlUniform.getUniformLocation(post.getGlRef(), LookLuts.SAMPLER);
        if (location >= 0) {
            GL20.glUniform1i(location, LookLuts.UNIT);
        }
        post.unbind();
        postLutBound = true;
    }

    /** Uniforme des parametres du look, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform postLook() {
        return postLook;
    }

    /** Uniforme du style du preset, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform postStyle() {
        return postStyle;
    }

    /** Programme de la premiere passe du bloom, ou {@code null} tant qu'il n'est pas charge. */
    public static @Nullable ShaderProgram bloomExtract() {
        return bloomExtract;
    }

    /** Uniforme du seuil du bloom, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform bloomThreshold() {
        return bloomThreshold;
    }

    /** Programme du flou, ou {@code null} tant qu'il n'est pas charge. */
    public static @Nullable ShaderProgram blur() {
        return blur;
    }

    /** Uniforme du pas du flou, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform blurStep() {
        return blurStep;
    }
}
