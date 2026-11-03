package com.gtgpuke.gtgsbackrooms.core.render.quality;

import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Le style de l'image, independant du palier de qualite : ce que chaque preset change au look d'une dimension. Un preset ne
 * touche ni a la lumiere ni au brouillard : il ne donne aucun avantage de visibilite.
 */
public enum VisualPreset {

    /** Le visuel cible, tel que le profil le decrit. */
    FAITHFUL("faithful"),
    /**
     * Une cassette de camescope : cadre 4:3 et objectif qui courbe les bords de l'image, image molle et accentuee, couleur qui bave,
     * bruit le long des lignes, tremble a l'epaule. Ni gris ni noirs releves : la couleur reste celle du look, un peu plus dense.
     */
    FOUND_FOOTAGE("found_footage"),
    /** Bandes noires, grain filmique, contraste plus dense. */
    CINEMATIC("cinematic");

    private static final float TAPE_VIGNETTE = 0.1f;
    private static final float TAPE_POWER = 1.03f;
    private static final float FILM_GRAIN = 0.03f;
    private static final float FILM_VIGNETTE = 0.12f;
    private static final float FILM_POWER = 1.12f;
    private static final float BARS = 0.1f;

    private final String key;

    VisualPreset(String key) {
        this.key = key;
    }

    /** Nom du preset, dans le fichier de configuration et les cles de langue. */
    public String key() {
        return this.key;
    }

    /** Force de l'effet de bande usee, de 0 a 1 : un post qui ondule et qui bave. */
    public float tape() {
        return this == FOUND_FOOTAGE ? 1.0f : 0.0f;
    }

    /** Part de la hauteur de l'image cachee par les bandes noires, en haut comme en bas. */
    public float bars() {
        return this == CINEMATIC ? BARS : 0.0f;
    }

    /** {@code true} si ce style tient au post, qu'il garde meme aux paliers sans post. */
    public boolean needsPost() {
        return this == FOUND_FOOTAGE || this == CINEMATIC;
    }

    /** Le preset de ce nom, ou le preset par defaut. */
    public static VisualPreset byKey(String key) {
        for (VisualPreset preset : values()) {
            if (preset.key.equals(key)) {
                return preset;
            }
        }
        return FAITHFUL;
    }

    /** Le look d'une dimension sous ce preset ; un profil sans look reste tel quel. */
    public LookProfile apply(LookProfile look) {
        if (look.vanilla()) {
            return look;
        }
        LookProfile.Post post = look.post();
        LookProfile.BakedLight baked = look.bakedLight();
        LookProfile.Grade grade = look.grade();
        return switch (this) {
            // Le bruit de la cassette est celui du post, le long de ses lignes : pas de grain au pixel en plus.
            case FOUND_FOOTAGE -> look.styled(look.grain(), look.desaturation(), post.adjusted(TAPE_VIGNETTE, 1.0f),
                    grade.adjusted(new Vec3d(1.0, 1.0, 1.0), TAPE_POWER, 1.0f), baked);
            case CINEMATIC -> look.styled(Math.min(look.grain() + FILM_GRAIN, 1.0f), look.desaturation(), post.adjusted(FILM_VIGNETTE, 1.2f),
                    grade.adjusted(new Vec3d(1.0, 1.0, 1.0), FILM_POWER, 0.92f), baked);
            case FAITHFUL -> look;
        };
    }
}
