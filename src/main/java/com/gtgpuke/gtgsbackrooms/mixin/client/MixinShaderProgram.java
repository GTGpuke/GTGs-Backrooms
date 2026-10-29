package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.util.Map;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderStage;
import net.minecraft.resource.ResourceFactory;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalClipping;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderClipInjector;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderStageOrigin;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookUniformUploader;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * A chaque activation d'un shader pendant le dessin d'une vue de portail, lui transmet le plan de decoupe courant
 * ({@link PortalClipping}) ; et a chaque activation, le look courant ({@link LookState}) s'il a change depuis le dernier
 * envoi a ce shader. Les emplacements des uniformes sont resolus une fois par shader. Avant le chargement d'un etage,
 * les ressources du moteur de shaders sont chargees depuis la meme fabrique que lui ({@link LookResources}).
 *
 * <p>Au chargement d'un programme, vanilla redonne tout etage deja compile sous le meme nom. Un pack de shaders nomme
 * pareil les programmes de chacun de ses pipelines (un par dimension) et n'en garde normalement qu'un seul vivant ; une
 * vue de portail ou une traversee en fait vivre deux, et le second cree recevait les etages compiles pour l'autre
 * dimension (entites, main et ciel dessines comme de l'autre cote). Un etage n'est redonne qu'aux programmes charges
 * par la meme fabrique de ressources que lui : un rechargement vanilla en partage une pour tous ses programmes.
 */
@Mixin(ShaderProgram.class)
public abstract class MixinShaderProgram {

    @Unique
    private boolean backrooms$clipPlaneResolved;
    @Unique
    private int backrooms$clipPlaneLocation;
    @Unique
    private boolean backrooms$lookResolved;
    @Unique
    private final int[] backrooms$lookLocations = new int[LookUniformUploader.LOCATIONS];
    @Unique
    private int backrooms$lookVersion;

    @Shadow
    public abstract int getGlRef();

    @Inject(method = "bind", at = @At("TAIL"))
    private void backrooms$uploadClipPlane(CallbackInfo ci) {
        if (!PortalClipping.isActive()) {
            return;
        }
        if (!this.backrooms$clipPlaneResolved) {
            this.backrooms$clipPlaneLocation = GlUniform.getUniformLocation(this.getGlRef(), ShaderClipInjector.UNIFORM);
            this.backrooms$clipPlaneResolved = true;
        }
        PortalClipping.onProgramBound(this.backrooms$clipPlaneLocation);
    }

    /** Le look courant, envoye seulement s'il a change depuis le dernier envoi a ce programme ; rien pour un programme non patche. */
    @Inject(method = "bind", at = @At("TAIL"))
    private void backrooms$uploadLook(CallbackInfo ci) {
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        if (!this.backrooms$lookResolved) {
            // Le bloc des neons est lie une fois a son point de liaison : le tampon y est lie par le rendu de chaque vue.
            LookUniformUploader.resolve(this.getGlRef(), this.backrooms$lookLocations);
            this.backrooms$lookResolved = true;
        }
        if (!LookUniformUploader.patched(this.backrooms$lookLocations)) {
            return;
        }
        int version = LookState.version();
        if (this.backrooms$lookVersion != version) {
            this.backrooms$lookVersion = version;
            LookUniformUploader.upload(this.backrooms$lookLocations);
        }
    }

    /** Les ressources du moteur de shaders viennent de la fabrique qui charge les shaders vanilla, avant le premier d'entre eux. */
    @Inject(method = "loadShader", at = @At("HEAD"))
    private static void backrooms$loadLookResources(ResourceFactory factory, ShaderStage.Type type, String name,
            CallbackInfoReturnable<ShaderStage> cir) {
        LookResources.ensure(factory);
    }

    /** Un etage deja compile n'est redonne qu'a un programme charge par la meme fabrique ; sinon il est recompile. */
    @WrapOperation(method = "loadShader", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object backrooms$reuseSameSourceOnly(Map<String, ShaderStage> loaded, Object name, Operation<Object> original,
            @Local(argsOnly = true) ResourceFactory factory) {
        Object stage = original.call(loaded, name);
        if (!ModConfig.ENABLE_PORTAL_FEATURE || stage == null || ((ShaderStageOrigin) stage).backrooms$getFactory() == factory) {
            return stage;
        }
        return null;
    }

    @ModifyExpressionValue(
            method = "loadShader",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/ShaderStage;createFromResource(Lnet/minecraft/client/gl/ShaderStage$Type;"
                    + "Ljava/lang/String;Ljava/io/InputStream;Ljava/lang/String;Lnet/minecraft/client/gl/GlImportProcessor;)"
                    + "Lnet/minecraft/client/gl/ShaderStage;"))
    private static ShaderStage backrooms$recordSource(ShaderStage stage, @Local(argsOnly = true) ResourceFactory factory) {
        ((ShaderStageOrigin) stage).backrooms$setFactory(factory);
        return stage;
    }
}
