package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import net.minecraft.util.math.Vec3d;

/**
 * L'etat est statique et la coupure du rendu distant est definitive pour la JVM : elle se teste en dernier dans cette
 * classe, et aucun autre test ne lit cet etat.
 */
@DisplayName("Etat de rendu des portails")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PortalRenderStateTest {

    @AfterEach
    void endFrame() {
        PortalRenderState.endFrame();
        PortalRenderState.setBlockOutlineAllowed(false);
    }

    @Test
    @Order(1)
    @DisplayName("Une image de rendu pose puis efface la garde de reentrance, numerote l'image et porte les substitutions")
    void frameLifecycle() {
        assertTrue(PortalRenderState.canRenderPortals(), "Rien ne s'oppose au rendu avant l'image.");
        assertFalse(PortalRenderState.isRendering(), "Hors rendu de portail.");
        int frame = PortalRenderState.frameIndex();
        PortalRenderState.beginFrame();
        assertFalse(PortalRenderState.canRenderPortals(), "Pas de rendu de portail dans un rendu de portail.");
        assertTrue(PortalRenderState.isRendering(), "En rendu de portail.");
        assertEquals(frame + 1, PortalRenderState.frameIndex(), "Chaque image a son numero.");
        Vec3d view = new Vec3d(1.0, 2.0, 3.0);
        PortalRenderState.setViewCameraPos(view);
        PortalRenderState.setBlockOutlineAllowed(true);
        assertFalse(PortalRenderState.reducedView(), "Vue en pleine qualite tant que rien n'est pose.");
        PortalRenderState.setReducedView(true);
        assertTrue(PortalRenderState.reducedView(), "Vue allegee en cours de dessin.");
        assertSame(view, PortalRenderState.viewCameraPos(), "Camera de la vue pour le ciel.");
        assertTrue(PortalRenderState.blockOutlineAllowed(), "Contour du bloc vise autorise.");
        PortalRenderState.endFrame();
        assertTrue(PortalRenderState.canRenderPortals(), "La garde est levee en fin d'image.");
        assertFalse(PortalRenderState.isRendering(), "Plus en rendu de portail.");
        assertNull(PortalRenderState.viewCameraPos(), "Plus de camera de vue.");
        assertFalse(PortalRenderState.reducedView(), "Plus de vue allegee apres l'image.");
        assertTrue(PortalRenderState.blockOutlineAllowed(), "Le contour reste decide par le rendu principal.");
    }

    @Test
    @Order(2)
    @DisplayName("Les cameras des vues distantes et imbriquees sont reutilisees et distinctes")
    void camerasAreShared() {
        assertSame(PortalRenderState.remoteCamera(), PortalRenderState.remoteCamera(), "Une camera distante.");
        assertSame(PortalRenderState.nestedCamera(), PortalRenderState.nestedCamera(), "Une camera imbriquee.");
        assertNotSame(PortalRenderState.remoteCamera(), PortalRenderState.nestedCamera(), "Deux cameras differentes.");
    }

    @Test
    @Order(3)
    @DisplayName("Vues coupees par la cohabitation avec un pack : cadres sombres le temps de l'image, stencil garde")
    void viewsSuppressedUnderAShaderPack() {
        assertTrue(PortalRenderState.remoteRenderingAvailable(), "Disponible au depart.");
        PortalRenderState.setViewsSuppressed(true);
        assertFalse(PortalRenderState.remoteRenderingAvailable(), "Vues coupees.");
        assertFalse(PortalRenderState.canRenderPortals(), "Aucun rendu de portail.");
        assertTrue(PortalRenderState.stencilAvailable(), "Le stencil reste tenu.");
        PortalRenderState.setViewsSuppressed(false);
        assertTrue(PortalRenderState.remoteRenderingAvailable(), "Pack coupe ou option rendue : les vues reviennent.");
    }

    @Test
    @Order(4)
    @DisplayName("Stencil refuse ou rendu en echec : le rendu distant est coupe pour la session, une seule fois")
    void failuresDisableRemoteRendering() {
        assertTrue(PortalRenderState.remoteRenderingAvailable(), "Disponible au depart.");
        assertTrue(PortalRenderState.stencilAvailable(), "Stencil tenu au depart.");
        PortalRenderState.disableRemoteRendering("Test sans cause.", null);
        assertFalse(PortalRenderState.remoteRenderingAvailable(), "Coupe apres un echec.");
        assertFalse(PortalRenderState.canRenderPortals(), "Plus de rendu de portail.");
        assertTrue(PortalRenderState.stencilAvailable(), "Le stencil, lui, reste tenu : le post-traitement masque continue.");
        PortalRenderState.disableRemoteRendering("Test avec cause.", new IllegalStateException("Cause de test."));
        PortalRenderState.markStencilUnavailable(0);
        PortalRenderState.markStencilUnavailable(0);
        assertFalse(PortalRenderState.remoteRenderingAvailable(), "Toujours coupe, sans effet de bord.");
        assertFalse(PortalRenderState.stencilAvailable(), "Stencil refuse par le pilote.");
    }
}
