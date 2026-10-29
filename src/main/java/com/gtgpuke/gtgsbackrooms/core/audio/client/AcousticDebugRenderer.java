package com.gtgpuke.gtgsbackrooms.core.audio.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.audio.acoustic.RayTrace;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;

/**
 * Debogage de l'acoustique (option {@code gtgsbackrooms.audio.debugRays}) : les segments des derniers calculs du thread
 * de calcul se dessinent dans le monde du joueur, apres ses couches translucides, hors des vues de portail. Rayons de
 * reverb : jaune pour un rebond, vert pour un rayon sorti sans mur, magenta pour un rayon passe par un cadre, gris pour
 * un rayon perdu hors de la copie du monde ; chemins d'occlusion du vert (rien de retire) au rouge (plafond).
 */
public final class AcousticDebugRenderer {

    private static final float[][] COLORS = {
        {1.0f, 0.85f, 0.1f},
        {0.2f, 1.0f, 0.3f},
        {1.0f, 0.2f, 1.0f},
        {0.5f, 0.5f, 0.5f},
    };

    /**
     * Ce que le dessin tient, cree au premier dessin seulement : la classe se charge a chaque lancement (pour
     * {@link #register}), et l'option coupee ne doit rien reserver (64 Kio hors du tas pour les tampons).
     */
    private static final class Drawing {

        private static final RayTrace SHOWN = new RayTrace(AcousticWorker.TRACE_SHOWN);
        private static final MatrixStack MATRICES = new MatrixStack();
        private static final VertexConsumerProvider.Immediate BUFFERS = VertexConsumerProvider.immediate(new BufferAllocator(1 << 16));
    }

    private AcousticDebugRenderer() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : rien sans l'option. */
    public static void register() {
        if (ModConfig.AUDIO_DEBUG_RAYS) {
            WorldRenderEvents.AFTER_TRANSLUCENT.register(AcousticDebugRenderer::render);
        }
    }

    private static void render(WorldRenderContext context) {
        if (PortalRenderState.isRendering()) {
            return;
        }
        AcousticWorker.copyTrace(Drawing.SHOWN);
        if (Drawing.SHOWN.count() == 0) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        MatrixStack.Entry entry = Drawing.MATRICES.peek();
        VertexConsumer lines = Drawing.BUFFERS.getBuffer(RenderLayer.getLines());
        for (int i = 0; i < Drawing.SHOWN.count(); i++) {
            float x0 = (float) (Drawing.SHOWN.coordinate(i, 0) - camera.x);
            float y0 = (float) (Drawing.SHOWN.coordinate(i, 1) - camera.y);
            float z0 = (float) (Drawing.SHOWN.coordinate(i, 2) - camera.z);
            float x1 = (float) (Drawing.SHOWN.coordinate(i, 3) - camera.x);
            float y1 = (float) (Drawing.SHOWN.coordinate(i, 4) - camera.y);
            float z1 = (float) (Drawing.SHOWN.coordinate(i, 5) - camera.z);
            float dx = x1 - x0;
            float dy = y1 - y0;
            float dz = z1 - z0;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-4f) {
                continue;
            }
            float red;
            float green;
            float blue;
            int kind = Drawing.SHOWN.kind(i);
            if (kind == RayTrace.OCCLUSION) {
                float occlusion = Math.min(1.0f, Drawing.SHOWN.value(i));
                red = occlusion;
                green = 1.0f - occlusion;
                blue = 0.1f;
            } else {
                red = COLORS[kind][0];
                green = COLORS[kind][1];
                blue = COLORS[kind][2];
            }
            lines.vertex(entry, x0, y0, z0).color(red, green, blue, 1.0f).normal(entry, dx / length, dy / length, dz / length);
            lines.vertex(entry, x1, y1, z1).color(red, green, blue, 1.0f).normal(entry, dx / length, dy / length, dz / length);
        }
        Drawing.BUFFERS.draw();
    }
}
