package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;

/**
 * Les corps qui portent une ombre : chaque tick, les plus proches de l'ancrage dans le monde de chaque zone de lumiere, celle du joueur
 * et celle d'une vue de portail ; a chaque vue, leurs formes a la position affichee, relatives au bloc de sa camera : les cubes du modele
 * qui le dessine, poses et animes comme vanilla les dessine ({@link BodyModels}), ou la boite de sa collision. Le joueur en fait partie,
 * vue a la premiere personne comprise : il voit son ombre au sol. Leurs ombres remplacent le disque d'ombre de vanilla. Les corps de
 * l'autre cote des portails qui laissent passer la lumiere, pres de leur arrivee, sont reportes derriere leur cadre : ils arretent la
 * lumiere qui vient de la-bas par lui, et leur ombre passe le cadre comme elle. Rien n'est alloue ici par image (la pose d'un corps par
 * vanilla alloue un peu, voir {@link BodyModels}), et aucune entite n'est gardee apres son monde. Le releve de chaque tick prend un
 * iterateur du monde, seul acces de vanilla a toutes ses entites : un pour les corps de ce cote, un par portail qui laisse passer la
 * lumiere pour ceux de l'autre cote.
 */
public final class EntityShadows {

    /** Corps gardes par zone. */
    private static final int MAX_BODIES = ShadowCasters.MAX_BODIES;
    /** Distance a l'ancrage au-dela de laquelle un corps ne compte pas, en blocs : les tuiles s'arretent avant. */
    private static final double RANGE = 48.0;
    /** Distance a l'arrivee d'un portail au-dela de laquelle un corps de l'autre cote ne compte pas, en blocs. */
    private static final double BEYOND_RANGE = 8.0;
    /** Ecart, en blocs, sous lequel un cadre envoye aux shaders est celui d'un portail : leurs centres sont gardes en flottants. */
    private static final float SAME_FRAME = 0.25f;
    private static final Bodies PLAYER = new Bodies();
    private static final Bodies VIEW = new Bodies();
    private static final Bodies PLAYER_BEYOND = new Bodies();
    private static final Bodies VIEW_BEYOND = new Bodies();
    private static final double[] TURN = new double[9];

    /** Les corps retenus pour une zone. */
    private static final class Bodies {

        private final LivingEntity[] bodies = new LivingEntity[MAX_BODIES];
        private final double[] distances = new double[MAX_BODIES];
        /** Le portail par lequel compte chaque corps de l'autre cote. */
        private final BackroomsPortalEntity[] portals = new BackroomsPortalEntity[MAX_BODIES];
        /** {@code true} pour un corps devant l'entree d'un portail sans cadre de retour, reporte derriere son arrivee. */
        private final boolean[] reversed = new boolean[MAX_BODIES];
        /** Ce que chaque corps de l'autre cote est devant la face par laquelle il compte, en blocs. */
        private final double[] aheads = new double[MAX_BODIES];
        private @Nullable World world;
        private int count;

        private void collect(ClientWorld target, double x, double y, double z) {
            this.world = target;
            this.count = 0;
            for (Entity entity : target.getEntities()) {
                if (entity instanceof LivingEntity living && casts(living)) {
                    double distance = living.squaredDistanceTo(x, y, z);
                    if (distance <= RANGE * RANGE) {
                        offer(living, distance, null, false, 0.0);
                    }
                }
            }
            forgetRest();
        }

        /**
         * Les corps de l'autre cote des portails de ce monde qui laissent passer la lumiere : devant leur arrivee, ou a cheval sur
         * elle, les plus proches d'elle.
         */
        private void collectBeyond(ClientWorld target) {
            this.world = target;
            this.count = 0;
            List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(target);
            for (int i = 0; i < portals.size(); i++) {
                BackroomsPortalEntity portal = portals.get(i);
                ClientWorld beyond = portal.visible() && portal.transmitsLight() && portal.teleportable() ? RemoteWorldRegistry.worldOf(portal) : null;
                if (beyond == null) {
                    continue;
                }
                Vec3d arrival = portal.destinationPos();
                Vec3d normal = portal.facingDestination().normal;
                for (Entity entity : beyond.getEntities()) {
                    if (entity instanceof LivingEntity living && casts(living)) {
                        double distance = living.squaredDistanceTo(arrival.x, arrival.y, arrival.z);
                        double ahead = (living.getX() - arrival.x) * normal.x + (living.getY() - arrival.y) * normal.y + (living.getZ() - arrival.z) * normal.z;
                        if (distance <= BEYOND_RANGE * BEYOND_RANGE && ahead > -living.getWidth()) {
                            offer(living, distance, portal, false, ahead);
                        }
                    }
                }
            }
            collectReturns(target, target);
            ClientWorld main = MinecraftClient.getInstance().world;
            if (main != null && main != target) {
                collectReturns(main, target);
            }
            forgetRest();
        }

