package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.KHRDebug;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Erreurs OpenGL pendant les tests du client : la sortie de debogage du pilote (KHR_debug), synchrone, remplace celle de
 * vanilla le temps des tests ; chaque erreur est gardee avec la pile d'appels qui l'a provoquee, et fait echouer le test
 * en cours. Un pilote strict (Mesa, celui de la CI) signale ce qu'un pilote tolerant laisse passer sans rien dire.
 */
public final class ClientTestGlErrors {

    /** Erreurs gardees au plus par test : la premiere suffit a trouver la cause, les suivantes la repetent. */
    private static final int KEPT = 4;
    private static final List<String> ERRORS = new ArrayList<>();
    private static int count;
    private static GLDebugMessageCallback callback;

    private ClientTestGlErrors() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche la sortie de debogage du pilote (thread de rendu, contexte courant) ; sans KHR_debug, rien. */
    static void install() {
        GLCapabilities capabilities = GL.getCapabilities();
        if (!capabilities.GL_KHR_debug) {
            ModConstants.LOGGER.warn("[Test client] Pas de KHR_debug : les erreurs OpenGL ne sont pas relevees.");
            return;
        }
        callback = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
            if (type == KHRDebug.GL_DEBUG_TYPE_ERROR) {
                record(GLDebugMessageCallback.getMessage(length, message));
            }
        });
        GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT);
        GL11.glEnable(KHRDebug.GL_DEBUG_OUTPUT_SYNCHRONOUS);
        KHRDebug.glDebugMessageCallback(callback, 0L);
        ModConstants.LOGGER.info("[Test client] Erreurs OpenGL relevees (sortie de debogage synchrone).");
    }

    /** Releve une erreur avec la pile d'appels qui l'a provoquee : celles du pilote, et celles que le mod de test guette. */
    public static void record(String message) {
        count++;
        if (ERRORS.size() < KEPT) {
            StringBuilder text = new StringBuilder(message);
            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                text.append("\n    at ").append(element);
            }
            ERRORS.add(text.toString());
        }
    }

    /** Nombre d'erreurs depuis le dernier {@link #reset}. */
    static int count() {
        return count;
    }

    /** Les premieres erreurs depuis le dernier {@link #reset}, avec leur pile d'appels. */
    static String describe() {
        return count + " erreur(s) OpenGL, dont : " + String.join("\n---\n", ERRORS);
    }

    static void reset() {
        count = 0;
        ERRORS.clear();
    }
}
