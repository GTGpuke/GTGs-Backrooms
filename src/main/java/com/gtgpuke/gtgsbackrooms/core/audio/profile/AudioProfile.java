package com.gtgpuke.gtgsbackrooms.core.audio.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

/**
 * L'acoustique d'une dimension, lue dans {@code assets/gtgs_backrooms/audio_profiles/*.json} : une donnee, le pendant
 * sonore du profil de look, liee a lui par la dimension. Une dimension sans profil a le profil {@link #VANILLA} : ses
 * sons ne recoivent aucun traitement. Le traitement d'un son depend de la dimension ou il est emis, jamais de celle du
 * joueur.
 *
 * @param dimension dimension du profil, {@code null} pour le profil vanilla
 * @param acoustics comment l'acoustique de chaque son se calcule
 * @param reverb reverberation de base de la dimension
 * @param occlusion poids des blocs entre une source et l'auditeur
 * @param airAbsorption absorption des aigus par la distance, de 0 (aucune) a 1
 * @param underwater ce que l'eau retire a un son quand l'auditeur ou la source y est plonge
 * @param perceivedDirection part dont un son bouche se deplace vers l'ouverture par ou il passe mieux (direction
 *        percue), de 0 (jamais) a 1
 * @param ambience ambiance de la dimension (bourdonnement des neons)
 */
