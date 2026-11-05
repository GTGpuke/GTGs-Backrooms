package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.nio.FloatBuffer;

import org.jetbrains.annotations.Nullable;
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
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
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
    /** Nombre de neons que le bloc peut porter, celui que declare {@code neon_block.glsl}. */
    public static final int CAPACITY = 32;
    /** Monde du joueur, une vue, une vue imbriquee, et une de marge. */
    static final int DEPTHS = 4;
    private static final int FLOATS_PER_NEON = 4;
    /** Perturbations dans le bloc, celles que declare {@code neon_block.glsl}. */
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
    /** Ouvertures des portails qui eclairent la vue, celles que declare {@code neon_block.glsl} : centre et face, puis couleur. */
    public static final int OPENINGS = 4;
    private static final int OPENING_FLOATS = 8;
    private static final int OPENINGS_OFFSET = CASTER_LIGHT_OFFSET + 4;
    private static final int OPENING_COUNT_OFFSET = OPENINGS_OFFSET + OPENINGS * OPENING_FLOATS;
    /** Cadres et neons vus par eux, ceux que declare {@code neon_block.glsl}. */
    private static final int FRAMES_OFFSET = OPENING_COUNT_OFFSET + 4;
    private static final int SEEN_OFFSET = FRAMES_OFFSET + PortalLightUniforms.FRAMES * PortalLightUniforms.FRAME_FLOATS;
    /** Plan d'arrivee de la vue : normale et decalage, relatif au bloc de sa camera ; nul pour le monde du joueur. */
    private static final int VIEW_PLANE_OFFSET = SEEN_OFFSET + PortalLightUniforms.SEEN * PortalLightUniforms.SEEN_FLOATS;
    /** Neons du halo d'une vue jusqu'a son plan d'arrivee, au plus : ceux du halo de la vue qui la contient ({@code look.glsl}, dix). */
    private static final int HALO_NEONS = 10;
    private static final int HALO_FLOATS = 8;
    private static final int HALO_OFFSET = VIEW_PLANE_OFFSET + 4;
    private static final int BLOCK_FLOATS = HALO_OFFSET + HALO_NEONS * HALO_FLOATS;
    private static final float[] FRAME_VALUES = new float[PortalLightUniforms.FRAMES * PortalLightUniforms.FRAME_FLOATS];
    private static final float[] SEEN_VALUES = new float[PortalLightUniforms.SEEN * PortalLightUniforms.SEEN_FLOATS];
    private static final int[] ORIGIN = new int[3];
    /** La plus grande lumiere qu'une tuile retient, celle de {@code surface.fsh}. */
    private static final float TILE_MAX_LIGHT = 4.0f;
    /** Bloc de la camera de chaque niveau de vue : les faces des neons de son bloc lui sont relatives. */
    private static final int[][] ORIGINS = new int[DEPTHS][3];
    private static final FloatBuffer[] BLOCKS = new FloatBuffer[DEPTHS];
    private static final int[] COUNTS = new int[DEPTHS];
    private static final NeonSelection SELECTION = new NeonSelection(CAPACITY);
    private static final int[] KEPT = new int[CAPACITY];
    private static final Vector3f VIEW_POSITION = new Vector3f();
    private static final ShadowCasters CASTERS = new ShadowCasters();
    private static int depth;
    private static int buffer;
    /** Ouvertures envoyees pour le monde du joueur a la derniere image (tests du client). */
    private static int playerOpenings;

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
        fill(world, cameraPos, positionMatrix, tickDelta, null);
    }

    /** Entree d'une vue : ses neons, vus de sa camera, le plan de son cadre d'arrivee et le halo de l'air d'avant lui. */
    public static void push(World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta, BackroomsPortalEntity portal) {
        if (depth == DEPTHS - 1) {
            throw new IllegalStateException("Plus de " + (DEPTHS - 1) + " vues de neons empilees.");
        }
        depth++;
        fill(world, cameraPos, positionMatrix, tickDelta, portal);
    }

    /** Sortie d'une vue : les neons de la vue qui la contient reprennent. */
    public static void pop() {
        depth--;
        upload();
    }

    private static void fill(World world, Vec3d cameraPos, Matrix4f positionMatrix, float tickDelta, @Nullable BackroomsPortalEntity portal) {
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
            // Les cadres d'abord : un corps de l'autre cote se lie a la place du sien.
            openings(block, world, cameraPos, behind, originX, originY, originZ);
            casters(block, world, LookProfiles.of(world.getRegistryKey()), originX, originY, originZ, tickDelta);
            ORIGINS[depth][0] = originX;
            ORIGINS[depth][1] = originY;
            ORIGINS[depth][2] = originZ;
            viewPlane(block, portal, originX, originY, originZ);
            block.put(OPENING_COUNT_OFFSET + 3, portal == null ? 0 : haloBefore(block, portal, originX, originY, originZ));
        } else {
            for (int i = DISTURBANCE_OFFSET; i < BLOCK_FLOATS; i++) {
                block.put(i, 0.0f);
            }
            // Aucun corps non plus : la passe des ombres des corps lit cette liste, et ceux du dernier remplissage, places autour
            // d'une autre camera, y faisaient calculer des tuiles pour rien.
            CASTERS.clear();
            if (depth == 0) {
                playerOpenings = 0;
            }
        }
        positionMatrix.transformDirection(0.0f, -1.0f, 0.0f, VIEW_POSITION);
        block.put(AXIS_OFFSET, VIEW_POSITION.x).put(AXIS_OFFSET + 1, VIEW_POSITION.y).put(AXIS_OFFSET + 2, VIEW_POSITION.z);
        block.position(0).limit(BLOCK_FLOATS);
        COUNTS[depth] = count;
        upload();
    }

    /**
     * Les corps qui portent une ombre et ce qu'il faut savoir des dalles pour la calculer, dans la vue du joueur comme dans celle d'un
     * portail : leur ombre de contact a tous les paliers a lumiere cuite, leurs ombres portees aux paliers qui ont des rayons, la ou la
     * zone liee a la vue a sa grille des blocs (les rayons y marchent). Les corps de l'autre cote des cadres sont envoyes apres ceux de ce
     * cote, a tous les paliers : leur ombre de contact passe le cadre (le joueur colle a un cadre a son ombre de part et d'autre du plan),
     * et, avec les rayons, ils coupent la lumiere qui passe par leur cadre.
     */
    private static void casters(FloatBuffer block, World world, LookProfile look, int originX, int originY, int originZ, float tickDelta) {
        LookProfile.BakedLight baked = look.bakedLight();
        boolean shown = baked.any();
        int rays = shown && LightVolume.surfacesBound() ? LookResources.quality().casterRays() : 0;
        if (shown) {
            EntityShadows.write(world, originX, originY, originZ, tickDelta, CASTERS);
            EntityShadows.writeBeyond(world, originX, originY, originZ, tickDelta, FRAME_VALUES, PortalLightUniforms.frames(), CASTERS);
        } else {
            CASTERS.clear();
        }
        block.position(CASTERS_OFFSET);
        block.put(CASTERS.values(), 0, CASTERS.bodies() * ShadowCasters.BODY_VALUES);
        LookProfile.Neons neons = look.neons();
        float emission = Math.max(neons.red(), Math.max(neons.green(), neons.blue())) * baked.emission();
        block.put(CASTER_RAYS_OFFSET, CASTERS.here()).put(CASTER_RAYS_OFFSET + 1, rays).put(CASTER_RAYS_OFFSET + 2, CASTERS.bodies() - CASTERS.here())
                .put(CASTER_RAYS_OFFSET + 3, baked.reach());
        block.put(CASTER_LIGHT_OFFSET, (float) Math.cos(Math.toRadians(baked.angle()))).put(CASTER_LIGHT_OFFSET + 1, baked.size())
                .put(CASTER_LIGHT_OFFSET + 2, emission > 0.0f ? TILE_MAX_LIGHT / emission : 0.0f).put(CASTER_LIGHT_OFFSET + 3, emission);
    }

    /**
     * Les passages des portails dans le monde de la vue, ceux que la cuisson de sa zone compte, au plus {@value #OPENINGS}, a portee de la
     * camera : centre relatif au bloc de la camera et face, puis ce qui en sort, mesure dans la lumiere cuite de l'autre cote (la moyenne
     * de ses directions, avec un rebond, comme une surface le renvoie). Les corps la recoivent a tous les paliers.
     */
    private static void openings(FloatBuffer block, World world, Vec3d cameraPos, float reach, int originX, int originY, int originZ) {
        PortalOpenings openings = LightCopy.openings(world);
        LightZone.PassageLight measured = LightCopy.passageLight(world);
        LightBakeSettings settings = LightCopy.zoneSettings(world);
        float[] light = measured != null && measured.openings() == openings ? measured.values() : null;
        int count = 0;
        for (int i = 0; light != null && settings != null && i < openings.count() && count < OPENINGS; i++) {
            if (openings.kind(i) != PortalOpenings.PASSAGE) {
                continue;
            }
            float x = openings.center(i, 0);
            float y = openings.center(i, 1);
            float z = openings.center(i, 2);
            float dx = (float) (x - cameraPos.x);
            float dy = (float) (y - cameraPos.y);
            float dz = (float) (z - cameraPos.z);
            if (dx * dx + dy * dy + dz * dz > (reach + SEARCH_MARGIN) * (reach + SEARCH_MARGIN)) {
                continue;
            }
            int at = OPENINGS_OFFSET + count * OPENING_FLOATS;
            block.put(at, x - originX).put(at + 1, y - originY).put(at + 2, z - originZ).put(at + 3, openings.face(i));
            for (int channel = 0; channel < 3; channel++) {
                float sum = 0.0f;
                for (int direction = 0; direction < LightZone.PASSAGE_DIRECTIONS; direction++) {
                    sum += light[i * LightZone.PASSAGE_VALUES + direction * 6 + 3 + channel];
                }
                block.put(at + 4 + channel, sum / LightZone.PASSAGE_DIRECTIONS * settings.bounce());
            }
            block.put(at + 7, 0.0f);
            count++;
        }
        block.put(OPENING_COUNT_OFFSET, count);
        if (depth == 0) {
            playerOpenings = count;
        }
        ORIGIN[0] = originX;
        ORIGIN[1] = originY;
        ORIGIN[2] = originZ;
        PortalLightUniforms.pack(openings, ORIGIN, cameraPos.x, cameraPos.y, cameraPos.z, reach + SEARCH_MARGIN, FRAME_VALUES, SEEN_VALUES);
        block.put(OPENING_COUNT_OFFSET + 1, PortalLightUniforms.frames()).put(OPENING_COUNT_OFFSET + 2, PortalLightUniforms.seen());
        block.position(FRAMES_OFFSET);
        block.put(FRAME_VALUES, 0, PortalLightUniforms.frames() * PortalLightUniforms.FRAME_FLOATS);
        block.position(SEEN_OFFSET);
        block.put(SEEN_VALUES, 0, PortalLightUniforms.seen() * PortalLightUniforms.SEEN_FLOATS);
    }

    /**
     * Le plan du cadre d'arrivee d'une vue, relatif au bloc de sa camera : normale du cote vu et decalage, {@code dot(normale, point) +
     * decalage >= 0} du cote vu. Jusqu'a ce plan, le rayon d'un pixel de la vue traverse l'air du monde du joueur, devant son cadre :
     * le halo y vient de ses neons, vus par le cadre d'arrivee. Nul pour le monde du joueur.
     */
    private static void viewPlane(FloatBuffer block, @Nullable BackroomsPortalEntity portal, int originX, int originY, int originZ) {
        if (portal == null) {
            block.put(VIEW_PLANE_OFFSET, 0.0f).put(VIEW_PLANE_OFFSET + 1, 0.0f).put(VIEW_PLANE_OFFSET + 2, 0.0f).put(VIEW_PLANE_OFFSET + 3, 0.0f);
            return;
        }
        Vec3d arrival = portal.destinationPos();
        Vec3d exitNormal = portal.facingDestination().normal;
        double offset = -(exitNormal.x * (arrival.x - originX) + exitNormal.y * (arrival.y - originY) + exitNormal.z * (arrival.z - originZ));
        block.put(VIEW_PLANE_OFFSET, (float) exitNormal.x).put(VIEW_PLANE_OFFSET + 1, (float) exitNormal.y).put(VIEW_PLANE_OFFSET + 2, (float) exitNormal.z)
                .put(VIEW_PLANE_OFFSET + 3, (float) offset);
    }

    /**
     * Le halo de l'air d'une vue d'avant son plan d'arrivee, celui de la vue qui la contient entre son oeil et le cadre : exactement les
     * neons du halo de cette vue (les {@value #HALO_NEONS} premiers de son bloc, avec leur etat et leur part dans la liste des plus
     * proches), passes par le portail. Ainsi l'air de part et d'autre du bord du cadre a le meme halo : ni coupe net, ni de trop.
     *
     * @return nombre de neons poses
     */
    private static int haloBefore(FloatBuffer block, BackroomsPortalEntity portal, int originX, int originY, int originZ) {
        FloatBuffer outer = BLOCKS[depth - 1];
        int[] outerOrigin = ORIGINS[depth - 1];
        Vec3d entry = portal.getPos();
        Vec3d arrival = portal.destinationPos();
        PortalFacing source = portal.facingSource();
        PortalFacing exit = portal.facingDestination();
        int count = Math.min(COUNTS[depth - 1], HALO_NEONS);
        int written = 0;
        for (int i = 0; i < count; i++) {
            int face = FACES_OFFSET + i * FLOATS_PER_NEON;
            double x = outer.get(face) + outerOrigin[0];
            double y = outer.get(face + 1) + outerOrigin[1];
            double z = outer.get(face + 2) + outerOrigin[2];
            double tx = PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y, z, 0);
            double ty = PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y, z, 1);
            double tz = PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y, z, 2);
            // La face qui eclaire, celle du bas, passee par le portail : l'image d'un point plus bas.
            int down = PortalOpenings.face(Math.round(PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y - 1.0, z, 0) - tx),
                    Math.round(PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y - 1.0, z, 1) - ty),
                    Math.round(PortalFacing.transformCoordinate(source, entry, exit, arrival, x, y - 1.0, z, 2) - tz));
            if (down < 0) {
                continue;
            }
            int color = CAPACITY * FLOATS_PER_NEON + i * FLOATS_PER_NEON;
            float weight = outer.get(color + 3) * outer.get(face + 3);
            int at = HALO_OFFSET + written * HALO_FLOATS;
            block.put(at, (float) (tx - originX)).put(at + 1, (float) (ty - originY)).put(at + 2, (float) (tz - originZ)).put(at + 3, down);
            block.put(at + 4, outer.get(color) * weight).put(at + 5, outer.get(color + 1) * weight).put(at + 6, outer.get(color + 2) * weight).put(at + 7, 0.0f);
            written++;
        }
        return written;
    }

    /** Ouvertures des portails envoyees aux shaders pour le monde du joueur a la derniere image (tests du client). */
    public static int playerOpenings() {
        return playerOpenings;
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

    /** Niveau de la vue courante : 0 pour le monde du joueur, puis une vue, une vue imbriquee. */
    static int depth() {
        return depth;
    }

    /** Les corps qui portent une ombre dans la vue courante, tels qu'envoyes dans son bloc. */
    static ShadowCasters casters() {
        return CASTERS;
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
