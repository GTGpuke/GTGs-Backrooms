package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

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
    /** Teinte du look de l'etage 1 (rgb) et sa force (a), quand les shaders ne le portent pas (pack, mod de terrain). */
    public static final String POST_TINT = "gtgs_PostTint";
    /** Desaturation (x), grain (y), vacillement (z), temps (w) du look de l'etage 1, quand les shaders ne le portent pas. */
    public static final String POST_LOOK = "gtgs_PostLook";
    /** Seuil de luminosite du bloom. */
    public static final String BLOOM_THRESHOLD = "gtgs_BloomThreshold";
    /** Pas du flou : un texel le long de l'axe de la passe. */
    public static final String BLUR_STEP = "gtgs_BlurStep";

    private static @Nullable ShaderProgram post;
    private static @Nullable GlUniform postParams;
    private static @Nullable GlUniform postTint;
    private static @Nullable GlUniform postLook;
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
                postTint = program.getUniform(POST_TINT);
                postLook = program.getUniform(POST_LOOK);
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

    /** Uniforme de la teinte du look, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform postTint() {
        return postTint;
    }

    /** Uniforme des parametres du look, ou {@code null} tant que le programme n'est pas charge. */
    public static @Nullable GlUniform postLook() {
        return postLook;
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
