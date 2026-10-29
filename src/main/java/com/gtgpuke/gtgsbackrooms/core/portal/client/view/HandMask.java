package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;

/**
 * Main du joueur gardee devant les vues de portail sous un pack de shaders. Le pack dessine la main avec la profondeur
 * de sa vraie distance : la camera a moins d'un demi-bloc du plan d'un cadre, ce plan passe devant elle au test de
 * profondeur, et la vue, dessinee apres l'image ({@link ShaderPackPortalRenderer}), la recouvrait. Pendant le dessin de la
 * main, chaque pixel qu'elle couvre prend le bit {@link #BIT} du stencil ; aucun cadre ne se marque la, et l'image
 * gardee du joueur, main comprise, y est remise. Sans pack, vanilla dessine la main apres le monde, par-dessus tout.
 */
public final class HandMask {

    /** Bit du stencil de la main, libre des valeurs de cadre (32 au plus) et du bit des cadres imbriques. */
    public static final int BIT = 0x40;
    private static final int FULL_MASK = 0xFF;

    private static boolean active;

    private HandMask() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Debut du dessin de la main du joueur : sous un pack, chaque pixel qu'elle couvre prend le bit de la main. */
    public static void begin() {
        if (active || !ModConfig.ENABLE_PORTAL_FEATURE || !ShaderPackState.inUse() || PortalRenderState.isRendering()) {
            return;
        }
        active = true;
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS, BIT, BIT);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
    }

    /** Fin du dessin de la main : le stencil ne change plus. */
    public static void end() {
        if (!active) {
            return;
        }
        active = false;
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, FULL_MASK);
        GL11.glStencilMask(FULL_MASK);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }

    /** Fin de l'image, apres les vues : le bit de la main s'efface pour la main de l'image suivante. */
    public static void clear(MinecraftClient client) {
        client.getFramebuffer().beginWrite(false);
        GL11.glStencilMask(BIT);
        GL11.glClearStencil(0);
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        GL11.glStencilMask(FULL_MASK);
    }
}
