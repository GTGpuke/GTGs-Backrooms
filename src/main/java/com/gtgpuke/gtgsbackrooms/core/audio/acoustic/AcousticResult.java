package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

/**
 * Ce qu'un calcul acoustique dit d'un son : la taille de l'espace autour de sa source et son ouverture (rayons de
 * reverb, {@link AcousticEvaluator#reverb}), la part des rayons perdus dans des sections pas encore copiees, et son
 * occlusion jusqu'a l'auditeur ({@link AcousticEvaluator#occlusion}).
 *
 * @param roomSize libre parcours moyen des rayons avant un mur, en blocs : la taille de la salle
 * @param openness part des rayons sortis sans rencontrer de mur, parmi ceux restes en monde connu, de 0 (salle fermee)
 *        a 1 (plein air)
 * @param unknown part des rayons sortis du monde copie, de 0 a 1 : au-dela de {@link #MAX_UNKNOWN}, le resultat ne vaut
 *        rien (la copie du monde est en cours)
 * @param occlusion occlusion du son jusqu'a l'auditeur, de 0 au plafond du profil
 */
public record AcousticResult(float roomSize, float openness, float unknown, float occlusion) {

    /** Part de rayons perdus au-dela de laquelle l'espace n'est pas connu. */
    public static final float MAX_UNKNOWN = 0.25f;

    /** {@code true} si assez de rayons sont restes en monde connu pour que l'espace soit fiable. */
    public boolean reliable() {
        return this.unknown <= MAX_UNKNOWN;
    }

    /** Meme espace, autre occlusion. */
    public AcousticResult withOcclusion(float value) {
        return new AcousticResult(this.roomSize, this.openness, this.unknown, value);
    }
}
