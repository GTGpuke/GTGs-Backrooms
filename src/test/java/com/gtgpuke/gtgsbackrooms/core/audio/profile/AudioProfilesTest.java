package com.gtgpuke.gtgsbackrooms.core.audio.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

@DisplayName("Registre des profils audio")
class AudioProfilesTest {

    private static final Identifier LEVEL_0 = Identifier.of("gtgs_backrooms", "backrooms_level_0");
    private static final Identifier LEVEL_1 = Identifier.of("gtgs_backrooms", "backrooms_level_1");

    @AfterEach
    void clear() {
        AudioProfiles.clear();
    }

    @Test
    @DisplayName("Chaque dimension retrouve son profil, par identifiant ou par cle de monde ; les autres sont vanilla")
    void profilesAreFoundByDimension() {
        AudioProfile level0 = profile(LEVEL_0);
        AudioProfile level1 = profile(LEVEL_1);
        AudioProfiles.load(List.of(level0, level1, AudioProfile.VANILLA));
        assertEquals(2, AudioProfiles.size(), "Le profil vanilla ne compte pas.");
        assertSame(level0, AudioProfiles.of(LEVEL_0), "Par dimension.");
        assertSame(level1, AudioProfiles.of(RegistryKey.of(RegistryKeys.WORLD, LEVEL_1)), "Par cle de monde.");
        assertSame(AudioProfile.VANILLA, AudioProfiles.of(Identifier.of("minecraft", "overworld")), "L'Overworld n'a jamais de profil.");
        assertSame(AudioProfile.VANILLA, AudioProfiles.of((Identifier) null), "Sans dimension.");
        assertSame(AudioProfile.VANILLA, AudioProfiles.of((RegistryKey<net.minecraft.world.World>) null), "Sans monde.");
        assertEquals(2, AudioProfiles.all().size(), "Tous les profils charges.");
        assertTrue(AudioProfiles.all().contains(level0) && AudioProfiles.all().contains(level1), "Ceux des deux dimensions.");
    }

    @Test
    @DisplayName("Deux profils pour une dimension : le premier charge gagne ; un rechargement remplace tout")
    void duplicatesKeepTheFirst() {
        AudioProfile first = profile(LEVEL_0);
        AudioProfiles.load(List.of(first, profile(LEVEL_0)));
        assertEquals(1, AudioProfiles.size(), "Un seul profil garde.");
        assertSame(first, AudioProfiles.of(LEVEL_0), "Le premier.");
        AudioProfiles.load(List.of());
        assertEquals(0, AudioProfiles.size(), "Un rechargement remplace tout.");
        assertSame(AudioProfile.VANILLA, AudioProfiles.of(LEVEL_0), "Plus de profil.");
    }

    private static AudioProfile profile(Identifier dimension) {
        return new AudioProfile(dimension, AudioProfile.Acoustics.NONE, AudioProfile.Reverb.NONE, AudioProfile.Occlusion.NONE, 0.0f,
                AudioProfile.Medium.NONE, 0.0f, AudioProfile.Ambience.NONE);
    }
}
