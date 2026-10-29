package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackState;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Etage 2 du moteur de shaders : le post-traitement masque. En fin de rendu du monde, main comprise, l'image est copiee
 * dans une texture ({@code gtgs_backrooms:screen_copy}, un blit), le bloom en est tire en qualite complete (pixels
 * lumineux a demi-resolution, flou separable a quart de resolution), puis, pour chaque look vu dans l'image dont le
 * profil a un post, un triangle plein ecran est dessine dans le framebuffer du jeu sous le test de stencil des bits de
 * ce look ({@link StencilLookMask#LOOK_MASK}) : les pixels des autres looks et ceux de vanilla sont rejetes avant le
 * fragment shader sur la plupart des cartes. Un seul programme de composition ({@code look_post}), sans {@code discard}
 * ni {@code gl_FragDepth}. Le triangle plein ecran est un tampon persistant ({@link FullscreenTriangle}) : rien n'est
 * alloue par image ; le temps GPU de chaque passe se mesure par {@link GpuTimer} quand le moniteur est actif.
 *
 * <p>Sous un pack de shaders, dont les shaders ne portent pas le look et dont la passe finale ignore le stencil, le look
 * entier du monde du joueur (etage 1 et post) se pose sur l'image du pack, sur tout l'ecran, avant les vues dessinees
 * apres l'image ; puis chaque vue, une fois dessinee par le pack, recoit le look entier de sa dimension sur les pixels
 * de son cadre (le plan B de la specification : un post par vue, sous le stencil du cadre). Avec un mod qui dessine le
 * terrain par ses propres shaders ({@link LookState#shadersCarryLook()} faux),
 * l'etage 1 s'applique ici aussi, sous le stencil de chaque look : terrain et entites recoivent le meme look, sans la
 * lumiere des neons. L'etat GL est rendu tel quel, exception comprise ; une exception coupe le post pour la session
 * (coupe-circuit, une ligne de journal), l'etage 1 des shaders patches restant.
 */
public final class LookPost {

    private static final Identifier SCREEN_COPY = Identifier.of(ModConstants.MOD_ID, "screen_copy");
    private static final Identifier BLOOM_HALF = Identifier.of(ModConstants.MOD_ID, "bloom_half");
    private static final Identifier BLOOM_QUARTER_A = Identifier.of(ModConstants.MOD_ID, "bloom_quarter_a");
    private static final Identifier BLOOM_QUARTER_B = Identifier.of(ModConstants.MOD_ID, "bloom_quarter_b");
    private static final String PERF_POST = "client.look.post";
    private static final String PERF_BLOOM = "client.look.bloom";
    /** Sous un pack : tout l'ecran, sans test de stencil. */
    public static final int NO_FRAME = -1;
    /** Coupe-circuit : vrai apres une exception pendant le post, qui ne se dessine plus de la session. */
    private static boolean failed;

    private LookPost() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Fin du rendu du monde sans pack de shaders, main comprise : le post de chaque look vu dans l'image, sous le stencil
     * de ses bits de look.
     */
    public static void render(MinecraftClient client) {
        int looks = LookState.usedLooks();
        if (ShaderPackState.inUse() || !PortalRenderState.stencilAvailable()) {
            return;
        }
        // Sans post a dessiner, rien ; sauf si l'etage 1 passe par ici (mod de terrain) et qu'un look a ete vu.
        if (!hasPost(looks) && (LookState.shadersCarryLook() || (looks & ~1) == 0)) {
            return;
        }
        render(client, looks, true, NO_FRAME);
    }

    /**
     * Sous un pack de shaders, apres l'image du monde du joueur et avant les vues : si sa dimension a un look, ce look
     * entier (etage 1 et post) sur tout l'ecran. Le stencil n'y porte pas les bits de look : aucun test.
     */
    public static void renderUnderShaderPack(MinecraftClient client) {
        if (client.world != null) {
            renderUnderShaderPackView(client, client.world.getRegistryKey(), NO_FRAME);
        }
    }

    /**
     * Sous un pack de shaders, une vue de portail que le pack vient de dessiner : le look entier de la dimension vue
     * (etage 1 et post) sur les pixels de son cadre, ceux dont le stencil porte la valeur de la vue, avant que l'image du
     * monde du joueur soit remise autour ; sur tout l'ecran pour une vue qui le couvre (troisieme personne derriere un
     * cadre). Rien si la dimension vue n'a pas de look.
     *
     * @param client client
     * @param dimension dimension que la vue montre
     * @param frameValue valeur du stencil des pixels de la vue, ou {@link #NO_FRAME} pour tout l'ecran
     */
    public static void renderUnderShaderPackView(MinecraftClient client, RegistryKey<World> dimension, int frameValue) {
        if (!ModConfig.ENABLE_LOOK) {
            return;
        }
        LookProfile look = LookProfiles.of(dimension);
        if (!look.vanilla()) {
            render(client, 1 << look.id(), false, frameValue);
        }
    }

    /**
     * Le post, puis l'etat GL que l'appelant tenait : hors pack, en fin de rendu du monde, le repos de vanilla entre deux
     * dessins (test de profondeur et stencil coupes) ; sous un pack, apres le rendu d'une image par le pack, le test de
     * profondeur actif, l'ecriture de la profondeur coupee dans le cadre d'une vue, et le test de stencil de la vue garde
     * (la suite de la vue, ses portails imbriques et l'image du joueur remise autour du cadre, en depend).
     *
     * @param looks looks a dessiner, un bit par identifiant
     * @param lookBits {@code true} pour tester, par look, les bits de look du stencil (hors pack) ; {@code false} sous un
     *        pack, ou l'etage 1 s'applique aussi ici
     * @param frameValue sous un pack, valeur du stencil des pixels a traiter ({@code GL_EQUAL} sur tous les bits), ou
     *        {@link #NO_FRAME} pour tout l'ecran
     */
    private static void render(MinecraftClient client, int looks, boolean lookBits, int frameValue) {
        if (failed) {
            return;
        }
        int pass = lookBits ? GlStateCheck.POST : frameValue == NO_FRAME ? GlStateCheck.POST_UNDER_PACK : GlStateCheck.POST_OF_VIEW;
        if (GlStateCheck.ENABLED) {
            GlStateCheck.before(pass);
        }
        draw(client, looks, lookBits, frameValue);
        if (GlStateCheck.ENABLED) {
            GlStateCheck.after(pass);
        }
    }

    @SuppressWarnings("try")
    private static void draw(MinecraftClient client, int looks, boolean lookBits, int frameValue) {
        boolean stencil = lookBits || frameValue != NO_FRAME;
        boolean stageOneInShaders = lookBits && LookState.shadersCarryLook();
        ShaderProgram program = LookPrograms.post();
        GlUniform params = LookPrograms.postParams();
        GlUniform tint = LookPrograms.postTint();
        GlUniform lookParams = LookPrograms.postLook();
        if (program == null || params == null || tint == null || lookParams == null) {
            return;
        }
        Framebuffer screen = client.getFramebuffer();
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_POST)) {
            // Tout ce qui touche a l'etat GL est sous le try : une exception le rend tel que l'appelant le tenait.
            try {
                SimpleFramebuffer copy = FramebufferRegistry.get(SCREEN_COPY, screen.textureWidth, screen.textureHeight);
                if (copy == null) {
                    return;
                }
                GpuTimer.begin(GpuTimer.COPY);
                copyScreen(screen, copy);
                GpuTimer.end(GpuTimer.COPY);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                SimpleFramebuffer bloom = wantsBloom(looks) ? bloom(screen, copy, looks) : null;
                screen.beginWrite(true);
                if (stencil) {
                    GL11.glEnable(GL11.GL_STENCIL_TEST);
                    GL11.glStencilMask(0);
                    GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                    if (!lookBits) {
                        GL11.glStencilFunc(GL11.GL_EQUAL, frameValue, StencilLookMask.FULL_MASK);
                    }
                }
                RenderSystem.setShaderTexture(0, copy.getColorAttachment());
                RenderSystem.setShaderTexture(1, bloom != null ? bloom.getColorAttachment() : copy.getColorAttachment());
                GpuTimer.begin(GpuTimer.POST);
                for (int id = 1; id <= StencilLookMask.MAX_LOOK; id++) {
                    if ((looks & 1 << id) == 0) {
                        continue;
                    }
                    LookProfile look = LookProfiles.byId(id);
                    LookProfile.Post post = look.post();
                    if (stageOneInShaders && post.none()) {
                        continue;
                    }
                    // Sous un pack (sans stencil) ou un mod qui dessine le terrain, le look de l'etage 1 s'applique ici : leurs
                    // shaders ne le portent pas. La vignette pulse avec un cri lie a l'image, dans sa dimension seulement.
                    float pulse = CueTracker.screenPulse(look.dimension(), listener.x, listener.y, listener.z);
                    params.set(Math.min(1.0f, post.vignette() + pulse), post.chromatic(), stageOneInShaders ? 0.0f : 1.0f,
                            bloom != null && post.bloom() ? post.bloomStrength() : 0.0f);
                    tint.set(look.tintRed(), look.tintGreen(), look.tintBlue(), look.tintStrength());
                    lookParams.set(look.desaturation(), look.grain(), look.flicker(), LookState.time());
                    if (lookBits) {
                        GL11.glStencilFunc(GL11.GL_EQUAL, id << StencilLookMask.LOOK_SHIFT, StencilLookMask.LOOK_MASK);
                    }
                    FullscreenTriangle.draw(program);
                }
                GpuTimer.end(GpuTimer.POST);
            } finally {
                GpuTimer.endOpen();
                if (stencil) {
                    GL11.glStencilMask(StencilLookMask.FULL_MASK);
                    if (lookBits) {
                        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, StencilLookMask.FULL_MASK);
                        GL11.glDisable(GL11.GL_STENCIL_TEST);
                    } else {
                        // Le test de la vue reste : coupe, l'image du joueur remise autour du cadre recouvrait la vue entiere.
                        GL11.glStencilFunc(GL11.GL_EQUAL, frameValue, StencilLookMask.FULL_MASK);
                    }
                }
                screen.beginWrite(true);
                RenderSystem.enableCull();
                if (lookBits) {
                    // Hors pack : le repos de vanilla entre deux dessins, test de profondeur coupe (chaque couche le coupe en sortie).
                    RenderSystem.depthMask(true);
                } else {
                    // Sous un pack : le test de profondeur actif ; dans le cadre d'une vue, l'ecriture de la profondeur coupee.
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(frameValue == NO_FRAME);
                }
            }
        } catch (RuntimeException exception) {
            // L'etat GL est deja rendu : le post se coupe pour la session, le reste du rendu continue.
            failed = true;
            ModConstants.LOGGER.error("[Look] Post coupe pour la session apres une exception.", exception);
        }
    }

    /** {@code true} si l'un des looks de ce masque a un post a dessiner. */
    private static boolean hasPost(int looks) {
        for (int id = 1; id <= StencilLookMask.MAX_LOOK; id++) {
            if ((looks & 1 << id) != 0 && !LookProfiles.byId(id).post().none()) {
                return true;
            }
        }
        return false;
    }

    /** {@code true} si la qualite le permet et qu'un des looks de ce masque veut le bloom. */
    private static boolean wantsBloom(int looks) {
        if (!LookResources.quality().bloom()) {
            return false;
        }
        for (int id = 1; id <= StencilLookMask.MAX_LOOK; id++) {
            if ((looks & 1 << id) != 0 && LookProfiles.byId(id).post().bloom()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Le bloom de la copie de l'ecran : pixels lumineux a demi-resolution, puis flou en deux passes a quart de
     * resolution. Le seuil est celui du premier look du masque qui demande le bloom.
     *
     * @return le framebuffer qui porte le bloom, ou {@code null} si un programme ou un framebuffer manque
     */
    @SuppressWarnings("try")
    private static @Nullable SimpleFramebuffer bloom(Framebuffer screen, SimpleFramebuffer copy, int looks) {
        ShaderProgram extract = LookPrograms.bloomExtract();
        ShaderProgram blur = LookPrograms.blur();
        GlUniform threshold = LookPrograms.bloomThreshold();
        GlUniform step = LookPrograms.blurStep();
        SimpleFramebuffer half = FramebufferRegistry.get(BLOOM_HALF, screen.textureWidth, screen.textureHeight);
        SimpleFramebuffer quarterA = FramebufferRegistry.get(BLOOM_QUARTER_A, screen.textureWidth, screen.textureHeight);
        SimpleFramebuffer quarterB = FramebufferRegistry.get(BLOOM_QUARTER_B, screen.textureWidth, screen.textureHeight);
        if (extract == null || blur == null || threshold == null || step == null || half == null || quarterA == null || quarterB == null) {
            return null;
        }
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_BLOOM)) {
            GpuTimer.begin(GpuTimer.BLOOM);
            threshold.set(bloomThreshold(looks));
            half.beginWrite(true);
            RenderSystem.setShaderTexture(0, copy.getColorAttachment());
            FullscreenTriangle.draw(extract);
            quarterA.beginWrite(true);
            RenderSystem.setShaderTexture(0, half.getColorAttachment());
            step.set(1.0f / quarterA.textureWidth, 0.0f);
            FullscreenTriangle.draw(blur);
            quarterB.beginWrite(true);
            RenderSystem.setShaderTexture(0, quarterA.getColorAttachment());
            step.set(0.0f, 1.0f / quarterA.textureHeight);
            FullscreenTriangle.draw(blur);
            GpuTimer.end(GpuTimer.BLOOM);
        }
        return quarterB;
    }

    /** Seuil du bloom : celui du premier look de ce masque qui le demande, le seuil par defaut sinon. */
    private static float bloomThreshold(int looks) {
        for (int id = 1; id <= StencilLookMask.MAX_LOOK; id++) {
            LookProfile.Post post = LookProfiles.byId(id).post();
            if ((looks & 1 << id) != 0 && post.bloom()) {
                return post.bloomThreshold();
            }
        }
        return LookProfile.Post.NONE.bloomThreshold();
    }

    /** Copie la couleur de l'ecran dans la texture de la copie, sur le GPU. */
    private static void copyScreen(Framebuffer screen, SimpleFramebuffer copy) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screen.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, screen.textureWidth, screen.textureHeight, 0, 0, copy.textureWidth, copy.textureHeight,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }
}
