package com.gtgpuke.gtgsbackrooms.compat.iris;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Iris present hors de la plage verifiee ({@link IrisVersion}) : le module de compatibilite ne s'applique pas, et sous un
 * pack de shaders le pack rend aussi les Backrooms, le look du mod pose sur son image. Le joueur en est prevenu a chaque
 * connexion, le journal au demarrage. Aucune classe d'Iris n'est touchee.
 */
public final class IrisVersionNotice {

    private IrisVersionNotice() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Au demarrage du client : rien si Iris est absent ou dans la plage verifiee. */
    public static void register() {
        if (!IrisVersion.present() || IrisVersion.supported()) {
            return;
        }
        String version = IrisVersion.version();
        ModConstants.LOGGER.warn("[Look] Mod de shaders Iris {} hors de la plage verifiee ({}) : sous un pack, il rend aussi les Backrooms, le look du mod pose "
                + "sur son image.", version, IrisVersion.SUPPORTED);
        // JOIN part a la fin de onGameJoin, deja sur le thread de rendu : le message s'ecrit sans attendre.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> warn(client, version));
    }

    private static void warn(MinecraftClient client, String version) {
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.translatable("compat.gtgs_backrooms.iris.unsupported", version, IrisVersion.SUPPORTED));
        }
    }
}
