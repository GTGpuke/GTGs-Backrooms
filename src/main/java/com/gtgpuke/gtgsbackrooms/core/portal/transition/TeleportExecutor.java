package com.gtgpuke.gtgsbackrooms.core.portal.transition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.VehicleMoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.WorldProperties;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.propagation.PortalPursuit;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalCrossPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalNetwork;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalSight;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalTeleportPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;
import com.gtgpuke.gtgsbackrooms.mixin.EntityAccessor;

/**
 * Fait passer joueurs, objets, mobs et projectiles d'un cote a l'autre des portails, cote serveur.
 *
 * <p>Pour les joueurs, deux declencheurs : le client annonce que sa camera a franchi le plan ({@link PortalCrossPayload},
 * chemin normal, verifie puis applique avec l'etat exact du client pour que sa prediction tienne), et
 * en secours le serveur detecte lui-meme le passage des pieds entre deux ticks. Un changement de
 * dimension suit la sequence vanilla sans le paquet de respawn : le client bascule sur le monde
 * distant qu'il a deja charge, sans ecran de chargement. Un joueur qui vient de traverser attend
 * {@link ModConfig#PORTAL_TELEPORT_COOLDOWN_TICKS} ticks avant de pouvoir repasser. Les autres entities sont
 * teleportees par le serveur seul, d'apres leur deplacement du tick, avec le cooldown de portail vanilla. Un vehicule
 * emmene tout ce qui le monte, joueurs compris : en selle, c'est le serveur qui mene la bascule, d'apres le trajet du
 * vehicule entre deux ticks ({@link #executeTree}).
 */
public final class TeleportExecutor {

    /** Jeu minuscule devant le portail d'arrivee : le joueur ressort exactement la ou il est entre, sans rester dans le plan. */
    public static final double EXIT_OFFSET = 0.02;
    /** Ticks laisses au client pour annoncer lui-meme un franchissement que le serveur a detecte : sa prediction doit gagner. */
    private static final int CLIENT_PREDICTION_GRACE_TICKS = 5;
    /** Distance au cadre a laquelle la position annoncee par un client qui traverse reste plausible. */
    private static final double ANNOUNCED_POSITION_TOLERANCE = 1.5;
    /** Ecart tolere entre la position annoncee par le client et celle connue du serveur (un tick d'avance). */
    private static final double MAX_PREDICTION_DRIFT = 3.0;
    /** Au-dela de ce deplacement par tick (ou par image, cote client), ce n'est pas une marche mais un saut de position (respawn, commande). */
    public static final double MAX_STEP_SQUARED = 8.0 * 8.0;
    /** Cooldown de portail vanilla pose sur un objet, un mob ou un projectile qui vient de passer : evite l'aller-retour immediat entre deux cadres proches. */
    private static final int ENTITY_PORTAL_COOLDOWN_TICKS = 10;
    /** Hauteur maximale de remontee pour sortir le joueur d'un bloc a l'arrivee. */
    private static final double MAX_LIFT = 0.30;
    private static final double LIFT_STEP = 0.02;
    private static final String PERF_TELEPORT_PLAYER = "portal.teleport.player";
    private static final String PERF_TELEPORT_VANILLA = "portal.teleport.vanilla";

    private static final Vec3d DOWN = new Vec3d(0.0, -1.0, 0.0);
    /** Jeu sur l'image du bas : en deca, le bas reste le bas de l'autre cote. */
    private static final double VERTICAL_KEPT = 1.0e-6;
    /** Vitesse horizontale gardee d'un tick a l'autre par une entity vivante en l'air (vanilla). */
    private static final double AIR_DRAG = 0.91;
    /** Gravite d'une entity vivante, en blocs par tick au carre, et vitesse verticale gardee d'un tick a l'autre (vanilla). */
    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    /** Poussee horizontale au plus, en blocs par tick, a la sortie d'un cadre au sol : l'elan d'un sprint. */
    public static final double MAX_EDGE_PUSH = 0.35;
    /** Elan vertical au plus a la sortie d'un cadre au sol, en blocs par tick : celui d'un saut vanilla. */
    public static final double MAX_EXIT_RISE = 0.42;
    /** Pas de l'elan vertical ajoute quand la poussee la plus forte ne suffit pas. */
    private static final double RISE_STEP = 0.01;
    /** Marge au-dela du bord du cadre ou retombe le corps pousse a la sortie d'un cadre au sol. */
    private static final double EDGE_CLEARANCE = 0.05;
    /** Vitesse annoncee la plus forte admise, en blocs par tick : bien au-dela d'un vol propulse a l'elytre. */
    private static final double MAX_ANNOUNCED_SPEED = 10.0;
    private static final float MAX_PITCH = 90.0f;

