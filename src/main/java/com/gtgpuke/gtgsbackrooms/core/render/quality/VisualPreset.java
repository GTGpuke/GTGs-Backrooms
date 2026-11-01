package com.gtgpuke.gtgsbackrooms.core.render.quality;

import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.light.TexelGrid;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Le style de l'image, independant du palier de qualite : ce que chaque preset change au look d'une dimension. Un preset ne
 * touche ni a la lumiere ni au brouillard : il ne donne aucun avantage de visibilite. Choisir un preset pose aussi sa taille
 * des pixels d'ombre, que le joueur peut changer ensuite.
 */
public enum VisualPreset {

    /** Le visuel cible, tel que le profil le decrit. */
    FAITHFUL("faithful", TexelGrid.DEFAULT_STEPS),
    /** Le plus Minecraft : pixels d'ombre plus gros et relief marque, moins de rayonnement. */
    PIXEL("pixel", 16),
    /** Lumiere lissee, matieres et reflets pousses. */
    REALISTIC("realistic", 0),
    /** Bande usee : bruit, couleurs qui bavent, image qui ondule, balance des blancs approximative. */
    FOUND_FOOTAGE("found_footage", TexelGrid.DEFAULT_STEPS),
    /** Bandes noires, grain filmique, contraste plus dense. */
    CINEMATIC("cinematic", TexelGrid.DEFAULT_STEPS),
    /** Aucun flash, aucun grain, aucune aberration, aucun tremblement. */
    COMFORT("comfort", TexelGrid.DEFAULT_STEPS);

    private static final float TAPE_GRAIN = 0.05f;
    private static final float TAPE_DESATURATION = 0.2f;
    private static final float TAPE_VIGNETTE = 0.2f;
    private static final float TAPE_CHROMATIC = 0.4f;
    private static final Vec3d TAPE_BALANCE = new Vec3d(0.98, 1.02, 0.96);
    private static final float FILM_GRAIN = 0.03f;
    private static final float FILM_VIGNETTE = 0.12f;
    private static final float FILM_POWER = 1.12f;
    private static final float BARS = 0.1f;

    private final String key;
    private final int pixelShadows;

    VisualPreset(String key, int pixelShadows) {
        this.key = key;
        this.pixelShadows = pixelShadows;
    }

    /** Nom du preset, dans le fichier de configuration et les cles de langue. */
    public String key() {
        return this.key;
    }

    /** Taille des pixels d'ombre que ce preset pose quand on le choisit. */
    public int pixelShadows() {
        return this.pixelShadows;
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
            case PIXEL -> look.styled(look.grain() * 0.5f, look.desaturation(), post.adjusted(0.0f, 0.0f, 0.5f), grade,
                    baked.scaled(1.4f, 0.5f, 0.7f).pixels(1.0f, baked.darkPixels() * 2.0f));
            case REALISTIC -> look.styled(look.grain(), look.desaturation(), post.adjusted(0.0f, 0.0f, 1.2f), grade, baked.scaled(1.3f, 1.4f, 1.4f).pixels(0.0f, 0.0f));
            case FOUND_FOOTAGE -> look.styled(Math.min(look.grain() + TAPE_GRAIN, 1.0f), Math.min(look.desaturation() + TAPE_DESATURATION, 1.0f),
                    post.adjusted(TAPE_VIGNETTE, TAPE_CHROMATIC, 1.0f), grade.adjusted(TAPE_BALANCE, 1.0f, 0.8f), baked);
            case CINEMATIC -> look.styled(Math.min(look.grain() + FILM_GRAIN, 1.0f), look.desaturation(), post.adjusted(FILM_VIGNETTE, 0.0f, 1.2f),
                    grade.adjusted(new Vec3d(1.0, 1.0, 1.0), FILM_POWER, 0.92f), baked);
            case FAITHFUL, COMFORT -> look;
        };
    }
}
