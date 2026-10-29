package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteViewRenderer;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Le look au fil d'une image : le profil et les neons du monde du joueur poses au debut du rendu du monde
 * ({@code WorldRenderer.render}), les vues de portail empilant les leurs entre-temps, et le look vanilla rendu a la
 * fin du rendu du monde, main comprise ({@code GameRenderer.renderWorld}), avant l'interface.
 */
public final class LookFrame {

    private LookFrame() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Debut du rendu du monde du joueur. Un mod qui dessine le terrain par ses propres shaders (grille vanilla a distance
     * nulle, {@link RemoteViewRenderer#foreignTerrain}) n'est pas atteint par les shaders patches : l'etage 1 passe alors
     * dans le post pour tout ce qui porte le look, et les shaders patches restent vanilla.
     *
     * @param world monde du joueur
     * @param renderer renderer du joueur
     * @param cameraPos camera du joueur
     * @param positionMatrix matrice de vue du rendu
     * @param tickDelta fraction de tick de l'image
     */
    public static void beginWorld(ClientWorld world, WorldRendererAccessor renderer, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta) {
        // Le plafond de clignotement des sons lies compte ses images, moteur de shaders coupe ou non.
        CueTracker.beginFrame(Util.getMeasuringTimeMs(), world.getRegistryKey().getValue());
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        LookProfile profile = LookProfiles.of(world.getRegistryKey());
        // L'horloge commune du son et de l'image : le temps du monde, pas celui de la machine.
        LookState.beginWorld(profile, CueClock.seconds(world.getTime(), tickDelta), cameraPos.x, cameraPos.y, cameraPos.z, CueClock.tick(world.getTime()));
        LookState.setShadersCarryLook(!RemoteViewRenderer.foreignTerrain(renderer));
        clearStencil(profile);
        NeonLights.beginWorld(world, cameraPos, positionMatrix, tickDelta);
        GpuTimer.begin(GpuTimer.WORLD);
    }

    /** Fin du rendu du monde par son renderer ({@code WorldRenderer.render}), vues de portail comprises : la mesure GPU du monde se ferme. */
    public static void endWorldRender() {
        if (ModConfig.ENABLE_LOOK) {
            GpuTimer.end(GpuTimer.WORLD);
        }
    }

    /**
     * Niveau 0 et look du monde du joueur sur tout le stencil, avant le premier dessin de l'image : le post masque de fin
     * d'image lit ces bits sur chaque pixel, qu'un portail soit visible ou non (la passe des portails refait le meme
     * effacement avant ses vues). Rien n'ecrit le stencil pendant le rendu vanilla du monde, le test etant coupe. Sous
     * un pack de shaders, le chemin par vue tient son propre stencil et le post n'en lit pas : rien ici.
     */
    private static void clearStencil(LookProfile profile) {
        if (!PortalRenderState.stencilAvailable() || ShaderPackState.inUse()) {
            return;
        }
        if (GlStateCheck.ENABLED) {
            GlStateCheck.before(GlStateCheck.STENCIL_CLEAR);
        }
        GL11.glStencilMask(StencilLookMask.FULL_MASK);
        GL11.glClearStencil(StencilLookMask.value(0, profile.id()));
        RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
        GL11.glClearStencil(0);
        if (GlStateCheck.ENABLED) {
            GlStateCheck.after(GlStateCheck.STENCIL_CLEAR);
        }
    }

    /**
     * Entree d'une vue de portail : son look et ses neons, vus de sa camera. A rendre par {@link #popView()}.
     *
     * @param world monde de la vue
     * @param cameraPos camera de la vue
     * @param positionMatrix matrice de vue de cette camera
     * @param tickDelta fraction de tick de l'image
     * @param screenArea part de l'ecran que couvre le cadre de la vue, de 0 a 1 (plafond de clignotement)
     */
    public static void pushView(ClientWorld world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta, float screenArea) {
        CueTracker.viewSeen(world.getRegistryKey().getValue(), screenArea);
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        LookState.push(LookProfiles.of(world.getRegistryKey()), cameraPos.x, cameraPos.y, cameraPos.z, CueClock.tick(world.getTime()));
        NeonLights.push(world, cameraPos, positionMatrix, tickDelta);
    }

    /** Sortie d'une vue : le look et les neons de la vue qui la contient reprennent. */
    public static void popView() {
        if (ModConfig.ENABLE_LOOK) {
            NeonLights.pop();
            LookState.pop();
        }
    }

    /**
     * Fin du rendu du monde du joueur, main comprise : le post masque de chaque look vu, le stencil a la place de l'image en
     * mode de debogage, puis tout redevient vanilla.
     */
    public static void endWorld(MinecraftClient client) {
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        LookPost.render(client);
        StencilDebugOverlay.render(client);
        LookState.endWorld();
    }
}