        /**
         * Les corps devant l'entree des portails de {@code source} qui arrivent dans {@code target} sans cadre de retour (aller simple) :
         * pour la lumiere, leur arrivee est un passage vers l'entree ({@code PortalOpeningLights}), et ces corps y sont reportes, derriere
         * elle, comme ceux de l'autre cote d'un cadre : l'ombre du joueur qui regarde par l'entree continue au-dela du plan.
         */
        private void collectReturns(ClientWorld source, ClientWorld target) {
            List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(source);
            for (int i = 0; i < portals.size(); i++) {
                BackroomsPortalEntity portal = portals.get(i);
                if (!portal.visible() || !portal.transmitsLight() || !portal.teleportable() || !portal.destinationDim().equals(target.getRegistryKey())
                        || hasFrameAt(target, portal.destinationPos())) {
                    continue;
                }
                Vec3d entry = portal.getPos();
                Vec3d normal = portal.facingSource().normal;
                for (Entity entity : source.getEntities()) {
                    if (entity instanceof LivingEntity living && casts(living)) {
                        double distance = living.squaredDistanceTo(entry.x, entry.y, entry.z);
                        double ahead = (living.getX() - entry.x) * normal.x + (living.getY() - entry.y) * normal.y + (living.getZ() - entry.z) * normal.z;
                        if (distance <= BEYOND_RANGE * BEYOND_RANGE && ahead > -living.getWidth()) {
                            offer(living, distance, portal, true, ahead);
                        }
                    }
                }
            }
        }

        private void forgetRest() {
            Arrays.fill(this.bodies, this.count, MAX_BODIES, null);
            Arrays.fill(this.portals, this.count, MAX_BODIES, null);
        }

        /**
         * Retient un corps a sa place dans la liste, du plus proche au plus loin. Un corps deja retenu par l'autre face du meme cadre (les
         * deux faces d'un portail ont le meme centre) ne compte qu'une fois : par celle devant laquelle il est le plus, sans quoi son ombre
         * de contact, qui s'additionne, compterait deux fois. Vu par deux cadres distincts, il compte par chacun.
         */
        private void offer(LivingEntity body, double distance, @Nullable BackroomsPortalEntity portal, boolean reverse, double ahead) {
            for (int i = 0; i < this.count; i++) {
                if (this.bodies[i] == body && sameFrame(this.portals[i], this.reversed[i], portal, reverse)) {
                    if (ahead > this.aheads[i]) {
                        this.portals[i] = portal;
                        this.reversed[i] = reverse;
                        this.aheads[i] = ahead;
                    }
                    return;
                }
            }
            if (this.count == MAX_BODIES && distance >= this.distances[MAX_BODIES - 1]) {
                return;
            }
            int at = this.count < MAX_BODIES ? this.count : MAX_BODIES - 1;
            while (at > 0 && this.distances[at - 1] > distance) {
                this.bodies[at] = this.bodies[at - 1];
                this.distances[at] = this.distances[at - 1];
                this.portals[at] = this.portals[at - 1];
                this.reversed[at] = this.reversed[at - 1];
                this.aheads[at] = this.aheads[at - 1];
                at--;
            }
            this.bodies[at] = body;
            this.distances[at] = distance;
            this.portals[at] = portal;
            this.reversed[at] = reverse;
            this.aheads[at] = ahead;
            if (this.count < MAX_BODIES) {
                this.count++;
            }
        }

        /** {@code true} si deux reports passent par le meme cadre : meme centre, celui de l'entree, ou de l'arrivee d'un aller simple. */
        private static boolean sameFrame(@Nullable BackroomsPortalEntity first, boolean firstReversed, @Nullable BackroomsPortalEntity second,
                boolean secondReversed) {
            if (first == null || second == null) {
                return false;
            }
            Vec3d one = firstReversed ? first.destinationPos() : first.getPos();
            Vec3d other = secondReversed ? second.destinationPos() : second.getPos();
            return one.squaredDistanceTo(other) < SAME_FRAME * SAME_FRAME;
        }

        private boolean holds(Entity entity) {
            for (int i = 0; i < this.count; i++) {
                if (this.bodies[i] == entity) {
                    return true;
                }
            }
            return false;
        }

        private void clear() {
            this.world = null;
            this.count = 0;
            Arrays.fill(this.bodies, null);
            Arrays.fill(this.portals, null);
        }
    }