    private static final Map<UUID, Vec3d> LAST_PLAYER_POS = new HashMap<>();
    /** Tick de la derniere traversee de chaque joueur, pour son cooldown. */
    private static final Object2LongMap<UUID> LAST_CROSSING_TICK = new Object2LongOpenHashMap<>();
    /** Franchissements detectes par le serveur, en attente de l'annonce du client. */
    private static final Map<UUID, PendingCrossing> PENDING_CROSSINGS = new HashMap<>();
    /** Position, au tick precedent, du vehicule racine de chaque joueur monte : mene par un client, il ne bouge qu'entre deux ticks. */
    private static final Map<UUID, Vec3d> LAST_VEHICLE_POS = new HashMap<>();
    /** Vehicule racine que chaque joueur monte montait au tick precedent : passer sur un autre n'est pas un trajet. */
    private static final Map<UUID, UUID> LAST_VEHICLE = new HashMap<>();
    /** Joueurs montes sur l'attelage qui traverse, et ce que chacun montait ; remis en selle de l'autre cote. */
    private static final List<ServerPlayerEntity> RIDERS = new ArrayList<>();
    private static final List<Entity> RIDER_VEHICLES = new ArrayList<>();
    private static final TypeFilter<Entity, Entity> ENTITY_FILTER = TypeFilter.instanceOf(Entity.class);
    private static final List<Entity> ENTITY_SCRATCH = new ArrayList<>();
    /**
     * Objets, mobs, projectiles et vehicules : les joueurs ont leur propre chemin, un portail ne traverse rien, et un
     * passager suit son vehicule (c'est l'attelage entier qui traverse, par sa racine).
     */
    private static final Predicate<Entity> CROSSING_CANDIDATE = entity -> !(entity instanceof PlayerEntity)
            && !(entity instanceof BackroomsPortalEntity)
            && entity.isAlive()
            && !entity.hasPortalCooldown()
            && !entity.hasVehicle();

    /** Portail franchi selon le serveur et tick a partir duquel il teleporte lui-meme. */
    private record PendingCrossing(UUID portal, long deadline) {
    }
    private static boolean initialized;

    private TeleportExecutor() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche le tick serveur, le recepteur client et le nettoyage ; idempotent. */
    public static void init() {
        if (initialized) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(TeleportExecutor::tick);
        ServerPlayNetworking.registerGlobalReceiver(PortalCrossPayload.ID, (payload, context) -> onClientCrossing(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            forget(handler.player.getUuid());
            PENDING_CROSSINGS.remove(handler.player.getUuid());
            LAST_CROSSING_TICK.removeLong(handler.player.getUuid());
        });
        // Apres un changement de monde vanilla ou un respawn, l'ancienne position (du joueur ou de son vehicule) n'est pas un
        // trajet : elle couperait les plans des portails du nouveau monde.
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> forget(player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> forget(newPlayer.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_PLAYER_POS.clear();
            LAST_VEHICLE_POS.clear();
            LAST_VEHICLE.clear();
            PENDING_CROSSINGS.clear();
            LAST_CROSSING_TICK.clear();
            PortalCollision.clear(PortalCollision.Side.SERVER);
        });
        initialized = true;
    }

    private static void tick(MinecraftServer server) {
        PortalCollision.tick(PortalCollision.Side.SERVER);
        processEntities();
        List<ServerPlayerEntity> players = server.getPlayerManager().getPlayerList();
        for (int i = 0; i < players.size(); i++) {
            if (processPlayer(players.get(i))) {
                // Une teleportation peut reorganiser les listes de joueurs : on reprend au tick suivant.
                return;
            }
        }
    }

    /**
     * Secours du serveur : un joueur dont les pieds ont franchi un plan est teleporte si son client ne l'annonce pas a temps.
     *
     * @return {@code true} si le joueur vient d'etre teleporte
     */
    private static boolean processPlayer(ServerPlayerEntity player) {
        // Un joueur retire n'est plus vivant non plus.
        if (!player.isAlive()) {
            forget(player.getUuid());
            return false;
        }
        Vec3d currentPos = player.getPos();
        Vec3d lastPos = LAST_PLAYER_POS.put(player.getUuid(), currentPos);
        if (player.hasVehicle()) {
            return processRider(player);
        }
        LAST_VEHICLE_POS.remove(player.getUuid());
        LAST_VEHICLE.remove(player.getUuid());
        // Un joueur immobile ne franchit aucun plan (deux positions egales), mais un franchissement en attente se joue quand
        // meme : qui tombe dans un cadre au sol touche le fond, a deux blocs, avant la fin du delai laisse a son client.
        if (lastPos == null || lastPos.squaredDistanceTo(currentPos) > MAX_STEP_SQUARED) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        if (isOnCooldown(player.getUuid(), world.getTime())) {
            return false;
        }
        PendingCrossing pending = PENDING_CROSSINGS.get(player.getUuid());
        if (pending != null) {
            // Le client n'a pas annonce le franchissement a temps : le serveur teleporte depuis son propre etat.
            if (world.getTime() < pending.deadline()) {
                return false;
            }
            PENDING_CROSSINGS.remove(player.getUuid());
            BackroomsPortalEntity portal = BackroomsPortalEntityRegistry.find(world, pending.portal());
            return portal != null && portal.teleportable() && execute(player, portal);
        }
        BackroomsPortalEntity portal = crossedPortal(world, player, player, lastPos, currentPos);
        if (portal != null) {
            PENDING_CROSSINGS.put(player.getUuid(), new PendingCrossing(portal.getUuid(), world.getTime() + CLIENT_PREDICTION_GRACE_TICKS));
        }
        return false;
    }

    /** Oublie les positions suivies d'un joueur parti ou retire. */
    private static void forget(UUID player) {
        LAST_PLAYER_POS.remove(player);
        LAST_VEHICLE_POS.remove(player);
        LAST_VEHICLE.remove(player);
    }

