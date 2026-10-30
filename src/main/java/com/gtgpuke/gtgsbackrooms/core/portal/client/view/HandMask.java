package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Main du joueur gardee de son monde devant les vues de portail, marquee dans le stencil pendant son dessin.
 *
 * <p>Sous un pack de shaders, le pack dessine la main avec la profondeur de sa vraie distance : la camera a moins d'un
 * demi-bloc du plan d'un cadre, ce plan passe devant elle au test de profondeur, et la vue, dessinee apres l'image
 * ({@link ShaderPackPortalRenderer}), la recouvrait. Chaque pixel de la main prend le bit {@link #BIT} ; aucun cadre ne se
 * marque la, et l'image gardee du joueur, main comprise, y est remise.
 *
 * <p>Sans pack, vanilla dessine la main apres le monde, par-dessus tout, mais les pixels d'un cadre derriere elle gardent
 * les bits de look de la dimension qu'il montre : le post-traitement de ce look, juste apres ({@code LookPost}), dessinait
 * la silhouette du cadre sur la main. Chaque pixel de la main prend les bits de look du monde du joueur, et seulement eux.
 */
public final class HandMask {

    /** Bit du stencil de la main, libre des valeurs de cadre (32 au plus) et du bit des cadres imbriques. */
    public static final int BIT = 0x40;
    private static final int FULL_MASK = 0xFF;

    private static boolean active;

    private HandMask() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Debut du dessin de la main du joueur : chaque pixel qu'elle couvre prend le bit de la main sous un pack, le look du
     * monde du joueur sans pack.
     */
    public static void begin() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (active || !ModConfig.ENABLE_PORTAL_FEATURE || PortalRenderState.isRendering() || client.world == null) {
            return;
        }
        active = true;
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        if (ShaderPackState.inUse()) {
            GL11.glStencilMask(BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, BIT, BIT);
        } else {
            GL11.glStencilMask(StencilLookMask.LOOK_MASK);
            GL11.glStencilFunc(GL11.GL_ALWAYS, StencilLookMask.value(0, LookProfiles.of(client.world.getRegistryKey()).id()), StencilLookMask.LOOK_MASK);
        }
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
