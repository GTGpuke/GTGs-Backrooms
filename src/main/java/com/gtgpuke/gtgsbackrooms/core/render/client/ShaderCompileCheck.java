package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.lwjgl.opengl.GL20;

import net.minecraft.client.gl.ShaderStage;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Repli d'un shader patche qui ne compile pas (pilote strict, pack de ressources) : la source patchee est compilee a
 * blanc avant d'etre donnee au jeu ; si le pilote la refuse, le journal garde son message une fois et le shader reste
 * vanilla. Un look manquant vaut mieux qu'un monde rose.
 */
public final class ShaderCompileCheck {

    private static final int LOG_LENGTH = 32768;
    private static final Set<String> LOGGED = new HashSet<>();
    private static int accepted;
    private static int refused;

    private ShaderCompileCheck() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * {@code true} si la source compile sur le pilote.
     *
     * @param type etage
     * @param name nom du shader, pour le journal
     * @param source source patchee
     */
    public static boolean compiles(ShaderStage.Type type, String name, String source) {
        int shader = GL20.glCreateShader(type == ShaderStage.Type.VERTEX ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0) {
                accepted++;
                return true;
            }
            refused++;
            if (LOGGED.add(type.getName() + ':' + name)) {
                ModConstants.LOGGER.error("[Look] Le shader {} patche ne compile pas, il reste vanilla ({}).", name,
                        StringUtils.trim(GL20.glGetShaderInfoLog(shader, LOG_LENGTH)));
            }
            return false;
        } finally {
            GL20.glDeleteShader(shader);
        }
    }

    /** Etages patches acceptes par le pilote depuis le dernier chargement des ressources du moteur. */
    public static int accepted() {
        return accepted;
    }

    /** Etages patches refuses par le pilote depuis le dernier chargement des ressources du moteur. */
    public static int refused() {
        return refused;
    }

    /** Nouveau chargement des ressources : les comptes repartent de zero. */
    public static void resetCounts() {
        accepted = 0;
        refused = 0;
    }
}
