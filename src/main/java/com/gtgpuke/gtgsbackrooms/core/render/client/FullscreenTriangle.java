package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Un triangle qui couvre l'ecran, garde dans un tampon de sommets du GPU : positions en coordonnees normalisees,
 * coordonnees de texture de 0 a 1 sur l'ecran. Construit une fois, dessine a chaque passe plein ecran du post sans
 * rien allouer : le tessellator vanilla alloue un tampon et ses parametres a chaque dessin, et
 * {@code ShaderProgram.initializeUniforms} douze noms de samplers ; ici les deux samplers du post sont poses sous leur
 * nom constant, et seulement quand leur texture change pour ce programme (un programme garde ses samplers, et le bloom
 * alterne trois programmes dans une image ; poser un sampler emballe l'identifiant de sa texture). Les textures sont celles de {@code RenderSystem.setShaderTexture}
 * (unites 0 et 1) ; les programmes du post ne lisent aucune matrice.
 */
public final class FullscreenTriangle {

    private static final String SAMPLER_0 = "Sampler0";
    private static final String SAMPLER_1 = "Sampler1";
    private static final int UNKNOWN = -1;
    /** Programmes suivis : ceux du post (copie, extraction et flou du bloom, look), plus une place. */
    private static final int PROGRAMS = 4;
    private static final ShaderProgram[] PROGRAM = new ShaderProgram[PROGRAMS];
    private static final int[] TEXTURE_0 = new int[PROGRAMS];
    private static final int[] TEXTURE_1 = new int[PROGRAMS];
    private static int nextSlot;
    private static @Nullable VertexBuffer buffer;

    private FullscreenTriangle() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Dessine le triangle avec ce programme, ses deux samplers sur les textures posees aux unites 0 et 1. */
    public static void draw(ShaderProgram program) {
        VertexBuffer triangle = buffer;
        if (triangle == null) {
            triangle = new VertexBuffer(VertexBuffer.Usage.STATIC);
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE);
            builder.vertex(-1.0f, -1.0f, 0.0f).texture(0.0f, 0.0f);
            builder.vertex(3.0f, -1.0f, 0.0f).texture(2.0f, 0.0f);
            builder.vertex(-1.0f, 3.0f, 0.0f).texture(0.0f, 2.0f);
            triangle.bind();
            triangle.upload(builder.end());
            buffer = triangle;
        } else {
            triangle.bind();
        }
        int texture0 = RenderSystem.getShaderTexture(0);
        int texture1 = RenderSystem.getShaderTexture(1);
        int slot = slotOf(program);
        if (texture0 != TEXTURE_0[slot]) {
            program.addSampler(SAMPLER_0, texture0);
            TEXTURE_0[slot] = texture0;
        }
        if (texture1 != TEXTURE_1[slot]) {
            program.addSampler(SAMPLER_1, texture1);
            TEXTURE_1[slot] = texture1;
        }
        program.bind();
        triangle.draw();
        program.unbind();
        VertexBuffer.unbind();
    }

    /** La place de ce programme parmi ceux suivis ; un nouveau prend la plus ancienne, ses samplers a poser. */
    private static int slotOf(ShaderProgram program) {
        for (int i = 0; i < PROGRAMS; i++) {
            if (PROGRAM[i] == program) {
                return i;
            }
        }
        int slot = nextSlot;
        nextSlot = (nextSlot + 1) % PROGRAMS;
        PROGRAM[slot] = program;
        TEXTURE_0[slot] = UNKNOWN;
        TEXTURE_1[slot] = UNKNOWN;
        return slot;
    }

    /** Fin de partie : le tampon est rendu au GPU, les samplers seront reposes. */
    public static void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        for (int i = 0; i < PROGRAMS; i++) {
            PROGRAM[i] = null;
        }
        nextSlot = 0;
    }
}
