package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;

/**
 * Les corps qui portent une ombre : chaque tick, les plus proches de l'ancrage dans le monde de la zone de lumiere ; a chaque vue,
 * leurs cylindres a la position affichee, relatifs au bloc de sa camera. Le joueur en fait partie, vue a la premiere personne comprise :
 * il voit son ombre au sol. Rien n'est alloue par image, et aucune entite n'est gardee apres son monde. Le releve de chaque tick prend un
 * iterateur du monde, seul acces de vanilla a toutes ses entites.
 */
final class EntityShadows {

    /** Corps gardes : trois cylindres chacun. */
    private static final int MAX_BODIES = ShadowCasters.MAX_CYLINDERS / 3;
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
     * Les cylindres des corps de ce monde, a leur position affichee.
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
            out.add(MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - originX, MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - originY,
                    MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - originZ, body.getWidth(), body.getHeight());
        }
    }

    /** Plus de zone de lumiere : plus aucun corps retenu. */
    static void clear() {
        world = null;
        count = 0;
        Arrays.fill(BODIES, null);
    }
}
