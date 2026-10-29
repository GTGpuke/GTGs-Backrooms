package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.Identifier;

@DisplayName("Formes de salles tirees de la generation")
class GeneratedAcousticsTest {

    private static final Identifier LEVEL_0 = Identifier.of("gtgs_backrooms", "backrooms_level_0");

    @AfterEach
    void clear() {
        GeneratedAcoustics.clear();
    }

    @Test
    @DisplayName("Une forme se retrouve par sa dimension ; sans forme, ou apres la fin de partie, les rayons s'en chargent")
    void shapesAreKeptByDimension() {
        GeneratedAcoustics.Shape shape = new GeneratedAcoustics.Shape() {
            @Override
            public AcousticResult estimate(int x, int y, int z) {
                return null;
            }

            @Override
            public boolean matches(SolidityWindow window, int x, int y, int z) {
                return true;
            }
        };
        assertNull(GeneratedAcoustics.of(LEVEL_0), "Pas encore donnee par le serveur.");
        GeneratedAcoustics.register(LEVEL_0, shape);
        assertSame(shape, GeneratedAcoustics.of(LEVEL_0), "Donnee.");
        assertNull(GeneratedAcoustics.of(Identifier.of("minecraft", "overworld")), "Une autre dimension n'en a pas.");
        assertNull(GeneratedAcoustics.of(null), "Sans dimension, rien.");
        GeneratedAcoustics.clear();
        assertNull(GeneratedAcoustics.of(LEVEL_0), "Oubliee a la fin de la partie.");
    }
}
