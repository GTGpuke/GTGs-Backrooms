package com.gtgpuke.gtgsbackrooms.compat;

import com.gtgpuke.gtgsbackrooms.compat.iris.IrisVersionNotice;

/**
 * Initialisation cote client des modules de compatibilite : le point d'entree du mod n'en connait que ce point d'acces,
 * sans nommer aucun autre mod. Chaque module ne fait rien si son mod est absent.
 */
public final class CompatClient {

    private CompatClient() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A l'initialisation du client. */
    public static void register() {
        IrisVersionNotice.register();
    }
}
