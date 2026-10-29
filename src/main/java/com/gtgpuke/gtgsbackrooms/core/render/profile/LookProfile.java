package com.gtgpuke.gtgsbackrooms.core.render.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;

/**
 * Le look d'une dimension, lu dans {@code assets/gtgs_backrooms/look_profiles/*.json} : une donnee, pas une
 * abstraction. Une dimension sans profil a le profil {@link #VANILLA} ({@code id} 0) : rendu strictement vanilla.
 *
 * @param dimension dimension du profil, {@code null} pour le profil vanilla
 * @param id identifiant du look, de 1 a {@value StencilLookMask#MAX_LOOK} ; 0 pour vanilla. Porte par le stencil et par l'uniforme {@code gtgs_LookId}
 * @param tintRed teinte, composante rouge
 * @param tintGreen teinte, composante verte
 * @param tintBlue teinte, composante bleue
 * @param tintStrength force de l'etalonnage, de 0 (aucun) a 1 (teinte entiere)
 * @param desaturation desaturation, de 0 a 1
 * @param grain amplitude du grain, de 0 a 1
 * @param flicker amplitude du vacillement de la lumiere, de 0 a 1
 * @param lightmap courbe de la lightmap
 * @param fog brouillard
 * @param post post-traitement
 * @param neons neons qui eclairent
 */
