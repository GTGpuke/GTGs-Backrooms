package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Les neons qui eclairent une vue : les plus proches de sa camera ({@link NeonIndex}), avec leur etat du moment
 * ({@link NeonSchedule}), envoyes aux shaders dans un bloc d'uniformes ({@code gtgs_NeonBlock}, position en espace
 * camera et portee, couleur et etat), un par niveau de vue : la vue qui contient une autre reprend le sien en sortant.
 * Le bloc porte aussi les perturbations des cris de la dimension de la vue (source relative a sa camera, portee, force)
 * : le jumeau GLSL du calendrier les applique a la face de chaque neon, au-dela des plus proches. Rien n'est alloue par
 * image : tampons et listes sont prepares une fois.
 */
public final class NeonLights {

    /** Point de liaison du bloc d'uniformes : les programmes patches y lient leur bloc une fois. */
    public static final int BINDING = 1;
    /** Nombre de neons que le bloc peut porter, celui que declare {@code look.glsl}. */
    public static final int CAPACITY = 32;
    /** Monde du joueur, une vue, une vue imbriquee, et une de marge. */
    private static final int DEPTHS = 4;
    private static final int FLOATS_PER_NEON = 4;
    /** Perturbations dans le bloc, celles que declare {@code look.glsl}. */
    private static final int DISTURBANCES = 4;
    private static final int DISTURBANCE_OFFSET = CAPACITY * FLOATS_PER_NEON * 2;
    private static final int FORCE_OFFSET = DISTURBANCE_OFFSET + DISTURBANCES * 4;
    /** Direction ou eclairent les dalles, en espace camera. */
    private static final int AXIS_OFFSET = FORCE_OFFSET + DISTURBANCES;
    /** Neons que comptent les halos et les reflets : les autres s'y fondent sur {@link #NEAR_FADE} blocs, sans saut. */
    private static final int NEAREST = 10;
    private static final float NEAR_FADE = 6.0f;
    private static final float SEARCH_MARGIN = 16.0f;
    private static final int FACES_OFFSET = AXIS_OFFSET + 4;
    private static final int CASTERS_OFFSET = FACES_OFFSET + CAPACITY * FLOATS_PER_NEON;
    private static final int CASTER_RAYS_OFFSET = CASTERS_OFFSET + ShadowCasters.VALUES;
    private static final int CASTER_LIGHT_OFFSET = CASTER_RAYS_OFFSET + 4;
    private static final int BLOCK_FLOATS = CASTER_LIGHT_OFFSET + 4;
    /** La plus grande lumiere qu'une tuile retient, celle de {@code surface.fsh}. */
    private static final float TILE_MAX_LIGHT = 4.0f;
    private static final FloatBuffer[] BLOCKS = new FloatBuffer[DEPTHS];
    private static final int[] COUNTS = new int[DEPTHS];
    private static final NeonSelection SELECTION = new NeonSelection(CAPACITY);
    private static final int[] KEPT = new int[CAPACITY];
    private static final Vector3f VIEW_POSITION = new Vector3f();
    private static final ShadowCasters CASTERS = new ShadowCasters();
    private static int depth;
    private static int buffer;

    static {
        for (int i = 0; i < DEPTHS; i++) {
            BLOCKS[i] = BufferUtils.createFloatBuffer(BLOCK_FLOATS);
        }
    }

    private NeonLights() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Monde du joueur, au debut du rendu de l'image : ses neons au fond de la pile. */
    public static void beginWorld(World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta) {
        depth = 0;
        fill(world, cameraPos, positionMatrix, tickDelta);
    }

    /** Entree d'une vue : ses neons, vus de sa camera. */
    public static void push(World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta) {
        if (depth == DEPTHS - 1) {
            throw new IllegalStateException("Plus de " + (DEPTHS - 1) + " vues de neons empilees.");
        }
        depth++;
        fill(world, cameraPos, positionMatrix, tickDelta);
    }

