package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

@DisplayName("Dimension d'emission d'un son")
class EmissionDimensionTest {

    private static final RegistryKey<World> LEVEL_0 = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("gtgs_backrooms", "backrooms_level_0"));
    private static final RegistryKey<World> OVERWORLD = World.OVERWORLD;

    @Test
    @DisplayName("Un son rejoue au cadre garde le monde de sa vraie source ; un son du joueur a celui de son monde")
    void anchoredSourceWinsOverPlayerWorld() {
        assertSame(LEVEL_0, EmissionDimension.resolve(LEVEL_0, OVERWORLD, false), "Entendu depuis l'Overworld, emis dans le Level 0.");
        assertSame(OVERWORLD, EmissionDimension.resolve(OVERWORLD, LEVEL_0, false), "Entendu depuis le Level 0, emis dans l'Overworld : vanilla.");
        assertSame(LEVEL_0, EmissionDimension.resolve(null, LEVEL_0, false), "Sans ancre, le monde du joueur au moment de la demande.");
    }

    @Test
    @DisplayName("Un son sans place dans un monde, ou sans monde connu, est vanilla")
    void unknownOrRelativeSoundsAreVanilla() {
        assertNull(EmissionDimension.resolve(LEVEL_0, LEVEL_0, true), "Interface et musique : jamais traites.");
        assertNull(EmissionDimension.resolve(null, null, false), "Hors partie : inconnu, donc vanilla.");
    }

    @Test
    @DisplayName("La reverb de la salle du joueur s'ajoute a un son entre par un cadre depuis un autre monde, a lui seul")
    void roomReverbOnlyForSoundsEnteringThroughAFrame() {
        assertSame(OVERWORLD, EmissionDimension.room(LEVEL_0, OVERWORLD), "Un son du Level 0 entendu dans l'Overworld : la salle de l'Overworld.");
        assertSame(LEVEL_0, EmissionDimension.room(OVERWORLD, LEVEL_0), "L'inverse aussi (si le Level 0 est profile, ce que decide son profil).");
        assertNull(EmissionDimension.room(LEVEL_0, LEVEL_0), "Un cadre qui ramene dans la meme dimension : pas de seconde reverb.");
        assertNull(EmissionDimension.room(null, LEVEL_0), "Un son emis dans le monde du joueur : la sienne seulement.");
        assertNull(EmissionDimension.room(LEVEL_0, null), "Hors partie : rien.");
    }
}
