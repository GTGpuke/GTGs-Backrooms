package com.gtgpuke.gtgsbackrooms.core.audio.cue;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.util.Identifier;

/**
 * Un lien son-image, lu dans {@code assets/gtgs_backrooms/sound_cues/*.json} : des sons qui, emis dans une dimension
 * profilee, declenchent des reactions visuelles (les neons proches vacillent au rythme du son, l'ecran pulse, la
 * lumiere baisse, la camera tremble). Un son qui n'est dans aucun lien est un son ordinaire. Donnee, validee au
 * chargement.
 *
 * @param id identifiant du lien
 * @param sounds sons qui le declenchent
 * @param envelope {@code true} pour lire l'enveloppe du son (precalculee) et la suivre ; sinon l'amplitude vaut 1
 * @param reactions reactions visuelles, au moins une
 */
public record Cue(Identifier id, List<Identifier> sounds, boolean envelope, List<Reaction> reactions) {

    /** Reactions visuelles possibles : une liste fermee, un type inconnu est refuse au chargement. */
    public enum Type {

        /** Les neons dans le rayon vacillent au rythme du son. */
        NEON_DISTURBANCE("neon_disturbance"),
        /** La vignette du post pulse au pic du son, dans la dimension du son seulement. */
        SCREEN_PULSE("screen_pulse"),
        /** Toute la lumiere de la dimension baisse un instant. */
        LIGHTMAP_FLICKER("lightmap_flicker"),
        /**
         * La camera du joueur tremble un peu, s'il est lui-meme dans la dimension du son (jamais dans une dimension
         * vanilla), si l'option l'autorise (coupee par defaut) et sans l'option de confort.
         */
        CAMERA_SHAKE("camera_shake");

        private final String key;

        Type(String key) {
            this.key = key;
        }

        /** Nom du type dans les fichiers. */
        public String key() {
            return this.key;
        }
    }

    /**
     * Une reaction.
     *
     * @param type type
     * @param radius portee autour de la source, en blocs (neons perturbes)
     * @param strength force, de 0 a 1
     * @param followEnvelope {@code true} si la force suit l'enveloppe du son ; sinon constante tant qu'il joue
     * @param maxDistance distance de l'auditeur a la source au-dela de laquelle la reaction ne se voit pas (pulsation,
     *        tremblement)
     * @param easing courbe par laquelle la force suit l'enveloppe
     */
    public record Reaction(Type type, float radius, float strength, boolean followEnvelope, float maxDistance, Easing easing) {

        /**
         * La force du moment, de 0 a {@link #strength} : l'amplitude du son passee par la courbe, ou la force entiere si la
         * reaction ne suit pas l'enveloppe.
         *
         * @param amplitude amplitude du son a sa position de lecture, de 0 a 1
         */
        public float force(float amplitude) {
            return this.strength * (this.followEnvelope ? this.easing.apply(amplitude) : 1.0f);
        }
    }

    private static final float MAX_RADIUS = 64.0f;

    /**
     * Lit un lien.
     *
     * @param json contenu du fichier
     * @throws JsonParseException si le fichier est invalide : identifiant ou son invalide, aucun son, aucune reaction,
     *         type inconnu, valeur hors de sa plage
     */
    public static Cue fromJson(JsonObject json) {
        Identifier id = Identifier.tryParse(string(json, "id"));
        if (id == null) {
            throw new JsonParseException("Identifiant de lien invalide : " + string(json, "id") + ".");
        }
        List<Identifier> sounds = new ArrayList<>();
        for (JsonElement element : array(json, "sounds")) {
            Identifier sound = element.isJsonPrimitive() ? Identifier.tryParse(element.getAsString()) : null;
            if (sound == null) {
                throw new JsonParseException("Son invalide dans " + id + " : " + element + ".");
            }
            sounds.add(sound);
        }
        if (sounds.isEmpty()) {
            throw new JsonParseException("Aucun son dans " + id + ".");
        }
        boolean envelope = json.has("envelope") && json.get("envelope").getAsBoolean();
        List<Reaction> reactions = new ArrayList<>();
        for (JsonElement element : array(json, "reactions")) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("Reaction invalide dans " + id + " : " + element + ".");
            }
            reactions.add(reaction(id, element.getAsJsonObject()));
        }
        if (reactions.isEmpty()) {
            throw new JsonParseException("Aucune reaction dans " + id + ".");
        }
        return new Cue(id, List.copyOf(sounds), envelope, List.copyOf(reactions));
    }

    private static Reaction reaction(Identifier id, JsonObject json) {
        String key = string(json, "type");
        Type type = null;
        for (Type candidate : Type.values()) {
            if (candidate.key.equals(key)) {
                type = candidate;
            }
        }
        if (type == null) {
            throw new JsonParseException("Reaction inconnue dans " + id + " : " + key
                    + " (neon_disturbance, screen_pulse, lightmap_flicker ou camera_shake).");
        }
        float radius = number(json, "radius", 0.0f, 0.0f, MAX_RADIUS);
        float strength = number(json, "strength", 1.0f, 0.0f, 1.0f);
        boolean follow = !json.has("follow_envelope") || json.get("follow_envelope").getAsBoolean();
        float maxDistance = number(json, "max_distance", MAX_RADIUS, 0.0f, MAX_RADIUS);
        Easing easing = Easing.LINEAR;
        if (json.has("easing")) {
            easing = Easing.of(string(json, "easing"));
            if (easing == null) {
                throw new JsonParseException("Courbe inconnue dans " + id + " : " + string(json, "easing") + ".");
            }
        }
        return new Reaction(type, radius, strength, follow, maxDistance, easing);
    }

    private static float number(JsonObject json, String key, float fallback, float min, float max) {
        float value = json.has(key) ? json.get(key).getAsFloat() : fallback;
        if (!(value >= min && value <= max)) {
            throw new JsonParseException("Valeur de " + key + " hors de [" + min + ", " + max + "] : " + value + ".");
        }
        return value;
    }

    private static JsonArray array(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException("Liste manquante : " + key + ".");
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Champ manquant : " + key + ".");
        }
        return element.getAsString();
    }
}
