package com.gtgpuke.gtgsbackrooms.core.render.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

@DisplayName("Pile des looks du rendu")
class LookStateTest {

    private static final LookProfile PLAYER = profile(1);
    private static final LookProfile VIEW = profile(2);
    private static final LookProfile NESTED = profile(3);

    @AfterEach
    void clear() {
        LookState.clear();
    }

    @Test
    @DisplayName("Le profil du joueur au fond, celui de chaque vue au sommet le temps de la vue, et la version avance a chaque changement")
    void stackFollowsViews() {
        int before = LookState.version();
        LookState.beginWorld(PLAYER, 12.5f, 0.0, 0.0, 0.0, 0);
        assertSame(PLAYER, LookState.current(), "Le joueur.");
        assertEquals(12.5f, LookState.time(), "Temps de l'image.");
        assertEquals(0, LookState.depth(), "Aucune vue.");
        int atStart = LookState.version();
        assertNotEquals(before, atStart, "Nouvelle version au debut de l'image.");
        LookState.push(VIEW, 0.0, 0.0, 0.0, 0);
        assertSame(VIEW, LookState.current(), "La vue.");
        assertEquals(1, LookState.depth(), "Une vue.");
        LookState.push(NESTED, 0.0, 0.0, 0.0, 0);
        assertSame(NESTED, LookState.current(), "La vue imbriquee.");
        LookState.pop();
        assertSame(VIEW, LookState.current(), "Retour a la vue.");
        LookState.pop();
        assertSame(PLAYER, LookState.current(), "Retour au joueur.");
        assertEquals(atStart + 4, LookState.version(), "Quatre changements de sommet.");
        assertEquals(0b1110, LookState.usedLooks(), "Les trois looks vus dans l'image.");
        LookState.setNeonCount(3);
        LookState.endWorld();
        assertSame(LookProfile.VANILLA, LookState.current(), "Vanilla pour l'interface.");
        assertEquals(atStart + 6, LookState.version(), "Et un de plus.");
        assertEquals(0, LookState.usedLooks(), "Plus aucun look vu.");
        assertEquals(0b1110, LookState.lastFrameLooks(), "La derniere image terminee les garde, pour le diagnostic.");
        assertEquals(3, LookState.lastFrameNeons(), "Et ses neons.");
        LookState.beginWorld(LookProfile.VANILLA, 0.0f, 0.0, 0.0, 0.0, 0);
        assertEquals(0b1, LookState.usedLooks(), "Le monde du joueur vanilla : le bit 0 seul.");
        LookState.push(NESTED, 0.0, 0.0, 0.0, 0);
        assertEquals(0b1001, LookState.usedLooks(), "Et la vue.");
        int beforeNeons = LookState.version();
        LookState.setNeonCount(5);
        assertEquals(5, LookState.neonCount(), "Neons de la vue.");
        assertEquals(beforeNeons + 1, LookState.version(), "Un compte qui change avance la version.");
        LookState.setNeonCount(5);
        assertEquals(beforeNeons + 1, LookState.version(), "Le meme compte ne change rien.");
        LookState.clear();
        assertEquals(0, LookState.neonCount(), "Aucun neon hors du rendu.");
        assertTrue(LookState.shadersCarryLook(), "Par defaut, les shaders patches portent l'etage 1.");
        int beforeCarry = LookState.version();
        LookState.setShadersCarryLook(false);
        assertFalse(LookState.shadersCarryLook(), "Un mod dessine le terrain : le post s'en charge.");
        assertEquals(beforeCarry + 1, LookState.version(), "Les shaders relisent leurs uniformes.");
        LookState.setShadersCarryLook(false);
        assertEquals(beforeCarry + 1, LookState.version(), "Sans changement, rien.");
        LookState.clear();
        assertTrue(LookState.shadersCarryLook(), "Remis a zero avec le reste.");
    }

    @Test
    @DisplayName("Une pile laissee pleine est remise a zero au debut et a la fin du rendu ; les bornes sont tenues")
    void unbalancedStacksAreReset() {
        LookState.beginWorld(PLAYER, 0.0f, 0.0, 0.0, 0.0, 0);
        LookState.push(VIEW, 0.0, 0.0, 0.0, 0);
        LookState.endWorld();
        assertEquals(0, LookState.depth(), "Videe a la fin.");
        LookState.push(VIEW, 0.0, 0.0, 0.0, 0);
        LookState.beginWorld(PLAYER, 1.0f, 0.0, 0.0, 0.0, 0);
        assertEquals(0, LookState.depth(), "Videe au debut.");
        assertSame(PLAYER, LookState.current(), "Le joueur.");
        assertThrows(IllegalStateException.class, LookState::pop, "Rien a retirer.");
        LookState.push(VIEW, 0.0, 0.0, 0.0, 0);
        LookState.push(NESTED, 0.0, 0.0, 0.0, 0);
        LookState.push(VIEW, 0.0, 0.0, 0.0, 0);
        assertThrows(IllegalStateException.class, () -> LookState.push(NESTED, 0.0, 0.0, 0.0, 0), "Plus de trois vues.");
        assertEquals(3, LookState.depth(), "Trois vues.");
    }

    @Test
    @DisplayName("Chaque vue porte sa camera (bloc entier et reste, positif) et le tick de son monde")
    void viewsCarryTheirCameraAndTick() {
        LookState.beginWorld(PLAYER, 0.0f, -10.25, 64.5, 2_000_000.75, 123);
        assertEquals(-11, LookState.cameraBlockX(), "Bloc entier vers le bas, meme sous zero.");
        assertEquals(0.75f, LookState.cameraFractX(), 1.0e-6f, "Reste positif.");
        assertEquals(64, LookState.cameraBlockY(), "Bloc y.");
        assertEquals(0.5f, LookState.cameraFractY(), 1.0e-6f, "Reste y.");
        assertEquals(2_000_000, LookState.cameraBlockZ(), "Loin de l'origine, le bloc est exact.");
        assertEquals(0.75f, LookState.cameraFractZ(), 1.0e-6f, "Et le reste aussi.");
        assertEquals(123, LookState.tick(), "Tick du monde du joueur.");
        LookState.push(VIEW, 5.5, 6.0, 7.25, 456);
        assertEquals(5, LookState.cameraBlockX(), "Camera de la vue.");
        assertEquals(6, LookState.cameraBlockY(), "Camera de la vue, y.");
        assertEquals(0.25f, LookState.cameraFractZ(), 1.0e-6f, "Reste de la vue.");
        assertEquals(456, LookState.tick(), "Tick du monde de la vue.");
        LookState.pop();
        assertEquals(-11, LookState.cameraBlockX(), "Retour a la camera du joueur.");
        assertEquals(123, LookState.tick(), "Et a son tick.");
        LookState.clear();
        assertEquals(0, LookState.cameraBlockX(), "Remis a zero.");
        assertEquals(0, LookState.tick(), "Tick remis a zero.");
    }

    private static LookProfile profile(int id) {
        return new LookProfile(Identifier.of("m", "d" + id), id, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, LookProfile.Lightmap.VANILLA,
                LookProfile.Fog.VANILLA, LookProfile.Post.NONE, LookProfile.Neons.NONE);
    }
}
