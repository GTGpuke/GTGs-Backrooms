package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.util.Identifier;

/**
 * Patch declaratif d'un etage de shaders vanilla, lu dans {@code assets/gtgs_backrooms/shader_patches/*.json} : les
 * shaders cibles (motifs a {@code *}, moins les exclus), les bibliotheques GLSL a inserer en tete et les injections
 * ({@link InjectionPoint}). Le patch s'applique a chaque shader cible au chargement ({@link ShaderPatcher}).
 *
 * @param id nom du fichier, pour les journaux
 * @param kind etage cible
 * @param targets motifs des noms de shaders cibles ({@code rendertype_*})
 * @param excludes motifs des noms a laisser vanilla, prioritaires sur les cibles
 * @param imports bibliotheques GLSL inserees en tete, {@code assets/<ns>/shaders/include/<chemin>}
 * @param injections code insere a chaque point, dans l'ordre du fichier
 */
public record ShaderPatch(String id, ShaderKind kind, List<Pattern> targets, List<Pattern> excludes, List<Identifier> imports,
        List<Injection> injections) {

    private static final String INCLUDE_PREFIX = "shaders/include/";

    /**
     * Une insertion : au point {@link InjectionPoint#OUTPUT}, {@code code} est le nom de la fonction par laquelle passe
     * la sortie ; ailleurs, le code insere tel quel.
     *
     * @param point ou inserer
     * @param code code, ou nom de fonction pour la sortie
     */
    public record Injection(InjectionPoint point, String code) {
    }

    /**
     * Lit un patch.
     *
     * @param id nom du fichier, pour les journaux
     * @param json contenu du fichier
     * @return le patch
     * @throws JsonParseException si le fichier est invalide : etage ou point inconnu, cible absente, motif vide
     */
    public static ShaderPatch fromJson(String id, JsonObject json) {
        ShaderKind kind = ShaderKind.byKey(string(json, "stage"));
        if (kind == null) {
            throw new JsonParseException("Etage inconnu dans " + id + " : " + string(json, "stage") + ".");
        }
        List<Pattern> targets = patterns(id, json, "targets");
        if (targets.isEmpty()) {
            throw new JsonParseException("Aucun shader cible dans " + id + ".");
        }
        List<Pattern> excludes = patterns(id, json, "excludes");
        List<Identifier> imports = new ArrayList<>();
        for (String name : strings(json, "imports")) {
            Identifier include = Identifier.tryParse(name);
            if (include == null) {
                throw new JsonParseException("Bibliotheque invalide dans " + id + " : " + name + ".");
            }
            imports.add(Identifier.of(include.getNamespace(), INCLUDE_PREFIX + include.getPath()));
        }
        List<Injection> injections = new ArrayList<>();
        if (json.has("inject")) {
            for (JsonElement element : json.getAsJsonArray("inject")) {
                injections.add(injection(id, kind, element.getAsJsonObject()));
            }
        }
        if (injections.isEmpty()) {
            throw new JsonParseException("Aucune injection dans " + id + ".");
        }
        return new ShaderPatch(id, kind, List.copyOf(targets), List.copyOf(excludes), List.copyOf(imports), List.copyOf(injections));
    }

    /** {@code true} si le shader de ce nom (sans extension) est une cible du patch et n'en est pas exclu. */
    public boolean appliesTo(String shaderName) {
        return matches(this.targets, shaderName) && !matches(this.excludes, shaderName);
    }

    private static boolean matches(List<Pattern> patterns, String name) {
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Injection injection(String id, ShaderKind kind, JsonObject json) {
        InjectionPoint point = InjectionPoint.byKey(string(json, "point"));
        if (point == null) {
            throw new JsonParseException("Point d'injection inconnu dans " + id + " : " + string(json, "point") + ".");
        }
        if (point == InjectionPoint.OUTPUT) {
            if (kind != ShaderKind.FRAGMENT) {
                throw new JsonParseException("La sortie ne se patche que dans un fragment shader (" + id + ").");
            }
            String function = string(json, "function");
            if (!function.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new JsonParseException("Nom de fonction invalide dans " + id + " : " + function + ".");
            }
            return new Injection(point, function);
        }
        String code = string(json, "code");
        if (code.isBlank()) {
            throw new JsonParseException("Code vide dans " + id + " au point " + point.key() + ".");
        }
        return new Injection(point, code);
    }

    private static List<Pattern> patterns(String id, JsonObject json, String key) {
        List<Pattern> patterns = new ArrayList<>();
        for (String glob : strings(json, key)) {
            if (glob.isBlank()) {
                throw new JsonParseException("Motif vide dans " + id + " (" + key + ").");
            }
            patterns.add(Pattern.compile(Pattern.quote(glob).replace("*", "\\E.*\\Q")));
        }
        return patterns;
    }

    private static List<String> strings(JsonObject json, String key) {
        List<String> values = new ArrayList<>();
        if (json.has(key)) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement element : array) {
                values.add(element.getAsString());
            }
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
