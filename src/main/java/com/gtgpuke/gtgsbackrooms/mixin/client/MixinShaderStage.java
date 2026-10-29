package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.util.List;

import com.llamalad7.mixinextras.sugar.Local;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gl.ShaderStage;
import net.minecraft.resource.ResourceFactory;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderClipInjector;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderStageOrigin;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.ShaderCompileCheck;
import com.gtgpuke.gtgsbackrooms.core.render.patch.PatchedVertexStages;
import com.gtgpuke.gtgsbackrooms.core.render.patch.ShaderKind;

/**
 * Au chargement d'un shader, ajoute le plan de decoupe des vues de portail aux vertex shaders
 * ({@link ShaderClipInjector}), puis applique les patchs du moteur de shaders ({@link LookResources}). La source arrive
 * ici deja debarrassee de ses {@code #moj_import}. Retient aussi la fabrique qui a fourni la source
 * ({@link ShaderStageOrigin}).
 */
@Mixin(ShaderStage.class)
public abstract class MixinShaderStage implements ShaderStageOrigin {

    @Unique
    private @Nullable ResourceFactory backrooms$factory;

    @Override
    public @Nullable ResourceFactory backrooms$getFactory() {
        return this.backrooms$factory;
    }

    @Override
    public void backrooms$setFactory(ResourceFactory factory) {
        this.backrooms$factory = factory;
    }

    @ModifyArg(
            method = "load",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;glShaderSource(ILjava/util/List;)V"),
            index = 1)
    private static List<String> backrooms$patchSource(List<String> sources, @Local(argsOnly = true) ShaderStage.Type type,
            @Local(argsOnly = true, ordinal = 0) String name) {
        String joined = String.join("", sources);
        String patched = joined;
        if (ModConfig.ENABLE_PORTAL_FEATURE) {
            patched = ShaderClipInjector.inject(patched);
        }
        if (ModConfig.ENABLE_LOOK) {
            boolean fragment = type == ShaderStage.Type.FRAGMENT;
            String looked = LookResources.patches().patch(fragment ? ShaderKind.FRAGMENT : ShaderKind.VERTEX, name, patched);
            boolean wanted = looked != patched;
            if (wanted && fragment && !PatchedVertexStages.fragmentAllowed(name)) {
                // Son vertex n'ecrit pas les entrees du look : patche, le programme ne se lierait plus.
                ModConstants.LOGGER.warn("[Look] Fragment {} laisse vanilla : son vertex n'a pas le patch du look.", name);
                wanted = false;
            }
            // Un patch que le pilote refuse laisse le shader tel qu'il etait avant lui.
            boolean accepted = wanted && ShaderCompileCheck.compiles(type, name, looked);
            if (accepted) {
                patched = looked;
            }
            if (!fragment) {
                PatchedVertexStages.vertexLoaded(name, accepted);
            }
        }
        // Meme instance : shader non concerne, la liste d'origine repart telle quelle.
        return patched == joined ? sources : List.of(patched);
    }
}
