package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.nio.ByteBuffer;
import java.util.List;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.client.GpuTimer;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.menu.BackroomsSettingsScreen;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;
import com.gtgpuke.gtgsbackrooms.core.render.quality.VisualPreset;

/**
 * Le menu des Backrooms dans un vrai client : l'icone n'existe que dans le menu pause des Backrooms, elle ouvre les
 * reglages, et chaque choix change le rendu en direct, sans relancer le jeu.
 */
final class SettingsMenuClientTests {

    private static final String KEY = "screen.gtgs_backrooms.settings.";
    /** Images laissees aux mesures GPU pour remonter jusqu'a la moyenne. */
    private static final int MEASURED_FRAMES = 30;
    /** Les six options de la partie avancee, et Termine. */
    private static final int ADVANCED_BUTTONS = 7;
    /** Largeur de la bande gauche de l'image relue, en pour cent : dans la bande noire de la cassette, pour un ecran de 16:9. */
    private static final int SIDE_PERCENT = 8;
    /** Palier choisi avant le test de la cassette. */
    private static String tierBefore = "";

    private SettingsMenuClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(settingsMenu(), tapeOnlyInBackrooms(), tapeCoversAVanillaView());
    }

    private static TexturedButtonWidget icon(Screen screen) {
        for (ClickableWidget widget : Screens.getButtons(screen)) {
            if (widget instanceof TexturedButtonWidget button && button.getMessage().equals(Text.translatable(KEY + "title"))) {
                return button;
            }
        }
        return null;
    }

    /** Clique le bouton de l'ecran ouvert qui porte ce nom. */
    private static void click(MinecraftClient client, Text label) {
        for (Element element : client.currentScreen.children()) {
            if (element instanceof ClickableWidget widget && widget.getMessage().getString().equals(label.getString())) {
                widget.onClick(0.0, 0.0);
                return;
            }
        }
        throw new AssertionError("Pas de bouton " + label.getString() + " dans l'ecran ouvert.");
    }

    private static Text label(String key) {
        return Text.translatable(KEY + key);
    }

    private static float uniform(String name) {
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        int location = GL20.glGetUniformLocation(program, name);
        ClientTestContext.require(location >= 0, "Terrain sans " + name + ".");
        float[] value = new float[1];
        GL20.glGetUniformfv(program, location, value);
        return value[0];
    }

    private static ClientScenario settingsMenu() {
        return ClientScenario.named("settings_menu")
                .run("dans l'Overworld", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .run("menu pause de l'Overworld", context -> context.client().setScreen(new GameMenuScreen(true)))
                .frames(2)
                .check("aucune icone hors des Backrooms", context -> ClientTestContext.require(icon(context.client().currentScreen) == null, "Icone dans l'Overworld."))
                .run("menu ferme", context -> context.client().setScreen(null))
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .frames(4)
                .run("menu pause du Level 0", context -> context.client().setScreen(new GameMenuScreen(true)))
                .frames(2)
                .check("une petite icone carree, a gauche du menu", SettingsMenuClientTests::requireIcon)
                .run("clic sur l'icone", context -> icon(context.client().currentScreen).onPress())
                .frames(2)
                .check("l'icone ouvre les reglages", context -> ClientTestContext.require(context.client().currentScreen instanceof BackroomsSettingsScreen,
                        "Ecran ouvert : " + context.client().currentScreen + "."))
                .run("palier Low", context -> click(context.client(), label("quality.low")))
                .frames(4)
                .check("Low se voit tout de suite : plus de cache de surfaces, plus de post", context -> {
                    ClientTestContext.require(LookResources.quality() == LookQuality.LOW, "Palier " + LookResources.quality() + ".");
                    ClientTestContext.require(uniform("gtgs_SurfaceLight") == 0.0f, "Le terrain lit encore le cache de surfaces.");
                    ClientTestContext.require(LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).post().none(), "Il reste un post en Low.");
                })
                .run("palier High", context -> click(context.client(), label("quality.high")))
                .frames(4)
                .check("High remet le cache de surfaces", context -> {
                    ClientTestContext.require(LookResources.surfaceLight(), "Pas de cache de surfaces en High.");
                    ClientTestContext.require(LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).post().bloom(), "Pas de bloom en High.");
                })
                .run("ombres pixelisees : non", context -> click(context.client(), label("pixels.0")))
                .frames(2)
                .check("la lumiere est lisse dans le shader", context -> ClientTestContext.require(uniformVec("gtgs_LookParams") == 0.0f, "La lumiere n'est pas lisse."))
                .run("ombres pixelisees : oui", context -> click(context.client(), label("pixels.32")))
                .frames(2)
                .check("les pixels d'ombre valent 32 dans le shader", context -> ClientTestContext.require(uniformVec("gtgs_LookParams") == 32.0f,
                        "Pixels d'ombre " + uniformVec("gtgs_LookParams") + " au lieu de 32."))
                .run("style VHS", context -> click(context.client(), label("preset.found_footage")))
                .frames(2)
                .check("le style VHS est pose", context -> ClientTestContext.require(LookSettings.current().preset() == VisualPreset.FOUND_FOOTAGE,
                        "Style " + LookSettings.current().preset() + "."))
                .run("preset Cinematique", context -> click(context.client(), label("preset.cinematic")))
                .frames(4)
                .run("une option de confort", context -> click(context.client(), label("option.reduce_flashes")))
                .frames(2)
                .check("le confort s'applique sans relancer", context -> ClientTestContext.require(LookSettings.current().flashesReduced(), "Option sans effet."))
                .run("reglages avances", context -> click(context.client(), label("advanced")))
                .frames(MEASURED_FRAMES)
                .check("le cout GPU se mesure en direct", context -> ClientTestContext.require(GpuTimer.milliseconds(GpuTimer.WORLD) > 0.0f, "Aucune mesure du monde."))
                .check("la partie avancee n'a que ses options et Termine", context -> ClientTestContext.require(
                        Screens.getButtons(context.client().currentScreen).size() == ADVANCED_BUTTONS,
                        "Boutons de la partie avancee : " + Screens.getButtons(context.client().currentScreen).size() + "."))
                .run("bloom coupe", context -> click(context.client(), label("option.bloom")))
                .frames(2)
                .check("une option avancee retire son effet", context -> ClientTestContext.require(!LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).post().bloom(),
                        "Bloom encore la."))
                .run("palier desactive, par la configuration", context -> LookResources.applySettings(LookSettings.current().withQuality(LookQuality.OFF.key()),
                        context.client()))
                .frames(6)
                .check("le palier desactive retire le look et la lumiere cuite", context -> {
                    ClientTestContext.require(LookResources.quality() == LookQuality.OFF, "Palier " + LookResources.quality() + ".");
                    ClientTestContext.require(!LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).bakedLight().any(), "La lumiere cuite reste.");
                    ClientTestContext.require(uniform("gtgs_BakedLight") == 0.0f, "Le terrain lit encore la zone.");
                })
                .cleanup(context -> {
                    LookResources.applySettings(LookSettings.defaults(), context.client());
                    context.client().setScreen(null);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * Le style VHS vaut pour les Backrooms : sa cassette pose ses bandes noires dans le Level 0 ; en sortant, le style revient a Fidele,
     * le palier restant, et l'image redevient normale.
     */
    private static ClientScenario tapeOnlyInBackrooms() {
        // D'abord l'Overworld cote client, puis le style une fois dans le Level 0 : un test precedent peut encore avoir une sortie des
        // Backrooms en cours cote client, et elle remettrait le style a Fidele.
        return ClientScenario.named("tape_only_in_backrooms")
                .run("dans l'Overworld", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .run("style VHS", context -> {
                    tierBefore = LookSettings.current().quality();
                    LookResources.applySettings(LookSettings.current().withPreset(VisualPreset.FOUND_FOOTAGE), context.client());
                })
                .frames(8)
                .check("dans le Level 0, la cassette pose sa bande noire, au bord droit", context -> {
                    float black = blackSide(context.client());
                    ClientTestContext.require(black > 0.99f, "Part noire de la bande gauche dans le Level 0 : " + black + ".");
                    int[] edge = frameEdge(context.client());
                    ClientTestContext.require(edge[0] == edge[1], "Bord du cadre dentele : de la colonne " + edge[0] + " a " + edge[1] + ".");
                })
                .run("retour dans l'Overworld", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .await("le style revient a Fidele", context -> LookSettings.current().preset() == VisualPreset.FAITHFUL, 200)
                .frames(8)
                .check("hors des Backrooms, le palier reste et l'image redevient normale", context -> {
                    ClientTestContext.require(LookSettings.current().quality().equals(tierBefore), "Palier change : " + LookSettings.current().quality() + ".");
                    float black = blackSide(context.client());
                    ClientTestContext.require(black < 0.05f, "Part noire de la bande gauche dans l'Overworld : " + black + ".");
                })
                .cleanup(context -> {
                    LookResources.applySettings(LookSettings.defaults(), context.client());
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Distance du joueur au plan du portail, en blocs : assez pres pour que le cadre couvre toute l'image. */
    private static final double CLOSE_TO_THE_FRAME = 0.25;
    /** Chunks de l'Overworld recus avant de tenir sa vue pour prete. */
    private static final int MIN_VIEW_CHUNKS = 9;
    private static BlockPos vanillaPortal;

    /**
     * Le style est celui de la camera du joueur : un monde sans look vu par un portail, l'Overworld depuis le Level 0, prend lui aussi
     * les bandes noires de la cassette, la ou son cadre couvre le bord de l'image. Sans cela, le cadre y faisait un trou.
     */
    private static ClientScenario tapeCoversAVanillaView() {
        Direction[] ahead = new Direction[1];
        return ClientScenario.named("tape_covers_a_vanilla_view")
                .run("dans l'Overworld", LevelZeroClientTests::backToOverworld)
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .run("un portail vers l'Overworld, le joueur tout contre", context -> {
                    BlockPos feet = context.player().getBlockPos();
                    for (Direction direction : Direction.Type.HORIZONTAL) {
                        BlockPos front = feet.offset(direction);
                        if (ahead[0] == null && context.world().getBlockState(front).isAir() && context.world().getBlockState(front.up()).isAir()) {
                            ahead[0] = direction;
                        }
                    }
                    ClientTestContext.require(ahead[0] != null, "Aucune place libre devant le joueur.");
                    vanillaPortal = feet.offset(ahead[0]);
                    double x = feet.getX() + 0.5 + ahead[0].getOffsetX() * (1.0 - CLOSE_TO_THE_FRAME);
                    double z = feet.getZ() + 0.5 + ahead[0].getOffsetZ() * (1.0 - CLOSE_TO_THE_FRAME);
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        level.setBlockState(vanillaPortal, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, ahead[0].getOpposite()));
                        if (level.getBlockEntity(vanillaPortal) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(PortalSettings.DEFAULT));
                        }
                        ClientTestContext.serverPlayer(server).teleport(level, x, feet.getY(), z, ahead[0].asRotation(), 0.0f);
                    });
                })
                .await("vue de l'Overworld prete", context -> {
                    RemoteWorld view = RemoteWorldRegistry.get(World.OVERWORLD);
                    return view != null && view.world().getChunkManager().getLoadedChunkCount() >= MIN_VIEW_CHUNKS;
                }, 1200)
                .run("style VHS", context -> {
                    context.player().setYaw(ahead[0].asRotation());
                    context.player().setPitch(0.0f);
                    LookResources.applySettings(LookSettings.current().withPreset(VisualPreset.FOUND_FOOTAGE), context.client());
                })
                .frames(30)
                .check("la bande noire couvre aussi l'Overworld vu par le cadre", context -> {
                    float black = blackSide(context.client());
                    ClientTestContext.require(black > 0.99f, "Part noire de la bande gauche, l'Overworld dans le cadre : " + black + ".");
                })
                .cleanup(context -> context.onServer(server -> {
                    if (vanillaPortal != null) {
                        ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(vanillaPortal, Blocks.AIR.getDefaultState());
                    }
                }))
                .cleanup(context -> {
                    LookResources.applySettings(LookSettings.defaults(), context.client());
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /**
     * Premiere et derniere colonne ou s'arrete la bande noire de gauche, sur toutes les lignes de la derniere image : egales, le bord du
     * cadre est droit, meme au bas de l'image que le changement de tete fait glisser.
     */
    private static int[] frameEdge(MinecraftClient client) {
        Framebuffer screen = client.getFramebuffer();
        int width = screen.textureWidth / 2;
        int height = screen.textureHeight;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screen.fbo);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int first = width;
            int last = 0;
            for (int row = 0; row < height; row++) {
                int column = 0;
                while (column < width && pixels.get((row * width + column) * 4) == 0 && pixels.get((row * width + column) * 4 + 1) == 0
                        && pixels.get((row * width + column) * 4 + 2) == 0) {
                    column++;
                }
                first = Math.min(first, column);
                last = Math.max(last, column);
            }
            return new int[] {first, last};
        } finally {
            screen.beginWrite(false);
            MemoryUtil.memFree(pixels);
        }
    }

    /** Part des pixels tout a fait noirs dans la bande gauche de la derniere image, que le tick du client relit entre deux images. */
    private static float blackSide(MinecraftClient client) {
        Framebuffer screen = client.getFramebuffer();
        int width = screen.textureWidth * SIDE_PERCENT / 100;
        int height = screen.textureHeight;
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, screen.fbo);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int black = 0;
            for (int i = 0; i < width * height; i++) {
                if (pixels.get(i * 4) == 0 && pixels.get(i * 4 + 1) == 0 && pixels.get(i * 4 + 2) == 0) {
                    black++;
                }
            }
            return black / (float) (width * height);
        } finally {
            screen.beginWrite(false);
            MemoryUtil.memFree(pixels);
        }
    }

    /** Premiere composante d'un uniforme vec4 du terrain. */
    private static float uniformVec(String name) {
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        float[] value = new float[4];
        GL20.glGetUniformfv(program, GL20.glGetUniformLocation(program, name), value);
        return value[0];
    }

    private static void requireIcon(ClientTestContext context) {
        Screen screen = context.client().currentScreen;
        TexturedButtonWidget icon = icon(screen);
        ClientTestContext.require(icon != null, "Pas d'icone dans le menu pause des Backrooms.");
        ClientTestContext.require(icon.getWidth() == 20 && icon.getHeight() == 20, "Icone de " + icon.getWidth() + " sur " + icon.getHeight() + ".");
        ClientTestContext.require(icon.getX() >= 0 && icon.getRight() <= screen.width && icon.getY() >= 0 && icon.getBottom() <= screen.height,
                "Icone hors de l'ecran, en " + icon.getX() + ", " + icon.getY() + ".");
        boolean besideFirstButton = false;
        for (ClickableWidget other : Screens.getButtons(screen)) {
            if (other != icon && other instanceof ButtonWidget) {
                ClientTestContext.require(icon.getRight() <= other.getX(), "L'icone chevauche un bouton du menu.");
                besideFirstButton |= other.getY() == icon.getY() && other.getX() - icon.getRight() == 4;
            }
        }
        ClientTestContext.require(besideFirstButton, "L'icone n'est pas juste a gauche de la premiere rangee.");
        ClientTestContext.require(LookSettings.current().preset() == VisualPreset.FAITHFUL, "Reglages de depart attendus.");
        ClientTestContext.require(LookSettings.current().has(LookOption.BLOOM), "Reglages de depart attendus.");
    }
}
