package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

@DisplayName("Les clefs des mondes distants : une dimension, ou l'ailleurs d'une dimension dans le monde du joueur")
class RemoteKeysTest {

    @Test
    @DisplayName("L'ailleurs d'une dimension la nomme, rend sa dimension, et reste le meme")
    void elsewhereNamesItsDimension() {
        RegistryKey<World> key = RemoteKeys.elsewhere(World.OVERWORLD);
        assertEquals(Identifier.of("gtgs_backrooms", "elsewhere/minecraft/overworld"), key.getValue(), "Un identifiant du mod qui nomme la dimension.");
        assertTrue(RemoteKeys.isElsewhere(key), "C'est un ailleurs.");
        assertSame(World.OVERWORLD, RemoteKeys.dimension(key), "Il rend sa dimension.");
        assertSame(key, RemoteKeys.elsewhere(World.OVERWORLD), "La meme clef a chaque demande.");
    }

    @Test
    @DisplayName("Une dimension est sa propre dimension, meme une dimension du mod")
    void dimensionIsItself() {
        RegistryKey<World> levelZero = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("gtgs_backrooms", "level_zero"));
        assertFalse(RemoteKeys.isElsewhere(levelZero), "Une dimension du mod n'est pas un ailleurs.");
        assertFalse(RemoteKeys.isElsewhere(World.NETHER), "Une dimension vanilla non plus.");
        assertSame(levelZero, RemoteKeys.dimension(levelZero), "Elle est sa propre dimension.");
    }

    @Test
    @DisplayName("Un ailleurs recu avant d'etre cree se lit : sa dimension, puis la meme clef quand on la redemande")
    void elsewhereReadBeforeCreated() {
        RegistryKey<World> received = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("gtgs_backrooms", "elsewhere/minecraft/the_end"));
        assertSame(World.END, RemoteKeys.dimension(received), "La dimension se lit dans l'identifiant.");
        assertSame(received, RemoteKeys.elsewhere(World.END), "L'ailleurs de cette dimension est la clef recue.");
        assertSame(World.END, RemoteKeys.dimension(received), "Puis se retrouve sans relire l'identifiant.");
    }
}