    /** Sortie d'une vue : les neons de la vue qui la contient reprennent. */
    public static void pop() {
        depth--;
        upload();
    }

    private static void fill(World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta) {
        LookProfile.Neons neons = LookProfiles.of(world.getRegistryKey()).neons();
        FloatBuffer block = BLOCKS[depth];
        block.clear();
        int count = 0;
        // Sans l'etage 1 dans les shaders (mod qui dessine le terrain), la lumiere des neons n'a personne pour la lire.
        if (neons.any() && LookState.shadersCarryLook()) {
            int wanted = Math.min(CAPACITY, LookResources.quality().neonCount());
            float radius = neons.radius();
            LookProfile.BakedLight baked = LookProfiles.of(world.getRegistryKey()).bakedLight();
            float behind = Math.max(radius, baked.reach());
            int originX = MathHelper.floor(cameraPos.x);
            int originY = MathHelper.floor(cameraPos.y);
            int originZ = MathHelper.floor(cameraPos.z);
            NeonIndex.collect(world, cameraPos.x, cameraPos.y, cameraPos.z, radius * 2.0 + SEARCH_MARGIN, SELECTION);
            int available = SELECTION.count();
            int tick = CueClock.tick(world.getTime());
            for (int i = 0; i < available && count < wanted; i++) {
                long packed = SELECTION.position(i);
                float dx = (float) (BlockPos.unpackLongX(packed) + 0.5 - cameraPos.x);
                float dy = (float) (BlockPos.unpackLongY(packed) + 0.5 - cameraPos.y);
                float dz = (float) (BlockPos.unpackLongZ(packed) + 0.5 - cameraPos.z);
                positionMatrix.transformPosition(dx, dy, dz, VIEW_POSITION);
                // Derriere la camera de plus que sa portee (la camera regarde vers -z) : aucun fragment visible ne le recoit.
                if (VIEW_POSITION.z > behind) {
                    continue;
                }
                block.put(VIEW_POSITION.x).put(VIEW_POSITION.y).put(VIEW_POSITION.z).put(radius);
                int at = FACES_OFFSET + count * FLOATS_PER_NEON;
                block.put(at, BlockPos.unpackLongX(packed) + 0.5f - originX).put(at + 1, (float) (BlockPos.unpackLongY(packed) - originY))
                        .put(at + 2, BlockPos.unpackLongZ(packed) + 0.5f - originZ).put(at + 3, SELECTION.nearShare(i, NEAREST, NEAR_FADE, radius * 2.0f + SEARCH_MARGIN));
                KEPT[count++] = i;
            }
            block.position(CAPACITY * FLOATS_PER_NEON);
            Identifier dimension = world.getRegistryKey().getValue();
            int seed = NeonSchedule.seed(dimension);
            for (int k = 0; k < count; k++) {
                long packed = SELECTION.position(KEPT[k]);
                // L'etat du calendrier, moins ce qu'un cri proche lui retire (liaison son-image, plafond de clignotement compris).
                float state = NeonSchedule.intensity(packed, seed, tick, neons.flickering(), neons.dead(), neons.blackout())
                        * CueTracker.neonFactor(dimension, BlockPos.unpackLongX(packed) + 0.5, BlockPos.unpackLongY(packed) + 0.5, BlockPos.unpackLongZ(packed) + 0.5);
                block.put(neons.red() * neons.strength()).put(neons.green() * neons.strength()).put(neons.blue() * neons.strength()).put(state);
            }
            disturbances(block, dimension, cameraPos);
            casters(block, world, LookProfiles.of(world.getRegistryKey()), originX, originY, originZ, tickDelta);
        } else {
            for (int i = DISTURBANCE_OFFSET; i < BLOCK_FLOATS; i++) {
                block.put(i, 0.0f);
            }
        }
        positionMatrix.transformDirection(0.0f, -1.0f, 0.0f, VIEW_POSITION);
        block.put(AXIS_OFFSET, VIEW_POSITION.x).put(AXIS_OFFSET + 1, VIEW_POSITION.y).put(AXIS_OFFSET + 2, VIEW_POSITION.z);
        block.position(0).limit(BLOCK_FLOATS);
        COUNTS[depth] = count;
        upload();
    }

