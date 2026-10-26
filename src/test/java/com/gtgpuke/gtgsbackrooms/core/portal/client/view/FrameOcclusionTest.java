package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.Vec3d;

@DisplayName("Preuve d'un cadre cache derriere des blocs")
class FrameOcclusionTest {

    /** Cadre de 1 x 2 dans le plan z = 0 : x de 0 a 1, y de 64 a 66. */
    private static final Vec3d CENTER = new Vec3d(0.5, 65.0, 0.0);
    private static final Vec3d AXIS_W = new Vec3d(1.0, 0.0, 0.0);
    private static final Vec3d AXIS_H = new Vec3d(0.0, 1.0, 0.0);
    /** Camera cinq blocs et demi devant le cadre, dans son axe. */
    private static final Vec3d FRONT = new Vec3d(0.5, 65.0, -5.5);

    private record Cell(int x, int y, int z) {
    }

    /** Blocs pleins et opaques poses a la main ; certains pas encore affiches (section pas reconstruite). */
    private static final class Blocks implements FrameOcclusion.Occluders {
        private final Set<Cell> cells = new HashSet<>();
        private final Set<Cell> pending = new HashSet<>();

        /** Mur perpendiculaire a Z dans la tranche {@code z}, de x -5 a 5 et de y 60 a 70. */
        Blocks wallZ(int z) {
            for (int x = -5; x <= 5; x++) {
                for (int y = 60; y <= 70; y++) {
                    this.cells.add(new Cell(x, y, z));
                }
            }
            return this;
        }

        /** Mur perpendiculaire a X dans la tranche {@code x}, de y 60 a 70 et de z -8 a 8. */
        Blocks wallX(int x) {
            for (int y = 60; y <= 70; y++) {
                for (int z = -8; z <= 8; z++) {
                    this.cells.add(new Cell(x, y, z));
                }
            }
            return this;
        }

        Blocks add(int x, int y, int z) {
            this.cells.add(new Cell(x, y, z));
            return this;
        }

        Blocks remove(int x, int y, int z) {
            this.cells.remove(new Cell(x, y, z));
            return this;
        }

        /** Bloc pose, pas encore dessine : sa section n'a pas ete reconstruite depuis. */
        Blocks pending(int x, int y, int z) {
            this.cells.add(new Cell(x, y, z));
            this.pending.add(new Cell(x, y, z));
            return this;
        }

        @Override
        public boolean opaque(int x, int y, int z) {
            return this.cells.contains(new Cell(x, y, z));
        }

        @Override
        public boolean shown(int x, int y, int z) {
            return opaque(x, y, z) && !this.pending.contains(new Cell(x, y, z));
        }
    }

    private static boolean hidden(Vec3d camera, Blocks blocks) {
        return FrameOcclusion.hidden(camera, CENTER, AXIS_W, AXIS_H, 0.5, 1.0, blocks);
    }

    @Test
    @DisplayName("Un mur plein entre la camera et le cadre le cache ; rien, ou un mur derriere le cadre, ne le cache pas")
    void aFullWallBetweenHidesTheFrame() {
        assertTrue(hidden(FRONT, new Blocks().wallZ(-3)), "Mur plein a mi-chemin.");
        assertFalse(hidden(FRONT, new Blocks()), "Rien entre la camera et le cadre.");
        assertFalse(hidden(FRONT, new Blocks().wallZ(0).wallZ(2)), "Des murs derriere le plan du cadre ne cachent rien.");
    }

