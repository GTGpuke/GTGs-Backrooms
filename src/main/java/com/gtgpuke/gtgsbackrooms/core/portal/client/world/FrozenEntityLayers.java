package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;

/**
 * Entities figees de la vue allegee d'un portail (palier de detail, {@code ViewDetail}) : leur geometrie, ecrite une fois
 * par le rendu vanilla dans des tampons a part, est televersee dans des tampons de sommets du GPU et se redessine telle
 * quelle a chaque image, sans repasser par les renderers d'entities. Elle se reprend toutes les {@value #REFRESH_TICKS}
 * ticks du monde de la vue, ou des que la camera s'est deplacee de plus d'un bloc depuis la prise (la prise ne garde que
 * ce que le cadre montre depuis elle) : camera immobile, les entities restent figees ; de loin, elles bougent par sauts.
 * Ses sommets sont relatifs a la camera de la prise : au dessin, ils se decalent de l'ecart entre cette camera et la
 * camera courante.
 *
 * <p>Thread de rendu seulement. Les tampons d'ecriture se gardent d'une prise a l'autre (un par couche de rendu), les
 * tampons du GPU aussi ; {@link #close} rend tout.
 */
final class FrozenEntityLayers implements VertexConsumerProvider {

    /** Ticks entre deux prises : cinq secondes. */
    static final int REFRESH_TICKS = 100;
    /** Deplacement de la camera, en blocs, au-dela duquel la prise est a refaire : le cadre montre alors autre chose. */
    private static final double REFRESH_DISTANCE = 1.0;
    private static final int INITIAL_BUFFER_SIZE = 1536;
    private static final Matrix4f SHIFTED = new Matrix4f();

    final BackroomsPortalEntity portal;
    private final Map<RenderLayer, BufferAllocator> allocators = new IdentityHashMap<>();
    private final Map<RenderLayer, VertexBuffer> uploaded = new IdentityHashMap<>();
    /** Couches de la prise en cours, dans l'ordre de leur premiere ecriture, et leurs tampons d'ecriture. */
    private final List<RenderLayer> capturing = new ArrayList<>();
    private final List<BufferBuilder> builders = new ArrayList<>();
    /** Couches de la derniere prise, dans l'ordre ou elles se dessinent. */
    private final List<RenderLayer> drawn = new ArrayList<>();
    private double capturedX;
    private double capturedY;
    private double capturedZ;
    private long capturedAt = Long.MIN_VALUE / 2;
    /** Derniere image ou cette vue s'est dessinee allegee. */
    int usedFrame;

    FrozenEntityLayers(BackroomsPortalEntity portal) {
        this.portal = portal;
    }

    /**
     * {@code true} si la geometrie est a reprendre a ce tick du monde de la vue, depuis cette camera : jamais prise, trop
     * vieille, horloge revenue en arriere, ou camera trop loin de celle de la prise.
     */
    boolean stale(long worldTime, Vec3d cameraPos) {
        return worldTime - this.capturedAt >= REFRESH_TICKS || worldTime < this.capturedAt
                || cameraPos.squaredDistanceTo(this.capturedX, this.capturedY, this.capturedZ) > REFRESH_DISTANCE * REFRESH_DISTANCE;
    }

    /** Debut d'une prise, depuis cette camera. */
    void begin(Vec3d cameraPos) {
        this.capturing.clear();
        this.builders.clear();
        this.capturedX = cameraPos.x;
        this.capturedY = cameraPos.y;
        this.capturedZ = cameraPos.z;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        for (int i = 0; i < this.capturing.size(); i++) {
            if (this.capturing.get(i) == layer) {
                return this.builders.get(i);
            }
        }
        BufferAllocator allocator = this.allocators.get(layer);
        if (allocator == null) {
            allocator = new BufferAllocator(INITIAL_BUFFER_SIZE);
            this.allocators.put(layer, allocator);
        }
        BufferBuilder builder = new BufferBuilder(allocator, layer.getDrawMode(), layer.getVertexFormat());
        this.capturing.add(layer);
        this.builders.add(builder);
        return builder;
    }

    /** Fin de la prise : chaque couche ecrite part au GPU (les translucides triees depuis la camera de la prise). */
    void end(long worldTime) {
        this.drawn.clear();
        for (int i = 0; i < this.capturing.size(); i++) {
            RenderLayer layer = this.capturing.get(i);
            BuiltBuffer built = this.builders.get(i).endNullable();
            if (built == null) {
                continue;
            }
            if (layer.isTranslucent()) {
                built.sortQuads(this.allocators.get(layer), RenderSystem.getVertexSorting());
            }
            VertexBuffer buffer = this.uploaded.get(layer);
            if (buffer == null) {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                this.uploaded.put(layer, buffer);
            }
            buffer.bind();
            buffer.upload(built);
            VertexBuffer.unbind();
            this.drawn.add(layer);
        }
        this.capturing.clear();
        this.builders.clear();
        this.capturedAt = worldTime;
    }

    /** Dessine la derniere prise, decalee de l'ecart entre la camera de la prise et celle-ci. */
    void draw(Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f projection) {
        SHIFTED.set(positionMatrix).translate(
                (float) (this.capturedX - cameraPos.x), (float) (this.capturedY - cameraPos.y), (float) (this.capturedZ - cameraPos.z));
        for (int i = 0; i < this.drawn.size(); i++) {
            RenderLayer layer = this.drawn.get(i);
            layer.startDrawing();
            try {
                ShaderProgram shader = RenderSystem.getShader();
                if (shader != null) {
                    VertexBuffer buffer = this.uploaded.get(layer);
                    buffer.bind();
                    buffer.draw(SHIFTED, projection, shader);
                    VertexBuffer.unbind();
                }
            } finally {
                layer.endDrawing();
            }
        }
    }

    /** Rend les tampons du GPU et d'ecriture. */
    void close() {
        for (VertexBuffer buffer : this.uploaded.values()) {
            buffer.close();
        }
        for (BufferAllocator allocator : this.allocators.values()) {
            allocator.close();
        }
        this.uploaded.clear();
        this.allocators.clear();
        this.drawn.clear();
        this.capturing.clear();
        this.builders.clear();
    }
}
