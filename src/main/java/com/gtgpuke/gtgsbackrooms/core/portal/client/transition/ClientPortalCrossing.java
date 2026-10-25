package com.gtgpuke.gtgsbackrooms.core.portal.client.transition;

import java.util.List;

import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalArea;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalWayBack;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.CrossingRoll;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalViewTransform;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.PortalSightReporter;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorld;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalCrossPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalTeleportPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.ArrivalGuard;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.mixin.EntityAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.AbstractClientPlayerEntityAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.ClientPlayNetworkHandlerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.LightmapTextureManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.MinecraftClientAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Traversee sans coupure cote client : prediction du passage et bascule de monde sans ecran de chargement.
 *
 * <p>A chaque image, si la camera a franchi le plan d'un portail depuis l'image precedente, le joueur
 * est teleporte localement (position, regard et vitesse transposes), le client bascule sur le monde
 * distant deja charge et previent le serveur. Tant que le serveur n'a pas confirme, les paquets
 * vanilla continuent d'alimenter l'ancien monde, qui devient un monde distant : on peut se retourner
 * et voir d'ou l'on vient. A la confirmation, le flux de paquets suit le joueur ; les dechargements
 * que vanilla envoie alors pour l'ancien monde sont ignores pour en garder la vue.
 *
 * <p>Le joueur reste le meme objet d'un monde a l'autre : ses animations (marche, balancement de la vue)
 * continuent. Tout ce qui s'interpole passe le portail avec lui, valeur courante et valeur precedente ensemble :
 * positions, regard, tete, corps et balancement du bras qui tient l'objet. Le portail tourne souvent le joueur d'un
 * demi-tour ; un seul angle oublie, et la main ou le corps rattrape ce demi-tour en quelques images.
 */
public final class ClientPortalCrossing {

    /** Delai de securite avant de considerer qu'une prediction est restee sans reponse. */
    private static final int SERVER_ANSWER_TIMEOUT_TICKS = 60;
    /** Duree maximale pendant laquelle les dechargements vanilla de l'ancien monde sont ignores. */
    private static final int UNLOAD_WINDOW_TICKS = 40;
    /** Duree pendant laquelle un retour predit attend le vrai : bien plus qu'un aller-retour reseau. */
    private static final int PREDICTED_RETURN_TICKS = 40;
    /** Ecart au-dela duquel la sortie confirmee par le serveur differe vraiment de celle predite (il a pu relever le joueur d'un sol irregulier). */
    private static final double CONFIRMATION_TOLERANCE_SQUARED = 0.3 * 0.3;

    /** Pieds du joueur a l'image precedente, en primitifs : ce test tourne a chaque image. */
    private static double lastFeetX;
    private static double lastFeetY;
    private static double lastFeetZ;
    private static boolean hasLastFeet;
    private static @Nullable ClientWorld lastFeetWorld;
    private static int lastCrossingAge = Integer.MIN_VALUE / 2;
    private static int awaitingServerTicks;
    private static int unloadWindowTicks;
    private static @Nullable ClientWorld previousWorld;
    /** Sortie calculee par la derniere prediction, en attente de confirmation. */
    private static @Nullable Vec3d predictedExit;

