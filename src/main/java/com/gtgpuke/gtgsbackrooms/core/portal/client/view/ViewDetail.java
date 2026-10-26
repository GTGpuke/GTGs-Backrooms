package com.gtgpuke.gtgsbackrooms.core.portal.client.view;

/**
 * Paliers de detail des vues de portail (LOD, decisions de l'auteur du 26 octobre 2026), choisis sur la taille qu'un bloc
 * de l'autre cote prend a l'ecran : la pleine qualite ne se garde que la ou elle se remarque. La resolution et le zoom
 * comptent d'eux-memes (un zoom resserre le champ et grandit chaque bloc), et l'on traverse toujours un cadre en pleine
 * qualite, puisqu'il remplit alors l'ecran.
 *
 * <p>Palier d'une vue ({@link Tier}), sur la taille d'un bloc juste derriere le cadre : pleine qualite (le rendu sans LOD),
 * allegee (ni meteo ni particules, entities figees, terrain simplifie par section), ou image figee. Niveau d'une section
 * de terrain d'une vue allegee ({@link #sectionLevel}), sur la taille de son bloc le plus proche : pleine (son maillage
 * vanilla) tant qu'il fait au moins {@value #SECTION_FULL_PIXELS} pixels, puis faces fusionnees en carres de deux blocs,
 * de quatre.
 *
 * <p>Une meilleure qualite revient aussitot. Une moindre attend que la taille soit nettement passee sous le seuil (ecart de
 * {@value #HYSTERESIS}) ; pour une vue, {@value #HOLD_FRAMES} images au moins apres son dernier changement : le palier ne
 * bat pas autour d'un seuil.
 */
public final class ViewDetail {

    /** Taille d'un bloc a l'ecran, en pixels, a partir de laquelle une vue se rend en pleine qualite. */
    static final double FULL_PIXELS = 24.0;
    /**
     * Dans une vue allegee, taille du bloc le plus proche d'une section a partir de laquelle elle garde son maillage vanilla
     * (decision de l'auteur du 26 octobre 2026 : le terrain simplifie seulement plus loin, la ou il se voit moins).
     */
    static final double SECTION_FULL_PIXELS = 12.0;
    /** En dessous, une section se simplifie en carres de quatre blocs ; entre les deux, de deux blocs. */
    static final double COARSE_PIXELS = 8.0;
    /** En dessous, la vue n'est plus qu'une image figee. */
    static final double FROZEN_PIXELS = 6.0;
    /** Rapport entre le seuil ou l'on remonte en qualite et celui ou l'on en descend. */
    static final double HYSTERESIS = 1.2;
    /** Images au moins entre deux changements de palier d'une vue, pour une descente. */
    static final int HOLD_FRAMES = 20;
    /**
     * Distance, en blocs, jusqu'a laquelle un portail garde son image figee au-dela de la distance de rendu des vues
     * (decision de l'auteur du 26 octobre 2026) : l'image prise de plus pres, sans reprise.
     */
    public static final double FROZEN_DISTANCE = 128.0;
    /** Niveaux de detail d'une section : pleine, carres de deux blocs, carres de quatre. */
    public static final int FULL_LEVEL = 0;
    public static final int COARSE_LEVEL = 2;

    /** Palier d'une vue, de la meilleure qualite a la moindre. */
    public enum Tier {
        /** Le rendu sans LOD. */
        FULL,
        /** Ni meteo ni particules, entities figees, terrain simplifie par section. */
        REDUCED,
        /** Une image figee de la vue, collee sur le cadre. */
        FROZEN
    }

    private static final Tier[] TIERS = Tier.values();

    private ViewDetail() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Taille a l'ecran, en pixels, d'un bloc a cette distance de la camera.
     *
     * @param distance distance du bloc a la camera, en blocs
     * @param verticalScale facteur vertical de la projection ({@code m11} : 1 / tan(champ vertical / 2))
     * @param screenHeight hauteur de l'image, en pixels
     */
    public static double blockPixels(double distance, double verticalScale, int screenHeight) {
        return distance <= 0.0 ? Double.POSITIVE_INFINITY : screenHeight * 0.5 * verticalScale / distance;
    }

    /**
     * Palier d'une vue a cette image.
     *
     * @param current palier a l'image precedente
     * @param framesSinceChange images depuis le dernier changement de palier de cette vue
     * @param pixels taille a l'ecran d'un bloc juste derriere le cadre ({@link #blockPixels})
     */
    public static Tier tier(Tier current, int framesSinceChange, double pixels) {
        Tier wanted = tierFor(pixels);
        if (wanted.ordinal() <= current.ordinal()) {
            return wanted;
        }
        if (framesSinceChange < HOLD_FRAMES) {
            return current;
        }
        Tier margin = tierFor(pixels * HYSTERESIS);
        return margin.ordinal() > current.ordinal() ? margin : current;
    }

    /** Palier d'une vue retenu sous forme de rang ({@link Tier#ordinal}), comme sur le portail. */
    public static Tier tierOf(int ordinal) {
        return TIERS[ordinal];
    }

    /**
     * Niveau de detail d'une section de terrain a cette image : {@value #FULL_LEVEL} (pleine), 1 (carres de deux blocs) ou
     * {@value #COARSE_LEVEL} (carres de quatre).
     *
     * @param current niveau a l'image precedente
     * @param pixels taille a l'ecran de son bloc le plus proche de la camera
     */
    public static int sectionLevel(int current, double pixels) {
        int wanted = levelFor(pixels);
        if (wanted <= current) {
            return wanted;
        }
        return Math.max(current, levelFor(pixels * HYSTERESIS));
    }

    private static Tier tierFor(double pixels) {
        if (pixels >= FULL_PIXELS) {
            return Tier.FULL;
        }
        return pixels >= FROZEN_PIXELS ? Tier.REDUCED : Tier.FROZEN;
    }

    private static int levelFor(double pixels) {
        if (pixels >= SECTION_FULL_PIXELS) {
            return FULL_LEVEL;
        }
        return pixels >= COARSE_PIXELS ? 1 : COARSE_LEVEL;
    }
}
