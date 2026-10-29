package com.gtgpuke.gtgsbackrooms.core.render.post;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Disposition du stencil : niveau et look")
class StencilLookMaskTest {

    @Test
    @DisplayName("Niveau dans les deux bits bas, look dans les trois suivants, chacun relu tel quel")
    void valueCarriesLevelAndLook() {
        for (int level = 0; level <= 3; level++) {
            for (int look = 0; look <= StencilLookMask.MAX_LOOK; look++) {
                int value = StencilLookMask.value(level, look);
                assertEquals(level, StencilLookMask.level(value), "Niveau relu.");
                assertEquals(look, StencilLookMask.look(value), "Look relu.");
                assertEquals(0, value & ~StencilLookMask.MARK_MASK, "Rien hors des bits de marquage.");
            }
        }
        assertEquals(7, StencilLookMask.MAX_LOOK, "Sept looks au plus.");
        assertEquals(0x1F, StencilLookMask.MARK_MASK, "Niveau et look.");
        assertEquals(0x0D, StencilLookMask.value(1, 3), "Niveau 1, look 3 : 0b01101.");
        assertEquals(0x05, StencilLookMask.value(5, 9), "Niveau et look hors bornes tronques a leurs bits.");
        assertEquals(3, StencilLookMask.level(0xFF), "Niveau d'une valeur pleine.");
        assertEquals(7, StencilLookMask.look(0xFF), "Look d'une valeur pleine.");
    }
}
