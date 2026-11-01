package com.gtgpuke.gtgsbackrooms.core.render.client.menu;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Le petit bouton carre du menu pause, une icone sans texte, a gauche de la premiere rangee de boutons. Il n'existe que dans
 * une dimension a look : ailleurs, le menu pause reste vanilla. Son nom ne sert qu'a la narration.
 */
public final class PauseMenuButton {

    private static final int SIZE = 20;
    private static final int GAP = 4;
    private static final ButtonTextures ICON = new ButtonTextures(Identifier.of(ModConstants.MOD_ID, "backrooms_settings"),
            Identifier.of(ModConstants.MOD_ID, "backrooms_settings_highlighted"));

    private PauseMenuButton() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GameMenuScreen && visible(client)) {
                add(client, screen);
            }
        });
    }

    /** {@code true} si le joueur est dans une dimension a look. */
    private static boolean visible(MinecraftClient client) {
        return ModConfig.ENABLE_LOOK && client.world != null && !LookProfiles.of(client.world.getRegistryKey()).vanilla();
    }

    private static void add(MinecraftClient client, Screen screen) {
        List<ClickableWidget> buttons = Screens.getButtons(screen);
        // Les vrais boutons seulement : le titre du menu est un widget lui aussi, de toute la largeur de l'ecran.
        ClickableWidget first = null;
        for (ClickableWidget button : buttons) {
            if (button instanceof ButtonWidget && (first == null || button.getY() < first.getY() || button.getY() == first.getY() && button.getX() < first.getX())) {
                first = button;
            }
        }
        if (first == null) {
            return;
        }
        TexturedButtonWidget icon = new TexturedButtonWidget(first.getX() - SIZE - GAP, first.getY(), SIZE, SIZE, ICON,
                button -> client.setScreen(new BackroomsSettingsScreen(screen)), Text.translatable(SettingsPanel.KEY + "title"));
        buttons.add(icon);
    }
}
