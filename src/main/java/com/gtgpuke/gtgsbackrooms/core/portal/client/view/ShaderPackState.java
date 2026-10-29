package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;

/**
 * Un pack de shaders rend-il le monde du joueur dans cette image ? Un tel pack dessine le monde dans son propre pipeline
 * (geometrie dans ses tampons, eclairage differe, passe finale ecrite dans la texture de couleur de l'ecran hors de tout
 * test de stencil) : une vue de portail dessinee au milieu du rendu vanilla n'y serait qu'une surface de plus. Les vues
 * passent alors par {@link ShaderPackPortalRenderer}, apres l'image du monde du joueur.
 *
 * <p>Le releve ne nomme aucun mod : au milieu du rendu du monde du joueur, juste avant la couche translucide (la ou les
 * portails se dessinent), un pack a remplace les programmes que vanilla donne aux entites et aux translucides par les
 * siens, d'une classe a lui ; sans pack, ce sont ceux du jeu de base. Plus tot dans l'image, en tete du rendu, ils ne
 * sont pas encore remplaces (verifie au banc, pack coupe puis rallume en cours de partie). Le releve vaut pour la suite
 * de l'image ; ce qui se decide avant lui (debut du look, entites dessinees avant les portails) lit celui de l'image
 * precedente.
 */
public final class ShaderPackState {

    /**
     * Les vues de portail se dessinent sous un pack : option {@code gtgsbackrooms.portal.shaderCohabitation} a {@code auto}
     * (le defaut). A {@code off}, sous un pack, les cadres sont sombres.
     */
    private static final boolean VIEWS_UNDER_PACK = !"off".equals(ModConfig.PORTAL_SHADER_COHABITATION);
    private static boolean inUse;

    private ShaderPackState() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Releve, au milieu du rendu du monde du joueur, avant les portails : un pack rend-il le monde ? Pose aussi la
     * cohabitation des vues pour l'image ({@link PortalRenderState#setViewsSuppressed}).
     */
    public static void refresh() {
        inUse = foreign(GameRenderer.getRenderTypeEntitySolidProgram()) || foreign(GameRenderer.getRenderTypeTranslucentProgram());
        PortalRenderState.setViewsSuppressed(inUse && !VIEWS_UNDER_PACK);
    }

    /** {@code true} si un pack de shaders rendait le monde au dernier releve. */
    public static boolean inUse() {
        return inUse;
    }

    /** Un programme qui n'est pas du jeu de base : celui d'un pipeline qui a pris sa place. */
    private static boolean foreign(@Nullable ShaderProgram program) {
        return program != null && program.getClass() != ShaderProgram.class;
    }
}
