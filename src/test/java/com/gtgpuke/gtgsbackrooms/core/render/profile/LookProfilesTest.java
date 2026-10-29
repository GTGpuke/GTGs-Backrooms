package com.gtgpuke.gtgsbackrooms.core.render.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

@DisplayName("Registre des profils de look")
class LookProfilesTest {

    private static final Identifier LEVEL_0 = Identifier.of("gtgs_backrooms", "backrooms_level_0");
    private static final Identifier LEVEL_1 = Identifier.of("gtgs_backrooms", "backrooms_level_1");

    @AfterEach
    void clear() {
        LookProfiles.clear();
    }

    @Test
    @DisplayName("Chaque dimension retrouve son profil, par cle de monde ou par identifiant de look ; les autres sont vanilla")
    void profilesAreFoundByDimensionAndId() {
        LookProfile level0 = profile(LEVEL_0, 1);
        LookProfile level1 = profile(LEVEL_1, 2);
        LookProfiles.load(List.of(level0, level1, LookProfile.VANILLA));
        assertEquals(2, LookProfiles.size(), "Le profil vanilla ne compte pas.");
        assertSame(level0, LookProfiles.of(LEVEL_0), "Par dimension.");
        assertSame(level1, LookProfiles.of(RegistryKey.of(RegistryKeys.WORLD, LEVEL_1)), "Par cle de monde.");
        assertSame(level1, LookProfiles.byId(2), "Par identifiant.");
        assertSame(LookProfile.VANILLA, LookProfiles.of(Identifier.of("minecraft", "overworld")), "L'Overworld n'a jamais de profil.");
        assertSame(LookProfile.VANILLA, LookProfiles.of((Identifier) null), "Sans dimension.");
        assertSame(LookProfile.VANILLA, LookProfiles.byId(0), "Identifiant 0.");
        assertSame(LookProfile.VANILLA, LookProfiles.byId(3), "Identifiant libre.");
        assertSame(LookProfile.VANILLA, LookProfiles.byId(-1), "Hors bornes.");
        assertSame(LookProfile.VANILLA, LookProfiles.byId(8), "Hors bornes.");
    }

    @Test
    @DisplayName("Deux profils pour une dimension ou un identifiant : le premier charge gagne")
    void duplicatesKeepTheFirst() {
        LookProfile first = profile(LEVEL_0, 1);
        LookProfile sameDimension = profile(LEVEL_0, 2);
        LookProfile sameId = profile(LEVEL_1, 1);
        LookProfiles.load(List.of(first, sameDimension, sameId));
        assertEquals(1, LookProfiles.size(), "Un seul profil garde.");
        assertSame(first, LookProfiles.of(LEVEL_0), "Le premier.");
        assertSame(LookProfile.VANILLA, LookProfiles.of(LEVEL_1), "L'autre dimension reste vanilla.");
        LookProfiles.load(List.of());
        assertEquals(0, LookProfiles.size(), "Un rechargement remplace tout.");
        assertSame(LookProfile.VANILLA, LookProfiles.of(LEVEL_0), "Plus de profil.");
    }

    static LookProfile profile(Identifier dimension, int id) {
        return new LookProfile(dimension, id, 1.0f, 1.0f, 1.0f, 0.5f, 0.1f, 0.0f, 0.0f, LookProfile.Lightmap.VANILLA, LookProfile.Fog.VANILLA,
                LookProfile.Post.NONE, LookProfile.Neons.NONE);
    }
}
