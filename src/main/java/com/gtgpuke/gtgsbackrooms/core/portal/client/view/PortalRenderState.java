package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Etat partage du rendu des portails : garde de reentrance et numero d'image, camera de la vue en cours pour le ciel,
 * et disponibilite du rendu distant.
 *
 * <p>Si le stencil ne peut pas etre attache ou si un rendu distant leve une exception, le rendu
 * distant est coupe pour la session et les portails s'affichent comme des cadres sombres.
 */
public final class PortalRenderState {

    private static final Camera REMOTE_CAMERA = new Camera();
    private static final Camera NESTED_CAMERA = new Camera();
    private static boolean rendering;
    private static int frameIndex;
    private static @Nullable Vec3d viewCameraPos;
    private static boolean stencilAvailable = true;
    private static boolean remoteRenderingDisabled;
    private static boolean blockOutlineAllowed;
    private static boolean reducedView;
    private static boolean viewsSuppressed;

    private PortalRenderState() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si le rendu des portails peut avoir lieu dans cette image. */
    public static boolean canRenderPortals() {
        return !rendering && remoteRenderingAvailable();
    }

    /**
     * {@code true} tant que le stencil est disponible, qu'aucun rendu distant n'a echoue et que la cohabitation avec un
     * pack de shaders ne coupe pas les vues de cette image.
     */
    public static boolean remoteRenderingAvailable() {
        return stencilAvailable && !remoteRenderingDisabled && !viewsSuppressed;
    }

    /**
     * Point d'acces : les vues de cette image sont coupees ou non par la cohabitation avec un pack de shaders (option
     * {@code gtgsbackrooms.portal.shaderCohabitation} a {@code off}). Coupees, les cadres sont sombres, comme sans
     * stencil, et rien ne se fait a travers eux. Pose a chaque image, au releve du pack ; le stencil des framebuffers,
     * lui, reste attache.
     */
    public static void setViewsSuppressed(boolean suppressed) {
        viewsSuppressed = suppressed;
    }

    /** {@code true} tant que le pilote tient le stencil du framebuffer : le post-traitement masque en depend aussi. */
    public static boolean stencilAvailable() {
        return stencilAvailable;
    }

    /** Debut du rendu des portails d'une image : garde posee, nouveau numero d'image. */
    public static void beginFrame() {
        rendering = true;
        frameIndex++;
    }

    /** {@code true} pendant le rendu des portails : le monde et le renderer de {@code MinecraftClient} sont alors substitues. */
    public static boolean isRendering() {
        return rendering;
    }

    /** Numero de l'image en cours : une vue s'en sert pour savoir si son renderer a deja prepare son terrain. */
    public static int frameIndex() {
        return frameIndex;
    }

    /** {@code true} si vanilla dessine le contour du bloc vise dans cette image (interface visible, pas spectateur). */
    public static boolean blockOutlineAllowed() {
        return blockOutlineAllowed;
    }

    /** Decision de vanilla pour le contour du bloc vise, relevee par le rendu principal. */
    public static void setBlockOutlineAllowed(boolean allowed) {
        blockOutlineAllowed = allowed;
    }

    /** Fin du rendu des portails de l'image : garde levee, camera et palier de vue oublies. */
    public static void endFrame() {
        rendering = false;
        viewCameraPos = null;
        reducedView = false;
    }

    /** {@code true} pendant le dessin d'une vue allegee (palier de detail, {@link ViewDetail}) : ni meteo ni particules. */
    public static boolean reducedView() {
        return reducedView;
    }

    /** Pose ou leve le palier allege de la vue en cours de dessin. */
    public static void setReducedView(boolean reduced) {
        reducedView = reduced;
    }

    /** Camera reutilisee pour les vues distantes (position et rotation reecrites a chaque portail). */
    public static Camera remoteCamera() {
        return REMOTE_CAMERA;
    }

    /** Camera des vues imbriquees (un portail vu a travers un autre), reecrite a chaque portail imbrique. */
    public static Camera nestedCamera() {
        return NESTED_CAMERA;
    }

    /** Camera de la vue en cours de rendu, ou {@code null} : le ciel vanilla s'y rapporte au lieu du joueur. */
    public static @Nullable Vec3d viewCameraPos() {
        return viewCameraPos;
    }

    /** Pose ({@code null} : retire) la camera de la vue en cours de rendu. */
    public static void setViewCameraPos(@Nullable Vec3d position) {
        viewCameraPos = position;
    }

    /** Appele par le mixin de framebuffer quand l'attachement depth-stencil est refuse par le pilote. */
    public static void markStencilUnavailable(int framebufferStatus) {
        if (stencilAvailable) {
            stencilAvailable = false;
            ModConstants.LOGGER.warn("[Portal] Stencil indisponible (statut framebuffer {}), les portails seront des cadres sombres.",
                    framebufferStatus);
        }
    }

    /** Coupe le rendu distant pour la session apres une exception, en la journalisant une seule fois. */
    public static void disableRemoteRendering(String reason, @Nullable Throwable cause) {
        if (!remoteRenderingDisabled) {
            remoteRenderingDisabled = true;
            // Sans cause, SLF4J ignore l'argument en trop : un seul appel pour les deux cas.
            ModConstants.LOGGER.error("[Portal] Rendu distant coupe pour la session : {}.", reason, cause);
        }
    }
}
