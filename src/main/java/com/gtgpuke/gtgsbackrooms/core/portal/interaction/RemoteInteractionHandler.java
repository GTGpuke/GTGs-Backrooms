package com.gtgpuke.gtgsbackrooms.core.portal.interaction;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Saddleable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityChangeListener;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalBlockActionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalEntityActionPayload;
import com.gtgpuke.gtgsbackrooms.mixin.EntityAccessor;

/**
 * Actions du joueur a travers un portail, cote serveur : frapper ou utiliser une entity, utiliser un bloc,
 * commencer ou abandonner le minage d'un bloc.
 *
 * <p>Le client vise a travers le cadre et envoie la cible dans la dimension d'arrivee. Le serveur verifie
 * que le portail est devant le joueur et a portee, puis que la cible est a portee du point ou son regard
 * ressort. L'action vanilla s'execute alors avec le joueur transpose de l'autre cote le temps de l'appel :
 * position et regard dans la dimension d'arrivee, sans que le gestionnaire d'entities ne le voie bouger,
 * pour que recul, orientation de pose et portee se calculent comme s'il etait sur place.
 *
 * <p>Vanilla n'envoie pas a l'auteur d'une action les sons et les effets qu'elle produit (bloc pose, bloc casse) : son
 * client les a deja joues en la predisant. A travers un cadre, rien n'est predit : ils lui parviennent quand meme
 * ({@link #sendWithheld}).
 */
public final class RemoteInteractionHandler {

    /** Marge ajoutee a la portee vanilla, comme le serveur en accorde a ses propres paquets d'interaction. */
    static final double REACH_MARGIN = 1.0;
    /** Au-dela de cette progression de charge, le coup est "fort" : meme seuil que le son choisi par vanilla. */
    private static final float STRONG_ATTACK_THRESHOLD = 0.9f;
    private static final Hand[] HANDS = Hand.values();
    private static boolean initialized;
    /** Joueur dont une action a travers un cadre s'execute en ce moment, ou {@code null}. */
    private static @Nullable ServerPlayerEntity actor;

    /** Portail valide devant le joueur, dimension d'arrivee et point d'ou son regard ressort. */
    record RemoteReach(BackroomsPortalEntity portal, ServerWorld destination, Vec3d transposedEye) {

        /**
         * Meme critere que {@code PlayerEntity.canInteractWithBlockAt}, depuis le point de sortie du regard. Le minage le
         * verifie a chaque tick : la distance a la boite du bloc se calcule sans la construire.
         */
        boolean canReachBlock(ServerPlayerEntity player, BlockPos pos) {
            double range = player.getBlockInteractionRange() + REACH_MARGIN;
            Vec3d eye = this.transposedEye;
            double dx = Math.max(0.0, Math.max(pos.getX() - eye.x, eye.x - (pos.getX() + 1.0)));
            double dy = Math.max(0.0, Math.max(pos.getY() - eye.y, eye.y - (pos.getY() + 1.0)));
            double dz = Math.max(0.0, Math.max(pos.getZ() - eye.z, eye.z - (pos.getZ() + 1.0)));
            return dx * dx + dy * dy + dz * dz < range * range;
        }

        /** Meme critere que {@code PlayerEntity.canInteractWithEntityIn}, depuis le point de sortie du regard. */
        boolean canReachEntity(ServerPlayerEntity player, Entity target) {
            double range = player.getEntityInteractionRange() + REACH_MARGIN;
            return target.getBoundingBox().squaredMagnitude(this.transposedEye) < range * range;
        }
    }

