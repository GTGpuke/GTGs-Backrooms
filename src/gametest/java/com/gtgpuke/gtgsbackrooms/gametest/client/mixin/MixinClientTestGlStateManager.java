package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtgpuke.gtgsbackrooms.gametest.client.ClientTestGlErrors;

/**
 * Suppression du tampon nul pendant les tests du client : sous Linux, vanilla vide un tampon avant de le supprimer, et
 * vider le tampon nul est une erreur OpenGL ; ailleurs le pilote l'ignore sans rien dire. La relever ici la rend visible
 * sur toutes les plateformes.
 */
@Mixin(GlStateManager.class)
public abstract class MixinClientTestGlStateManager {

    @Inject(method = "_glDeleteBuffers", at = @At("HEAD"), remap = false)
    private static void gtgsTest$deleteNullBuffer(int buffer, CallbackInfo ci) {
        if (buffer == 0) {
            ClientTestGlErrors.record("glDeleteBuffers(0) : suppression du tampon nul (erreur sous Linux).");
        }
    }
}
