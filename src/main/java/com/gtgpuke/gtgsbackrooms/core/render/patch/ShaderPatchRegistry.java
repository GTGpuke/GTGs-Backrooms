package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Les patchs charges avec les ressources et leurs bibliotheques GLSL, appliques a chaque shader vanilla au chargement.
 * Un patch invalide est refuse au chargement et journalise ; un shader qu'un patch ne sait pas patcher est journalise
 * une fois et reste vanilla.
 */
public final class ShaderPatchRegistry {

    /** Aucun patch : chaque shader reste tel quel. */
    public static final ShaderPatchRegistry EMPTY = new ShaderPatchRegistry(List.of(), Map.of());

    private final List<ShaderPatch> patches;
    private final Map<Identifier, String> includes;
    /** Refus deja journalises (patch et shader) : un shader se charge a chaque rechargement des ressources. */
    private final Set<String> logged = new HashSet<>();

    private ShaderPatchRegistry(List<ShaderPatch> patches, Map<Identifier, String> includes) {
        this.patches = patches;
        this.includes = includes;
    }

    /**
     * Construit le registre depuis les fichiers de patch et un lecteur de bibliotheques.
     *
     * @param patchFiles contenu de chaque fichier de patch, par nom
     * @param includeReader lit une bibliotheque GLSL par identifiant complet ({@code shaders/include/...}), {@code null} si absente
     * @return le registre ; les fichiers invalides et les bibliotheques absentes sont journalises et ignores
     */
    public static ShaderPatchRegistry of(Map<String, String> patchFiles, Function<Identifier, @Nullable String> includeReader) {
        List<ShaderPatch> patches = new ArrayList<>();
        Map<Identifier, String> includes = new HashMap<>();
        List<String> names = new ArrayList<>(patchFiles.keySet());
        names.sort(null);
        for (String name : names) {
            ShaderPatch patch;
            try {
                JsonObject json = JsonParser.parseString(patchFiles.get(name)).getAsJsonObject();
                patch = ShaderPatch.fromJson(name, json);
            } catch (JsonParseException | IllegalStateException exception) {
                ModConstants.LOGGER.error("[Look] Patch de shaders {} refuse ({}).", name, exception.getMessage());
                continue;
            }
            boolean complete = true;
            for (Identifier include : patch.imports()) {
                if (!includes.containsKey(include)) {
                    String text = includeReader.apply(include);
                    if (text == null) {
                        ModConstants.LOGGER.error("[Look] Patch de shaders {} refuse : bibliotheque {} introuvable.", name, include);
                        complete = false;
                        break;
                    }
                    includes.put(include, text);
                }
            }
            if (complete) {
                patches.add(patch);
            }
        }
        return patches.isEmpty() ? EMPTY : new ShaderPatchRegistry(List.copyOf(patches), Map.copyOf(includes));
    }

    /** Nombre de patchs charges. */
    public int size() {
        return this.patches.size();
    }

    /**
     * Applique au shader les patchs qui le ciblent, dans l'ordre des fichiers. Un seul patch s'applique par etage : le
     * premier enveloppe le {@code main}, et les suivants, trouvant un {@code main} deja enveloppe, ne changent rien.
     *
     * @param kind etage du shader
     * @param name nom du shader, sans extension ({@code rendertype_solid})
     * @param source source compilee
     * @return la source patchee, ou la source recue (meme instance) si aucun patch ne s'applique
     */
    public String patch(ShaderKind kind, String name, String source) {
        String current = source;
        for (int i = 0; i < this.patches.size(); i++) {
            ShaderPatch patch = this.patches.get(i);
            if (patch.kind() != kind || !patch.appliesTo(name)) {
                continue;
            }
            List<String> texts = new ArrayList<>(patch.imports().size());
            for (Identifier include : patch.imports()) {
                texts.add(this.includes.get(include));
            }
            ShaderPatcher.Result result = ShaderPatcher.apply(kind, current, texts, patch.injections());
            if (result.refusal() != null) {
                if (this.logged.add(patch.id() + ':' + name)) {
                    ModConstants.LOGGER.warn("[Look] Shader {} non patche par {} ({}) : rendu vanilla.", name, patch.id(), result.refusal());
                }
                continue;
            }
            current = result.source();
        }
        return current;
    }
}
