package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

/**
 * Generations d'une section de terrain construite par vanilla ({@code ChunkBuilder.BuiltChunk}), tenues par
 * {@code MixinBuiltChunk} et {@code MixinRebuildTask} : chaque demande de reconstruction (un bloc change, sa lumiere, un
 * voisin) ouvre une generation, et une construction reussie note celle de l'instantane de blocs qu'elle a lu. Les deux
 * egales, ce que la section affiche contient la derniere version de ses blocs : un bloc plein qui vient d'y apparaitre n'y
 * est pas encore dessine tant qu'elles different, meme si la section a deja ete construite avant.
 */
public interface SectionGenerations {

    /** Generation de la derniere demande de reconstruction. */
    int backrooms$requested();

    /** Une construction reussie vient de remplacer les donnees affichees, a partir de l'instantane de cette generation. */
    void backrooms$built(int generation);

    /** {@code true} si les donnees affichees ont ete construites apres la derniere demande de reconstruction. */
    boolean backrooms$current();
}
