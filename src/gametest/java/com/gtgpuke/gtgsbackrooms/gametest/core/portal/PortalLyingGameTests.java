package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalTeleportPayload;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;

/**
 * Tests en jeu des cadres couches, ou la traversee se decide aux yeux : un corps tombe dans un cadre au sol et ressort d'un
 * cadre au plafond (le corps a deja passe le plan), on monte dans un cadre au plafond, la tete la premiere, et on ressort
 * d'un cadre au sol, le serveur rattrape un joueur qui tombe dans un cadre au sol. Les deux cadres sont dans le monde du test, un
 * bloc sur deux, couches (largeur le long de X, hauteur le long de Z), et tout reste sous le plafond de l'arene (huit
 * blocs) : une entity posee dessus n'en tombe pas.
 */
public final class PortalLyingGameTests implements FabricGameTest {

    private static final Vec3d FLOOR_FRAME = new Vec3d(4.5, 3.5, 4.0);
    private static final Vec3d CEILING_FRAME = new Vec3d(14.5, 3.5, 4.0);
    /** Pas d'un joueur simule qui tombe, par tick. */
    private static final double FALL_STEP = 0.4;

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aFallingBodyGoesThroughAFloorPortalAndDropsOutOfTheCeiling(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity floor = lying(world, context.getAbsolute(FLOOR_FRAME), PortalFacing.UP, context.getAbsolute(CEILING_FRAME));
        // Un porte-armure : un corps vivant qui tombe, sans comportement qui le ferait deriver hors d'un cadre d'un bloc de large
        // (un mob dont le comportement est coupe, lui, n'a plus de gravite).
        ArmorStandEntity body = EntityType.ARMOR_STAND.create(world);
        body.refreshPositionAndAngles(floor.getX(), floor.getY() + 2.0, floor.getZ(), 0.0f, 0.0f);
        world.spawnEntity(body);
        context.runAtTick(50, () -> {
            try {
                Vec3d exit = context.getAbsolute(CEILING_FRAME);
                context.assertTrue(!body.isRemoved() && body.getWorld() == world, "Dans le meme monde, le corps reste la meme entity.");
                context.assertTrue(Math.abs(body.getX() - exit.x) < 0.1 && Math.abs(body.getZ() - exit.z) < 0.1,
                        "Tombe dans le cadre au sol, le corps ressort sous le cadre au plafond, a l'aplomb : " + body.getPos() + ".");
                context.assertTrue(body.getY() < exit.y - 1.0 && body.isOnGround(), "Et retombe au sol sous lui : " + body.getPos() + ".");
            } finally {
                body.discard();
                floor.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aRisingItemEntersACeilingPortalAndComesOutOfTheFloor(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity ceiling = lying(world, context.getAbsolute(FLOOR_FRAME), PortalFacing.DOWN, context.getAbsolute(CEILING_FRAME));
        ItemEntity item = new ItemEntity(world, ceiling.getX(), ceiling.getY() - 2.5, ceiling.getZ(), new ItemStack(Items.FEATHER));
        item.setNoGravity(true);
        item.setVelocity(0.0, 0.3, 0.0);
        world.spawnEntity(item);
        context.runAtTick(12, () -> {
            try {
                Vec3d exit = context.getAbsolute(CEILING_FRAME);
                context.assertTrue(!item.isRemoved() && Math.abs(item.getX() - exit.x) < 0.5 && Math.abs(item.getZ() - exit.z) < 0.5,
                        "Montee dans le cadre au plafond, la plume ressort du cadre au sol : " + item.getPos() + ".");
                context.assertTrue(item.getY() > exit.y && item.getVelocity().y > 0.0, "Au-dessus de lui, et elle continue de monter : " + item.getPos() + ".");
                context.assertTrue(item.hasPortalCooldown(), "Le cooldown de portail vanilla est arme.");
            } finally {
                item.discard();
                ceiling.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aPlayerFallingThroughAFloorPortalIsCaughtByTheServer(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d entry = context.getAbsolute(FLOOR_FRAME);
        BackroomsPortalEntity floor = lying(world, entry, PortalFacing.UP, context.getAbsolute(CEILING_FRAME));
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, entry.add(0.0, 2.0, 0.0), 0.0f, 90.0f);
        // Le joueur tombe de quatre dixiemes par tick, comme le dirait son client, et s'arrete deux blocs et demi sous le plan ;
        // le serveur voit ses yeux passer le plan et, faute d'annonce, le fait passer lui-meme cinq ticks plus tard, arrete.
        for (int tick = 1; tick <= 20; tick++) {
            context.runAtTick(tick, () -> {
                if (player.getServerWorld() == world && player.getX() < entry.x + 5.0 && player.getY() > entry.y - 2.5) {
                    player.refreshPositionAndAngles(player.getX(), player.getY() - FALL_STEP, player.getZ(), 0.0f, 90.0f);
                }
            });
        }
        context.runAtTick(30, () -> {
            try {
                Vec3d exit = context.getAbsolute(CEILING_FRAME);
                context.assertTrue(Math.abs(player.getX() - exit.x) < 0.1 && Math.abs(player.getZ() - exit.z) < 0.1 && player.getY() < exit.y - 1.0,
                        "Le serveur a fait passer le joueur sous le cadre au plafond : " + player.getPos() + ".");
                context.assertTrue(!GameTestPlayers.sent(player, PortalTeleportPayload.class).isEmpty(), "Son client a recu la bascule.");
            } finally {
                GameTestPlayers.disconnect(player);
                floor.discard();
            }
            context.complete();
        });
    }

    /** Cadre couche d'un bloc sur deux, en {@code center}, face au haut (au sol) ou au bas (au plafond), vers un cadre couche a l'oppose. */
    private static BackroomsPortalEntity lying(ServerWorld world, Vec3d center, PortalFacing facing, Vec3d destination) {
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(facing)
                .facingDestination(facing.opposite())
                .destinationDim(World.OVERWORLD)
                .destinationPos(destination)
                .size(1.0f, 2.0f)
                .build();
        world.spawnEntity(portal);
        return portal;
    }
}
