package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;

import org.lwjgl.opengl.GL20;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
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

    private SettingsMenuClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(settingsMenu());
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
                .run("preset Pixel", context -> click(context.client(), label("preset.pixel")))
                .frames(2)
                .check("le preset pose ses pixels d'ombre dans le shader", context -> ClientTestContext.require(uniformVec("gtgs_LookParams") == 16.0f,
                        "Pixels d'ombre " + uniformVec("gtgs_LookParams") + " au lieu de 16."))
                .run("lumiere lisse", context -> click(context.client(), label("pixels.0")))
                .frames(2)
                .check("les pixels d'ombre se reglent seuls", context -> ClientTestContext.require(uniformVec("gtgs_LookParams") == 0.0f, "La lumiere n'est pas lisse."))
                .run("preset Cinematique", context -> click(context.client(), label("preset.cinematic")))
                .frames(4)
                .run("une option de confort", context -> click(context.client(), label("option.reduce_flashes")))
                .frames(2)
                .check("le confort s'applique sans relancer", context -> ClientTestContext.require(LookSettings.current().flashesReduced(), "Option sans effet."))
                .run("reglages avances", context -> click(context.client(), label("advanced")))
                .frames(MEASURED_FRAMES)
                .check("le cout GPU se mesure en direct", context -> ClientTestContext.require(GpuTimer.milliseconds(GpuTimer.WORLD) > 0.0f, "Aucune mesure du monde."))
                .run("bloom coupe", context -> click(context.client(), label("option.bloom")))
                .frames(2)
                .check("une option avancee retire son effet", context -> ClientTestContext.require(!LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).post().bloom(),
                        "Bloom encore la."))
                .run("tout couper", context -> click(context.client(), label("quality.off")))
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