public record LookProfile(@Nullable Identifier dimension, int id, float tintRed, float tintGreen, float tintBlue, float tintStrength,
        float desaturation, float grain, float flicker, Lightmap lightmap, Fog fog, Post post, Neons neons) {

    /** Aucun look : la couleur ressort telle quelle, la lightmap et le brouillard restent ceux de vanilla. */
    public static final LookProfile VANILLA = new LookProfile(null, 0, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, Lightmap.VANILLA, Fog.VANILLA, Post.NONE,
            Neons.NONE);

    /**
     * Les neons d'une dimension : un bloc, dont chaque exemplaire proche de la camera eclaire ce qui l'entoure, vacille
     * ou meurt selon le calendrier deterministe ({@code NeonSchedule}).
     *
     * @param block bloc des neons, {@code null} sans neons
     * @param radius portee de la lumiere d'un neon, en blocs
     * @param red couleur de la lumiere, rouge
     * @param green couleur de la lumiere, vert
     * @param blue couleur de la lumiere, bleu
     * @param strength force de la lumiere au pied du neon, de 0 a 2
     * @param flickering part des neons qui vacillent, de 0 a 1
     * @param dead part des neons morts, de 0 a 1 ; avec la part qui vacille, au plus 1
     */
    public record Neons(@Nullable Identifier block, float radius, float red, float green, float blue, float strength, float flickering, float dead) {

        /** Aucun neon. */
        public static final Neons NONE = new Neons(null, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f);

        /** {@code true} si la dimension a des neons. */
        public boolean any() {
            return this.block != null;
        }
    }

    /**
     * Courbe appliquee a la lightmap d'un monde, une fois par tick.
     *
     * @param floor luminosite minimale, de 0 a 1
     * @param warmth chaleur ajoutee, de 0 a 1 : le rouge monte, le bleu baisse
     * @param maxBrightness luminosite maximale, de 0 a 1
     */
    public record Lightmap(float floor, float warmth, float maxBrightness) {

        /** Lightmap vanilla : aucune courbe. */
        public static final Lightmap VANILLA = new Lightmap(0.0f, 0.0f, 1.0f);

        /** {@code true} si la courbe ne change rien. */
        public boolean vanilla() {
            return this.floor == 0.0f && this.warmth == 0.0f && this.maxBrightness == 1.0f;
        }
    }

    /**
     * Brouillard d'un monde : couleur et distances, a la place de celles de vanilla hors de l'eau, de la lave et de la
     * neige poudreuse.
     *
     * @param enabled {@code false} pour le brouillard vanilla
     * @param color couleur
     * @param start distance ou le brouillard commence
     * @param end distance ou il est total
     */
    public record Fog(boolean enabled, Vec3d color, float start, float end) {

        /** Brouillard vanilla. */
        public static final Fog VANILLA = new Fog(false, Vec3d.ZERO, 0.0f, 0.0f);
    }

    /**
     * Post-traitement, sur les seuls pixels du look.
     *
     * @param vignette force de la vignette, de 0 a 1
     * @param chromatic force de l'aberration chromatique, de 0 a 1
     * @param bloom {@code true} pour le bloom, en qualite complete
     * @param bloomThreshold luminosite a partir de laquelle un pixel rayonne, de 0 a 1
     * @param bloomStrength force du bloom ajoute, de 0 a 2
     */
    public record Post(float vignette, float chromatic, boolean bloom, float bloomThreshold, float bloomStrength) {

        /** Aucun post-traitement. */
        public static final Post NONE = new Post(0.0f, 0.0f, false, 0.7f, 0.6f);

        /** {@code true} si rien n'est a dessiner. */
        public boolean none() {
            return this.vignette == 0.0f && this.chromatic == 0.0f && !this.bloom;
        }
    }

    /** {@code true} pour le profil vanilla. */
    public boolean vanilla() {
        return this.id == 0;
    }

    /**
     * Le profil sous les options de confort du joueur. Sans flashs : ni vacillement de la lumiere de l'ecran, ni tube qui
     * vacille (sa part ramenee a zero : son bourdonnement, qui suit le meme calendrier, ne vacille pas non plus). Sans
     * bruit visuel : ni grain, ni vacillement de la lumiere, ni aberration chromatique. Teinte, lumiere, brouillard,
     * vignette, bloom, tubes morts et lumiere des neons restent.
     *
     * @param noFlashes option qui reduit les flashs
     * @param noNoise option qui reduit le bruit visuel
     * @return ce profil, ou une copie sans ce que les options retirent
     */
    public LookProfile comfort(boolean noFlashes, boolean noNoise) {
        if (!noFlashes && !noNoise) {
            return this;
        }
        Neons calmNeons = noFlashes
                ? new Neons(this.neons.block(), this.neons.radius(), this.neons.red(), this.neons.green(), this.neons.blue(), this.neons.strength(), 0.0f,
                        this.neons.dead())
                : this.neons;
        Post calmPost = noNoise ? new Post(this.post.vignette(), 0.0f, this.post.bloom(), this.post.bloomThreshold(), this.post.bloomStrength()) : this.post;
        return new LookProfile(this.dimension, this.id, this.tintRed, this.tintGreen, this.tintBlue, this.tintStrength, this.desaturation,
                noNoise ? 0.0f : this.grain, 0.0f, this.lightmap, this.fog, calmPost, calmNeons);
    }

    /**
     * Lit un profil.
     *
     * @param json contenu du fichier
     * @return le profil
     * @throws JsonParseException si le fichier est invalide : dimension ou identifiant absents ou hors bornes, valeurs hors de leur plage
     */
    public static LookProfile fromJson(JsonObject json) {
        Identifier dimension = Identifier.tryParse(string(json, "dimension"));
        if (dimension == null) {
            throw new JsonParseException("Dimension invalide : " + string(json, "dimension") + ".");
        }
        int id = json.has("id") ? json.get("id").getAsInt() : -1;
        if (id < 1 || id > StencilLookMask.MAX_LOOK) {
            throw new JsonParseException("Identifiant de look hors bornes (1 a " + StencilLookMask.MAX_LOOK + ") : " + id + ".");
        }
        float[] tint = floats(json, "tint", 4, new float[] {1.0f, 1.0f, 1.0f, 0.0f});
        for (int i = 0; i < 3; i++) {
            check("tint", tint[i], 0.0f, 4.0f);
        }
        check("tint[3]", tint[3], 0.0f, 1.0f);
        float desaturation = unit(json, "desaturation");
        float grain = unit(json, "grain");
        float flicker = unit(json, "flicker");
        return new LookProfile(dimension, id, tint[0], tint[1], tint[2], tint[3], desaturation, grain, flicker, lightmap(json), fog(json), post(json),
                neons(json));
    }

    private static Neons neons(JsonObject json) {
        if (!json.has("neons")) {
            return Neons.NONE;
        }
        JsonObject section = json.getAsJsonObject("neons");
        Identifier block = Identifier.tryParse(string(section, "block"));
        if (block == null) {
            throw new JsonParseException("Bloc des neons invalide : " + string(section, "block") + ".");
        }
        float radius = section.has("radius") ? section.get("radius").getAsFloat() : 6.0f;
        check("neons.radius", radius, 0.5f, 64.0f);
        float[] color = floats(section, "color", 3, new float[] {1.0f, 1.0f, 1.0f});
        for (int i = 0; i < 3; i++) {
            check("neons.color", color[i], 0.0f, 1.0f);
        }
        float strength = section.has("strength") ? section.get("strength").getAsFloat() : 0.5f;
        check("neons.strength", strength, 0.0f, 2.0f);
        float flickering = unit(section, "flickering");
        float dead = unit(section, "dead");
        if (flickering + dead > 1.0f) {
            throw new JsonParseException("Neons : flickering (" + flickering + ") et dead (" + dead + ") depassent 1 a eux deux.");
        }
        return new Neons(block, radius, color[0], color[1], color[2], strength, flickering, dead);
    }

    private static Lightmap lightmap(JsonObject json) {
        if (!json.has("lightmap")) {
            return Lightmap.VANILLA;
        }
        JsonObject section = json.getAsJsonObject("lightmap");
        float floor = unit(section, "floor");
        float warmth = unit(section, "warmth");
        float max = section.has("max_brightness") ? section.get("max_brightness").getAsFloat() : 1.0f;
        check("max_brightness", max, 0.0f, 1.0f);
        if (floor > max) {
            throw new JsonParseException("Lightmap : floor (" + floor + ") depasse max_brightness (" + max + ").");
        }
        return new Lightmap(floor, warmth, max);
    }

    private static Fog fog(JsonObject json) {
        if (!json.has("fog")) {
            return Fog.VANILLA;
        }
        JsonObject section = json.getAsJsonObject("fog");
        float[] color = floats(section, "color", 3, new float[] {0.0f, 0.0f, 0.0f});
        for (int i = 0; i < 3; i++) {
            check("fog.color", color[i], 0.0f, 1.0f);
        }
        float start = section.has("start") ? section.get("start").getAsFloat() : 0.0f;
        float end = section.has("end") ? section.get("end").getAsFloat() : 0.0f;
        if (start < 0.0f || end <= start) {
            throw new JsonParseException("Brouillard : start (" + start + ") et end (" + end + ") incoherents.");
        }
        return new Fog(true, new Vec3d(color[0], color[1], color[2]), start, end);
    }

    private static Post post(JsonObject json) {
        if (!json.has("post")) {
            return Post.NONE;
        }
        JsonObject section = json.getAsJsonObject("post");
        boolean bloom = section.has("bloom") && section.get("bloom").getAsBoolean();
        float threshold = section.has("bloom_threshold") ? section.get("bloom_threshold").getAsFloat() : Post.NONE.bloomThreshold();
        check("post.bloom_threshold", threshold, 0.0f, 1.0f);
        float strength = section.has("bloom_strength") ? section.get("bloom_strength").getAsFloat() : Post.NONE.bloomStrength();
        check("post.bloom_strength", strength, 0.0f, 2.0f);
        return new Post(unit(section, "vignette"), unit(section, "chromatic"), bloom, threshold, strength);
    }

    private static float unit(JsonObject json, String key) {
        float value = json.has(key) ? json.get(key).getAsFloat() : 0.0f;
        check(key, value, 0.0f, 1.0f);
        return value;
    }

    private static void check(String key, float value, float min, float max) {
        if (!(value >= min && value <= max)) {
            throw new JsonParseException("Valeur de " + key + " hors de [" + min + ", " + max + "] : " + value + ".");
        }
    }

    private static float[] floats(JsonObject json, String key, int count, float[] defaults) {
        if (!json.has(key)) {
            return defaults;
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != count) {
            throw new JsonParseException(key + " : " + count + " valeurs attendues, " + array.size() + " lues.");
        }
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Champ manquant : " + key + ".");
        }
        return element.getAsString();
    }
}
