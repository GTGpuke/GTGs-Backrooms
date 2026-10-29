package com.gtgpuke.gtgsbackrooms.compat.iris;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

/**
 * Le mod de shaders Iris est-il la, dans une version dont le module de compatibilite a verifie les classes internes
 * (gestionnaire de pipelines, pipeline vanilla, reglages globaux : identiques de 1.8.8 a 1.8.14-beta.1) ? Lu par Fabric
 * Loader seul : aucune classe d'Iris n'est chargee, ce qui permet de le demander pendant le demarrage de Mixin.
 */
public final class IrisVersion {

    /**
     * Plage de versions dont les classes visees ont ete verifiees, bornee a la derniere verifiee : les mixins du module
     * sont requis, et une version non verifiee qui renommerait un membre vise empecherait le jeu de demarrer. A elargir a
     * chaque version verifiee.
     */
    public static final String SUPPORTED = ">=1.8.8 <=1.8.14-beta.1";
    private static final String MOD_ID = "iris";
    private static final boolean SUPPORTED_HERE = check();

    private IrisVersion() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si Iris est la, dans la plage verifiee. */
    public static boolean supported() {
        return SUPPORTED_HERE;
    }

    /** {@code true} si Iris est la, quelle que soit sa version. */
    public static boolean present() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** Version d'Iris telle que son JAR la declare, ou {@code null} sans Iris. */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(MOD_ID).map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    private static boolean check() {
        ModContainer iris = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
        return iris != null && inRange(iris.getMetadata().getVersion());
    }

    /** {@code true} si cette version d'Iris est dans la plage verifiee ({@link #SUPPORTED}). */
    static boolean inRange(Version version) {
        try {
            return VersionPredicate.parse(SUPPORTED).test(version);
        } catch (VersionParsingException exception) {
            throw new IllegalStateException("Plage de versions invalide : " + SUPPORTED + ".", exception);
        }
    }
}
