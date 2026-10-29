package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.openal.AL10;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AdjustedSource;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;
import com.gtgpuke.gtgsbackrooms.core.render.client.FullscreenTriangle;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookPrograms;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.gametest.client.mixin.ClientTestShaderProgramAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SourceAccessor;

/**
 * Regles des deux moteurs qu'aucune image ne montre a coup sur, verifiees dans le Level 0 par un vrai client : seuil du
 * bloom du profil, stencil garde quand les vues d'une image sont coupees, samplers du dessin plein ecran, volume d'une
 * source sous une echelle, meme son rejoue sur une nouvelle source.
 */
final class EngineRulesClientTests {

    /** Identifiant du disque joue pres du joueur, tel que le diagnostic du moteur audio l'ecrit. */
    private static final String RECORD = "minecraft:music_disc.13";
    /** Ticks apres lesquels la revue du moteur audio (toutes les dix ticks) a vu deux fois chaque son traite. */
    private static final int REVIEWS_TICKS = 30;
    /** Atlas des particules : une seconde texture toujours chargee, a cote de celui des blocs. */
    private static final Identifier PARTICLE_ATLAS = Identifier.ofVanilla("textures/atlas/particles.png");

    private EngineRulesClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(engineRulesInLevelZero());
    }

    private static ClientScenario engineRulesInLevelZero() {
        SoundInstance[] record = new SoundInstance[1];
        float[] gain = {Float.NaN};
        return ClientScenario.named("engine_rules_in_level_zero")
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 16, 1200)
                .frames(20)
                .check("seuil du bloom du profil dans le shader", context -> {
                    GlUniform threshold = LookPrograms.bloomThreshold();
                    ClientTestContext.require(threshold != null, "Le programme d'extraction du bloom n'a pas d'uniforme de seuil.");
                    float expected = LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).post().bloomThreshold();
                    float actual = threshold.getFloatData().get(0);
                    ClientTestContext.require(Math.abs(actual - expected) < 1.0e-4f, String.format(Locale.ROOT,
                            "Seuil du bloom %.3f au lieu de celui du profil du Level 0 (%.3f).", actual, expected));
                })
                .check("stencil garde par un framebuffer recree quand les vues de l'image sont coupees", EngineRulesClientTests::requireStencilWithViewsSuppressed)
                .check("samplers du dessin plein ecran reposes quand la texture change", EngineRulesClientTests::requireSamplerFollowsTexture)
                .run("disque joue pres du joueur", context -> {
                    MinecraftClient client = context.client();
                    record[0] = new PositionedSoundInstance(SoundEvents.MUSIC_DISC_13.value(), SoundCategory.RECORDS, 1.0f, 1.0f, Random.create(0L),
                            client.player.getX(), client.player.getEyeY(), client.player.getZ());
                    client.getSoundManager().play(record[0]);
                })
                .await("disque route par le moteur audio", context -> LevelZeroClientTests.routed().contains(RECORD), 200)
                .run("volume pose sous une echelle, lu sur la source", context -> {
                    Channel.SourceManager manager = sources(context.client()).get(record[0]);
                    ClientTestContext.require(manager != null, "Le disque n'a pas de source.");
                    manager.run(source -> {
                        source.setVolume(0.5f);
                        ((AdjustedSource) source).backrooms$setVolumeScale(0.5f);
                        gain[0] = AL10.alGetSourcef(((SourceAccessor) source).backrooms$getPointer(), AL10.AL_GAIN);
                    });
                })
                .await("gain lu sur le thread du son", context -> !Float.isNaN(gain[0]), 100)
                .check("gain de la source : volume brut fois l'echelle", context -> ClientTestContext.require(Math.abs(gain[0] - 0.25f) < 1.0e-4f,
                        "Gain " + gain[0] + " pour un volume de 0,5 sous une echelle de 0,5 (0,25 attendu)."))
                .run("meme son arrete puis rejoue sur une nouvelle source", context -> {
                    context.client().getSoundManager().stop(record[0]);
                    context.client().getSoundManager().play(record[0]);
                })
                .ticks(REVIEWS_TICKS)
                .check("son rejoue toujours traite, sur sa nouvelle source", context -> {
                    ClientTestContext.require(sources(context.client()).get(record[0]) != null, "Le disque rejoue n'a pas de source.");
                    String routed = LevelZeroClientTests.routed();
                    int line = routed.indexOf("  " + RECORD + " : ");
                    ClientTestContext.require(line >= 0, "Le disque rejoue n'est plus traite par le moteur audio : " + routed + ".");
                    String entry = routed.substring(line, routed.indexOf('\n', line));
                    ClientTestContext.require(!entry.contains("source perimee"), "Le disque rejoue garde les reglages de sa source d'avant : " + entry + ".");
                })
                .cleanup(context -> {
                    if (record[0] != null) {
                        context.client().getSoundManager().stop(record[0]);
                    }
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * Un framebuffer recree (fenetre redimensionnee) garde son stencil tant que le pilote le tient, meme quand les vues de
     * l'image sont coupees (cohabitation avec un pack) : le post du look s'en sert aussi.
     */
    private static void requireStencilWithViewsSuppressed(ClientTestContext context) {
        Framebuffer screen = context.client().getFramebuffer();
        int width = screen.textureWidth;
        int height = screen.textureHeight;
        boolean stencil;
        PortalRenderState.setViewsSuppressed(true);
        try {
            screen.resize(width + 2, height, MinecraftClient.IS_SYSTEM_MAC);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, screen.fbo);
            stencil = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL11.GL_NONE;
        } finally {
            PortalRenderState.setViewsSuppressed(false);
            screen.resize(width, height, MinecraftClient.IS_SYSTEM_MAC);
            // La creation laisse le framebuffer par defaut lie et le test de profondeur actif : etat de repos rendu.
            screen.beginWrite(false);
            RenderSystem.disableDepthTest();
        }
        ClientTestContext.require(stencil, "Framebuffer recree sans stencil pendant une image aux vues coupees.");
    }

    /** Deux dessins du meme programme sur deux textures : le sampler du programme suit la seconde. */
    private static void requireSamplerFollowsTexture(ClientTestContext context) {
        ShaderProgram blur = LookPrograms.blur();
        ClientTestContext.require(blur != null, "Le programme de flou du bloom n'est pas charge.");
        int blocks = context.client().getTextureManager().getTexture(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).getGlId();
        int particles = context.client().getTextureManager().getTexture(PARTICLE_ATLAS).getGlId();
        int previous = RenderSystem.getShaderTexture(0);
        Object bound;
        try {
            RenderSystem.setShaderTexture(0, blocks);
            FullscreenTriangle.draw(blur);
            RenderSystem.setShaderTexture(0, particles);
            FullscreenTriangle.draw(blur);
            bound = ((ClientTestShaderProgramAccessor) blur).gtgsTest$getSamplers().get("Sampler0");
        } finally {
            RenderSystem.setShaderTexture(0, previous);
            context.client().getFramebuffer().beginWrite(false);
        }
        ClientTestContext.require(Integer.valueOf(particles).equals(bound), "Sampler du flou sur la texture " + bound + " au lieu de " + particles
                + " (la seconde dessinee).");
    }

    private static Map<SoundInstance, Channel.SourceManager> sources(MinecraftClient client) {
        return ((SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem()).backrooms$getSources();
    }
}
