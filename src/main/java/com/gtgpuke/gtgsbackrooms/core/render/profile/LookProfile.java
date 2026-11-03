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
 * @param tintStrength force de la teinte, de 0 (aucune) a 1 (teinte entiere)
 * @param desaturation desaturation, de 0 a 1
 * @param grain amplitude du grain, de 0 a 1
 * @param flicker amplitude du vacillement de la lumiere, de 0 a 1
 * @param lightmap courbe de la lightmap
 * @param fog brouillard
 * @param post post-traitement
 * @param neons neons qui eclairent
 * @param grade etalonnage applique apres la desaturation et la teinte
 * @param bakedLight lumiere cuite du terrain, a la place de la lightmap
 */
public record LookProfile(@Nullable Identifier dimension, int id, float tintRed, float tintGreen, float tintBlue, float tintStrength,
        float desaturation, float grain, float flicker, Lightmap lightmap, Fog fog, Post post, Neons neons, Grade grade, BakedLight bakedLight) {

    /** Aucun look : la couleur ressort telle quelle, la lightmap et le brouillard restent ceux de vanilla. */
    public static final LookProfile VANILLA = new LookProfile(null, 0, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, Lightmap.VANILLA, Fog.VANILLA, Post.NONE,
            Neons.NONE, Grade.NEUTRAL, BakedLight.NONE);

    /**
     * Lumiere cuite du terrain : chaque surface recoit la lumiere des dalles et de leurs rebonds, calculee par cellule sur
     * les blocs du monde, a la place de la lightmap et de l'ombrage vanilla par face.
     *
     * @param emission lumiere d'une dalle, multipliee par la couleur des neons, 0 sans lumiere cuite
     * @param ambient lumiere de fond de toute surface, par composante
     * @param reach portee d'une dalle, en blocs
     * @param size cote du rectangle qui eclaire, en blocs
     * @param angle angle ou une dalle cesse d'eclairer, compte depuis sa normale, en degres
     * @param occlusion ce qu'une surface perd dans un coin ferme, de 0 a 1
     * @param relief relief que le cache de surfaces tire des textures, de 0 (aucun) a 4
     * @param haze force des halos autour des neons, de 0 (aucun) a 1
     * @param sheen force de ce que les matieres lisses renvoient des dalles, de 0 (rien) a 2
     * @param shoulder niveau au-dessus duquel les hautes lumieres se tassent vers 1 ; 1 pour un simple plafond
     * @param bounceColor ce que les surfaces ajoutent a la couleur de leur bloc dans ce qu'elles renvoient, a chaque rebond : la lumiere qui a
     *         tourne dans une piece y prend sa couleur, sans toucher a ce qu'une dalle eclaire directement
     * @param lightLimit ce que vaut au plus la lumiere d'une surface, composante par composante, 0 sans limite : sous une dalle, toute lumiere finit
     *         par valoir cette limite, et la surface a la couleur de sa texture ; ce qui n'a que des rebonds garde leur couleur
     * @param detail part du contraste des textures gardee : 1 les garde, 0 rend la couleur moyenne de chaque bloc
     * @param exposure ce qui multiplie la lumiere des surfaces
     * @param direct ce que vaut la lumiere directe a l'image, les rebonds restant entiers
     * @param traced ce que change la lumiere tracee
     */
    public record BakedLight(float emission, Vec3d ambient, float reach, float size, float angle, float shadowSize, float occlusion, float relief, float haze, float sheen,
            float shoulder, float toe, float pixelLight, float darkPixels, float bounce, Vec3d bounceColor, float lightLimit, float detail, float exposure, float direct,
            Traced traced) {

        /** Aucune lumiere cuite : la lightmap reste. */
        public static final BakedLight NONE = new BakedLight(0.0f, Vec3d.ZERO, 0.0f, 1.0f, 90.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                new Vec3d(1.0, 1.0, 1.0), 0.0f, 1.0f, 1.0f, 1.0f, new Traced(1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f));

        /** {@code true} si ce profil cuit sa lumiere. */
        public boolean any() {
            return this.emission > 0.0f;
        }

        /** Cette lumiere, sans le relief, les halos ou le reflet que le palier et les options retirent. */
        public BakedLight masked(boolean keepRelief, boolean keepHaze, boolean keepSheen) {
            return new BakedLight(this.emission, this.ambient, this.reach, this.size, this.angle, this.shadowSize, this.occlusion, keepRelief ? this.relief : 0.0f,
                    keepHaze ? this.haze : 0.0f, keepSheen ? this.sheen : 0.0f, this.shoulder, this.toe, this.pixelLight, this.darkPixels, this.bounce, this.bounceColor,
                    this.lightLimit, this.detail, this.exposure, this.direct, this.traced);
        }

        /** Cette lumiere sous le trace : ce que le profil demande a la lumiere tracee remplace ses valeurs. */
        public BakedLight tracedLight() {
            return new BakedLight(this.emission, this.ambient, this.reach, this.size, this.angle, this.traced.shadowSize(), this.occlusion, this.traced.relief(), this.haze,
                    this.traced.sheen(), this.traced.shoulder(), this.traced.toe(), this.pixelLight, this.darkPixels, this.traced.bounce(), this.bounceColor,
                    this.lightLimit, this.traced.detail(), this.traced.exposure(), this.traced.direct(), this.traced);
        }
    }

    /**
     * Ce que change la lumiere tracee : une image eclairee comme une photo, presque toute en lumiere indirecte. Une valeur absente du
     * profil garde celle de la lumiere cuite ; la matiere reste celle du jeu, ses pixels compris.
     *
     * @param shadowSize cote de ce que les rayons des ombres visent, en blocs : plus grand que la dalle, les ombres s'etalent
     * @param relief relief que le cache de surfaces tire des textures
     * @param sheen force de ce que les matieres lisses renvoient des dalles
     * @param bounce part de la lumiere que renvoient les surfaces
     * @param toe pied de la courbe
     * @param detail part du contraste des textures gardee : 1 les garde, 0 rend la couleur moyenne de chaque bloc
     * @param exposure ce qui multiplie la lumiere des surfaces : les rebonds apportent bien plus que la lumiere cuite
     * @param direct ce que vaut la lumiere directe a l'image, les rebonds restant entiers : plus basse, la lumiere s'egalise
     * @param shoulder epaule des hautes lumieres
     */
    public record Traced(float shadowSize, float relief, float sheen, float bounce, float toe, float detail, float exposure, float direct, float shoulder) {
    }

    /**
     * Etalonnage d'une couleur, apres la desaturation et la teinte du profil : par composante, la couleur est multipliee par
     * la pente, decalee, portee a la puissance, puis la saturation s'applique autour de sa luminance. Le tout forme la LUT
     * de la dimension ({@code LookLut}), appliquee a chaque pixel du look.
     *
     * @param slope pente par composante (rouge, vert, bleu), de 0 a 4
     * @param offset decalage par composante, de -1 a 1
     * @param power puissance par composante, de 0,1 a 4
     * @param saturation saturation finale, de 0 a 2 (1 : inchangee)
     */
    public record Grade(Vec3d slope, Vec3d offset, Vec3d power, float saturation) {

        /** Cet etalonnage, pente multipliee composante par composante, puissance et saturation multipliees. */
        public Grade adjusted(Vec3d slopeFactor, float powerFactor, float saturationFactor) {
            return new Grade(this.slope.multiply(slopeFactor), this.offset, this.power.multiply(powerFactor), this.saturation * saturationFactor);
        }

        /** Aucun etalonnage. */
        public static final Grade NEUTRAL = new Grade(new Vec3d(1.0, 1.0, 1.0), Vec3d.ZERO, new Vec3d(1.0, 1.0, 1.0), 1.0f);
    }

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
    public record Neons(@Nullable Identifier block, float radius, float red, float green, float blue, float strength, float flickering, float dead, float blackout) {

        /** Aucun neon. */
        public static final Neons NONE = new Neons(null, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f);

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

        /** Ce post, vignette augmentee (bornee a 1), bloom multiplie. */
        public Post adjusted(float vignetteAdded, float bloomFactor) {
            return new Post(Math.min(this.vignette + vignetteAdded, 1.0f), this.chromatic, this.bloom, this.bloomThreshold, this.bloomStrength * bloomFactor);
        }

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

    /** Ce look sans rien de ce que le moteur ajoute : le palier desactive. L'identifiant reste : le stencil le porte toujours. */
    public LookProfile plain() {
        return new LookProfile(this.dimension, this.id, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, this.lightmap, this.fog, Post.NONE, Neons.NONE, Grade.NEUTRAL,
                BakedLight.NONE);
    }

    /** Ce look sous un style : grain, desaturation, post, etalonnage et lumiere cuite remplaces. */
    public LookProfile styled(float nextGrain, float nextDesaturation, Post nextPost, Grade nextGrade, BakedLight nextBaked) {
        return new LookProfile(this.dimension, this.id, this.tintRed, this.tintGreen, this.tintBlue, this.tintStrength, nextDesaturation, nextGrain, this.flicker,
                this.lightmap, this.fog, nextPost, this.neons, nextGrade, nextBaked);
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
                        this.neons.dead(), this.neons.blackout())
                : this.neons;
        Post calmPost = noNoise ? new Post(this.post.vignette(), 0.0f, this.post.bloom(), this.post.bloomThreshold(), this.post.bloomStrength()) : this.post;
        return new LookProfile(this.dimension, this.id, this.tintRed, this.tintGreen, this.tintBlue, this.tintStrength, this.desaturation,
                noNoise ? 0.0f : this.grain, 0.0f, this.lightmap, this.fog, calmPost, calmNeons, this.grade, this.bakedLight);
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
                neons(json), grade(json), bakedLight(json));
    }

    private static BakedLight bakedLight(JsonObject json) {
        if (!json.has("baked_light")) {
            return BakedLight.NONE;
        }
        if (!json.has("neons")) {
            throw new JsonParseException("baked_light demande une section neons : ce sont eux qui eclairent.");
        }
        JsonObject section = json.getAsJsonObject("baked_light");
        float emission = section.has("emission") ? section.get("emission").getAsFloat() : 8.0f;
        check("baked_light.emission", emission, 0.1f, 64.0f);
        float[] ambient = floats(section, "ambient", 3, new float[] {0.0f, 0.0f, 0.0f});
        for (int i = 0; i < 3; i++) {
            check("baked_light.ambient", ambient[i], 0.0f, 1.0f);
        }
        float reach = section.has("reach") ? section.get("reach").getAsFloat() : 12.0f;
        check("baked_light.reach", reach, 1.0f, 15.0f);
        float size = section.has("size") ? section.get("size").getAsFloat() : 1.0f;
        check("baked_light.size", size, 0.5f, 8.0f);
        float angle = section.has("angle") ? section.get("angle").getAsFloat() : 90.0f;
        check("baked_light.angle", angle, 10.0f, 90.0f);
        float occlusion = section.has("occlusion") ? section.get("occlusion").getAsFloat() : 0.0f;
        check("baked_light.occlusion", occlusion, 0.0f, 1.0f);
        float relief = section.has("relief") ? section.get("relief").getAsFloat() : 0.0f;
        check("baked_light.relief", relief, 0.0f, 4.0f);
        float haze = section.has("haze") ? section.get("haze").getAsFloat() : 0.0f;
        check("baked_light.haze", haze, 0.0f, 1.0f);
        float sheen = section.has("sheen") ? section.get("sheen").getAsFloat() : 0.0f;
        check("baked_light.sheen", sheen, 0.0f, 2.0f);
        float shoulder = section.has("shoulder") ? section.get("shoulder").getAsFloat() : 1.0f;
        check("baked_light.shoulder", shoulder, 0.1f, 1.0f);
        float shadowSize = section.has("shadow_size") ? section.get("shadow_size").getAsFloat() : 1.0f;
        check("baked_light.shadow_size", shadowSize, 0.0f, 8.0f);
        float toe = section.has("toe") ? section.get("toe").getAsFloat() : 0.0f;
        check("baked_light.toe", toe, 0.0f, 1.0f);
        float pixelLight = section.has("pixel_light") ? section.get("pixel_light").getAsFloat() : 0.0f;
        check("baked_light.pixel_light", pixelLight, 0.0f, 1.0f);
        float darkPixels = section.has("dark_pixels") ? section.get("dark_pixels").getAsFloat() : 0.0f;
        check("baked_light.dark_pixels", darkPixels, 0.0f, 2.0f);
        float bounce = section.has("bounce") ? section.get("bounce").getAsFloat() : 1.0f;
        check("baked_light.bounce", bounce, 0.0f, 2.0f);
        float[] bounceColor = floats(section, "bounce_color", 3, new float[] {1.0f, 1.0f, 1.0f});
        for (float part : bounceColor) {
            check("baked_light.bounce_color", part, 0.0f, 2.0f);
        }
        float lightLimit = section.has("light_limit") ? section.get("light_limit").getAsFloat() : 0.0f;
        check("baked_light.light_limit", lightLimit, 0.0f, 4.0f);
        float detail = section.has("detail") ? section.get("detail").getAsFloat() : 1.0f;
        check("baked_light.detail", detail, 0.0f, 1.0f);
        float exposure = section.has("exposure") ? section.get("exposure").getAsFloat() : 1.0f;
        check("baked_light.exposure", exposure, 0.05f, 4.0f);
        float direct = section.has("direct") ? section.get("direct").getAsFloat() : 1.0f;
        check("baked_light.direct", direct, 0.0f, 4.0f);
        return new BakedLight(emission, new Vec3d(ambient[0], ambient[1], ambient[2]), reach, size, angle, shadowSize, occlusion, relief, haze, sheen, shoulder, toe,
                pixelLight, darkPixels, bounce, new Vec3d(bounceColor[0], bounceColor[1], bounceColor[2]), lightLimit, detail, exposure, direct, traced(section,
                new Traced(shadowSize, relief, sheen, bounce, toe, detail, exposure, direct, shoulder)));
    }

    /** La section {@code traced} d'une lumiere cuite ; ce qu'elle ne dit pas garde la valeur de {@code plain}. */
    private static Traced traced(JsonObject baked, Traced plain) {
        if (!baked.has("traced")) {
            return plain;
        }
        JsonObject section = baked.getAsJsonObject("traced");
        float tracedShadow = section.has("shadow_size") ? section.get("shadow_size").getAsFloat() : plain.shadowSize();
        check("baked_light.traced.shadow_size", tracedShadow, 0.0f, 8.0f);
        float tracedRelief = section.has("relief") ? section.get("relief").getAsFloat() : plain.relief();
        check("baked_light.traced.relief", tracedRelief, 0.0f, 4.0f);
        float tracedSheen = section.has("sheen") ? section.get("sheen").getAsFloat() : plain.sheen();
        check("baked_light.traced.sheen", tracedSheen, 0.0f, 2.0f);
        float tracedBounce = section.has("bounce") ? section.get("bounce").getAsFloat() : plain.bounce();
        check("baked_light.traced.bounce", tracedBounce, 0.0f, 2.0f);
        float tracedToe = section.has("toe") ? section.get("toe").getAsFloat() : plain.toe();
        check("baked_light.traced.toe", tracedToe, 0.0f, 1.0f);
        float detail = section.has("detail") ? section.get("detail").getAsFloat() : 1.0f;
        check("baked_light.traced.detail", detail, 0.0f, 1.0f);
        float exposure = section.has("exposure") ? section.get("exposure").getAsFloat() : 1.0f;
        check("baked_light.traced.exposure", exposure, 0.05f, 4.0f);
        float direct = section.has("direct") ? section.get("direct").getAsFloat() : 1.0f;
        check("baked_light.traced.direct", direct, 0.0f, 4.0f);
        float tracedShoulder = section.has("shoulder") ? section.get("shoulder").getAsFloat() : plain.shoulder();
        check("baked_light.traced.shoulder", tracedShoulder, 0.1f, 1.0f);
        return new Traced(tracedShadow, tracedRelief, tracedSheen, tracedBounce, tracedToe, detail, exposure, direct, tracedShoulder);
    }

    private static Grade grade(JsonObject json) {
        if (!json.has("grade")) {
            return Grade.NEUTRAL;
        }
        JsonObject section = json.getAsJsonObject("grade");
        float[] slope = floats(section, "slope", 3, new float[] {1.0f, 1.0f, 1.0f});
        float[] offset = floats(section, "offset", 3, new float[] {0.0f, 0.0f, 0.0f});
        float[] power = floats(section, "power", 3, new float[] {1.0f, 1.0f, 1.0f});
        for (int i = 0; i < 3; i++) {
            check("grade.slope", slope[i], 0.0f, 4.0f);
            check("grade.offset", offset[i], -1.0f, 1.0f);
            check("grade.power", power[i], 0.1f, 4.0f);
        }
        float saturation = section.has("saturation") ? section.get("saturation").getAsFloat() : 1.0f;
        check("grade.saturation", saturation, 0.0f, 2.0f);
        return new Grade(new Vec3d(slope[0], slope[1], slope[2]), new Vec3d(offset[0], offset[1], offset[2]), new Vec3d(power[0], power[1], power[2]), saturation);
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
        return new Neons(block, radius, color[0], color[1], color[2], strength, flickering, dead, unit(section, "blackout"));
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
