package com.gtgpuke.gtgsbackrooms.core.render.patch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Patch du fragment lie a celui du vertex du meme programme")
class PatchedVertexStagesTest {

    @Test
    @DisplayName("Un fragment n'est patche que si le vertex du meme nom l'est, a son dernier chargement")
    void fragmentFollowsItsVertex() {
        assertFalse(PatchedVertexStages.fragmentAllowed("rendertype_jamais_charge"), "Vertex jamais charge : le fragment reste vanilla.");
        PatchedVertexStages.vertexLoaded("rendertype_solid", true);
        assertTrue(PatchedVertexStages.fragmentAllowed("rendertype_solid"), "Vertex patche : le fragment peut l'etre.");
        assertFalse(PatchedVertexStages.fragmentAllowed("rendertype_cutout"), "Un autre programme n'en profite pas.");
        PatchedVertexStages.vertexLoaded("rendertype_solid", false);
        assertFalse(PatchedVertexStages.fragmentAllowed("rendertype_solid"), "Recharge sans patch (refuse par le pilote) : le fragment reste vanilla.");
    }
}
