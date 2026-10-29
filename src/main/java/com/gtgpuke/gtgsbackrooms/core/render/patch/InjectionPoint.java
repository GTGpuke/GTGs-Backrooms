package com.gtgpuke.gtgsbackrooms.core.render.patch;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * Ou un patch declaratif insere son code dans un shader vanilla : le code vanilla n'est jamais modifie ligne a ligne,
 * sa fonction {@code main} est renommee et enveloppee par une nouvelle {@code main} ({@link ShaderPatcher}).
 */
public enum InjectionPoint {

    /** Juste apres la directive {@code #version} : uniformes, fonctions et varyings ajoutes. */
    HEAD("head"),
    /** Dans la nouvelle {@code main}, avant l'appel de la {@code main} vanilla. */
    MAIN_BEFORE("main_before"),
    /** Dans la nouvelle {@code main}, apres l'appel de la {@code main} vanilla. */
    MAIN_AFTER("main_after"),
    /** Fragment seulement : la variable de sortie declaree passe par la fonction donnee, apres la {@code main} vanilla. */
    OUTPUT("output");

    private final String key;

    InjectionPoint(String key) {
        this.key = key;
    }

    /** Nom du point dans les fichiers de patch. */
    public String key() {
        return this.key;
    }

    /** Point designe par son nom dans un fichier de patch, ou {@code null} s'il n'existe pas. */
    public static @Nullable InjectionPoint byKey(String key) {
        String wanted = key.toLowerCase(Locale.ROOT);
        for (InjectionPoint point : values()) {
            if (point.key.equals(wanted)) {
                return point;
            }
        }
        return null;
    }
}