public record AudioProfile(@Nullable Identifier dimension, Acoustics acoustics, Reverb reverb, Occlusion occlusion, float airAbsorption,
        Medium underwater, float perceivedDirection, Ambience ambience) {

    /** Aucun profil : sons strictement vanilla. */
    public static final AudioProfile VANILLA = new AudioProfile(null, Acoustics.NONE, Reverb.NONE, Occlusion.NONE, 0.0f, Medium.NONE, 0.0f,
            Ambience.NONE);

    /**
     * Comment l'acoustique d'un son se calcule : la reverb de base partout et un rayon d'occlusion sur le thread de rendu
     * ({@link #NONE}) ; des rayons lances sur une copie du monde hors du thread de rendu, qui donnent a chaque son la
     * reverb de l'espace autour de lui et une occlusion par plusieurs chemins ({@link #RAYCAST}) ; ou la forme des salles
     * tiree de la generation de la dimension ({@link #GENERATED}, le contenu la donne au client), les rayons ne servant
     * plus qu'a l'occlusion et aux endroits ou un bloc a ete pose ou casse.
     */
    public enum Acoustics {

        /** Reverb de base de la dimension, un rayon d'occlusion. */
        NONE,
        /** Rayons sur une copie du monde, reverb par cellule, chemins d'occlusion. */
        RAYCAST,
        /** Forme des salles tiree de la generation ; rayons la ou la copie du monde s'en ecarte, et pour l'occlusion. */
        GENERATED;

        /** {@code true} si l'acoustique se calcule hors du thread de rendu, sur une copie du monde (rayons ou forme generee). */
        public boolean computed() {
            return this != NONE;
        }
    }

    /**
     * Reverberation de base d'une dimension, celle de ses salles telles que la generation les fait.
     *
     * @param decay duree de decroissance, en secondes, de 0,1 a 20
     * @param density densite des reflexions, de 0 a 1
     * @param diffusion diffusion des reflexions, de 0 a 1
     * @param gain part de la reverb dans le son, de 0 (aucune reverb) a 1
     * @param brightness part des aigus gardee par la reverb, de 0 (sourde) a 1 (claire)
     */
    public record Reverb(float decay, float density, float diffusion, float gain, float brightness) {

        /** Aucune reverberation. */
        public static final Reverb NONE = new Reverb(0.1f, 0.0f, 0.0f, 0.0f, 0.0f);

        /** {@code true} si la reverb s'entend. */
        public boolean any() {
            return this.gain > 0.0f;
        }
    }

    /**
     * Occlusion : ce qu'un bloc entre la source et l'auditeur retire au son.
     *
     * @param wall part retiree par un bloc plein, de 0 a 1
     * @param partialBlock part retiree par un bloc partiel (dalle, escalier, vitre), de 0 a 1
     * @param max occlusion maximale, de 0 inclus a 1 exclu : le son n'est jamais entierement coupe
     */
    public record Occlusion(float wall, float partialBlock, float max) {

        /** Aucune occlusion. */
        public static final Occlusion NONE = new Occlusion(0.0f, 0.0f, 0.0f);

        /** {@code true} si les blocs retirent quelque chose. */
        public boolean any() {
            return this.max > 0.0f && (this.wall > 0.0f || this.partialBlock > 0.0f);
        }
    }

    /**
     * Un milieu qui filtre le son (l'eau) : quand l'auditeur ou la source y est plonge, le filtre du son garde cette part
     * de son gain et de ses aigus, en plus de l'occlusion.
     *
     * @param gain part du gain gardee, de 0 a 1
     * @param gainHf part des aigus gardee, de 0 a 1
     */
    public record Medium(float gain, float gainHf) {

        /** Aucun effet : le milieu laisse tout passer. */
        public static final Medium NONE = new Medium(1.0f, 1.0f);

        /** {@code true} si le milieu retire quelque chose. */
        public boolean any() {
            return this.gain < 1.0f || this.gainHf < 1.0f;
        }
    }

    /**
     * Ambiance d'une dimension.
     *
     * @param hum son du bourdonnement des neons, {@code null} sans bourdonnement
     * @param humVolume volume du bourdonnement a la source, de 0 a 1
     * @param clickOff son d'un neon qui s'eteint (vacillement, {@code NeonSchedule}), {@code null} sans claquement
     * @param clickOn son d'un neon qui se rallume, {@code null} sans claquement
     */
    public record Ambience(@Nullable Identifier hum, float humVolume, @Nullable Identifier clickOff, @Nullable Identifier clickOn) {

        /** Aucune ambiance. */
        public static final Ambience NONE = new Ambience(null, 0.0f, null, null);

        /** {@code true} si les neons bourdonnent. */
        public boolean any() {
            return this.hum != null && this.humVolume > 0.0f;
        }
    }

    /** {@code true} pour le profil vanilla. */
    public boolean vanilla() {
        return this.dimension == null;
    }

    /**
     * Lit un profil.
     *
     * @param json contenu du fichier
     * @return le profil
     * @throws JsonParseException si le fichier est invalide : dimension absente, valeur hors de sa plage, son invalide
     */
    public static AudioProfile fromJson(JsonObject json) {
        Identifier dimension = Identifier.tryParse(string(json, "dimension"));
        if (dimension == null) {
            throw new JsonParseException("Dimension invalide : " + string(json, "dimension") + ".");
        }
        float absorption = json.has("air_absorption") ? json.get("air_absorption").getAsFloat() : 0.0f;
        check("air_absorption", absorption, 0.0f, 1.0f);
        return new AudioProfile(dimension, acoustics(json), reverb(json), occlusion(json), absorption, underwater(json),
                unit(json, "perceived_direction", 0.0f), ambience(json));
    }

    private static Acoustics acoustics(JsonObject json) {
        if (!json.has("acoustics")) {
            return Acoustics.NONE;
        }
        String value = string(json, "acoustics");
        return switch (value) {
            case "none" -> Acoustics.NONE;
            case "raycast" -> Acoustics.RAYCAST;
            case "generated" -> Acoustics.GENERATED;
            default -> throw new JsonParseException("Acoustique inconnue : " + value + " (none, raycast ou generated).");
        };
    }

    private static Reverb reverb(JsonObject json) {
        if (!json.has("reverb_base")) {
            return Reverb.NONE;
        }
        JsonObject section = json.getAsJsonObject("reverb_base");
        float decay = section.has("decay") ? section.get("decay").getAsFloat() : 1.0f;
        check("reverb_base.decay", decay, 0.1f, 20.0f);
        return new Reverb(decay, unit(section, "density", 1.0f), unit(section, "diffusion", 1.0f), unit(section, "gain", 0.0f),
                unit(section, "brightness", 0.5f));
    }

    private static Occlusion occlusion(JsonObject json) {
        if (!json.has("occlusion")) {
            return Occlusion.NONE;
        }
        JsonObject section = json.getAsJsonObject("occlusion");
        float max = unit(section, "max", 0.0f);
        // Le son n'est jamais entierement coupe : une part gardee nulle ferait perdre son volume a une source.
        if (max >= 1.0f) {
            throw new JsonParseException("Occlusion max hors de [0, 1[ : " + max + ".");
        }
        return new Occlusion(unit(section, "wall", 0.0f), unit(section, "partial_block", 0.0f), max);
    }

    private static Medium underwater(JsonObject json) {
        if (!json.has("underwater")) {
            return Medium.NONE;
        }
        JsonObject section = json.getAsJsonObject("underwater");
        return new Medium(unit(section, "gain", 1.0f), unit(section, "gain_hf", 1.0f));
    }

    private static Ambience ambience(JsonObject json) {
        if (!json.has("ambience")) {
            return Ambience.NONE;
        }
        JsonObject section = json.getAsJsonObject("ambience");
        if (!section.has("hum")) {
            return Ambience.NONE;
        }
        Identifier hum = Identifier.tryParse(string(section, "hum"));
        if (hum == null) {
            throw new JsonParseException("Son du bourdonnement invalide : " + string(section, "hum") + ".");
        }
        return new Ambience(hum, unit(section, "hum_volume", 0.25f), sound(section, "click_off"), sound(section, "click_on"));
    }

    private static @Nullable Identifier sound(JsonObject section, String key) {
        if (!section.has(key)) {
            return null;
        }
        Identifier sound = Identifier.tryParse(string(section, key));
        if (sound == null) {
            throw new JsonParseException("Son invalide pour " + key + " : " + string(section, key) + ".");
        }
        return sound;
    }

    private static float unit(JsonObject json, String key, float fallback) {
        float value = json.has(key) ? json.get(key).getAsFloat() : fallback;
        check(key, value, 0.0f, 1.0f);
        return value;
    }

    private static void check(String key, float value, float min, float max) {
        if (!(value >= min && value <= max)) {
            throw new JsonParseException("Valeur de " + key + " hors de [" + min + ", " + max + "] : " + value + ".");
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Champ manquant : " + key + ".");
        }
        return element.getAsString();
    }
}