    private EntityShadows() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** {@code true} si un portail de ce monde a son centre a cet endroit : un cadre de retour. */
    private static boolean hasFrameAt(World world, Vec3d at) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(world);
        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).getPos().squaredDistanceTo(at) < SAME_FRAME * SAME_FRAME) {
                return true;
            }
        }
        return false;
    }

    /** Un corps qui porte une ombre : visible, vivant, pas spectateur. */
    private static boolean casts(LivingEntity living) {
        return !living.isInvisible() && !living.isSpectator() && living.isAlive();
    }

    /** Chaque tick : les corps les plus proches de l'ancrage de la zone du joueur, et ceux de l'autre cote de ses portails. */
    static void tick(ClientWorld target, double x, double y, double z) {
        PLAYER.collect(target, x, y, z);
        PLAYER_BEYOND.collectBeyond(target);
    }

    /** Chaque tick : les corps les plus proches de la camera de la vue qui a sa propre zone, et ceux de l'autre cote de ses portails. */
    static void tickView(ClientWorld target, double x, double y, double z) {
        VIEW.collect(target, x, y, z);
        VIEW_BEYOND.collectBeyond(target);
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
        Bodies found = bound();
        if (view != found.world) {
            return;
        }
        for (int i = 0; i < found.count; i++) {
            LivingEntity body = found.bodies[i];
            double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - originX;
            double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - originY;
            double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - originZ;
            if (!shape(body, x, y, z, tickDelta, out)) {
                return;
            }
        }
    }

    /**
     * Les corps de l'autre cote des portails de ce monde, reportes derriere leur cadre, apres ceux de ce cote : seulement par les cadres
     * envoyes aux shaders qui passent la lumiere d'une zone de l'autre cote, les seuls par ou elle arrive.
     *
     * @param view monde de la vue
     * @param originX bloc de la camera de la vue
     * @param originY bloc de la camera de la vue
     * @param originZ bloc de la camera de la vue
     * @param tickDelta fraction de tick de l'image
     * @param frames cadres envoyes aux shaders, {@link PortalLightUniforms#FRAME_FLOATS} valeurs chacun
     * @param frameCount nombre de cadres envoyes
     * @param out liste a completer
     */
    static void writeBeyond(World view, int originX, int originY, int originZ, float tickDelta, float[] frames, int frameCount, ShadowCasters out) {
        Bodies found = LightVolume.playerBound() ? PLAYER_BEYOND : VIEW_BEYOND;
        if (view != found.world) {
            return;
        }
        for (int i = 0; i < found.count; i++) {
            BackroomsPortalEntity portal = found.portals[i];
            // Un corps de l'autre cote d'un cadre passe de son arrivee a son entree ; un corps devant l'entree d'un aller simple, de son entree a
            // son arrivee, qui tient lieu de cadre de retour.
            boolean reverse = found.reversed[i];
            Vec3d center = reverse ? portal.destinationPos() : portal.getPos();
            PortalFacing here = reverse ? portal.facingDestination() : portal.facingSource();
            int frame = frameOf(center, here, originX, originY, originZ, frames, frameCount);
            if (frame < 0) {
                continue;
            }
            LivingEntity body = found.bodies[i];
            Vec3d arrival = reverse ? portal.getPos() : portal.destinationPos();
            PortalOpeningLights.rotation(reverse ? portal.facingSource() : portal.facingDestination(), here, TURN);
            out.beyond(TURN, center.x - originX, center.y - originY, center.z - originZ, frame);
            double x = MathHelper.lerp(tickDelta, body.lastRenderX, body.getX()) - arrival.x;
            double y = MathHelper.lerp(tickDelta, body.lastRenderY, body.getY()) - arrival.y;
            double z = MathHelper.lerp(tickDelta, body.lastRenderZ, body.getZ()) - arrival.z;
            if (!shape(body, x, y, z, tickDelta, out)) {
                return;
            }
        }
    }

    /** La place du cadre de ce centre et de cette orientation parmi ceux envoyes aux shaders, s'il y est et passe la lumiere d'une zone ; -1 sinon. */
    private static int frameOf(Vec3d center, PortalFacing facing, int originX, int originY, int originZ, float[] frames, int frameCount) {
        int face = PortalOpenings.face(facing.normal.x, facing.normal.y, facing.normal.z);
        for (int slot = 0; slot < frameCount; slot++) {
            int at = slot * PortalLightUniforms.FRAME_FLOATS;
            if (frames[at + 7] > 0.5f && (int) frames[at + 3] == face && Math.abs(frames[at] - (float) (center.x - originX)) < SAME_FRAME
                    && Math.abs(frames[at + 1] - (float) (center.y - originY)) < SAME_FRAME && Math.abs(frames[at + 2] - (float) (center.z - originZ)) < SAME_FRAME) {
                return slot;
            }
        }
        return -1;
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
     * {@code true} si les ombres du mod remplacent le disque d'ombre de vanilla de cette entite : un corps retenu pour la zone liee a la vue
     * en cours de dessin, dont le terrain lit la lumiere cuite, celle du joueur comme celle d'un portail. Ailleurs (corps au-dela des plus proches,
     * terrain dessine par un autre mod, hors de toute zone), vanilla garde son disque.
     */
    public static boolean replacesVanilla(Entity entity) {
        if (!LookUniformUploader.bakedLight()) {
            return false;
        }
        Bodies found = bound();
        return entity.getWorld() == found.world && found.holds(entity);
    }

    /** Les corps de la zone liee a la vue en cours de dessin : celle du joueur, ou celle de la vue. */
    private static Bodies bound() {
        return LightVolume.playerBound() ? PLAYER : VIEW;
    }

    /** Plus de zone de lumiere : plus aucun corps retenu. */
    static void clear() {
        PLAYER.clear();
        VIEW.clear();
        PLAYER_BEYOND.clear();
        VIEW_BEYOND.clear();
    }

    /** Plus de zone de vue : plus aucun corps retenu pour elle. */
    static void clearView() {
        VIEW.clear();
        VIEW_BEYOND.clear();
    }
}
