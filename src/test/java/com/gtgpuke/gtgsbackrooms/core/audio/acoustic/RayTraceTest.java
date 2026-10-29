package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Segments enregistres d'un calcul acoustique")
class RayTraceTest {

    private static final float EPSILON = 1.0e-6f;

    @Test
    @DisplayName("Les segments se lisent tels qu'ajoutes ; la capacite est tenue, a l'ajout comme a la recopie")
    void segmentsAreKeptWithinCapacity() {
        RayTrace trace = new RayTrace(2);
        trace.add(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, RayTrace.ESCAPE, 0.0f);
        trace.add(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, RayTrace.OCCLUSION, 0.4f);
        trace.add(9.0, 9.0, 9.0, 9.0, 9.0, 9.0, RayTrace.LOST, 0.0f);
        assertEquals(2, trace.count(), "Le troisieme ne tient pas.");
        for (int field = 0; field < 6; field++) {
            assertEquals(field + 1.0f, trace.coordinate(0, field), EPSILON, "Bout " + field + ".");
        }
        assertEquals(RayTrace.ESCAPE, trace.kind(0), "Sorti.");
        assertEquals(RayTrace.OCCLUSION, trace.kind(1), "Chemin.");
        assertEquals(0.4f, trace.value(1), EPSILON, "Son occlusion.");
        RayTrace shown = new RayTrace(3);
        shown.append(trace);
        shown.append(trace);
        assertEquals(3, shown.count(), "La recopie s'arrete a la capacite.");
        assertEquals(RayTrace.ESCAPE, shown.kind(2), "Le reste recopie dans l'ordre.");
        shown.clear();
        assertEquals(0, shown.count(), "Vide.");
    }
}
