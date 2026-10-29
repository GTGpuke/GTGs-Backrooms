package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

/**
 * La direction d'ou un son semble venir quand le chemin droit est bouche et qu'un detour passe mieux : par l'ouverture,
 * pas par le mur. La source est deplacee, a la meme distance de l'auditeur, vers le point de passage du detour, d'une
 * part donnee par le profil et par ce que le detour gagne sur le chemin droit. Logique pure, sans allocation : le
 * decalage est ecrit dans un tableau de l'appelant.
 */
public final class PerceivedDirection {

    /** En deca, l'auditeur est sur la source : rien a deplacer. */
    private static final double MIN_DISTANCE = 1.0e-3;

    private PerceivedDirection() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Le decalage a donner a la source.
     *
     * @param px point ou le son joue
     * @param py point ou le son joue
     * @param pz point ou le son joue
     * @param lx auditeur
     * @param ly auditeur
     * @param lz auditeur
     * @param wx point de passage du meilleur detour, relatif au point ou le son joue (nul : le chemin droit l'emporte)
     * @param wy point de passage
     * @param wz point de passage
     * @param blend part du deplacement vers le detour, de 0 (aucun) a 1 (toute la direction du detour)
     * @param out le decalage, trois valeurs
     */
    public static void offset(double px, double py, double pz, double lx, double ly, double lz, double wx, double wy, double wz, float blend,
            double[] out) {
        out[0] = 0.0;
        out[1] = 0.0;
        out[2] = 0.0;
        double realX = px - lx;
        double realY = py - ly;
        double realZ = pz - lz;
        double distance = Math.sqrt(realX * realX + realY * realY + realZ * realZ);
        double wayX = realX + wx;
        double wayY = realY + wy;
        double wayZ = realZ + wz;
        double wayLength = Math.sqrt(wayX * wayX + wayY * wayY + wayZ * wayZ);
        if (blend <= 0.0f || distance < MIN_DISTANCE || wayLength < MIN_DISTANCE || wx == 0.0 && wy == 0.0 && wz == 0.0) {
            return;
        }
        double dirX = realX / distance * (1.0 - blend) + wayX / wayLength * blend;
        double dirY = realY / distance * (1.0 - blend) + wayY / wayLength * blend;
        double dirZ = realZ / distance * (1.0 - blend) + wayZ / wayLength * blend;
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < MIN_DISTANCE) {
            return;
        }
        out[0] = lx + dirX / length * distance - px;
        out[1] = ly + dirY / length * distance - py;
        out[2] = lz + dirZ / length * distance - pz;
    }

    /**
     * La part du deplacement : celle du profil, ponderee par ce que le detour gagne sur le chemin droit (rien s'il ne
     * gagne rien, toute la part du profil s'il passe sans rien perdre la ou le droit est bouche jusqu'au plafond).
     *
     * @param profileShare part du profil, de 0 a 1
     * @param direct occlusion du chemin droit
     * @param best occlusion du meilleur chemin
     * @param max plafond d'occlusion du profil
     */
    public static float blend(float profileShare, float direct, float best, float max) {
        if (max <= 0.0f || direct <= best) {
            return 0.0f;
        }
        return profileShare * Math.min(1.0f, (direct - best) / max);
    }
}
