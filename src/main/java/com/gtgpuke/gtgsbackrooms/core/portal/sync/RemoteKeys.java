package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Les clefs des mondes distants d'un joueur. Une dimension distante a pour clef sa dimension. Un portail qui mene ailleurs dans le monde
 * du joueur, plus loin que ce que vanilla lui envoie autour de lui, a sa propre fenetre, tenue par le client dans un monde distant a
 * part : sa clef est l'ailleurs de la dimension, un identifiant du mod qui la nomme ({@code gtgs_backrooms:elsewhere/<espace>/<chemin>}).
 * La synchronisation et le client rangent tout sous ces clefs ; seul le monde reel se cherche par la dimension.
 */
public final class RemoteKeys {

    private static final String ELSEWHERE = "elsewhere/";
    private static final Map<RegistryKey<World>, RegistryKey<World>> ELSEWHERES = new HashMap<>();
    private static final Map<RegistryKey<World>, RegistryKey<World>> DIMENSIONS = new HashMap<>();

    private RemoteKeys() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** La clef de l'ailleurs d'une dimension : creee une fois, puis la meme a chaque demande. */
    public static synchronized RegistryKey<World> elsewhere(RegistryKey<World> dimension) {
        RegistryKey<World> key = ELSEWHERES.get(dimension);
        if (key == null) {
            Identifier id = dimension.getValue();
            key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ModConstants.MOD_ID, ELSEWHERE + id.getNamespace() + "/" + id.getPath()));
            ELSEWHERES.put(dimension, key);
            DIMENSIONS.put(key, dimension);
        }
        return key;
    }

    /** {@code true} si cette clef est l'ailleurs d'une dimension. */
    public static boolean isElsewhere(RegistryKey<World> key) {
        Identifier id = key.getValue();
        return id.getNamespace().equals(ModConstants.MOD_ID) && id.getPath().startsWith(ELSEWHERE);
    }

    /** La dimension d'une clef : celle dont c'est l'ailleurs, ou la clef elle-meme. */
    public static synchronized RegistryKey<World> dimension(RegistryKey<World> key) {
        if (!isElsewhere(key)) {
            return key;
        }
        RegistryKey<World> dimension = DIMENSIONS.get(key);
        if (dimension == null) {
            String rest = key.getValue().getPath().substring(ELSEWHERE.length());
            int slash = rest.indexOf('/');
            dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(rest.substring(0, slash), rest.substring(slash + 1)));
            DIMENSIONS.put(key, dimension);
            ELSEWHERES.put(dimension, key);
        }
        return dimension;
    }
}
