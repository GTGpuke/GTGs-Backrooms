package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Les corps qui portent une ombre : chaque tick, les plus proches de l'ancrage dans le monde de la zone de lumiere ; a chaque vue,
 * leurs formes a la position affichee, relatives au bloc de sa camera : les cubes du modele qui le dessine, poses et animes comme
 * vanilla les dessine ({@link BodyModels}), ou la boite de sa collision. Le joueur en fait partie, vue a la premiere personne comprise : il
 * voit son ombre au sol. Leurs ombres remplacent le disque d'ombre de vanilla. Rien n'est alloue ici par image (la pose d'un corps par
 * vanilla alloue un peu, voir {@link BodyModels}), et aucune entite n'est gardee apres son monde. Le releve de chaque tick prend un
 * iterateur du monde, seul acces de vanilla a toutes ses entites.
 */
public final class EntityShadows {

    /** Corps gardes. */
    private static final int MAX_BODIES = ShadowCasters.MAX_BODIES;
    /** Distance a l'ancrage au-dela de laquelle un corps ne compte pas, en blocs : les tuiles s'arretent avant. */
    private static final double RANGE = 48.0;
    private static final LivingEntity[] BODIES = new LivingEntity[MAX_BODIES];
    private static final double[] DISTANCES = new double[MAX_BODIES];
    private static @Nullable World world;
    private static int count;

    private EntityShadows() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Chaque tick : les corps les plus proches de l'ancrage. */
    static void tick(ClientWorld target, Vec3d anchor) {
        world = target;
        count = 0;
        for (Entity entity : target.getEntities()) {
            if (entity instanceof LivingEntity living && !living.isInvisible() && !living.isSpectator() && living.isAlive()) {
                double distance = living.squaredDistanceTo(anchor);
                if (distance <= RANGE * RANGE) {
                    offer(living, distance);
                }
            }
        }
        Arrays.fill(BODIES, count, MAX_BODIES, null);
    }

    private static void offer(LivingEntity body, double distance) {
        if (count == MAX_BODIES && distance >= DISTANCES[MAX_BODIES - 1]) {
            return;
        }
        int at = count < MAX_BODIES ? count : MAX_BODIES - 1;
        while (at > 0 && DISTANCES[at - 1] > distance) {
            BODIES[at] = BODIES[at - 1];
            DISTANCES[at] = DISTANCES[at - 1];
            at--;
        }
        BODIES[at] = body;
        DISTANCES[at] = distance;
        if (count < MAX_BODIES) {
            count++;
        }
    }

    /**
     * Les formes des corps de ce monde, a leur position et dans leur pose affichees.
     *
     * @param view monde de la vue
     * @param originX bloc de la camera de la vue
     * @param originY bloc de la camera de la vue
     * @param originZ bloc de la camera de la vue
     * @param tickDelta fraction de tick de l'image
     * @param out liste a remplir
     */
    static void write(World view, int originX, int originY, int originZ, float tickDelta, ShadowCasters out) {
        out.clear();
        if (view != world) {
            return;
        }
        for (int i = 0; i < count; i++) {
            LivingEntity body = BODIES[i];
            double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - originX;
            double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - originY;
            double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - originZ;
            if (!shape(body, x, y, z, tickDelta, out)) {
                return;
            }
        }
    }

    /**
     * Ajoute la forme d'un corps, ses pieds a cette position : les cubes de son modele, poses comme vanilla le dessine, ou la boite de sa
     * collision.
     *
     * @return {@code false} si la liste est pleine
     */
    public static boolean shape(LivingEntity body, double x, double y, double z, float tickDelta, ShadowCasters out) {
        if (!out.begin(x, y, z)) {
            return false;
        }
        EntityRenderDispatcher renderers = MinecraftClient.getInstance().getEntityRenderDispatcher();
        if (!BodyModels.pose(renderers, body, tickDelta, x, y, z, out)) {
            out.boxOf(body.getWidth(), body.getHeight(), MathHelper.lerpAngleDegrees(tickDelta, body.prevBodyYaw, body.bodyYaw));
        }
        out.end();
        return true;
    }

    /**
     * {@code true} si les ombres du mod remplacent le disque d'ombre de vanilla de cette entite : un corps retenu, dans la vue du joueur,
     * dont le terrain lit la lumiere cuite. Ailleurs (vue de portail, corps au-dela des plus proches, terrain dessine par un autre mod, hors
     * de la zone), vanilla garde son disque.
     */
    public static boolean replacesVanilla(Entity entity) {
        if (LookState.depth() != 0 || !LookUniformUploader.bakedLight()) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (BODIES[i] == entity) {
                return true;
            }
        }
        return false;
    }

    /** Plus de zone de lumiere : plus aucun corps retenu. */
    static void clear() {
        world = null;
        count = 0;
        Arrays.fill(BODIES, null);
    }
}
