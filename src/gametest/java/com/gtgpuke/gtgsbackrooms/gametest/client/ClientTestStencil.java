package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.nio.ByteBuffer;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;

import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;

/**
 * Stencil de l'image du jeu, relu pendant un tick du client : entre deux images, il tient encore celui de la derniere,
 * effacee seulement au debut du rendu du monde suivant.
 */
final class ClientTestStencil {

    private ClientTestStencil() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Nombre de pixels de la derniere image dont les bits de look du stencil valent {@code look}. */
    static int pixelsWithLook(MinecraftClient client, int look) {
        Framebuffer screen = client.getFramebuffer();
        int width = screen.textureWidth;
        int height = screen.textureHeight;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height);
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screen.fbo);
            // Un octet par pixel, lignes sans remplissage.
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_STENCIL_INDEX, GL11.GL_UNSIGNED_BYTE, pixels);
            int count = 0;
            for (int i = 0; i < width * height; i++) {
                if (StencilLookMask.look(pixels.get(i) & 0xFF) == look) {
                    count++;
                }
            }
            return count;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            screen.beginWrite(false);
            MemoryUtil.memFree(pixels);
        }
    }
}
