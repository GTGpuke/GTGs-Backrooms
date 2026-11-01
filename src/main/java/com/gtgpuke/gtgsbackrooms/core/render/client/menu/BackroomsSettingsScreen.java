package com.gtgpuke.gtgsbackrooms.core.render.client.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;
import com.gtgpuke.gtgsbackrooms.core.render.quality.VisualPreset;

/** L'ecran de reglages ouvert par le bouton du menu pause : qualite, preset visuel, pixels d'ombre, confort. */
public final class BackroomsSettingsScreen extends SettingsPanel {

    private static final LookQuality[] TIERS = {LookQuality.LOW, LookQuality.MEDIUM, LookQuality.HIGH, LookQuality.ULTRA};
    private static final int[] PIXEL_SIZES = {16, 32, 0};
    private static final VisualPreset[] FIRST_PRESETS = {VisualPreset.FAITHFUL, VisualPreset.PIXEL, VisualPreset.REALISTIC};
    private static final VisualPreset[] SECOND_PRESETS = {VisualPreset.FOUND_FOOTAGE, VisualPreset.CINEMATIC, VisualPreset.COMFORT};

    /** Ecran de reglages. */
    public BackroomsSettingsScreen(Screen parent) {
        super(Text.translatable(KEY + "title"), parent);
    }

    @Override
    protected void init() {
        LookSettings settings = LookSettings.current();
        int y = header(top());
        y = section(y, Text.translatable(KEY + "quality"));
        y = qualityRow(y, settings);
        y = section(y, Text.translatable(KEY + "preset"));
        y = presetRow(y, settings, FIRST_PRESETS);
        y = presetRow(y, settings, SECOND_PRESETS);
        y = section(y, Text.translatable(KEY + "pixels"));
        y = pixelRow(y, settings);
        y = section(y, Text.translatable(KEY + "comfort"));
        y = row(y, new Text[] {optionLabel(LookOption.REDUCE_FLASHES), optionLabel(LookOption.REDUCE_MOTION), optionLabel(LookOption.REDUCE_NOISE)},
                new boolean[] {settings.has(LookOption.REDUCE_FLASHES), settings.has(LookOption.REDUCE_MOTION), settings.has(LookOption.REDUCE_NOISE)},
                new Runnable[] {toggle(LookOption.REDUCE_FLASHES), toggle(LookOption.REDUCE_MOTION), toggle(LookOption.REDUCE_NOISE)});
        y += GAP;
        Screen self = this;
        y = row(y, new Text[] {Text.translatable(KEY + "advanced"), ScreenTexts.DONE}, new boolean[2],
                new Runnable[] {() -> MinecraftClient.getInstance().setScreen(new BackroomsAdvancedScreen(self)), this::close});
        end(y);
        explainUltra();
    }

    private int qualityRow(int y, LookSettings settings) {
        Text[] labels = new Text[TIERS.length + 1];
        boolean[] selected = new boolean[TIERS.length + 1];
        Runnable[] actions = new Runnable[TIERS.length + 1];
        labels[0] = Text.translatable(KEY + "quality.auto");
        selected[0] = LookQuality.AUTO.equals(settings.quality());
        actions[0] = () -> apply(settings.withQuality(LookQuality.AUTO));
        for (int i = 0; i < TIERS.length; i++) {
            LookQuality tier = TIERS[i];
            labels[i + 1] = tierLabel(tier);
            selected[i + 1] = !selected[0] && LookResources.quality() == tier;
            actions[i + 1] = () -> apply(settings.withQuality(tier.key()));
        }
        return row(y, labels, selected, actions);
    }

    private int presetRow(int y, LookSettings settings, VisualPreset[] presets) {
        Text[] labels = new Text[presets.length];
        boolean[] selected = new boolean[presets.length];
        Runnable[] actions = new Runnable[presets.length];
        for (int i = 0; i < presets.length; i++) {
            VisualPreset preset = presets[i];
            labels[i] = presetLabel(preset);
            selected[i] = settings.preset() == preset;
            actions[i] = () -> apply(settings.withPreset(preset));
        }
        return row(y, labels, selected, actions);
    }

    private int pixelRow(int y, LookSettings settings) {
        Text[] labels = new Text[PIXEL_SIZES.length];
        boolean[] selected = new boolean[PIXEL_SIZES.length];
        Runnable[] actions = new Runnable[PIXEL_SIZES.length];
        for (int i = 0; i < PIXEL_SIZES.length; i++) {
            int size = PIXEL_SIZES[i];
            labels[i] = Text.translatable(KEY + "pixels." + size);
            selected[i] = settings.pixelShadows() == size;
            actions[i] = () -> apply(settings.withPixelShadows(size));
        }
        return row(y, labels, selected, actions);
    }
}
