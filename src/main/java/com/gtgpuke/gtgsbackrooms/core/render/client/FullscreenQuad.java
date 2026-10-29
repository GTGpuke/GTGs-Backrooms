package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Un quadrilatere qui couvre l'ecran, dessine avec le shader des positions colorees et des matrices a l'identite, puis
 * les matrices rendues : masques du mode de debogage du stencil seulement (il passe par le tessellator, qui alloue a
 * chaque dessin ; le post passe par {@link FullscreenTriangle}). L'appelant pose tests et masques.
 */
public final class FullscreenQuad {

    private static final Matrix4f IDENTITY = new Matrix4f();

    private FullscreenQuad() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Dessine le quadrilatere d'une couleur unie, opaque. */
    public static void draw(float red, float green, float blue) {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorter sorter = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(IDENTITY, VertexSorter.BY_Z);
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(-1.0f, -1.0f, 0.0f).color(red, green, blue, 1.0f);
            buffer.vertex(1.0f, -1.0f, 0.0f).color(red, green, blue, 1.0f);
            buffer.vertex(1.0f, 1.0f, 0.0f).color(red, green, blue, 1.0f);
            buffer.vertex(-1.0f, 1.0f, 0.0f).color(red, green, blue, 1.0f);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorter);
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }
}