    private ClientPortalCrossing() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Enregistre le recepteur de teleportation ; a appeler une fois depuis le point d'entree client. */
    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PortalTeleportPayload.ID,
                (payload, context) -> onServerTeleport(context.client(), payload));
    }

    /**
     * Appele au debut de chaque rendu du monde : detecte le franchissement du plan d'un portail par le joueur.
     *
     * @param tickDelta fraction de tick de l'image
     */
    public static void predictCrossing(float tickDelta) {
        if (!ModConfig.ENABLE_PORTAL_FEATURE) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }
        // Position affichee a cette image, pas celle du tick : la continuite visuelle se joue la. Le test
        // porte sur les pieds (sur les yeux pour un cadre couche), exactement comme celui du serveur : devant un cadre
        // debout la camera franchit le plan au meme instant que les pieds, et client comme serveur prennent la meme decision.
        double feetX = MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double feetY = MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double feetZ = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
        boolean hasFrom = hasLastFeet && lastFeetWorld == world;
        double fromX = lastFeetX;
        double fromY = lastFeetY;
        double fromZ = lastFeetZ;
        lastFeetX = feetX;
        lastFeetY = feetY;
        lastFeetZ = feetZ;
        hasLastFeet = true;
        lastFeetWorld = world;
        // Un nouveau joueur (respawn, reconnexion) repart d'un age nul : la derniere traversee etait celle d'un autre.
        if (player.age < lastCrossingAge) {
            lastCrossingAge = Integer.MIN_VALUE / 2;
        }
        if (!hasFrom || awaitingServerTicks > 0 || player.hasVehicle()
                || player.age - lastCrossingAge < ModConfig.PORTAL_TELEPORT_COOLDOWN_TICKS
                || squaredDistance(fromX, fromY, fromZ, feetX, feetY, feetZ) > TeleportExecutor.MAX_STEP_SQUARED) {
            // Un saut de position (commande, respawn) traverse les plans sans les franchir : meme garde que le serveur.
            return;
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            // Le cadre d'arrivee au sol dont le joueur vient de sortir ne le renvoie pas : meme garde que le serveur.
            if (portal.teleportable() && TeleportExecutor.crossedPortalPlane(portal, player, fromX, fromY, fromZ, feetX, feetY, feetZ)
                    && !ArrivalGuard.guards(player, portal)) {
                predict(client, player, world, portal, new Vec3d(feetX, feetY, feetZ));
                return;
            }
        }
    }

    private static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Tick client : expiration des attentes. */
    public static void onClientTick() {
        ArrivalGuard.tick(PortalCollision.Side.CLIENT);
        PortalCollision.tick(PortalCollision.Side.CLIENT);
        if (awaitingServerTicks > 0) {
            awaitingServerTicks--;
        }
        if (unloadWindowTicks > 0 && --unloadWindowTicks == 0) {
            previousWorld = null;
        }
    }

    /** Deconnexion : plus rien a attendre. */
    public static void clear() {
        hasLastFeet = false;
        lastFeetWorld = null;
        lastCrossingAge = Integer.MIN_VALUE / 2;
        awaitingServerTicks = 0;
        unloadWindowTicks = 0;
        previousWorld = null;
        predictedExit = null;
        SoundAnchors.clear();
        CrossingRoll.clear();
        ArrivalGuard.clear(PortalCollision.Side.CLIENT);
        PortalCollision.clear(PortalCollision.Side.CLIENT);
    }

    /** {@code true} tant que les dechargements de chunks vanilla concernent l'ancien monde et doivent etre ignores. */
    public static boolean swallowsVanillaUnloads() {
        return unloadWindowTicks > 0;
    }

    /**
     * Pendant la fenetre de bascule, les entities detruites par vanilla sont celles de l'ancien monde.
     *
     * @return {@code true} si les identifiants ont ete traites et que vanilla ne doit pas les appliquer
     */
    public static boolean redirectDestroyedEntities(IntList entityIds) {
        if (unloadWindowTicks <= 0 || previousWorld == null) {
            return false;
        }
        // Le serveur renvoie aussitot celles que le joueur voit encore par le portail de retour : retrait differe.
        EntityHandover.deferRemoval(previousWorld, entityIds);
        return true;
    }

    /** Le serveur recentre le suivi de chunks dans le nouveau monde : le nettoyage de l'ancien est termine. */
    public static void onVanillaChunkCenter() {
        unloadWindowTicks = 0;
        previousWorld = null;
    }

    private static void predict(MinecraftClient client, ClientPlayerEntity player, ClientWorld world, BackroomsPortalEntity portal, Vec3d feet) {
        RegistryKey<World> destination = portal.destinationDim();
        boolean sameDimension = world.getRegistryKey().equals(destination);
        RemoteWorld remote = RemoteWorldRegistry.get(destination);
        if (!sameDimension && remote == null) {
            // Sans monde distant charge, autant laisser le serveur mener la bascule.
            return;
        }
        Vec3d velocity = player.getVelocity();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        PortalFacing source = portal.facingSource();
        PortalFacing exitFacing = portal.facingDestination();
        Vec3d exit = TeleportExecutor.exitPoint(portal, feet, player.getHeight(), portal.crossingHeight(player));
        Vec3d exitVelocity = PortalFacing.transformVector(source, exitFacing, velocity);
        Vec2f look = PortalFacing.transformLook(source, exitFacing, yaw, pitch);
        // Le portail tourne le regard d'un meme angle pour toutes les valeurs, courantes et precedentes, ramene au plus
        // court pour que le lacet reste continu (pas de tour complet entre deux images) ; un cadre couche change aussi
        // le tangage.
        float yawShift = MathHelper.wrapDegrees(look.x - yaw);
        float pitchShift = look.y - pitch;
        // Les positions du tick courant et du tick precedent passent aussi le portail, decalees comme celle de cette image a
        // sa sortie : l'interpolation de l'image continue sans a-coup, le joueur ne s'arrete pas sur le plan le temps d'un tick.
        Vec3d shift = exit.subtract(portal.toDestination(feet));
        Vec3d thisTick = portal.toDestination(player.getPos()).add(shift);
        Vec3d lastTick = portal.toDestination(new Vec3d(player.prevX, player.prevY, player.prevZ)).add(shift);

        // Le serveur doit recevoir l'annonce avant le prochain paquet de deplacement.
        ClientPlayNetworking.send(new PortalCrossPayload(portal.getUuid(), feet, velocity, yaw, pitch));
        predictFleetingReturns(world, sameDimension ? world : remote.world(), portal);
        predictedExit = exit;
        awaitingServerTicks = SERVER_ANSWER_TIMEOUT_TICKS;
        lastCrossingAge = player.age;
        PortalSightReporter.onArrival();
        // La camera part de l'image vue par le cadre, penchee si le portail tourne autour d'un axe horizontal ou retourne
        // l'arrivee, et placee a l'image de l'oeil : elle rejoint ensuite l'oeil, la ou le corps ressort debout.
        double eyeHeight = player.getEyeY() - player.getY();
        Vec3d cameraShift = portal.toDestination(feet.add(0.0, eyeHeight, 0.0)).subtract(exit.x, exit.y + eyeHeight, exit.z);
        CrossingRoll.start(PortalViewTransform.crossingRoll(source, exitFacing, yaw, pitch), cameraShift, Util.getMeasuringTimeMs());
        turn(player, yawShift, pitchShift);
        carryCape(player, portal);
        if (sameDimension) {
            place(player, thisTick, exitVelocity);
        } else {
            switchWorld(client, remote, () -> place(player, thisTick, exitVelocity), false);
        }
        continueInterpolation(player, lastTick);
        ArrivalGuard.arrive(player, portal);
        hasLastFeet = false;
        SoundAnchors.refresh(client);
    }

    /**
     * Sortie fugace perdue de vue : le bloc portail ne repose ses retours qu'a l'annonce de ce passage, que le client a deja
     * fait. Chaque face de l'entree passee qui n'a pas son retour a l'arrivee en recoit un predit, a la place exacte du vrai
     * et ferme a la traversee, le temps que le vrai arrive : le joueur qui se retourne voit le portail, et non ce qu'il y a
     * derriere le cadre d'arrivee (un eclair noir ou blanc, le temps d'un aller-retour reseau). Jamais pour un aller simple,
     * qui n'a pas de retour.
     */
    private static void predictFleetingReturns(ClientWorld departure, ClientWorld arrival, BackroomsPortalEntity crossed) {
        if (!fleetingEntry(departure, crossed)) {
            return;
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(departure);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity entry = portals.get(i);
            if (!entry.centeredAt(crossed.getPos()) || !entry.destinationDim().equals(crossed.destinationDim()) || hasReturn(arrival, entry)) {
                continue;
            }
            ClientPortalIndex.predict(arrival, BackroomsPortalEntity.builder(arrival)
                    .position(entry.destinationPos())
                    .facingSource(entry.facingDestination())
                    .facingDestination(entry.facingSource())
                    .destinationDim(departure.getRegistryKey())
                    .destinationPos(entry.getPos())
                    .size(entry.width(), entry.height())
                    .transmitsLight(entry.transmitsLight())
                    .fleeting(true)
                    .teleportable(false)
                    .build(), PREDICTED_RETURN_TICKS);
        }
    }

    /**
     * {@code true} si l'entree passee est celle d'un bloc portail a sortie fugace : ses reglages arrivent avec son chunk. Le
     * bloc est au pied du cadre ; sa colonne (largeur paire) et le cote de son plan (au bord du bloc) dependent de la face,
     * d'ou les deux orientations.
     */
    private static boolean fleetingEntry(ClientWorld departure, BackroomsPortalEntity crossed) {
        int width = Math.round(crossed.width());
        int height = Math.round(crossed.height());
        PortalFacing facing = crossed.facingSource().upright();
        for (int side = 0; side < 2; side++) {
            if (departure.getBlockEntity(PortalArea.anchor(crossed.getPos(), facing, width, height)) instanceof BackroomsPortalBlockEntity block) {
                return PortalWayBack.of(block.settings()).fleetingExit();
            }
            facing = facing.opposite();
        }
        return false;
    }

    /** {@code true} si le monde d'arrivee a deja le retour de cette entree : a son centre d'arrivee, dans son orientation. */
    private static boolean hasReturn(ClientWorld arrival, BackroomsPortalEntity entry) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(arrival);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            if (portal.centeredAt(entry.destinationPos()) && portal.facingSource() == entry.facingDestination()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tourne le joueur du meme angle que le portail, chaque valeur avec sa valeur precedente : regard, tete, corps, et
     * balancement du bras (sans lui, la main tenue rattrape le demi-tour en quelques images).
     */
    private static void turn(ClientPlayerEntity player, float yawShift, float pitchShift) {
        player.setYaw(player.getYaw() + yawShift);
        player.prevYaw += yawShift;
        player.setHeadYaw(player.getHeadYaw() + yawShift);
        player.prevHeadYaw += yawShift;
        player.setBodyYaw(player.getBodyYaw() + yawShift);
        player.prevBodyYaw += yawShift;
        player.renderYaw += yawShift;
        player.lastRenderYaw += yawShift;
        player.setPitch(player.getPitch() + pitchShift);
        player.prevPitch += pitchShift;
        player.renderPitch += pitchShift;
        player.lastRenderPitch += pitchShift;
    }

    /**
     * La cape passe le portail avec le joueur : son point d'ancrage, qui suit le joueur avec retard, et sa valeur precedente
     * sont transposes comme lui. Laisses de l'autre cote, vanilla ne les rattrape qu'au tick suivant (un ecart de plus de
     * dix blocs les ramene d'un coup, un ecart moindre se comble par quarts) et la cape s'etire vers l'ancienne position
     * sur toutes les images d'ici la.
     */
    private static void carryCape(ClientPlayerEntity player, BackroomsPortalEntity portal) {
        Vec3d cape = portal.toDestination(new Vec3d(player.capeX, player.capeY, player.capeZ));
        Vec3d previous = portal.toDestination(new Vec3d(player.prevCapeX, player.prevCapeY, player.prevCapeZ));
        player.capeX = cape.x;
        player.capeY = cape.y;
        player.capeZ = cape.z;
        player.prevCapeX = previous.x;
        player.prevCapeY = previous.y;
        player.prevCapeZ = previous.z;
    }

    /** Pose le joueur a la sortie, sans toucher a ses angles ni a ses valeurs d'interpolation. */
    private static void place(ClientPlayerEntity player, Vec3d position, Vec3d velocity) {
        player.setPosition(position.x, position.y, position.z);
        player.setVelocity(velocity);
    }

    /**
     * Apres une prediction, l'image en cours interpole depuis la position du tick precedent, elle aussi passee par le
     * portail : la camera comme le modele vu en troisieme personne.
     */
    private static void continueInterpolation(ClientPlayerEntity player, Vec3d lastTick) {
        player.prevX = lastTick.x;
        player.prevY = lastTick.y;
        player.prevZ = lastTick.z;
        player.lastRenderX = lastTick.x;
        player.lastRenderY = lastTick.y;
        player.lastRenderZ = lastTick.z;
    }

    private static void onServerTeleport(MinecraftClient client, PortalTeleportPayload payload) {
        awaitingServerTicks = 0;
        PortalSightReporter.onArrival();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (player == null || world == null || handler == null) {
            return;
        }
        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, payload.dimension());
        Vec3d predicted = predictedExit;
        predictedExit = null;
        if (world.getRegistryKey().equals(dimension)) {
            if (predicted == null || predicted.squaredDistanceTo(payload.position()) > CONFIRMATION_TOLERANCE_SQUARED) {
                // Bascule menee par le serveur, ou position corrigee : alignement sur lui, et garde du cadre au sol ou il le pose.
                applyPosition(player, payload.position(), payload.yaw(), payload.pitch(), payload.velocity());
                ArrivalGuard.arriveOnFrame(player, ClientPortalIndex.portals(world));
            }
            // Sinon la prediction est confirmee telle quelle : le joueur a continue d'avancer depuis, on ne le ramene pas en arriere.
            if (handler.getWorld() != world) {
                bindNetworkHandler(client, world, handler.getWorld());
            }
            SoundAnchors.refresh(client);
            return;
        }
        // Bascule menee par le serveur avant toute annonce : la dimension n'a pas encore d'index chez le joueur.
        RemoteWorld remote = RemoteWorldRegistry.announce(dimension, payload.dimensionType(), -1);
        if (remote == null) {
            ModConstants.LOGGER.error("[Portal] Impossible de basculer vers {} : monde distant indisponible.", payload.dimension());
            return;
        }
        switchWorld(client, remote,
                () -> applyPosition(player, payload.position(), payload.yaw(), payload.pitch(), payload.velocity()), true);
        ArrivalGuard.arriveOnFrame(player, ClientPortalIndex.portals(remote.world()));
        SoundAnchors.refresh(client);
    }

    /**
     * Echange le monde principal et un monde distant : le joueur change de monde, le client rend le
     * nouveau monde, l'ancien reste disponible pour les portails de retour.
     *
     * @param client client du joueur
     * @param remote monde distant d'arrivee, deja annonce par le serveur
     * @param placement pose le joueur a l'arrivee, entre son retrait de l'ancien monde et son ajout au nouveau
     * @param confirmed {@code true} si le serveur a deja deplace le joueur : le flux de paquets suit alors le joueur
     */
    private static void switchWorld(MinecraftClient client, RemoteWorld remote, Runnable placement, boolean confirmed) {
        ClientPlayerEntity player = client.player;
        ClientWorld oldWorld = client.world;
        WorldRenderer oldRenderer = client.worldRenderer;
        if (player == null || oldWorld == null) {
            return;
        }
        ModPerformanceMonitor.onCrossingStart();
        ClientWorld newWorld = remote.world();
        RemoteWorldRegistry.swapMainWorld(oldWorld, oldRenderer, remote,
                ChunkSectionPos.getSectionCoord(player.getX()), ChunkSectionPos.getSectionCoord(player.getZ()));

        oldWorld.removeEntity(player.getId(), Entity.RemovalReason.CHANGED_DIMENSION);
        ((EntityAccessor) player).backrooms$unsetRemoved();
        ((EntityAccessor) player).backrooms$setWorld(newWorld);
        // Le monde ou vanilla a cree le joueur, garde a part : sans cela, il resterait vivant une fois libere.
        ((AbstractClientPlayerEntityAccessor) player).backrooms$setClientWorld(newWorld);
        placement.run();
        newWorld.addEntity(player);

        client.world = newWorld;
        ((MinecraftClientAccessor) client).backrooms$setWorldRenderer(remote.renderer());
        // Les vues de portail ont laisse a ce renderer leurs sections, limitees au cadre. Vanilla ne refait sa liste qu'apres
        // un changement de son graphe ou un regard tourne de deux degres : un joueur qui ressort sans tourner la tete (le
        // portail double face ne le fait pas tourner) et s'arrete ne verrait que ce que montrait le portail.
        ((WorldRendererAccessor) remote.renderer()).backrooms$setLastCameraPitch(Double.MIN_VALUE);
        client.getBlockEntityRenderDispatcher().setWorld(newWorld);
        client.getEntityRenderDispatcher().setWorld(newWorld);
        client.setCameraEntity(player);
        // La lightmap ne se recalcule qu'au tick : forcee ici, sinon l'autre monde s'eclaire une ou deux images avec celle
        // de l'ancien (retour de nuit du Level 0 vers un Overworld en plein jour : une image sombre, puis la bonne).
        ((LightmapTextureManagerAccessor) client.gameRenderer.getLightmapTextureManager()).backrooms$setDirty(true);
        hasLastFeet = false;
        lastFeetWorld = null;
        // Un passage refuse ramene le joueur dans le monde que le flux vanilla n'a pas quitte : rien a rebrancher, et aucun
        // nettoyage de l'ancien monde a attendre (les destructions de ce monde-ci, detournees, n'y seraient jamais faites).
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (confirmed && handler != null && handler.getWorld() != newWorld) {
            bindNetworkHandler(client, newWorld, oldWorld);
        }
        ModPerformanceMonitor.onCrossingSwitched(newWorld.getRegistryKey().getValue());
        ModConstants.LOGGER.debug("[Portal] Bascule client vers {} ({}).", newWorld.getRegistryKey().getValue(),
                confirmed ? "confirmee par le serveur" : "prediction");
    }

    /** Les paquets vanilla alimentent desormais {@code world} ; le nettoyage de l'ancien monde qui suit est ignore. */
    private static void bindNetworkHandler(MinecraftClient client, ClientWorld world, @Nullable ClientWorld oldWorld) {
        ClientPlayNetworkHandlerAccessor handler = (ClientPlayNetworkHandlerAccessor) client.getNetworkHandler();
        if (handler == null) {
            return;
        }
        handler.backrooms$setWorld(world);
        handler.backrooms$setWorldProperties(world.getLevelProperties());
        previousWorld = oldWorld;
        unloadWindowTicks = UNLOAD_WINDOW_TICKS;
    }

    /** Place le joueur sans interpolation depuis l'ancienne position ni l'ancien regard. */
    private static void applyPosition(ClientPlayerEntity player, Vec3d position, float yaw, float pitch, Vec3d velocity) {
        player.refreshPositionAndAngles(position.x, position.y, position.z, yaw, pitch);
        player.prevYaw = yaw;
        player.prevPitch = pitch;
        player.setHeadYaw(yaw);
        player.prevHeadYaw = yaw;
        player.setBodyYaw(yaw);
        player.prevBodyYaw = yaw;
        player.setVelocity(velocity);
        // Bascule menee par le serveur, sans portail connu : la cape repart de la nouvelle position, comme a une reapparition.
        player.capeX = position.x;
        player.capeY = position.y;
        player.capeZ = position.z;
        player.prevCapeX = position.x;
        player.prevCapeY = position.y;
        player.prevCapeZ = position.z;
    }
}
