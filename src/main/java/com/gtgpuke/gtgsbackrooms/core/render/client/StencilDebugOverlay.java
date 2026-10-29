package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;

/**
 * Mode de debogage {@code gtgsbackrooms.look.debugStencil} : en fin de rendu du monde, l'ecran ne montre plus que le
 * stencil, une couleur par niveau d'imbrication (gris, bleu, vert, rouge) eclaircie et jaunie par l'identifiant du look.
 * Les tests de niveau et de look se lisent ainsi a l'oeil et sous un outil de capture d'images.
 */
public final class StencilDebugOverlay {

    private static final int LEVELS = StencilLookMask.LEVEL_MASK + 1;
    private static final float[][] LEVEL_COLORS = {
            {0.25f, 0.25f, 0.25f},
            {0.1f, 0.2f, 0.9f},
            {0.1f, 0.8f, 0.2f},
            {0.9f, 0.15f, 0.1f},
    };

    private StencilDebugOverlay() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Remplace l'image par le stencil, en mode de debogage seulement. */
    public static void render(MinecraftClient client) {
        if (!ModConfig.DEBUG_LOOK_STENCIL) {
            return;
        }
        if (GlStateCheck.ENABLED) {
            GlStateCheck.before(GlStateCheck.STENCIL_DEBUG);
        }
        client.getFramebuffer().beginWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(0);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        try {
            for (int level = 0; level < LEVELS; level++) {
                for (int look = 0; look <= StencilLookMask.MAX_LOOK; look++) {
                    GL11.glStencilFunc(GL11.GL_EQUAL, StencilLookMask.value(level, look), StencilLookMask.MARK_MASK);
                    float lift = look / (float) StencilLookMask.MAX_LOOK;
                    float[] base = LEVEL_COLORS[level];
                    FullscreenQuad.draw(base[0] + (1.0f - base[0]) * lift, base[1] + (1.0f - base[1]) * lift, base[2] * (1.0f - lift));
                }
            }
        } finally {
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0, StencilLookMask.FULL_MASK);
            GL11.glStencilMask(StencilLookMask.FULL_MASK);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            // Le test de profondeur reste coupe, comme vanilla le laisse entre deux dessins.
            RenderSystem.depthMask(true);
        }
        if (GlStateCheck.ENABLED) {
            GlStateCheck.after(GlStateCheck.STENCIL_DEBUG);
        }
    }
}
