package com.gtgpuke.gtgsbackrooms.core.render.fbo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.util.Identifier;

/**
 * Un framebuffer du moteur de shaders declare en donnees ({@code assets/gtgs_backrooms/framebuffers/*.json}) : sa
 * taille relative a l'ecran, s'il a une profondeur, et le filtrage de sa texture. Le seul format est {@code RGBA8},
 * tenu partout ; un autre format est refuse au chargement. Cree a la demande et recree au seul redimensionnement
 * ({@code FramebufferRegistry}).
 *
 * @param name identifiant du framebuffer
 * @param scale taille relative a l'ecran, de 0 exclu a 1
 * @param depth {@code true} pour un attachement de profondeur
 * @param linear {@code true} pour un filtrage lineaire de la texture, au plus proche sinon
 */
public record FramebufferSpec(Identifier name, float scale, boolean depth, boolean linear) {

    private static final String FORMAT = "RGBA8";

    /**
     * Lit une declaration.
     *
     * @param json contenu du fichier
     * @return la declaration
     * @throws JsonParseException si le fichier est invalide : nom absent ou invalide, echelle hors de ]0, 1], format ou filtre inconnu
     */
    public static FramebufferSpec fromJson(JsonObject json) {
        JsonElement nameElement = json.get("name");
        Identifier name = nameElement != null && nameElement.isJsonPrimitive() ? Identifier.tryParse(nameElement.getAsString()) : null;
        if (name == null) {
            throw new JsonParseException("Nom de framebuffer absent ou invalide.");
        }
        float scale = json.has("scale") ? json.get("scale").getAsFloat() : 1.0f;
        if (!(scale > 0.0f && scale <= 1.0f)) {
            throw new JsonParseException("Echelle hors de ]0, 1] pour " + name + " : " + scale + ".");
        }
        String format = json.has("format") ? json.get("format").getAsString() : FORMAT;
        if (!format.equals(FORMAT)) {
            throw new JsonParseException("Format inconnu pour " + name + " : " + format + " (seul " + FORMAT + " est tenu partout).");
        }
        boolean depth = json.has("depth") && json.get("depth").getAsBoolean();
        String filter = json.has("filter") ? json.get("filter").getAsString() : "nearest";
        boolean linear;
        switch (filter) {
            case "linear" -> linear = true;
            case "nearest" -> linear = false;
            default -> throw new JsonParseException("Filtre inconnu pour " + name + " : " + filter + ".");
        }
        return new FramebufferSpec(name, scale, depth, linear);
    }

    /** Largeur pour cette largeur d'ecran, un pixel au moins. */
    public int width(int screenWidth) {
        return Math.max(1, Math.round(screenWidth * this.scale));
    }

    /** Hauteur pour cette hauteur d'ecran, un pixel au moins. */
    public int height(int screenHeight) {
        return Math.max(1, Math.round(screenHeight * this.scale));
    }
}