    /**
     * Portail du monde dont le plan est franchi par le trajet d'un corps, depuis le cote d'entree. Une sortie fugace
     * que le joueur ne regarde pas n'existe pas pour lui : il la traverse comme de l'air.
     *
     * @param world monde du joueur
     * @param player joueur concerne, pour son regard sur les sorties fugaces
     * @param mover corps dont le trajet est teste : le joueur lui-meme, ou le vehicule racine qu'il monte
     * @param lastPos position precedente du corps
     * @param currentPos position courante du corps
     * @return le portail franchi, ou {@code null}
     */
    private static @Nullable BackroomsPortalEntity crossedPortal(
            ServerWorld world, ServerPlayerEntity player, Entity mover, Vec3d lastPos, Vec3d currentPos) {
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.getWorld() == world && portal.teleportable()
                    && crossedPortalPlane(portal, mover, lastPos.x, lastPos.y, lastPos.z, currentPos.x, currentPos.y, currentPos.z)
                    && (!portal.fleeting() || PortalSight.sees(player, portal))) {
                return portal;
            }
        }
        return null;
    }

    /**
     * Joueur monte : son attelage traverse d'apres le deplacement de son vehicule racine entre deux ticks. Mene par un
     * client, un vehicule ne bouge qu'a la reception de ses paquets, hors du tick : son trajet ne se lit pas dans
     * {@code prevX}. Le serveur mene seul la bascule, le client ne predit pas une traversee en selle.
     *
     * @return {@code true} si l'attelage vient d'etre teleporte
     */
    private static boolean processRider(ServerPlayerEntity player) {
        Entity root = player.getRootVehicle();
        UUID rider = player.getUuid();
        Vec3d currentPos = root.getPos();
        Vec3d lastPos = LAST_VEHICLE_POS.put(rider, currentPos);
        UUID lastRoot = LAST_VEHICLE.put(rider, root.getUuid());
        ServerWorld world = player.getServerWorld();
        if (lastPos == null || lastPos == currentPos || !root.getUuid().equals(lastRoot)
                || lastPos.squaredDistanceTo(currentPos) > MAX_STEP_SQUARED || isOnCooldown(rider, world.getTime())) {
            return false;
        }
        BackroomsPortalEntity portal = crossedPortal(world, player, root, lastPos, currentPos);
        return portal != null && executeTree(root, portal);
    }

    /** Objets, mobs et projectiles : le serveur decide seul, d'apres le deplacement du tick. */
    private static void processEntities() {
        List<BackroomsPortalEntity> portals = BackroomsPortalEntityRegistry.all();
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (!portal.teleportable()) {
                continue;
            }
            // Le registre ne tient que des portails serveur.
            ServerWorld world = (ServerWorld) portal.getWorld();
            ENTITY_SCRATCH.clear();
            world.collectEntitiesByType(ENTITY_FILTER, portal.crossingBox(), CROSSING_CANDIDATE, ENTITY_SCRATCH);
            for (int j = 0; j < ENTITY_SCRATCH.size(); j++) {
                Entity entity = ENTITY_SCRATCH.get(j);
                if (crossedThisTick(portal, entity)) {
                    execute(entity, portal);
                }
            }
        }
        ENTITY_SCRATCH.clear();
    }

    /**
     * {@code true} si le deplacement du tick d'une entity passe le plan du portail. Une entity qui vient d'apparaitre n'a
     * pas encore de position precedente (l'origine du monde, avant son premier tick) : comme un saut de position, son
     * trajet traverserait les plans sans les franchir, et elle serait teleportee sans avoir bouge.
     */
    static boolean crossedThisTick(BackroomsPortalEntity portal, Entity entity) {
        return entity.squaredDistanceTo(entity.prevX, entity.prevY, entity.prevZ) <= MAX_STEP_SQUARED
                && crossedPortalPlane(portal, entity, entity.prevX, entity.prevY, entity.prevZ, entity.getX(), entity.getY(), entity.getZ());
    }

    /**
     * Le client annonce un franchissement : on l'applique s'il est plausible, sinon on le ramene a l'etat serveur. Le point
     * qui decide de la traversee (les yeux pour un cadre couche) doit etre pres du cadre, pas les pieds : tombe dans un sol,
     * on a les pieds a plus d'un bloc et demi sous le plan quand les yeux le passent.
     */
    private static void onClientCrossing(ServerPlayerEntity player, PortalCrossPayload payload) {
        PENDING_CROSSINGS.remove(player.getUuid());
        ServerWorld world = player.getServerWorld();
        BackroomsPortalEntity portal = BackroomsPortalEntityRegistry.find(world, payload.portal());
        boolean plausible = portal != null
                && portal.teleportable()
                && !player.hasVehicle()
                && !isOnCooldown(player.getUuid(), world.getTime())
                && payload.position().squaredDistanceTo(player.getPos()) <= MAX_PREDICTION_DRIFT * MAX_PREDICTION_DRIFT
                && portal.detectionBox().expand(ANNOUNCED_POSITION_TOLERANCE)
                        .contains(payload.position().add(0.0, portal.crossingHeight(player), 0.0))
                && isSane(payload.velocity(), payload.yaw(), payload.pitch());
        if (plausible && execute(player, portal, payload.position(), payload.velocity(), payload.yaw(), payload.pitch(), true)) {
            return;
        }
        // Le client a deja bascule de son cote : ce payload le renvoie la ou le serveur le voit.
        PortalNetwork.send(player, PortalTeleportPayload.of(world, player.getPos(), player.getYaw(), player.getPitch(), player.getVelocity()));
        ModConstants.LOGGER.debug("[Portal] Franchissement refuse pour {} (portail {}).", player.getName().getString(), payload.portal());
    }

    /**
     * {@code true} si la vitesse et le regard annonces sont des valeurs qu'un client honnete enverrait : finis, une vitesse
     * bornee (vanilla retranche la vitesse du joueur de son controle des deplacements trop rapides) et un regard vertical
     * dans [-90, 90].
     */
    static boolean isSane(Vec3d velocity, float yaw, float pitch) {
        return Double.isFinite(velocity.x) && Double.isFinite(velocity.y) && Double.isFinite(velocity.z)
                && velocity.lengthSquared() <= MAX_ANNOUNCED_SPEED * MAX_ANNOUNCED_SPEED
                && Float.isFinite(yaw) && Math.abs(pitch) <= MAX_PITCH;
    }

    /**
     * {@code true} si le joueur a traverse un portail il y a moins de {@link ModConfig#PORTAL_TELEPORT_COOLDOWN_TICKS} ticks.
     *
     * @param player identifiant du joueur
     * @param currentTick tick courant du monde
     */
    private static boolean isOnCooldown(UUID player, long currentTick) {
        return isOnCooldown(player, currentTick, ModConfig.PORTAL_TELEPORT_COOLDOWN_TICKS);
    }

    /** Meme regle pour une duree de cooldown donnee ; nulle, aucun joueur n'attend. */
    static boolean isOnCooldown(UUID player, long currentTick, int cooldownTicks) {
        return LAST_CROSSING_TICK.containsKey(player) && currentTick - LAST_CROSSING_TICK.getLong(player) < cooldownTicks;
    }

    /** Arme le cooldown d'un joueur qui vient de traverser. */
    static void markCrossed(UUID player, long tick) {
        LAST_CROSSING_TICK.put(player, tick);
    }

    /**
     * {@code true} si les pieds d'une entity (ses yeux pour un cadre couche, {@link BackroomsPortalEntity#crossingHeight})
     * passent le plan du portail depuis le cote d'entree, entre deux positions, le centre en face de l'ouverture, et que sa
     * boite touche alors le cadre. Sert aussi a la prediction client.
     *
     * @param portal portail teste
     * @param entity entity qui se deplace, pour sa largeur et sa hauteur
     * @param lastX position precedente des pieds, X
     * @param lastY position precedente des pieds, Y
     * @param lastZ position precedente des pieds, Z
     * @param currentX position courante des pieds, X
     * @param currentY position courante des pieds, Y
     * @param currentZ position courante des pieds, Z
     */
    public static boolean crossedPortalPlane(
            BackroomsPortalEntity portal, Entity entity, double lastX, double lastY, double lastZ, double currentX, double currentY, double currentZ) {
        return crossedPortalPlane(portal, lastX, lastY, lastZ, currentX, currentY, currentZ, entity.getWidth() / 2.0, entity.getHeight(),
                portal.crossingHeight(entity), entity instanceof PlayerEntity);
    }

    /**
     * Meme test pour une boite de dimensions donnees, sans allocation. Le centre doit passer dans la largeur du cadre et le
     * corps toucher le cadre en hauteur : contourner le portail en frolant son bord ne fait pas traverser, et passer a
     * cote ressortirait dans le mur qui encadre l'arrivee.
     *
     * @param portal portail teste
     * @param lastX position precedente des pieds, X
     * @param lastY position precedente des pieds, Y
     * @param lastZ position precedente des pieds, Z
     * @param currentX position courante des pieds, X
     * @param currentY position courante des pieds, Y
     * @param currentZ position courante des pieds, Z
     * @param halfWidth demi-largeur de la boite
     * @param height hauteur de la boite, au-dessus des pieds
     * @param lift hauteur, au-dessus des pieds, du point qui decide de la traversee (les yeux pour un cadre couche, sinon zero)
     * @return {@code true} si ce point passe le plan depuis le cote d'entree, dans la largeur du cadre, la boite contre lui
     */
    static boolean crossedPortalPlane(
            BackroomsPortalEntity portal,
            double lastX,
            double lastY,
            double lastZ,
            double currentX,
            double currentY,
            double currentZ,
            double halfWidth,
            double height,
            double lift,
            boolean fullHeight) {
        double last = portal.signedDistance(lastX, lastY + lift, lastZ);
        double current = portal.signedDistance(currentX, currentY + lift, currentZ);
        if (last <= 0.0 || current > 0.0) {
            return false;
        }
        double t = last / (last - current);
        double x = lastX + (currentX - lastX) * t;
        double y = lastY + (currentY - lastY) * t;
        double z = lastZ + (currentZ - lastZ) * t;
        // Au point de passage, la boite chevauche le plan : la toucher, c'est toucher le cadre en hauteur ; le point qui
        // decide, lui, doit etre en face de l'ouverture.
        return portal.withinOpening(fullHeight, x, y + lift, z)
                && portal.detectionBox().intersects(x - halfWidth, y, z - halfWidth, x + halfWidth, y + height, z + halfWidth);
    }

    /**
     * Point de sortie des pieds d'une entity qui traverse. Le point qui decide de la traversee (les pieds, les yeux pour un
     * cadre au sol, {@link BackroomsPortalEntity#crossingHeight}) garde sa place : son image de l'autre cote, a peine
     * devant le cadre d'arrivee, le corps debout sous lui. Puis le corps est tenu du cote de la sortie et dans l'ouverture :
     * sous un cadre au plafond, il pend tout entier sous le plan (sinon sa tete entrerait dans le plafond) ; sur un cadre au
     * sol, il se tient au-dessus (tombe par un sol, on ressort de l'autre les pieds au ras du plan) ; sorti d'un cadre debout
     * ou le bas n'est plus le bas (apres un cadre couche, ou tete en bas), ses pieds restent dans la hauteur de l'ouverture,
     * sa tete sous son haut (la place qu'il avait donnerait sinon n'importe quelle hauteur, ou les pieds en haut du cadre).
     * Sert aussi a la prediction client.
     *
     * @param portal portail traverse
     * @param position pieds de l'entity
     * @param height hauteur de son corps
     * @param lift hauteur, au-dessus des pieds, du point qui decide de la traversee
     * @return pieds de l'entity a l'arrivee
     */
    public static Vec3d exitPoint(BackroomsPortalEntity portal, Vec3d position, double height, double lift) {
        PortalFacing arrival = portal.facingDestination();
        Vec3d exit = portal.toDestination(position.add(0.0, lift, 0.0))
                .add(arrival.normal.x * EXIT_OFFSET, arrival.normal.y * EXIT_OFFSET - lift, arrival.normal.z * EXIT_OFFSET);
        double plane = portal.destinationPos().y;
        if (arrival.normal.y < 0.0) {
            double highest = plane - EXIT_OFFSET - height;
            return exit.y > highest ? new Vec3d(exit.x, highest, exit.z) : exit;
        }
        if (arrival.normal.y > 0.0) {
            double lowest = plane + EXIT_OFFSET;
            return exit.y < lowest ? new Vec3d(exit.x, lowest, exit.z) : exit;
        }
        if (PortalFacing.transformVector(portal.facingSource(), arrival, DOWN).y > -1.0 + VERTICAL_KEPT) {
            double bottom = plane - portal.height() / 2.0;
            double top = Math.max(bottom, bottom + portal.height() - height);
            return new Vec3d(exit.x, MathHelper.clamp(exit.y, bottom, top), exit.z);
        }
        return exit;
    }

    /**
     * Vitesse d'un joueur a la sortie d'un portail : celle du franchissement, transposee. Entre par un cadre debout et sorti
     * d'un cadre couche au sol, il jaillit vers le haut de la vitesse de sa marche et retomberait aussitot dans le cadre : il
     * recoit une poussee horizontale dans le sens de son regard a la sortie, juste ce qu'il faut pour que son corps retombe
     * tout entier au-dela du bord du cadre devant lui (decision de l'auteur du 31 octobre 2026). Il sort souvent du cote
     * oppose a ce bord (ses yeux, en haut du cadre debout, vont a l'arriere du cadre couche) : quand la poussee la plus forte
     * ({@link #MAX_EDGE_PUSH}) ne suffit pas, un petit saut allonge son temps en l'air, au plus celui d'un saut vanilla
     * ({@link #MAX_EXIT_RISE}). D'un cadre au sol a un autre, l'elan reste horizontal : rien ne s'ajoute. Le client la
     * calcule pour sa prediction, le serveur pour la sienne, des memes valeurs.
     *
     * @param portal portail traverse
     * @param exit pieds a la sortie ({@link #exitPoint})
     * @param velocity vitesse du franchissement, transposee
     * @param exitYaw lacet a la sortie, en degres
     * @param width largeur du corps
     * @return vitesse a la sortie
     */
    public static Vec3d playerExitVelocity(BackroomsPortalEntity portal, Vec3d exit, Vec3d velocity, float exitYaw, double width) {
        PortalFacing arrival = portal.facingDestination();
        if (arrival.normal.y <= 0.0 || portal.facingSource().normal.y != 0.0) {
            return velocity;
        }
        double yaw = Math.toRadians(exitYaw);
        double headingX = -Math.sin(yaw);
        double headingZ = Math.cos(yaw);
        double dx = exit.x - portal.destinationPos().x;
        double dz = exit.z - portal.destinationPos().z;
        // Distance, le long du regard, de la sortie a la ligne ou le corps est tout entier au-dela du bord vise : le bord de
        // chaque direction du cadre recule de la demi-largeur du corps et d'une marge, et le premier atteint compte.
        double beyond = width / 2.0 + EDGE_CLEARANCE;
        double distance = Math.min(
                toEdge(dx * arrival.axisW.x + dz * arrival.axisW.z, headingX * arrival.axisW.x + headingZ * arrival.axisW.z, portal.width() / 2.0 + beyond),
                toEdge(dx * arrival.axisH.x + dz * arrival.axisH.z, headingX * arrival.axisH.x + headingZ * arrival.axisH.z, portal.height() / 2.0 + beyond));
        double rise = velocity.y;
        double push = pushOver(distance, rise);
        // Trop loin pour la poussee la plus forte : un petit saut allonge le temps en l'air.
        while (push > MAX_EDGE_PUSH && rise < MAX_EXIT_RISE) {
            rise = Math.min(MAX_EXIT_RISE, rise + RISE_STEP);
            push = pushOver(distance, rise);
        }
        push = Math.min(push, MAX_EDGE_PUSH);
        return new Vec3d(velocity.x + headingX * push, rise, velocity.z + headingZ * push);
    }

    /**
     * Vitesse horizontale qui, freinee par l'air, couvre {@code distance} avant que les pieds, partis du plan a {@code rise}
     * vers le haut, le retrouvent.
     */
    private static double pushOver(double distance, double rise) {
        int airborne = 0;
        double height = 0.0;
        double vertical = rise;
        do {
            height += vertical;
            vertical = (vertical - GRAVITY) * VERTICAL_DRAG;
            airborne++;
        } while (height > 0.0);
        return distance * (1.0 - AIR_DRAG) / (1.0 - Math.pow(AIR_DRAG, airborne));
    }

    /**
     * Distance, le long du regard, du point {@code offset} (sa coordonnee sur une direction du cadre) a la limite a
     * {@code half} du centre vers laquelle va {@code direction} (composante unitaire du regard sur cette direction) ;
     * infinie si le regard lui est perpendiculaire.
     */
    private static double toEdge(double offset, double direction, double half) {
        return (half - offset * Math.signum(direction)) / Math.abs(direction);
    }

    /**
     * Chute gardee a la traversee : celle d'avant si le bas reste le bas de l'autre cote (on continue de tomber), nulle
     * sinon. Tombe dans un sol et sorti d'un mur, on file a l'horizontale : la hauteur de chute devient de la vitesse, pas
     * des degats a l'atterrissage.
     */
    static float fallAfterCrossing(PortalFacing source, PortalFacing exit, float fallDistance) {
        return PortalFacing.transformVector(source, exit, DOWN).y < 0.0 ? fallDistance : 0.0f;
    }

    /**
     * Teleporte un objet, un mob, un projectile ou un vehicule a travers un portail : meme transposition que pour un
     * joueur, appliquee directement par le serveur. Le passage pose le cooldown de portail vanilla. Un vehicule emmene
     * tout ce qui le monte ({@link #executeTree}).
     *
     * @param entity entity qui traverse, cote serveur
     * @param portal portail traverse
     * @return {@code true} si la teleportation a eu lieu
     */
    public static boolean execute(Entity entity, BackroomsPortalEntity portal) {
        return entity.hasPassengers() ? executeTree(entity, portal) : teleportEntity(entity, portal) != null;
    }

    /**
     * Un attelage traverse : le vehicule racine emmene tout ce qui le monte. Vanilla fait changer de monde les
     * passagers avec leur vehicule et les remet en selle de l'autre cote, mais un joueur y passerait par son ecran de
     * chargement : les joueurs descendent d'abord, traversent par leur propre chemin (bascule menee par le serveur), et
     * remontent sur ce qu'ils montaient, retrouve de l'autre cote. Dans le meme monde, le vehicule garde son instance
     * et vanilla ne signale pas des passagers redevenus les memes : le client du joueur, que le serveur a fait
     * descendre, recoit lui-meme ses passagers et la position de son vehicule (il le mene, et ne la tient que de ce
     * paquet).
     *
     * @param root vehicule racine de l'attelage, cote serveur
     * @param portal portail traverse
     * @return {@code true} si l'attelage a traverse
     */
    static boolean executeTree(Entity root, BackroomsPortalEntity portal) {
        collectRiders(root);
        try {
            for (int i = 0; i < RIDERS.size(); i++) {
                RIDERS.get(i).stopRiding();
            }
            Entity arrived = teleportEntity(root, portal);
            if (arrived == null) {
                // Rien n'a traverse : chacun remonte ou il etait.
                for (int i = 0; i < RIDERS.size(); i++) {
                    RIDERS.get(i).startRiding(RIDER_VEHICLES.get(i), true);
                }
                return false;
            }
            for (int i = 0; i < RIDERS.size(); i++) {
                ServerPlayerEntity rider = RIDERS.get(i);
                execute(rider, portal);
                Entity vehicle = arrivedVehicle(arrived, RIDER_VEHICLES.get(i).getUuid());
                rider.startRiding(vehicle, true);
                if (arrived == root) {
                    rider.networkHandler.sendPacket(new EntityPassengersSetS2CPacket(vehicle));
                    rider.networkHandler.sendPacket(new VehicleMoveS2CPacket(root));
                }
            }
            return true;
        } finally {
            // Listes de travail : aucun joueur ni vehicule n'y reste apres la traversee, reussie ou non.
            RIDERS.clear();
            RIDER_VEHICLES.clear();
        }
    }

    /** Ajoute a {@link #RIDERS} les joueurs montes sur ce vehicule ou sur l'un de ses passagers, avec ce qu'ils montent. */
    private static void collectRiders(Entity vehicle) {
        List<Entity> passengers = vehicle.getPassengerList();
        for (int i = 0; i < passengers.size(); i++) {
            Entity passenger = passengers.get(i);
            if (passenger instanceof ServerPlayerEntity rider) {
                RIDERS.add(rider);
                RIDER_VEHICLES.add(vehicle);
            }
            collectRiders(passenger);
        }
    }

    /** Ce qu'un joueur montait, retrouve de l'autre cote parmi la racine arrivee et ses passagers ; la racine a defaut. */
    private static Entity arrivedVehicle(Entity arrivedRoot, UUID vehicle) {
        Entity found = arrivedRoot;
        for (Entity passenger : arrivedRoot.getPassengersDeep()) {
            if (passenger.getUuid().equals(vehicle)) {
                found = passenger;
            }
        }
        return found;
    }

    /**
     * Teleporte une entity seule, ou un vehicule avec ses passagers autres que des joueurs (vanilla les emmene et les
     * remet en selle sur sa copie).
     *
     * @return l'entity de l'autre cote (la meme dans le meme monde, sa copie dans un autre), ou {@code null} si la
     *         dimension d'arrivee est absente ou l'entity deja retiree
     */
    private static @Nullable Entity teleportEntity(Entity entity, BackroomsPortalEntity portal) {
        ServerWorld world = (ServerWorld) entity.getWorld();
        ServerWorld destinationWorld = world.getServer().getWorld(portal.destinationDim());
        if (destinationWorld == null) {
            return null;
        }
        PortalFacing source = portal.facingSource();
        PortalFacing exitFacing = portal.facingDestination();
        Vec3d exit = exitPoint(portal, entity.getPos(), entity.getHeight(), portal.crossingHeight(entity));
        float fall = fallAfterCrossing(source, exitFacing, entity.fallDistance);
        Vec3d exitVelocity = PortalFacing.transformVector(source, exitFacing, entity.getVelocity());
        Vec2f look = PortalFacing.transformLook(source, exitFacing, entity.getYaw(), entity.getPitch());
        LivingEntity chased = PortalPursuit.targetAfterCrossing(entity, portal);
        Entity arrived;
        if (destinationWorld == world) {
            entity.refreshPositionAndAngles(exit.x, exit.y, exit.z, look.x, look.y);
            entity.setVelocity(exitVelocity);
            // Position et vitesse partent aux clients des le prochain tick, la position en absolu : ils font passer le
            // portail a l'entity au lieu de la faire glisser jusqu'a l'arrivee.
            entity.velocityDirty = true;
            PortalViewTracker.onEntityJumped(world, entity);
            arrived = entity;
        } else {
            arrived = entity.teleportTo(new TeleportTarget(destinationWorld, exit, exitVelocity, look.x, look.y, false, TeleportTarget.NO_OP));
            if (arrived == null) {
                return null;
            }
            // Vanilla ne reprend que le yaw de la cible : le pitch suit, pour les projectiles.
            arrived.setPitch(look.y);
        }
        arrived.setPortalCooldown(ENTITY_PORTAL_COOLDOWN_TICKS);
        arrived.fallDistance = fall;
        // Seul un mob poursuit, et sa proie est dans le monde d'arrivee : de ce cote, il la reprend sans s'arreter.
        if (chased != null) {
            PortalPursuit.resumeAfterCrossing((MobEntity) arrived, chased);
        }
        if (arrived != entity) {
            // Des copies neuves dans l'autre dimension, passagers compris : ceux qui les voient par un portail les
            // recoivent tout de suite.
            PortalViewTracker.onEntityArrived(destinationWorld, arrived);
            for (Entity passenger : arrived.getPassengersDeep()) {
                PortalViewTracker.onEntityArrived(destinationWorld, passenger);
            }
        }
        ModConstants.LOGGER.debug("[Portal] {} traverse un portail vers {}.", entity.getType().getUntranslatedName(), portal.destinationDim().getValue());
        return arrived;
    }

    /**
     * Teleporte un joueur a travers un portail depuis l'etat courant connu du serveur : le secours quand le client
     * n'annonce pas son franchissement.
     *
     * @param player joueur a teleporter
     * @param portal portail traverse
     * @return {@code true} si la teleportation a eu lieu
     */
    public static boolean execute(ServerPlayerEntity player, BackroomsPortalEntity portal) {
        return execute(player, portal, player.getPos(), player.getVelocity(), player.getYaw(), player.getPitch(), false);
    }

    /**
     * Teleporte un joueur a travers un portail depuis un etat donne (celui du client au franchissement,
     * ou celui du serveur). Position, vitesse et regard sont transposes par {@link PortalFacing}.
     *
     * @param player joueur a teleporter
     * @param portal portail traverse
     * @param position position des pieds au franchissement
     * @param velocity vitesse au franchissement
     * @param yaw lacet au franchissement, en degres
     * @param pitch tangage au franchissement, en degres
     * @param predicted {@code true} si le client a lui-meme franchi le portail et annonce cet etat : il est deja de
     *                  l'autre cote, le serveur s'aligne sans paquet de teleportation vanilla
     * @return {@code true} si la teleportation a eu lieu
     */
    @SuppressWarnings("try")
    public static boolean execute(
            ServerPlayerEntity player, BackroomsPortalEntity portal, Vec3d position, Vec3d velocity, float yaw, float pitch, boolean predicted) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_TELEPORT_PLAYER)) {
            return teleportPlayer(player, portal, position, velocity, yaw, pitch, predicted);
        }
    }

    private static boolean teleportPlayer(
            ServerPlayerEntity player, BackroomsPortalEntity portal, Vec3d position, Vec3d velocity, float yaw, float pitch, boolean predicted) {
        ServerWorld destinationWorld = player.getServerWorld().getServer().getWorld(portal.destinationDim());
        if (destinationWorld == null) {
            ModConstants.LOGGER.warn("[Portal] Teleportation annulee, dimension d'arrivee absente : {}.",
                    portal.destinationDim().getValue());
            return false;
        }
        PortalFacing source = portal.facingSource();
        PortalFacing exitFacing = portal.facingDestination();
        Vec3d exitPoint = exitPoint(portal, position, player.getHeight(), portal.crossingHeight(player));
        Vec3d exit = liftOutOfBlocks(player, destinationWorld, exitPoint);
        Vec2f look = PortalFacing.transformLook(source, exitFacing, yaw, pitch);
        Vec3d exitVelocity = playerExitVelocity(portal, exitPoint, PortalFacing.transformVector(source, exitFacing, velocity), look.x, player.getWidth());

        player.setCameraEntity(player);
        player.stopRiding();
        int heldChunks = 0;
        if (destinationWorld == player.getServerWorld()) {
            placePlayer(player, exit, look.x, look.y, predicted);
            player.networkHandler.syncWithPlayerPosition();
            PortalViewTracker.onEntityJumped(destinationWorld, player);
            PortalNetwork.send(player, PortalTeleportPayload.of(destinationWorld, exit, look.x, look.y, exitVelocity));
        } else {
            heldChunks = changeDimension(player, destinationWorld, exit, look.x, look.y, exitVelocity, predicted);
        }
        // Pas de velocityModified : le client recoit sa vitesse avec le payload, ou l'a deja s'il a predit le passage. Un
        // paquet de vitesse vanilla arriverait un tick plus tard et ecraserait celle qu'il a depuis (un saut rejoue).
        player.setVelocity(exitVelocity);
        player.fallDistance = fallAfterCrossing(source, exitFacing, player.fallDistance);

        markCrossed(player.getUuid(), destinationWorld.getTime());
        PortalSight.onArrival(player, portal);
        // Un spectateur n'agit pas sur le monde : il ne rouvre pas une sortie fugace perdue de vue.
        if (!player.isSpectator()) {
            BackroomsPortalBlockEntity.onPlayerCrossed(portal);
        }
        LAST_PLAYER_POS.put(player.getUuid(), player.getPos());
        ModConstants.LOGGER.info("[Portal] {} traverse un portail vers {} {} ({} chunk(s) d'arrivee deja chez le client).",
                player.getName().getString(), portal.destinationDim().getValue(), exit, heldChunks);
        return true;
    }

    /**
     * Changement de dimension sans paquet de respawn : la sequence de {@code ServerPlayerEntity.teleportTo},
     * precedee de notre propre annonce pour que le client bascule sur son monde distant deja charge.
     *
     * @return nombre de chunks de l'arrivee que le client tient deja, que vanilla ne lui renverra pas
     */
    @SuppressWarnings("try")
    private static int changeDimension(
            ServerPlayerEntity player, ServerWorld destination, Vec3d exit, float yaw, float pitch, Vec3d velocity, boolean predicted) {
        ServerWorld origin = player.getServerWorld();
        PlayerManager playerManager = origin.getServer().getPlayerManager();
        WorldProperties properties = destination.getLevelProperties();
        // Le client garde le monde quitte : ses chunks ne lui seront ni decharges trop tot, ni renvoyes s'il revient. Avant
        // l'annonce de la bascule : les changements de ce tick, envoyes ici, s'appliquent encore au monde quitte.
        PortalViewTracker.recordDeparture(player, origin);
        PortalNetwork.send(player, PortalTeleportPayload.of(destination, exit, yaw, pitch, velocity));
        player.networkHandler.sendPacket(new DifficultyS2CPacket(properties.getDifficulty(), properties.isDifficultyLocked()));
        playerManager.sendCommandTree(player);
        // Avant que vanilla ne retire les tickets du joueur : le cote quitte, qu'il voit encore par le portail, reste charge.
        DestinationLoader.holdDeparture(origin, player.getPos());
        origin.removePlayer(player, Entity.RemovalReason.CHANGED_DIMENSION);
        ((EntityAccessor) player).backrooms$unsetRemoved();
        player.setServerWorld(destination);
        // Avant que la destination ne suive le joueur : les chunks qu'il tient deja ne lui seront pas renvoyes, et ses
        // copies distantes d'entities sont retirees chez lui d'abord.
        int heldChunks = PortalViewTracker.keepHeldChunks(player, destination.getRegistryKey());
        PortalViewTracker.onPlayerChangedWorld(player);
        placePlayer(player, exit, yaw, pitch, predicted);
        player.networkHandler.syncWithPlayerPosition();
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(PERF_TELEPORT_VANILLA)) {
            destination.onDimensionChanged(player);
        }
        // Ceux qui voient la destination par un portail recoivent sa copie au meme tick que la destruction de l'ancienne.
        PortalViewTracker.onEntityArrived(destination, player);
        Criteria.CHANGED_DIMENSION.trigger(player, origin.getRegistryKey(), destination.getRegistryKey());
        player.networkHandler.sendPacket(new PlayerAbilitiesS2CPacket(player.getAbilities()));
        playerManager.sendWorldInfo(player, destination);
        playerManager.sendPlayerStatus(player);
        playerManager.sendStatusEffects(player);
        return heldChunks;
    }

    /**
     * Place le joueur a la sortie. Apres une prediction, il y est deja : le serveur se contente de s'aligner, sans
     * paquet de teleportation vanilla, qui le ramenerait la ou il etait un tick plus tot et annulerait son elan.
     * Sinon la teleportation vanilla s'impose : elle ecarte les deplacements deja en route depuis l'ancienne position.
     */
    private static void placePlayer(ServerPlayerEntity player, Vec3d exit, float yaw, float pitch, boolean predicted) {
        if (predicted) {
            player.refreshPositionAndAngles(exit.x, exit.y, exit.z, yaw, pitch);
        } else {
            player.networkHandler.requestTeleport(exit.x, exit.y, exit.z, yaw, pitch);
        }
    }

    /** Remonte legerement le point d'arrivee si la boite du joueur y chevauche un bloc (sol irregulier). */
    private static Vec3d liftOutOfBlocks(ServerPlayerEntity player, ServerWorld destinationWorld, Vec3d exit) {
        Box box = player.getBoundingBox().offset(exit.subtract(player.getPos()));
        if (destinationWorld.isSpaceEmpty(player, box)) {
            return exit;
        }
        for (double lift = LIFT_STEP; lift <= MAX_LIFT; lift += LIFT_STEP) {
            if (destinationWorld.isSpaceEmpty(player, box.offset(0.0, lift, 0.0))) {
                return exit.add(0.0, lift, 0.0);
            }
        }
        return exit;
    }
}