    private RemoteInteractionHandler() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Branche les recepteurs et le suivi du minage ; idempotent. */
    public static void init() {
        if (initialized) {
            return;
        }
        ServerPlayNetworking.registerGlobalReceiver(PortalEntityActionPayload.ID, (payload, context) -> onEntityAction(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PortalBlockActionPayload.ID, (payload, context) -> onBlockAction(context.player(), payload));
        RemoteMiningTracker.init();
        initialized = true;
    }

    /**
     * Envoi vanilla aux joueurs proches dont l'auteur d'une action est exclu ({@code PlayerManager.sendToAround}) : s'il
     * agit en ce moment a travers un cadre de son propre monde, l'envoi part aussi a lui, que son client n'a rien predit.
     * Vers une autre dimension, il n'est pas dans ce monde : le relais aux joueurs qui le voient par un portail le sert
     * ({@code PortalViewTracker.relayToViewers}). Appele par le mixin de {@code PlayerManager}.
     *
     * @param except joueur que vanilla exclut de l'envoi, ou {@code null}
     * @param worldKey dimension de l'envoi
     * @param packet son, evenement de monde ou particules
     */
    public static void sendWithheld(@Nullable PlayerEntity except, RegistryKey<World> worldKey, Packet<?> packet) {
        if (except != null && except == actor && except.getWorld().getRegistryKey() == worldKey) {
            actor.networkHandler.sendPacket(packet);
        }
    }

    /** Execute une action du joueur a travers un cadre : ce que vanilla lui retient lui parvient ({@link #sendWithheld}). */
    static void actThroughFrame(ServerPlayerEntity player, Runnable action) {
        actor = player;
        try {
            action.run();
        } finally {
            actor = null;
        }
    }

    /**
     * Valide un portail annonce par le client : dans le monde du joueur, visible, menant bien a la dimension
     * annoncee, devant le joueur, et dont le cadre lui-meme est a portee de bloc : pres du plan mais loin a cote du cadre,
     * on ne vise rien par lui.
     *
     * @return la portee distante, ou {@code null} si la demande ne tient pas
     */
    static @Nullable RemoteReach resolve(ServerPlayerEntity player, UUID portalUuid, Identifier dimension) {
        ServerWorld world = player.getServerWorld();
        BackroomsPortalEntity portal = BackroomsPortalEntityRegistry.find(world, portalUuid);
        // Un portail ferme ou invisible ne laisse rien passer.
        if (portal == null || !portal.visible() || !portal.teleportable() || !portal.destinationDim().getValue().equals(dimension)) {
            return null;
        }
        ServerWorld destination = world.getServer().getWorld(portal.destinationDim());
        if (destination == null) {
            return null;
        }
        Vec3d eye = player.getEyePos();
        double range = player.getBlockInteractionRange() + REACH_MARGIN;
        if (portal.signedDistance(eye) <= 0.0 || portal.squaredDistanceToFrame(eye.x, eye.y, eye.z) > range * range) {
            return null;
        }
        return new RemoteReach(portal, destination, portal.toDestination(eye));
    }

    private static void onEntityAction(ServerPlayerEntity player, PortalEntityActionPayload payload) {
        // Un spectateur n'agit sur rien a travers un portail : frapper lui ferait suivre la creature (setCameraEntity),
        // qui le teleporterait dans son monde pendant qu'il est transpose.
        if (player.isSpectator()) {
            return;
        }
        RemoteReach reach = resolve(player, payload.portal(), payload.dimension());
        if (reach == null) {
            return;
        }
        Entity target = reach.destination().getEntityById(payload.entityId());
        if (target == null || target == player || target instanceof BackroomsPortalEntity || !reach.canReachEntity(player, target)) {
            return;
        }
        if (payload.action() == PortalEntityActionPayload.Action.ATTACK) {
            attack(player, reach, target);
        } else {
            interact(player, reach, target);
        }
    }

    private static void attack(ServerPlayerEntity player, RemoteReach reach, Entity target) {
        if (!target.isAttackable()) {
            return;
        }
        float charge = player.getAttackCooldownProgress(0.5f);
        runTransposed(player, reach, () -> player.attack(target));
        // Les sons du coup partent dans le monde du joueur, a sa position transposee : il entend le sien ici.
        player.playSoundToPlayer(
                charge > STRONG_ATTACK_THRESHOLD ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /** Utilisation d'une creature (tonte, nourrissage, nommage) ; ni monture ni vehicule, qui emmeneraient le joueur. */
    private static void interact(ServerPlayerEntity player, RemoteReach reach, Entity target) {
        if (!(target instanceof LivingEntity) || target instanceof Saddleable) {
            return;
        }
        runTransposed(player, reach, () -> {
            for (Hand hand : HANDS) {
                ActionResult result = player.interact(target, hand);
                if (result.isAccepted()) {
                    swing(player, hand, result);
                    return;
                }
            }
        });
    }

    private static void onBlockAction(ServerPlayerEntity player, PortalBlockActionPayload payload) {
        // Un spectateur ne mine ni n'utilise rien, de ce cote comme de l'autre.
        if (player.isSpectator()) {
            return;
        }
        if (payload.action() == PortalBlockActionPayload.Action.ABORT_MINING) {
            RemoteMiningTracker.abort(player);
            return;
        }
        RemoteReach reach = resolve(player, payload.portal(), payload.dimension());
        BlockPos pos = payload.hit().getBlockPos();
        if (reach == null || !reach.canReachBlock(player, pos) || !reach.destination().canPlayerModifyAt(player, pos)) {
            return;
        }
        if (payload.action() == PortalBlockActionPayload.Action.START_MINING) {
            RemoteMiningTracker.start(player, reach, pos);
        } else {
            runTransposed(player, reach, () -> useBlock(player, reach.destination(), payload.hit()));
        }
    }

    /**
     * Reprise de l'interaction vanilla avec un bloc, main principale puis secondaire. Les blocs a block
     * entity ne repondent pas au clic : leurs ecrans se fermeraient aussitot, le joueur etant trop loin.
     */
    private static void useBlock(ServerPlayerEntity player, ServerWorld world, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        boolean holding = !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
        boolean itemOnly = player.shouldCancelInteraction() && holding;
        for (Hand hand : HANDS) {
            ItemStack stack = player.getStackInHand(hand);
            if (!itemOnly && !state.hasBlockEntity()) {
                ItemActionResult itemResult = state.onUseWithItem(stack, world, player, hand, hit);
                if (itemResult.isAccepted()) {
                    swing(player, hand, itemResult.toActionResult());
                    return;
                }
                if (itemResult == ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == Hand.MAIN_HAND) {
                    ActionResult blockResult = state.onUse(world, player, hit);
                    if (blockResult.isAccepted()) {
                        swing(player, hand, blockResult);
                        return;
                    }
                }
            }
            if (stack.isEmpty() || player.getItemCooldownManager().isCoolingDown(stack.getItem())) {
                continue;
            }
            ItemUsageContext context = new ItemUsageContext(world, player, hand, stack, hit);
            ActionResult result;
            if (player.isCreative()) {
                int count = stack.getCount();
                result = stack.useOnBlock(context);
                stack.setCount(count);
            } else {
                result = stack.useOnBlock(context);
            }
            if (result.isAccepted()) {
                swing(player, hand, result);
                return;
            }
        }
    }

    private static void swing(ServerPlayerEntity player, Hand hand, ActionResult result) {
        if (result.shouldSwingHand()) {
            player.swingHand(hand, true);
        }
    }

    /**
     * Execute une action avec le joueur place et oriente comme son regard ressort du portail, puis le remet
     * en place. Le gestionnaire d'entities ne voit pas ce deplacement : il sortirait le joueur de sa section,
     * voire de son suivi, si la position transposee tombe dans une zone non chargee de son propre monde.
     */
    private static void runTransposed(ServerPlayerEntity player, RemoteReach reach, Runnable action) {
        BackroomsPortalEntity portal = reach.portal();
        Vec3d position = player.getPos();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float headYaw = player.getHeadYaw();
        float bodyYaw = player.getBodyYaw();
        Vec2f look = PortalFacing.transformLook(portal.facingSource(), portal.facingDestination(), yaw, pitch);
        EntityChangeListener listener = ((EntityAccessor) player).backrooms$getChangeListener();
        player.setChangeListener(EntityChangeListener.NONE);
        try {
            player.setPosition(portal.toDestination(position));
            player.setYaw(look.x);
            player.setPitch(look.y);
            player.setHeadYaw(look.x);
            player.setBodyYaw(look.x);
            actThroughFrame(player, action);
        } finally {
            player.setPosition(position);
            player.setYaw(yaw);
            player.setPitch(pitch);
            player.setHeadYaw(headYaw);
            player.setBodyYaw(bodyYaw);
            player.setChangeListener(listener);
        }
    }
}
