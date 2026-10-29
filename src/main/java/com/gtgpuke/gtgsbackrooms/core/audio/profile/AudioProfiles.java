package com.gtgpuke.gtgsbackrooms.core.audio.profile;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Registre des profils audio, un par dimension, recharge avec les ressources. Une dimension absente a le profil
 * {@link AudioProfile#VANILLA}. Deux profils pour une meme dimension : le premier charge gagne, l'autre est journalise
 * et ignore.
 */
public final class AudioProfiles {

    private static final Map<Identifier, AudioProfile> BY_DIMENSION = new HashMap<>();

    private AudioProfiles() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Remplace tous les profils par ceux-ci. */
    public static void load(Collection<AudioProfile> profiles) {
        clear();
        for (AudioProfile profile : profiles) {
            if (profile.vanilla()) {
                continue;
            }
            if (BY_DIMENSION.putIfAbsent(profile.dimension(), profile) != null) {
                ModConstants.LOGGER.error("[Audio] Deux profils pour la dimension {} : le second est ignore.", profile.dimension());
            }
        }
    }

    /** Plus aucun profil : tout est vanilla. */
    public static void clear() {
        BY_DIMENSION.clear();
    }

    /** Les profils charges, sans ordre. */
    public static Collection<AudioProfile> all() {
        return Collections.unmodifiableCollection(BY_DIMENSION.values());
    }

    /** Nombre de profils charges. */
    public static int size() {
        return BY_DIMENSION.size();
    }

    /** Profil de cette dimension, {@link AudioProfile#VANILLA} si elle n'en a pas ou si elle est inconnue. */
    public static AudioProfile of(@Nullable Identifier dimension) {
        AudioProfile profile = dimension == null ? null : BY_DIMENSION.get(dimension);
        return profile == null ? AudioProfile.VANILLA : profile;
    }

    /** Profil du monde de cette cle, {@link AudioProfile#VANILLA} s'il n'en a pas ; {@code null} vaut vanilla. */
    public static AudioProfile of(@Nullable RegistryKey<World> world) {
        return of(world == null ? null : world.getValue());
    }
}
