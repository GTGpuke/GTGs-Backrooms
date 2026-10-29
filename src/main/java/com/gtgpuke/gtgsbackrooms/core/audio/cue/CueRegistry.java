package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Les liens son-image charges, par son : un son n'appartient qu'a un lien (le second qui le reclame est refuse,
 * journalise). Recharge avec les ressources.
 */
public final class CueRegistry {

    private static final Map<Identifier, Cue> BY_SOUND = new HashMap<>();
    private static final List<Cue> CUES = new ArrayList<>();

    private CueRegistry() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Remplace les liens. */
    public static void load(Collection<Cue> cues) {
        clear();
        for (Cue cue : cues) {
            CUES.add(cue);
            for (Identifier sound : cue.sounds()) {
                Cue previous = BY_SOUND.putIfAbsent(sound, cue);
                if (previous != null) {
                    ModConstants.LOGGER.error("[Audio] Le son {} est deja dans le lien {} : {} l'ignore.", sound, previous.id(), cue.id());
                }
            }
        }
    }

    /** Le lien d'un son, ou {@code null} pour un son ordinaire. */
    public static @Nullable Cue of(Identifier sound) {
        return BY_SOUND.get(sound);
    }

    /** Tous les liens charges, dans l'ordre de chargement. */
    public static List<Cue> cues() {
        return CUES;
    }

    /** Nombre de liens. */
    public static int size() {
        return CUES.size();
    }

    /** Plus aucun lien. */
    public static void clear() {
        BY_SOUND.clear();
        CUES.clear();
    }
}
