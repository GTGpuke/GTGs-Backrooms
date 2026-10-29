package com.gtgpuke.gtgsbackrooms.core.audio.client;

/**
 * Une source du moteur de son que l'acoustique ajuste : son volume se multiplie par une part gardee (l'occlusion et le
 * milieu rendus sans EFX), et sa position se decale (la direction d'ou le son semble venir, par l'ouverture plutot que
 * par le mur). Ajoutee aux sources vanilla par {@code MixinSource} ; appelee sur le thread du son seulement. Une part de
 * 1 et un decalage nul (le cas de toute source non traitee) ne changent rien.
 */
public interface AdjustedSource {

    /**
     * Pose la part du volume gardee : le volume courant de la source la suit aussitot, et chaque volume que vanilla
     * reposera ensuite en sera multiplie.
     *
     * @param scale part gardee, de 0 a 1
     */
    void backrooms$setVolumeScale(float scale);

    /**
     * Pose le decalage de la position de la source : sa position courante le suit aussitot, et chaque position que
     * vanilla reposera ensuite (un son qui bouge) en sera decalee.
     *
     * @param dx decalage, en blocs
     * @param dy decalage
     * @param dz decalage
     */
    void backrooms$setPositionOffset(double dx, double dy, double dz);
}
