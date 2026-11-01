package com.gtgpuke.gtgsbackrooms.core.render.client.menu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** Un bouton plat, dans l'esthetique du mod : papier peint jauni pour ce qui est choisi, gris pour ce qui ne l'est pas. */
final class PanelButton extends ClickableWidget {

    private static final int SELECTED = 0xFFC9B458;
    private static final int SELECTED_TEXT = 0xFF241E0C;
    private static final int IDLE = 0xFF2A261A;
    private static final int HOVERED = 0xFF4A4228;
    private static final int DISABLED = 0xFF1A1812;
    private static final int TEXT = 0xFFE8DFB8;
    private static final int DISABLED_TEXT = 0xFF6B6650;
    private static final int BORDER = 0xFF6E6438;

    private final Runnable action;
    private final boolean selected;

    PanelButton(int x, int y, int width, int height, Text label, boolean selected, Runnable action) {
        super(x, y, width, height, label);
        this.selected = selected;
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int fill = !this.active ? DISABLED : this.selected ? SELECTED : isHovered() ? HOVERED : IDLE;
        context.fill(getX(), getY(), getRight(), getBottom(), BORDER);
        context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, fill);
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int color = !this.active ? DISABLED_TEXT : this.selected ? SELECTED_TEXT : TEXT;
        context.drawText(font, getMessage(), getX() + (getWidth() - font.getWidth(getMessage())) / 2, getY() + (getHeight() - font.fontHeight + 1) / 2, color, false);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
