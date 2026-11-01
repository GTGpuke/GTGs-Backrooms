package com.gtgpuke.gtgsbackrooms.core.render.client.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;
import com.gtgpuke.gtgsbackrooms.core.render.quality.VisualPreset;

/**
 * Ce que partagent les ecrans de reglages : un panneau a gauche, sans flou ni assombrissement d'arriere-plan pour voir le
 * jeu changer derriere lui, et des rangees de boutons dont le choix courant est marque. Chaque clic s'applique tout de suite.
 */
abstract class SettingsPanel extends Screen {

    /** Prefixe des cles de langue. */
    static final String KEY = "screen.gtgs_backrooms.settings.";
    static final int LEFT = 8;
    static final int INNER_WIDTH = 216;
    static final int ROW = 18;
    static final int GAP = 2;
    private static final int PANEL_PADDING = 6;
    private static final int LABEL_HEIGHT = 11;
    private static final int PANEL_COLOR = 0xD0181508;
    private static final int PANEL_BORDER = 0xFF6E6438;
    private static final int LABEL_COLOR = 0xFFC9B458;
    private static final int TITLE_COLOR = 0xFFF2E7B5;

    private final Screen parent;
    private int bottom;

    SettingsPanel(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** Une ligne de boutons de meme largeur sur toute la largeur du panneau ; rend le Y de la ligne suivante. */
    final int row(int y, Text[] labels, boolean[] selected, Runnable[] actions) {
        int width = (INNER_WIDTH - GAP * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            addDrawableChild(new PanelButton(LEFT + PANEL_PADDING + i * (width + GAP), y, width, ROW, labels[i], selected[i], actions[i]));
        }
        return y + ROW + GAP;
    }

    /** Un titre de partie, ecrit au rendu ; rend le Y de ce qui le suit. */
    final int section(int y, Text label) {
        addDrawable((context, mouseX, mouseY, delta) -> context.drawText(this.textRenderer, label, LEFT + PANEL_PADDING, y + 2, LABEL_COLOR, false));
        return y + LABEL_HEIGHT;
    }

    /** Le titre de l'ecran ; rend le Y de ce qui le suit. */
    final int header(int y) {
        addDrawable((context, mouseX, mouseY, delta) -> context.drawText(this.textRenderer, this.title, LEFT + PANEL_PADDING, y, TITLE_COLOR, false));
        return y + LABEL_HEIGHT + GAP;
    }

    /** La fin du contenu : le panneau s'arrete la. */
    final void end(int y) {
        this.bottom = y;
    }

    /** Applique des reglages et redessine les boutons. */
    final void apply(LookSettings next) {
        LookResources.applySettings(next, MinecraftClient.getInstance());
        clearAndInit();
    }

    /** Pose une option et applique. */
    final Runnable toggle(LookOption option) {
        LookSettings settings = LookSettings.current();
        return () -> apply(settings.with(option, !settings.has(option)));
    }

    /** Le palier Ultra est experimental : son bouton le dit. */
    final void explainUltra() {
        for (var child : children()) {
            if (child instanceof PanelButton button && button.getMessage().getString().equals(tierLabel(LookQuality.ULTRA).getString())) {
                button.setTooltip(Tooltip.of(Text.translatable(KEY + "quality.ultra.hint")));
            }
        }
    }

    /** Nom d'un palier. */
    static Text tierLabel(LookQuality tier) {
        return Text.translatable(KEY + "quality." + tier.key());
    }

    /** Nom d'un preset. */
    static Text presetLabel(VisualPreset preset) {
        return Text.translatable(KEY + "preset." + preset.key());
    }

    /** Nom d'une option. */
    static Text optionLabel(LookOption option) {
        return Text.translatable(KEY + "option." + option.key());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Ni flou ni voile : le joueur voit le jeu changer. Seul le panneau se dessine.
        context.fill(LEFT, LEFT, LEFT + INNER_WIDTH + PANEL_PADDING * 2, this.bottom + PANEL_PADDING, PANEL_BORDER);
        context.fill(LEFT + 1, LEFT + 1, LEFT + INNER_WIDTH + PANEL_PADDING * 2 - 1, this.bottom + PANEL_PADDING - 1, PANEL_COLOR);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    /** Premier Y du contenu. */
    static int top() {
        return LEFT + PANEL_PADDING;
    }

    /** Ecran d'ou l'on vient. */
    final Screen parent() {
        return this.parent;
    }
}