    @Test
    @DisplayName("Un trou dans le cone laisse voir le cadre ; un trou hors du cone, non")
    void aHoleInsideTheConeShowsTheFrame() {
        // A z = -3, les rayons passent entre x 0,27 et 0,73 et entre y 64,5 et 65,5 : cases x 0, y 64 et 65.
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).remove(0, 65, -3)), "Une case du cone ouverte.");
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).remove(0, 64, -3)), "L'autre case du cone ouverte.");
        assertTrue(hidden(FRONT, new Blocks().wallZ(-3).remove(3, 65, -3).remove(0, 67, -3)), "Des trous hors du cone.");
        // Deux murs troues a des endroits differents : aucune tranche n'est pleine, un rayon peut passer en zigzag.
        assertFalse(hidden(FRONT, new Blocks().wallZ(-4).remove(0, 64, -4).wallZ(-2).remove(0, 65, -2)), "Deux tranches trouees.");
    }

    @Test
    @DisplayName("Un mur colle au plan du cadre, de ce cote, le cache ; la tranche du plan lui-meme ne compte pas")
    void aWallFlushWithTheFrameHidesIt() {
        assertTrue(hidden(FRONT, new Blocks().wallZ(-1)), "La tranche juste devant le plan.");
        assertFalse(hidden(FRONT, new Blocks().wallZ(0)), "La tranche qui commence au plan.");
    }

    @Test
    @DisplayName("Un bloc pose mais pas encore dessine ne cache rien ; un bloc plein devant le mur non plus, sauf hors du cone")
    void onlyBlocksAlreadyDrawnWithAFreeFrontCount() {
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).pending(0, 65, -3)), "Le trou vient d'etre bouche : la section le montre encore ouvert.");
        assertTrue(hidden(FRONT, new Blocks().wallZ(-3).pending(4, 65, -3)), "Un bloc pas encore dessine hors du cone ne change rien.");
        // Devant le mur, un bloc plein cache la face du mur ; s'il n'est pas encore dessine, le rayon traverse les deux.
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).pending(0, 65, -4)), "Bloc pose devant le mur, dans le cone.");
        assertTrue(hidden(FRONT, new Blocks().wallZ(-3).add(4, 65, -4)), "Bloc devant le mur, hors du cone.");
        // Un mur de deux d'epaisseur se prouve par sa tranche cote camera, devant laquelle tout est libre.
        assertTrue(hidden(FRONT, new Blocks().wallZ(-3).wallZ(-2)), "Mur epais.");
        assertTrue(hidden(new Vec3d(0.5, 65.0, 5.5), new Blocks().wallZ(2).wallZ(3)), "Mur epais, de l'autre cote.");
    }

    @Test
    @DisplayName("Une camera dans un bloc plein voit a travers lui : rien n'est prouve")
    void aCameraInsideABlockProvesNothing() {
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).add(0, 65, -6)), "Camera dans un bloc, mur plein devant le cadre.");
        assertFalse(hidden(FRONT, new Blocks().wallZ(-3).pending(0, 65, -6)), "Camera dans un bloc pas encore dessine : il compte deja.");
        // La tranche de la camera elle-meme n'est jamais testee : un mur qui la contient ne cache rien.
        assertFalse(hidden(new Vec3d(0.5, 65.0, -3.5), new Blocks().wallZ(-4).remove(0, 65, -4)), "Mur derriere la camera.");
    }

    @Test
    @DisplayName("Les tranches se cherchent dans les deux sens de chaque axe")
    void slabsAreFoundInBothDirectionsOfEachAxis() {
        assertTrue(hidden(new Vec3d(0.5, 65.0, 5.5), new Blocks().wallZ(2)), "Camera derriere le cadre, mur entre eux.");
        assertFalse(hidden(new Vec3d(0.5, 65.0, 5.5), new Blocks().wallZ(-2)), "Camera derriere le cadre, mur de l'autre cote.");
        assertTrue(hidden(new Vec3d(-6.5, 65.0, -0.5), new Blocks().wallX(-3)), "Camera a l'ouest, mur perpendiculaire a X.");
        assertTrue(hidden(new Vec3d(7.5, 65.0, -0.5), new Blocks().wallX(4)), "Camera a l'est, mur perpendiculaire a X.");
        assertFalse(hidden(new Vec3d(7.5, 65.0, -0.5), new Blocks().wallX(0)), "Mur qui coupe le cadre : il ne le cache pas tout entier.");
    }

    @Test
    @DisplayName("Sous la camera ou au-dessus, une dalle de blocs pleins cache un cadre")
    void aFloorOrACeilingHidesTheFrame() {
        assertTrue(hidden(new Vec3d(0.5, 72.5, -0.5), new Blocks().add(0, 68, 0).add(0, 68, -1).add(1, 68, 0).add(1, 68, -1)
                .add(-1, 68, 0).add(-1, 68, -1)), "Camera au-dessus, plafond plein entre elle et le cadre.");
        assertTrue(hidden(new Vec3d(0.5, 58.5, -0.5), new Blocks().add(0, 61, 0).add(0, 61, -1).add(1, 61, 0).add(1, 61, -1)
                .add(-1, 61, 0).add(-1, 61, -1)), "Camera en dessous, sol plein entre elle et le cadre.");
    }

    @Test
    @DisplayName("De biais, les cases qui comptent sont celles de la face du mur tournee vers la camera")
    void obliqueRaysAreJudgedAtTheFaceTowardTheCamera() {
        // Camera six blocs a l'est, cinq et demi devant : les rayons entrent dans le mur de z -3 entre x 3,5 et 4 (face
        // z = -3), et en sortent entre x 2,4 et 3 (face z = -2). Un trou en x 4 laisse passer des rayons.
        Vec3d front = new Vec3d(6.5, 65.0, -5.5);
        assertTrue(hidden(front, new Blocks().wallZ(-3)), "Mur plein, de biais.");
        assertFalse(hidden(front, new Blocks().wallZ(-3).remove(4, 65, -3)), "Trou a l'entree des rayons dans le mur.");
        assertTrue(hidden(front, new Blocks().wallZ(-3).remove(2, 65, -3)), "Trou que les rayons n'atteignent qu'apres un bloc plein.");
        // Meme chose de l'autre cote du cadre : le mur de z 2 a sa face tournee vers la camera en z = 3.
        Vec3d back = new Vec3d(6.5, 65.0, 5.5);
        assertTrue(hidden(back, new Blocks().wallZ(2)), "Mur plein, de biais, de l'autre cote.");
        assertFalse(hidden(back, new Blocks().wallZ(2).remove(4, 65, 2)), "Trou a l'entree des rayons, de l'autre cote.");
        assertTrue(hidden(back, new Blocks().wallZ(2).remove(2, 65, 2)), "Trou atteint apres un bloc plein, de l'autre cote.");
    }

    @Test
    @DisplayName("Un rayon sur l'arete commune de deux cases les demande toutes les deux")
    void aRayOnACellEdgeNeedsBothCells() {
        // Camera dans le plan x = 0 du bord du cadre : a z = -1, les rayons passent entre x 0 et 0,5, l'arete x = 0 comprise.
        Vec3d edge = new Vec3d(0.0, 65.0, -2.0);
        Blocks wall = new Blocks().wallZ(-1);
        assertTrue(hidden(edge, wall), "Le mur plein cache le cadre.");
        assertFalse(hidden(edge, wall.remove(-1, 65, -1)), "La case de l'autre cote de l'arete ouverte : le rayon y passe peut-etre.");
    }
}
