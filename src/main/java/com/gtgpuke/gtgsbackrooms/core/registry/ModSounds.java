package com.gtgpuke.gtgsbackrooms.core.registry;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Registre des sons du mod. Chaque son est declare dans {@code assets/gtgs_backrooms/sounds.json} sous le meme chemin,
 * avec son sous-titre traduit.
 */
public final class ModSounds {

    /** Un portail s'ouvre : la baguette vient de poser un bloc portail. */
    public static final SoundEvent PORTAL_WAND_PLACE = register("item.portal_wand.place");
    /** Bourdonnement d'un neon des Backrooms (son par defaut : celui d'un phare vanilla, en attendant le vrai). */
    public static final SoundEvent NEON_HUM = register("block.neon.hum");
    /** Claquement d'un neon qui s'eteint (son par defaut : un levier vanilla). */
    public static final SoundEvent NEON_CLICK_OFF = register("block.neon.click_off");
    /** Claquement d'un neon qui se rallume (son par defaut : un levier vanilla). */
    public static final SoundEvent NEON_CLICK_ON = register("block.neon.click_on");
    /** Cri d'une creature des Backrooms, lie a l'image (son par defaut : un cri vanilla). */
    public static final SoundEvent HOWLER_SCREAM = register("entity.howler.scream");

    private ModSounds() {
        throw new UnsupportedOperationException("Classe de registre.");
    }

    /** Force le chargement de la classe, donc l'enregistrement des sons. */
    public static void register() {
        ModConstants.LOGGER.debug("Sons du mod enregistres.");
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(ModConstants.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
