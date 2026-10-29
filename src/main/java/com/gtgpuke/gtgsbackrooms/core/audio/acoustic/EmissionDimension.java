package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * La dimension ou un son est emis, dont depend tout son traitement : celle du monde ou il a ete cree, jamais celle du
 * joueur ni celle du point ou il est rejoue. Un son rejoue au cadre d'un portail garde la dimension de sa vraie source
 * (son ancre) ; un son du monde du joueur a celle de ce monde au moment de la demande ; un son sans place dans un monde
 * (interface, musique) ou de dimension inconnue est vanilla : dans le doute, on ne touche a rien.
 */
public final class EmissionDimension {

    private EmissionDimension() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Dimension d'emission d'un son.
     *
     * @param anchored monde de la vraie source du son, s'il est rejoue au cadre d'un portail ; {@code null} sinon
     * @param playerWorld monde du joueur au moment de la demande, {@code null} hors partie
     * @param relative {@code true} pour un son sans position dans un monde (interface, musique)
     * @return la dimension d'emission, ou {@code null} pour un son a laisser vanilla
     */
    public static @Nullable RegistryKey<World> resolve(@Nullable RegistryKey<World> anchored, @Nullable RegistryKey<World> playerWorld, boolean relative) {
        if (relative) {
            return null;
        }
        return anchored != null ? anchored : playerWorld;
    }

    /**
     * Dimension dont la reverb s'ajoute a celle d'un son entre par un cadre : celle du joueur, quand la vraie source est
     * dans un autre monde (le cadre est alors une seconde source, dans sa salle a lui) ; {@code null} pour un son emis
     * dans le monde du joueur, qui n'a que la reverb de sa dimension.
     *
     * @param anchored monde de la vraie source du son, s'il est rejoue au cadre d'un portail ; {@code null} sinon
     * @param playerWorld monde du joueur, {@code null} hors partie
     */
    public static @Nullable RegistryKey<World> room(@Nullable RegistryKey<World> anchored, @Nullable RegistryKey<World> playerWorld) {
        return anchored != null && playerWorld != null && !anchored.equals(playerWorld) ? playerWorld : null;
    }
}
