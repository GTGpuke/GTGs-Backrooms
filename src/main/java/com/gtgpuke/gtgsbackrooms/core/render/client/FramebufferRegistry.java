package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.util.Identifier;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.fbo.FramebufferSpec;

/**
 * Les framebuffers declares en donnees ({@link FramebufferSpec}), crees a la demande, recrees au seul changement de
 * taille de l'ecran, jamais par image, et rendus a la fin de la partie. Creer, redimensionner ou rendre un framebuffer
 * laisse lie celui par defaut : l'appelant relie celui du jeu avant de dessiner.
 */
public final class FramebufferRegistry {

    private static final Map<Identifier, FramebufferSpec> SPECS = new HashMap<>();
    private static final Map<Identifier, SimpleFramebuffer> FRAMEBUFFERS = new HashMap<>();
    private static final Set<Identifier> UNKNOWN_LOGGED = new HashSet<>();

    private FramebufferRegistry() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Remplace les declarations ; un framebuffer dont la declaration a disparu, ou dont l'attachement de profondeur a
     * change, est rendu. Un changement d'echelle ou de filtrage vaut au prochain redimensionnement.
     */
    public static void load(Collection<FramebufferSpec> specs) {
        SPECS.clear();
        for (FramebufferSpec spec : specs) {
            if (SPECS.putIfAbsent(spec.name(), spec) != null) {
                ModConstants.LOGGER.error("[Look] Deux declarations du framebuffer {} : la seconde est ignoree.", spec.name());
            }
        }
        FRAMEBUFFERS.entrySet().removeIf(entry -> {
            FramebufferSpec spec = SPECS.get(entry.getKey());
            boolean stale = spec == null || entry.getValue().useDepthAttachment != spec.depth();
            if (stale) {
                entry.getValue().delete();
            }
            return stale;
        });
        UNKNOWN_LOGGED.clear();
    }

    /**
     * Le framebuffer de ce nom, a la taille voulue par sa declaration pour cet ecran.
     *
     * @return le framebuffer, ou {@code null} s'il n'est pas declare (journalise une fois)
     */
    public static @Nullable SimpleFramebuffer get(Identifier name, int screenWidth, int screenHeight) {
        FramebufferSpec spec = SPECS.get(name);
        if (spec == null) {
            if (UNKNOWN_LOGGED.add(name)) {
                ModConstants.LOGGER.error("[Look] Framebuffer {} non declare.", name);
            }
            return null;
        }
        int width = spec.width(screenWidth);
        int height = spec.height(screenHeight);
        SimpleFramebuffer framebuffer = FRAMEBUFFERS.get(name);
        if (framebuffer == null) {
            framebuffer = new SimpleFramebuffer(width, height, spec.depth(), MinecraftClient.IS_SYSTEM_MAC);
            framebuffer.setTexFilter(spec.linear() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
            framebuffer.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            FRAMEBUFFERS.put(name, framebuffer);
        } else if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
            framebuffer.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            framebuffer.setTexFilter(spec.linear() ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        } else {
            return framebuffer;
        }
        // Creer ou redimensionner un framebuffer vanilla active le test de profondeur (Framebuffer.resize) : le post, seul
        // a demander ces framebuffers, le tient coupe.
        RenderSystem.disableDepthTest();
        return framebuffer;
    }

    /** Fin de partie : tous les framebuffers sont rendus ; les declarations restent. */
    public static void close() {
        // Appele a chaque tick hors partie : sans framebuffer, aucun iterateur.
        if (FRAMEBUFFERS.isEmpty()) {
            return;
        }
        for (SimpleFramebuffer framebuffer : FRAMEBUFFERS.values()) {
            framebuffer.delete();
        }
        FRAMEBUFFERS.clear();
    }
}
