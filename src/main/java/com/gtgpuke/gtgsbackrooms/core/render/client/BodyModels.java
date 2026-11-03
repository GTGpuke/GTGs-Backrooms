package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.mixin.client.AnimalModelAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.CuboidAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.LivingEntityRendererAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.ModelPartAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.PlayerEntityRendererAccessor;

/**
 * Les cubes d'un corps tels que vanilla le dessine : le modele de son rendu, pose par les memes appels qu'au dessin (marche, tete,
 * accroupi, bras, taille, petit), puis chacun de ses cubes visibles, dans le repere relatif au bloc de la camera. Un cube dont la texture
 * du corps laisse plus de la moitie des pixels transparents se voit a peine, et ne fait pas d'ombre : le bord du chapeau d'un villageois
 * sans metier. Chaque modele s'aplatit une fois (ses parties, chacune apres son parent, et leurs cubes), et chaque texture se relit une
 * fois sur la carte graphique ; ensuite, rien n'est alloue ici par image. Les appels de vanilla allouent encore : le
 * {@code setupTransforms} d'un corps cree le quaternion de sa rotation (32 octets par corps et par image, mesure), et relit le nom d'un
 * corps qui en porte un (quelques chaines). Un joueur debout ou en marche, toujours la, se tourne donc ici, sans lui. Un modele qui n'est
 * ni d'une seule piece ni en tete et corps (celui d'un animal, d'un humanoide) n'est pas lu.
 */
final class BodyModels {

    /** Pixels du modele par bloc. */
    private static final float PIXELS = 16.0f;
    /** Ce que vanilla descend un modele sous les pieds de son corps. */
    private static final float MODEL_BASE = -1.501f;
    /** Recul des yeux d'un corps couche, comme vanilla. */
    private static final float SLEEP_EYES = 0.1f;
    /** Pas de la marche d'un petit, comme vanilla. */
    private static final float CHILD_WALK = 3.0f;
    /** La tete d'un petit qui la garde plus grosse, comme vanilla. */
    private static final float CHILD_HEAD = 1.5f;
    /** Ce qu'un corps monte tourne au plus par rapport a sa monture, et au-dela de quoi il s'y penche, comme vanilla. */
    private static final float RIDING_TURN = 85.0f;
    private static final float RIDING_LEAN = 2500.0f;
    private static final float RIDING_LEAN_SHARE = 0.2f;
    /** Demi-epaisseur minimale d'un cube, en blocs : un cube plat (aile, nageoire) fait de l'ombre. */
    private static final float THIN = 1.0f / 64.0f;
    /** Valeurs d'un cube aplati : centre puis demi-cotes, dans le repere de sa partie. */
    private static final int CUBOID_VALUES = 6;
    /** Valeurs d'une face aplatie : le rectangle de sa texture, de 0 a 1. */
    private static final int FACE_VALUES = 4;
    /** Part opaque des pixels de ses faces, au moins, d'un cube qui fait de l'ombre. */
    private static final float OPAQUE = 0.5f;
    /** Les noms qui mettent un corps la tete en bas, et ce que vanilla le remonte alors au-dessus de sa taille, comme vanilla. */
    private static final String FLIP_NAME = "Dinnerbone";
    private static final String FLIP_OTHER_NAME = "Grumm";
    private static final float FLIP_LIFT = 0.1f;
    /** Le tremblement d'un corps qui gele : cadence et ampleur, comme vanilla. */
    private static final double SHAKE_RATE = 3.25;
    private static final double SHAKE = 0.4;
    private static final Map<EntityModel<?>, Flat> FLATS = new WeakHashMap<>();
    private static final MatrixStack STACK = new MatrixStack();
    private static final Matrix4f HEAD = new Matrix4f();
    private static final Matrix4f BODY = new Matrix4f();
    private static final Vector3f CENTER = new Vector3f();
    private static final Vector3f U = new Vector3f();
    private static final Vector3f V = new Vector3f();
    private static final Vector3f W = new Vector3f();
    private static Matrix4f[] matrices = new Matrix4f[0];
    private static boolean[] shown = new boolean[0];

