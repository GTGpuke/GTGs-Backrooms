package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.BlockPos;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

@DisplayName("Occlusion d'un son par les blocs entre sa source et l'auditeur")
class OcclusionTraceTest {

    private static final float EPSILON = 1.0e-6f;
    private static final AudioProfile.Occlusion RULE = new AudioProfile.Occlusion(0.3f, 0.1f, 0.85f);

    /** Un monde de test : des blocs pleins et des blocs partiels poses a la main, de l'air partout ailleurs. */
    private static final class World implements OcclusionTrace.Solidity {

        private final Set<Long> full = new HashSet<>();
        private final Set<Long> partial = new HashSet<>();
        private int queries;

        World full(int x, int y, int z) {
            this.full.add(BlockPos.asLong(x, y, z));
            return this;
        }

        World partial(int x, int y, int z) {
            this.partial.add(BlockPos.asLong(x, y, z));
            return this;
        }

        @Override
        public int at(int x, int y, int z) {
            this.queries++;
            long key = BlockPos.asLong(x, y, z);
            return this.full.contains(key) ? OcclusionTrace.FULL : this.partial.contains(key) ? OcclusionTrace.PARTIAL : OcclusionTrace.AIR;
        }
    }

    @Test
    @DisplayName("Le long d'un axe, chaque bloc plein ou partiel traverse retire sa part ; la source et l'auditeur ne comptent pas")
    void blocksAlongAnAxisAddUp() {
        World world = new World().full(2, 0, 0).full(3, 0, 0).partial(4, 0, 0).full(0, 0, 0).full(6, 0, 0);
        assertEquals(0.7f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 6.5, 0.5, 0.5, world, RULE), EPSILON,
                "Deux murs et un bloc partiel, sans le bloc de la source ni celui de l'auditeur.");
        assertEquals(0.7f, OcclusionTrace.occlusion(6.5, 0.5, 0.5, 0.5, 0.5, 0.5, world, RULE), EPSILON, "Pareil dans l'autre sens.");
        assertEquals(0.0f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 1.5, 0.5, 0.5, world, RULE), EPSILON, "Voisins directs : rien entre eux.");
        assertEquals(0.0f, OcclusionTrace.occlusion(0.2, 0.5, 0.5, 0.8, 0.5, 0.5, world, RULE), EPSILON, "Meme bloc : rien.");
    }

    @Test
    @DisplayName("Le plafond du profil borne l'occlusion, et sans regle rien n'est calcule")
    void occlusionIsCapped() {
        World world = new World();
        for (int x = 1; x < 10; x++) {
            world.full(x, 0, 0);
        }
        assertEquals(0.85f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 10.5, 0.5, 0.5, world, RULE), EPSILON, "Neuf murs : le plafond.");
        int queries = world.queries;
        assertEquals(3, queries, "Le parcours s'arrete au plafond atteint, au troisieme mur.");
        assertEquals(0.0f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 10.5, 0.5, 0.5, world, AudioProfile.Occlusion.NONE), EPSILON, "Sans regle, rien.");
        assertEquals(queries, world.queries, "Et aucun bloc lu.");
    }

    @Test
    @DisplayName("Un rayon en diagonale traverse les blocs dans l'ordre de leurs faces, en montant comme en descendant")
    void diagonalRaysVisitCrossedBlocks() {
        World world = new World().full(1, 1, 0).full(2, 2, 0).full(1, 0, 0);
        // De (0.5, 0.5) a (2.5, 2.5) : les cases (1, 1) et (2, 2) sont traversees par la diagonale, (1, 0) est a cote.
        assertEquals(0.3f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 2.5, 2.5, 0.5, world, RULE), EPSILON,
                "La case (1, 1) sur le chemin, la case (2, 2) etant celle de l'auditeur.");
        World vertical = new World().full(0, 1, 0).full(0, 2, 0).partial(0, 0, 0);
        assertEquals(0.7f, OcclusionTrace.occlusion(0.5, 3.5, 0.5, 0.5, -0.5, 0.5, vertical, RULE), EPSILON, "Vers le bas : deux murs et le bloc partiel.");
        assertEquals(0.7f, OcclusionTrace.occlusion(0.5, -0.5, 0.5, 0.5, 3.5, 0.5, vertical, RULE), EPSILON, "Vers le haut : pareil.");
        World depth = new World().full(0, 0, -1).full(0, 0, -2);
        assertEquals(0.6f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 0.5, 0.5, -2.5, depth, RULE), EPSILON, "Le long de l'axe z, vers les negatifs.");
        // De (0.5, 0.5, 0.5) a (2.5, 0.5, 4.5), z deux fois plus vite que x : les cases (0, 1), (1, 1), (1, 2), (1, 3), (2, 3) en x et z.
        World slanted = new World().full(0, 0, 1).full(1, 0, 3).full(0, 0, 2).full(2, 0, 1);
        assertEquals(0.6f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 2.5, 0.5, 4.5, slanted, RULE), EPSILON,
                "Les faces en z franchies avant celles en x ; (0, 2) et (2, 1) sont a cote du chemin.");
    }

    @Test
    @DisplayName("Un trajet trop long s'arrete apres le nombre de pas maximal")
    void longRaysStop() {
        World world = new World().full(100, 0, 0);
        assertEquals(0.0f, OcclusionTrace.occlusion(0.5, 0.5, 0.5, 120.5, 0.5, 0.5, world, RULE), EPSILON, "Le mur a cent blocs n'est jamais atteint.");
        assertEquals(OcclusionTrace.MAX_STEPS, world.queries, "Autant de blocs lus que de pas permis.");
    }

    @Test
    @DisplayName("Le filtre passe-bas coupe d'abord les aigus")
    void lowPassFollowsOcclusion() {
        assertEquals(1.0f, OcclusionTrace.lowPassGain(0.0f), EPSILON, "Rien a couper.");
        assertEquals(1.0f, OcclusionTrace.lowPassGainHf(0.0f), EPSILON, "Rien a couper non plus.");
        assertEquals(0.5f, OcclusionTrace.lowPassGain(0.5f), EPSILON, "La moitie du son.");
        assertEquals(0.25f, OcclusionTrace.lowPassGainHf(0.5f), EPSILON, "Le quart des aigus.");
    }

    @Test
    @DisplayName("Le milieu s'ajoute a l'occlusion, et sans filtre le volume rend la perte moyenne")
    void mediumAndFallbackGain() {
        assertEquals(0.5f, OcclusionTrace.filterGain(0.5f, 1.0f), EPSILON, "Hors de l'eau : l'occlusion seule.");
        assertEquals(0.4f, OcclusionTrace.filterGain(0.5f, 0.8f), EPSILON, "Sous l'eau : le milieu en plus.");
        assertEquals(0.25f, OcclusionTrace.filterGainHf(0.5f, 1.0f), EPSILON, "Aigus hors de l'eau.");
        assertEquals(0.05f, OcclusionTrace.filterGainHf(0.5f, 0.2f), EPSILON, "Aigus sous l'eau.");
        assertEquals(1.0f, OcclusionTrace.fallbackGain(1.0f, 1.0f), EPSILON, "Rien a rendre : volume entier.");
        assertEquals(0.375f, OcclusionTrace.fallbackGain(0.5f, 0.25f), EPSILON, "Moyenne des graves et des aigus gardes.");
    }

    @Test
    @DisplayName("Deux segments d'un trajet par un cadre se combinent, sous le plafond")
    void segmentsCombineUnderTheCap() {
        assertEquals(0.0f, OcclusionTrace.combine(0.0f, 0.0f, RULE), EPSILON, "Rien d'un cote ni de l'autre.");
        assertEquals(0.5f, OcclusionTrace.combine(0.5f, 0.0f, RULE), EPSILON, "Un seul segment occlus : son occlusion.");
        assertEquals(0.5f, OcclusionTrace.combine(0.0f, 0.5f, RULE), EPSILON, "Dans l'autre ordre aussi.");
        assertEquals(0.75f, OcclusionTrace.combine(0.5f, 0.5f, RULE), EPSILON, "La moitie de la moitie passe encore.");
        assertEquals(RULE.max(), OcclusionTrace.combine(0.8f, 0.8f, RULE), EPSILON, "Jamais au-dessus du plafond du profil.");
    }
}
