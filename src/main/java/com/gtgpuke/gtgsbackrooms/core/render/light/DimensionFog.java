package com.gtgpuke.gtgsbackrooms.core.render.light;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Quand le brouillard d'un profil remplace celui de vanilla : pour le brouillard du terrain seulement, la camera hors
 * de l'eau, de la lave et de la neige poudreuse, sans cecite ni obscurite, et sans brouillard epais impose. Dans tous
 * les autres cas, vanilla garde la main : ses brouillards-la disent quelque chose au joueur.
 */
public final class DimensionFog {

    private DimensionFog() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} si les distances du profil remplacent celles de vanilla.
     *
     * @param fog brouillard du profil
     * @param terrain {@code true} pour le brouillard du terrain, {@code false} pour celui du ciel
     * @param submerged {@code true} si la camera est dans un fluide ou la neige poudreuse
     * @param sightImpaired {@code true} si l'entite de la camera est aveugle ou dans l'obscurite
     * @param thick {@code true} si un brouillard epais est impose (dimension, boss)
     */
    public static boolean applies(LookProfile.Fog fog, boolean terrain, boolean submerged, boolean sightImpaired, boolean thick) {
        return fog.enabled() && terrain && !submerged && !sightImpaired && !thick;
    }
}