    private BodyModels() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Pose le modele de ce corps et ajoute ses cubes au corps commence.
     *
     * @param x position affichee du corps, relative au bloc de la camera
     * @param y position affichee du corps, relative au bloc de la camera
     * @param z position affichee du corps, relative au bloc de la camera
     * @return {@code false} si son rendu n'a pas de modele lisible : le corps garde la boite de sa collision
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static boolean pose(EntityRenderDispatcher renderers, LivingEntity body, float tickDelta, double x, double y, double z, ShadowCasters out) {
        EntityRenderer<? super LivingEntity> renderer = renderers.getRenderer(body);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return false;
        }
        EntityModel model = living.getModel();
        Flat flat = flat(model);
        if (flat.parts.length == 0) {
            return false;
        }
        boolean[] opaque = flat.opacity(((LivingEntityRenderer) living).getTexture(body));
        if (living instanceof PlayerEntityRenderer player && body instanceof AbstractClientPlayerEntity client) {
            ((PlayerEntityRendererAccessor) player).backrooms$setModelPose(client);
        }
        LivingEntityRendererAccessor calls = (LivingEntityRendererAccessor) living;
        model.handSwingProgress = calls.backrooms$getHandSwingProgress(body, tickDelta);
        model.riding = body.hasVehicle();
        model.child = body.isBaby();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, body.prevBodyYaw, body.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, body.prevHeadYaw, body.headYaw);
        if (body.hasVehicle() && body.getVehicle() instanceof LivingEntity mount) {
            bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, mount.prevBodyYaw, mount.bodyYaw);
            float turned = MathHelper.clamp(MathHelper.wrapDegrees(headYaw - bodyYaw), -RIDING_TURN, RIDING_TURN);
            bodyYaw = headYaw - turned;
            if (turned * turned > RIDING_LEAN) {
                bodyYaw += turned * RIDING_LEAN_SHARE;
            }
        }
        float turn = headYaw - bodyYaw;
        float pitch = MathHelper.lerp(tickDelta, body.prevPitch, body.getPitch());
        boolean upsideDown = upsideDown(body);
        if (upsideDown) {
            pitch = -pitch;
            turn = -turn;
        }
        turn = MathHelper.wrapDegrees(turn);
        Vec3d offset = renderer.getPositionOffset(body, tickDelta);
        STACK.loadIdentity();
        STACK.translate(x + offset.x, y + offset.y, z + offset.z);
        if (body.isInPose(EntityPose.SLEEPING)) {
            Direction direction = body.getSleepingDirection();
            if (direction != null) {
                float eyes = body.getEyeHeight(EntityPose.STANDING) - SLEEP_EYES;
                STACK.translate(-direction.getOffsetX() * eyes, 0.0f, -direction.getOffsetZ() * eyes);
            }
        }
        float scale = body.getScale();
        STACK.scale(scale, scale, scale);
        float progress = calls.backrooms$getAnimationProgress(body, tickDelta);
        if (living instanceof PlayerEntityRenderer && body instanceof PlayerEntity player && standing(player, tickDelta)) {
            turn(player, bodyYaw, upsideDown, scale);
        } else {
            calls.backrooms$setupTransforms(body, STACK, progress, bodyYaw, tickDelta, scale);
        }
        STACK.scale(-1.0f, -1.0f, 1.0f);
        calls.backrooms$scale(body, STACK, tickDelta);
        STACK.translate(0.0f, MODEL_BASE, 0.0f);
        float speed = 0.0f;
        float walk = 0.0f;
        if (!body.hasVehicle() && body.isAlive()) {
            speed = Math.min(body.limbAnimator.getSpeed(tickDelta), 1.0f);
            walk = body.limbAnimator.getPos(tickDelta) * (body.isBaby() ? CHILD_WALK : 1.0f);
        }
        model.animateModel(body, walk, speed, tickDelta);
        model.setAngles(body, walk, speed, progress, turn, pitch);
        Matrix4f root = STACK.peek().getPositionMatrix();
        HEAD.set(root);
        BODY.set(root);
        if (flat.animal && model.child) {
            if (flat.headScaled) {
                HEAD.scale(CHILD_HEAD / flat.childHeadScale);
            }
            HEAD.translate(0.0f, flat.childHeadY / PIXELS, flat.childHeadZ / PIXELS);
            BODY.scale(1.0f / flat.childBodyScale);
            BODY.translate(0.0f, flat.childBodyY / PIXELS, 0.0f);
        }
        boxes(flat, opaque, out);
        return true;
    }

    /** {@code true} si vanilla poserait ce joueur comme un corps debout : ni vol plane, ni nage, ni trident, ni sommeil, ni mort. */
    private static boolean standing(PlayerEntity player, float tickDelta) {
        return !player.isFallFlying() && player.getLeaningPitch(tickDelta) <= 0.0f && !player.isUsingRiptide() && !player.isInPose(EntityPose.SLEEPING)
                && player.deathTime <= 0;
    }

    /**
     * Ce que vanilla fait d'un corps debout avant son modele : il le tourne selon son corps, qui tremble s'il gele, et le retourne sous
     * l'un des deux noms. Seule la matrice des positions sert : la rotation s'y pose directement.
     */
    private static void turn(LivingEntity body, float bodyYaw, boolean upsideDown, float scale) {
        float yaw = bodyYaw;
        if (body.isFrozen()) {
            yaw += (float) (Math.cos(body.age * SHAKE_RATE) * Math.PI * SHAKE);
        }
        Matrix4f matrix = STACK.peek().getPositionMatrix();
        matrix.rotateY((float) Math.toRadians(180.0f - yaw));
        if (upsideDown) {
            matrix.translate(0.0f, (body.getHeight() + FLIP_LIFT) / scale, 0.0f);
            matrix.rotateZ((float) Math.PI);
        }
    }

    /**
     * {@code true} si ce corps se dessine la tete en bas, comme {@code LivingEntityRenderer.shouldFlipUpsideDown} : un joueur par le nom
     * de son profil, cape visible, sans le texte de son nom que vanilla recalcule a chaque appel ; un autre corps par vanilla, qui ne lit
     * que le nom donne d'un corps qui en porte un.
     */
    private static boolean upsideDown(LivingEntity body) {
        if (body instanceof PlayerEntity player) {
            String name = player.getGameProfile().getName();
            return (FLIP_NAME.equals(name) || FLIP_OTHER_NAME.equals(name)) && player.isPartVisible(PlayerModelPart.CAPE);
        }
        return LivingEntityRenderer.shouldFlipUpsideDown(body);
    }

    /**
     * Les cubes visibles du modele pose, partie par partie, chacune dans le repere de son parent, comme {@code ModelPart.render}, sauf
     * ceux que la texture laisse transparents.
     */
    private static void boxes(Flat flat, boolean[] opaque, ShadowCasters out) {
        for (int i = 0; i < flat.parts.length; i++) {
            ModelPart part = flat.parts[i];
            int parent = flat.parents[i];
            shown[i] = part.visible && (parent < 0 || shown[parent]);
            if (!shown[i]) {
                continue;
            }
            Matrix4f matrix = matrices[i].set(parent >= 0 ? matrices[parent] : (flat.heads[i] ? HEAD : BODY));
            matrix.translate(part.pivotX / PIXELS, part.pivotY / PIXELS, part.pivotZ / PIXELS);
            if (part.pitch != 0.0f || part.yaw != 0.0f || part.roll != 0.0f) {
                matrix.rotateZYX(part.roll, part.yaw, part.pitch);
            }
            if (part.xScale != 1.0f || part.yScale != 1.0f || part.zScale != 1.0f) {
                matrix.scale(part.xScale, part.yScale, part.zScale);
            }
            if (part.hidden) {
                continue;
            }
            for (int at = flat.firstCuboid[i]; at < flat.firstCuboid[i + 1]; at += CUBOID_VALUES) {
                if (!opaque[at / CUBOID_VALUES]) {
                    continue;
                }
                float[] cuboid = flat.cuboids;
                matrix.transformPosition(cuboid[at], cuboid[at + 1], cuboid[at + 2], CENTER);
                matrix.transformDirection(cuboid[at + 3], 0.0f, 0.0f, U);
                matrix.transformDirection(0.0f, cuboid[at + 4], 0.0f, V);
                matrix.transformDirection(0.0f, 0.0f, cuboid[at + 5], W);
                out.box(CENTER.x, CENTER.y, CENTER.z, U.x, U.y, U.z, V.x, V.y, V.z, W.x, W.y, W.z);
            }
        }
    }

    /** Le modele aplati, la premiere fois qu'il se presente. */
    private static Flat flat(EntityModel<?> model) {
        Flat flat = FLATS.get(model);
        if (flat == null) {
            flat = new Flat(model);
            FLATS.put(model, flat);
            if (matrices.length < flat.parts.length) {
                Matrix4f[] grown = new Matrix4f[flat.parts.length];
                System.arraycopy(matrices, 0, grown, 0, matrices.length);
                for (int i = matrices.length; i < grown.length; i++) {
                    grown[i] = new Matrix4f();
                }
                matrices = grown;
                shown = new boolean[grown.length];
            }
        }
        return flat;
    }

    /** Un modele aplati : ses parties, chacune apres son parent, et leurs cubes en centre et demi-cotes. */
    private static final class Flat {

        private final ModelPart[] parts;
        private final int[] parents;
        private final boolean[] heads;
        /** Debut des cubes de chaque partie dans {@link #cuboids}, et la fin en dernier. */
        private final int[] firstCuboid;
        private final float[] cuboids;
        /** Debut des faces de chaque cube dans {@link #faces}, et la fin en dernier. */
        private final int[] firstFace;
        private final float[] faces;
        /** Les cubes opaques, par texture deja relue. */
        private final Map<Identifier, boolean[]> opaque = new HashMap<>();
        private final boolean animal;
        private final boolean headScaled;
        private final float childHeadY;
        private final float childHeadZ;
        private final float childHeadScale;
        private final float childBodyScale;
        private final float childBodyY;

        private Flat(EntityModel<?> model) {
            List<ModelPart> parts = new ArrayList<>();
            IntArrayList parents = new IntArrayList();
            BooleanArrayList heads = new BooleanArrayList();
            IntArrayList first = new IntArrayList();
            FloatArrayList cuboids = new FloatArrayList();
            IntArrayList firstFace = new IntArrayList();
            FloatArrayList faces = new FloatArrayList();
            Lists lists = new Lists(parts, parents, heads, first, cuboids, firstFace, faces);
            if (model instanceof SinglePartEntityModel<?> single) {
                add(single.getPart(), -1, false, lists);
            }
            AnimalModelAccessor animal = model instanceof AnimalModel<?> ? (AnimalModelAccessor) model : null;
            if (animal != null) {
                for (ModelPart part : animal.backrooms$getHeadParts()) {
                    add(part, -1, true, lists);
                }
                for (ModelPart part : animal.backrooms$getBodyParts()) {
                    add(part, -1, false, lists);
                }
            }
            first.add(cuboids.size());
            firstFace.add(faces.size());
            this.parts = parts.toArray(new ModelPart[0]);
            this.parents = parents.toIntArray();
            this.heads = heads.toBooleanArray();
            this.firstCuboid = first.toIntArray();
            this.cuboids = cuboids.toFloatArray();
            this.firstFace = firstFace.toIntArray();
            this.faces = faces.toFloatArray();
            this.animal = animal != null;
            this.headScaled = animal != null && animal.backrooms$isHeadScaled();
            this.childHeadY = animal != null ? animal.backrooms$getChildHeadYOffset() : 0.0f;
            this.childHeadZ = animal != null ? animal.backrooms$getChildHeadZOffset() : 0.0f;
            this.childHeadScale = animal != null ? animal.backrooms$getInvertedChildHeadScale() : 1.0f;
            this.childBodyScale = animal != null ? animal.backrooms$getInvertedChildBodyScale() : 1.0f;
            this.childBodyY = animal != null ? animal.backrooms$getChildBodyYOffset() : 0.0f;
        }

        /**
         * Les cubes opaques dans cette texture : la part des pixels de leurs faces que la texture ne laisse pas transparents, relue une
         * fois sur la carte graphique. Sans image lisible, tous.
         */
        private boolean[] opacity(Identifier id) {
            boolean[] known = this.opaque.get(id);
            if (known != null) {
                return known;
            }
            boolean[] solid = new boolean[this.firstFace.length - 1];
            Arrays.fill(solid, true);
            int unit = GlStateManager._getActiveTexture();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int bound = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                RenderSystem.bindTexture(MinecraftClient.getInstance().getTextureManager().getTexture(id).getGlId());
                int width = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                if (width > 0 && height > 0) {
                    try (NativeImage image = new NativeImage(width, height, false)) {
                        image.loadFromTextureImage(0, false);
                        for (int c = 0; c < solid.length; c++) {
                            solid[c] = opaque(image, this.faces, this.firstFace[c], this.firstFace[c + 1]);
                        }
                    }
                }
            } finally {
                RenderSystem.bindTexture(bound);
                RenderSystem.activeTexture(unit);
            }
            this.opaque.put(id, solid);
            return solid;
        }

        /** {@code true} si au moins la moitie des pixels de ces faces sont visibles dans l'image ; une face sans pixel ne compte pas. */
        private static boolean opaque(NativeImage image, float[] faces, int from, int to) {
            int seen = 0;
            int total = 0;
            for (int at = from; at < to; at += FACE_VALUES) {
                int left = Math.round(faces[at] * image.getWidth());
                int right = Math.round(faces[at + 2] * image.getWidth());
                int top = Math.round(faces[at + 1] * image.getHeight());
                int bottom = Math.round(faces[at + 3] * image.getHeight());
                for (int y = Math.max(top, 0); y < Math.min(bottom, image.getHeight()); y++) {
                    for (int x = Math.max(left, 0); x < Math.min(right, image.getWidth()); x++) {
                        total++;
                        if (image.getColor(x, y) >>> 24 != 0) {
                            seen++;
                        }
                    }
                }
            }
            return total == 0 || seen >= total * OPAQUE;
        }

        private static void add(ModelPart part, int parent, boolean head, Lists lists) {
            int index = lists.parts.size();
            lists.parts.add(part);
            lists.parents.add(parent);
            lists.heads.add(head);
            lists.first.add(lists.cuboids.size());
            ModelPartAccessor access = (ModelPartAccessor) (Object) part;
            for (ModelPart.Cuboid cuboid : access.backrooms$getCuboids()) {
                lists.cuboids.add((cuboid.minX + cuboid.maxX) * 0.5f / PIXELS);
                lists.cuboids.add((cuboid.minY + cuboid.maxY) * 0.5f / PIXELS);
                lists.cuboids.add((cuboid.minZ + cuboid.maxZ) * 0.5f / PIXELS);
                lists.cuboids.add(Math.max((cuboid.maxX - cuboid.minX) * 0.5f / PIXELS, THIN));
                lists.cuboids.add(Math.max((cuboid.maxY - cuboid.minY) * 0.5f / PIXELS, THIN));
                lists.cuboids.add(Math.max((cuboid.maxZ - cuboid.minZ) * 0.5f / PIXELS, THIN));
                lists.firstFace.add(lists.faces.size());
                for (ModelPart.Quad side : ((CuboidAccessor) cuboid).backrooms$getSides()) {
                    float minU = Float.MAX_VALUE;
                    float minV = Float.MAX_VALUE;
                    float maxU = -Float.MAX_VALUE;
                    float maxV = -Float.MAX_VALUE;
                    for (ModelPart.Vertex vertex : side.vertices) {
                        minU = Math.min(minU, vertex.u);
                        minV = Math.min(minV, vertex.v);
                        maxU = Math.max(maxU, vertex.u);
                        maxV = Math.max(maxV, vertex.v);
                    }
                    lists.faces.add(minU);
                    lists.faces.add(minV);
                    lists.faces.add(maxU);
                    lists.faces.add(maxV);
                }
            }
            for (ModelPart child : access.backrooms$getChildren().values()) {
                add(child, index, head, lists);
            }
        }

        /** Ce qu'un modele aplati accumule en le parcourant. */
        private record Lists(List<ModelPart> parts, IntArrayList parents, BooleanArrayList heads, IntArrayList first, FloatArrayList cuboids,
                IntArrayList firstFace, FloatArrayList faces) {
        }
    }
}
