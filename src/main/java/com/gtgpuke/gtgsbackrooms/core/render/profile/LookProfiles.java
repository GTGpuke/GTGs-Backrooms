package com.gtgpuke.gtgsbackrooms.core.render.profile;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;

/**
 * Registre des profils de look, un par dimension, recharge avec les ressources. Une dimension absente a le profil
 * {@link LookProfile#VANILLA}. Deux profils pour une meme dimension ou un meme identifiant : le premier charge gagne,
 * l'autre est journalise et ignore.
 */
public final class LookProfiles {

    private static final Map<Identifier, LookProfile> BY_DIMENSION = new HashMap<>();
    private static final LookProfile[] BY_ID = new LookProfile[StencilLookMask.MAX_LOOK + 1];

    static {
        clear();
    }

    private LookProfiles() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Remplace tous les profils par ceux-ci. */
    public static void load(Collection<LookProfile> profiles) {
        clear();
        for (LookProfile profile : profiles) {
            if (profile.vanilla()) {
                continue;
            }
            Identifier dimension = profile.dimension();
            if (BY_DIMENSION.containsKey(dimension)) {
                ModConstants.LOGGER.error("[Look] Deux profils pour la dimension {} : le second est ignore.", dimension);
                continue;
            }
            if (!BY_ID[profile.id()].vanilla()) {
                ModConstants.LOGGER.error("[Look] Identifiant de look {} deja pris par {} : le profil de {} est ignore.", profile.id(),
                        BY_ID[profile.id()].dimension(), dimension);
                continue;
            }
            BY_DIMENSION.put(dimension, profile);
            BY_ID[profile.id()] = profile;
        }
    }

    /** Plus aucun profil : tout est vanilla. */
    public static void clear() {
        BY_DIMENSION.clear();
        for (int i = 0; i < BY_ID.length; i++) {
            BY_ID[i] = LookProfile.VANILLA;
        }
    }

    /** Profil d'une dimension, {@link LookProfile#VANILLA} si elle n'en a pas. */
    public static LookProfile of(@Nullable Identifier dimension) {
        LookProfile profile = dimension == null ? null : BY_DIMENSION.get(dimension);
        return profile == null ? LookProfile.VANILLA : profile;
    }

    /** Profil du monde de cette cle, {@link LookProfile#VANILLA} s'il n'en a pas. */
    public static LookProfile of(RegistryKey<World> world) {
        return of(world.getValue());
    }

    /** Profil d'un identifiant de look, {@link LookProfile#VANILLA} pour 0 ou un identifiant sans profil. */
    public static LookProfile byId(int id) {
        return id < 0 || id >= BY_ID.length ? LookProfile.VANILLA : BY_ID[id];
    }

    /** Nombre de profils charges. */
    public static int size() {
        return BY_DIMENSION.size();
    }
}
