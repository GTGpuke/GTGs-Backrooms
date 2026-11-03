package com.gtgpuke.gtgsbackrooms.core.render.quality;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.render.light.TexelGrid;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Les reglages du menu du joueur, stockes cote client : palier demande, preset visuel, taille des pixels d'ombre, et
 * chaque option. Une copie immuable ; la courante se lit de tout thread. Les options de lancement ({@code -D}) donnent
 * les valeurs de depart, tant que le joueur n'a rien choisi.
 *
 * @param quality palier demande : cle d'un {@link LookQuality}, ou {@link LookQuality#AUTO}
 * @param preset preset visuel
 * @param pixelShadows pixels d'ombre par bloc : 32, ou 0 pour une lumiere lisse
 * @param options masque des {@link LookOption} actives
 */
public record LookSettings(String quality, VisualPreset preset, int pixelShadows, int options) {

    private static final String QUALITY = "quality";
    private static final String PRESET = "preset";
    private static final String PIXEL_SHADOWS = "pixel_shadows";
    private static final String OPTIONS = "options";
    private static volatile LookSettings current = defaults();

    /** Les reglages de depart : ceux des options de lancement. */
    public static LookSettings defaults() {
        return defaults(ModConfig.REDUCE_FLASHES, ModConfig.AUDIO_CAMERA_SHAKE, ModConfig.REDUCE_VISUAL_NOISE);
    }

    /** Les reglages de depart pour ces options de confort. */
    static LookSettings defaults(boolean reduceFlashes, boolean cameraShake, boolean reduceNoise) {
        int options = 0;
        for (LookOption option : new LookOption[] {LookOption.SURFACE_CACHE, LookOption.BLOOM, LookOption.HALOS, LookOption.SHEEN, LookOption.RELIEF, LookOption.VIGNETTE}) {
            options |= option.bit();
        }
        options |= reduceFlashes ? LookOption.REDUCE_FLASHES.bit() : 0;
        options |= cameraShake ? 0 : LookOption.REDUCE_MOTION.bit();
        options |= reduceNoise ? LookOption.REDUCE_NOISE.bit() : 0;
        return new LookSettings(ModConfig.LOOK_QUALITY, VisualPreset.FAITHFUL, TexelGrid.steps(ModConfig.LOOK_PIXEL_SHADOWS), options);
    }

    /** Les reglages en vigueur. */
    public static LookSettings current() {
        return current;
    }

    /** Pose les reglages en vigueur. */
    public static void set(LookSettings settings) {
        current = settings;
    }

    /** {@code true} si cette option est active. */
    public boolean has(LookOption option) {
        return (this.options & option.bit()) != 0;
    }

    /** Ces reglages, cette option mise a cette valeur. */
    public LookSettings with(LookOption option, boolean active) {
        return new LookSettings(this.quality, this.preset, this.pixelShadows, active ? this.options | option.bit() : this.options & ~option.bit());
    }

    /** Ces reglages, a ce palier. */
    public LookSettings withQuality(String next) {
        return new LookSettings(next, this.preset, this.pixelShadows, this.options);
    }

    /** Ces reglages, sous ce preset. */
    public LookSettings withPreset(VisualPreset next) {
        return new LookSettings(this.quality, next, this.pixelShadows, this.options);
    }

    /** Ces reglages, avec cette taille des pixels d'ombre. */
    public LookSettings withPixelShadows(int next) {
        return new LookSettings(this.quality, this.preset, TexelGrid.steps(next), this.options);
    }

    /** {@code true} si aucune variation forte de lumiere n'est permise. */
    public boolean flashesReduced() {
        return has(LookOption.REDUCE_FLASHES);
    }

    /** {@code true} si la camera ne tremble jamais. */
    public boolean motionReduced() {
        return has(LookOption.REDUCE_MOTION);
    }

    /** {@code true} si l'image n'a ni grain ni aberration. */
    public boolean noiseReduced() {
        return has(LookOption.REDUCE_NOISE);
    }

    /** {@code true} si le cache de surfaces tourne a ce palier. */
    public boolean surfaceLight(LookQuality tier) {
        return tier.surfaceLight() && has(LookOption.SURFACE_CACHE);
    }

    /**
     * Le look d'une dimension sous ces reglages : le confort, puis le preset, puis ce que le palier et les options retirent.
     *
     * @param base look du profil, tel que le fichier le decrit
     * @param tier palier en vigueur
     * @return le look a rendre
     */
    public LookProfile effective(LookProfile base, LookQuality tier) {
        if (tier == LookQuality.OFF && !base.vanilla()) {
            return base.plain();
        }
        LookProfile shaped = tier.traced() && !base.vanilla() ? base.styled(base.grain(), base.desaturation(), base.post(), base.grade(), base.bakedLight().tracedLight()) : base;
        LookProfile styled = this.preset.apply(shaped.comfort(flashesReduced(), noiseReduced()));
        if (styled.vanilla()) {
            return styled;
        }
        boolean post = tier.post() || this.preset.needsPost();
        LookProfile.Post basePost = styled.post();
        LookProfile.Post kept = new LookProfile.Post(post && has(LookOption.VIGNETTE) ? basePost.vignette() : 0.0f, post ? basePost.chromatic() : 0.0f,
                basePost.bloom() && tier.bloom() && has(LookOption.BLOOM), basePost.bloomThreshold(), basePost.bloomStrength());
        LookProfile.BakedLight light = styled.bakedLight();
        LookProfile.BakedLight masked = light.masked(surfaceLight(tier) && has(LookOption.RELIEF), tier.viewEffects() && has(LookOption.HALOS),
                surfaceLight(tier) && has(LookOption.SHEEN));
        return styled.styled(styled.grain(), styled.desaturation(), kept, styled.grade(), masked);
    }

    /** Ces reglages, pour le fichier de configuration. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(QUALITY, this.quality);
        json.addProperty(PRESET, this.preset.key());
        json.addProperty(PIXEL_SHADOWS, this.pixelShadows);
        JsonArray active = new JsonArray();
        for (LookOption option : LookOption.values()) {
            if (has(option)) {
                active.add(option.key());
            }
        }
        json.add(OPTIONS, active);
        return json;
    }

    /**
     * Reglages lus dans le fichier de configuration : ce qui manque vient des valeurs de depart, et une valeur inconnue est
     * ignoree : un fichier d'une autre version n'empeche jamais le jeu de demarrer.
     *
     * @param json contenu du fichier
     * @param fallback valeurs de depart
     */
    public static LookSettings fromJson(JsonObject json, LookSettings fallback) {
        String quality = json.has(QUALITY) ? json.get(QUALITY).getAsString() : fallback.quality;
        VisualPreset preset = json.has(PRESET) ? VisualPreset.byKey(json.get(PRESET).getAsString()) : fallback.preset;
        int pixelShadows = json.has(PIXEL_SHADOWS) ? TexelGrid.steps(json.get(PIXEL_SHADOWS).getAsInt()) : fallback.pixelShadows;
        int options = fallback.options;
        if (json.has(OPTIONS)) {
            options = 0;
            for (JsonElement element : json.getAsJsonArray(OPTIONS)) {
                LookOption option = LookOption.byKey(element.getAsString());
                options |= option == null ? 0 : option.bit();
            }
        }
        return new LookSettings(quality, preset, pixelShadows, options);
    }
}