    /**
     * Les corps qui portent une ombre et ce qu'il faut savoir des dalles pour la calculer, rien dans une vue de portail, plafonnee sous ce
     * palier : leur ombre de contact a tous les paliers a lumiere cuite, leurs ombres portees aux paliers qui ont des rayons.
     */
    private static void casters(FloatBuffer block, World world, LookProfile look, int originX, int originY, int originZ, float tickDelta) {
        LookProfile.BakedLight baked = look.bakedLight();
        boolean shown = depth == 0 && baked.any();
        int rays = shown ? LookResources.quality().casterRays() : 0;
        if (shown) {
            EntityShadows.write(world, originX, originY, originZ, tickDelta, CASTERS);
        } else {
            CASTERS.clear();
        }
        block.position(CASTERS_OFFSET);
        block.put(CASTERS.values(), 0, CASTERS.bodies() * ShadowCasters.BODY_VALUES);
        LookProfile.Neons neons = look.neons();
        float emission = Math.max(neons.red(), Math.max(neons.green(), neons.blue())) * baked.emission();
        block.put(CASTER_RAYS_OFFSET, CASTERS.bodies()).put(CASTER_RAYS_OFFSET + 1, rays).put(CASTER_RAYS_OFFSET + 3, baked.reach());
        block.put(CASTER_LIGHT_OFFSET, (float) Math.cos(Math.toRadians(baked.angle()))).put(CASTER_LIGHT_OFFSET + 1, baked.size())
                .put(CASTER_LIGHT_OFFSET + 2, emission > 0.0f ? TILE_MAX_LIGHT / emission : 0.0f).put(CASTER_LIGHT_OFFSET + 3, emission);
    }

    /** Les perturbations de la dimension de la vue : source relative a sa camera (axes du monde), portee, force. */
    private static void disturbances(FloatBuffer block, Identifier dimension, Vec3d cameraPos) {
        for (int slot = 0; slot < DISTURBANCES; slot++) {
            int at = DISTURBANCE_OFFSET + slot * 4;
            if (CueTracker.disturbsNeons(slot, dimension)) {
                block.put(at, (float) (CueTracker.disturbanceX(slot) - cameraPos.x));
                block.put(at + 1, (float) (CueTracker.disturbanceY(slot) - cameraPos.y));
                block.put(at + 2, (float) (CueTracker.disturbanceZ(slot) - cameraPos.z));
                block.put(at + 3, CueTracker.neonRadius(slot));
                block.put(FORCE_OFFSET + slot, CueTracker.neonForce(slot));
            } else {
                block.put(at + 3, 0.0f);
                block.put(FORCE_OFFSET + slot, 0.0f);
            }
        }
    }

    /** Envoie le bloc du niveau courant au GPU et le lie ; le compte part avec les autres uniformes du look. */
    private static void upload() {
        if (GlStateCheck.ENABLED) {
            GlStateCheck.before(GlStateCheck.NEON_UPLOAD);
        }
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, (long) BLOCK_FLOATS * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        } else {
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, buffer);
        }
        FloatBuffer block = BLOCKS[depth];
        block.position(0).limit(BLOCK_FLOATS);
        GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0L, block);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, buffer);
        GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);
        LookState.setNeonCount(COUNTS[depth]);
        if (GlStateCheck.ENABLED) {
            GlStateCheck.after(GlStateCheck.NEON_UPLOAD);
        }
    }

    /** Fin de partie : le tampon GPU est rendu. */
    public static void close() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer);
            buffer = 0;
        }
        depth = 0;
    }
}
