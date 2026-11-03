package com.gtgpuke.gtgsbackrooms.core.render.client.menu;

import java.util.Locale;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import com.gtgpuke.gtgsbackrooms.core.render.client.GpuTimer;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * Les reglages avances : chaque effet un par un, le palier desactive, et ce que coutent vraiment les passes du moteur : le
 * temps GPU mesure sur la derniere seconde, relu une fois par seconde tant que cet ecran est ouvert.
 */
final class BackroomsAdvancedScreen extends SettingsPanel {

    private static final LookOption[][] ROWS = {{LookOption.SURFACE_CACHE, LookOption.RELIEF}, {LookOption.BLOOM, LookOption.VIGNETTE}, {LookOption.HALOS, LookOption.SHEEN}};
    private static final int TICKS_PER_SECOND = 20;
    private static final int LINE_COLOR = 0xFFE8DFB8;
    private static final int LINE_HEIGHT = 10;
    private Text world = Text.empty();
    private Text bloom = Text.empty();
    private Text post = Text.empty();
    private int ticks;

    BackroomsAdvancedScreen(Screen parent) {
        super(Text.translatable(KEY + "advanced"), parent);
    }

    @Override
    protected void init() {
        GpuTimer.measure(true);
        refresh();
        LookSettings settings = LookSettings.current();
        int y = header(top());
        for (LookOption[] options : ROWS) {
            y = row(y, new Text[] {optionLabel(options[0]), optionLabel(options[1])}, new boolean[] {settings.has(options[0]), settings.has(options[1])},
                    new Runnable[] {toggle(options[0]), toggle(options[1])});
        }
        y = section(y + GAP, Text.translatable(KEY + "cost"));
        int lines = y;
        addDrawable((context, mouseX, mouseY, delta) -> {
            context.drawText(this.textRenderer, this.world, LEFT + 6, lines, LINE_COLOR, false);
            context.drawText(this.textRenderer, this.bloom, LEFT + 6, lines + LINE_HEIGHT, LINE_COLOR, false);
            context.drawText(this.textRenderer, this.post, LEFT + 6, lines + LINE_HEIGHT * 2, LINE_COLOR, false);
        });
        y = lines + LINE_HEIGHT * 3 + GAP;
        y = row(y, new Text[] {ScreenTexts.DONE}, new boolean[1], new Runnable[] {this::close});
        end(y);
    }

    private void refresh() {
        this.world = cost("cost.world", GpuTimer.milliseconds(GpuTimer.WORLD));
        this.bloom = cost("cost.bloom", GpuTimer.milliseconds(GpuTimer.BLOOM));
        this.post = cost("cost.post", GpuTimer.milliseconds(GpuTimer.POST) + GpuTimer.milliseconds(GpuTimer.COPY));
    }

    private static Text cost(String key, float milliseconds) {
        return Text.translatable(KEY + key, String.format(Locale.ROOT, "%.2f", milliseconds));
    }

    @Override
    public void tick() {
        if (++this.ticks % TICKS_PER_SECOND == 0) {
            refresh();
        }
    }

    @Override
    public void removed() {
        GpuTimer.measure(false);
    }
}
