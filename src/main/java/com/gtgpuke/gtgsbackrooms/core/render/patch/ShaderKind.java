package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/** Etage d'un shader vanilla, tel que les patchs le ciblent ; independant des classes du jeu pour rester testable a sec. */
public enum ShaderKind {

    /** Vertex shader ({@code .vsh}). */
    VERTEX("vertex"),
    /** Fragment shader ({@code .fsh}) : une seule sortie {@code out vec4}, par laquelle passe le look. */
    FRAGMENT("fragment");

    private final String key;

    ShaderKind(String key) {
        this.key = key;
    }

    /** Nom de l'etage dans les fichiers de patch. */
    public String key() {
        return this.key;
    }

    /** Etage designe par son nom dans un fichier de patch, ou {@code null} s'il n'existe pas. */
    public static @Nullable ShaderKind byKey(String key) {
        String wanted = key.toLowerCase(Locale.ROOT);
        for (ShaderKind kind : values()) {
            if (kind.key.equals(wanted)) {
                return kind;
            }
        }
        return null;
    }
}
