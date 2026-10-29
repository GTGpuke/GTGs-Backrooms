package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.util.Window;

import com.gtgpuke.gtgsbackrooms.gametest.client.ClientTestMod;

/**
 * Fenetre du client de test cachee : le jeu rend dans son tampon principal, que les tests lisent, sans prendre la main
 * sur l'ecran de qui lance le build. {@code -Dgtgsbackrooms.clientTest.visible=true} la montre (mise au point d'un test).
 */
@Mixin(Window.class)
public abstract class MixinClientTestWindow {

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J", remap = false))
    private void gtgsTest$hide(CallbackInfo ci) {
        if (System.getProperty(ClientTestMod.REPORT_PROPERTY) != null && !Boolean.getBoolean("gtgsbackrooms.clientTest.visible")) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
    }
}
