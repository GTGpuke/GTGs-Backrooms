package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalArea;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModItems;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;

/**
 * Tests en jeu des portails d'une orientation a une autre, les 144 paires : debout, au sol, au plafond, dans tous les
 * sens, droits ou retournes (tete en bas). Un joueur qui traverse chaque paire file hors du cadre d'arrivee, regarde ou il
 * va, garde sa chute seulement si le bas reste le bas, et ressort du bon cote, dans l'ouverture ; un objet traverse seul
 * chacune des six normales, un cadre sur deux retourne ; une longue chute renvoyee a l'horizontale ne blesse pas a
 * l'atterrissage, une chute qui continue si ; un portail couche n'a que sa face avant ; un bloc a l'arrivee tete en bas
 * retourne les portails de son cadre d'arrivee, et on en ressort tete en bas.
 */
public final class PortalOrientationGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-3;
    private static final Vec3d ENTRY = new Vec3d(6.5, 4.0, 6.5);
    private static final Vec3d ARRIVAL = new Vec3d(16.5, 4.0, 16.5);
    /** Chute du joueur au moment de traverser, en blocs. */
    private static final float FALL = 12.0f;
    private static final double SPEED = 0.5;
    /** Vitesse des objets qui traversent seuls, en blocs par tick. */
    private static final double ITEM_SPEED = 0.3;
    /** Ticks laisses a un joueur tout juste connecte pour perdre son invulnerabilite d'arrivee (soixante en vanilla). */
    private static final int JOIN_INVULNERABILITY_TICKS = 61;
    /** Une chute de vingt blocs : dix-sept points de degats, sous les vingt points de vie d'un joueur. */
    private static final float LONG_FALL = 20.0f;
    private static final float FULL_HEALTH = 20.0f;

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void everyPairOfOrientationsCarriesAPlayerOutOfTheArrivalFrame(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d entry = context.getAbsolute(ENTRY);
        Vec3d arrival = context.getAbsolute(ARRIVAL);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, entry, 0.0f, 0.0f);
        try {
            for (PortalFacing source : PortalFacing.values()) {
                for (PortalFacing exit : PortalFacing.values()) {
                    crossAndCheck(context, player, frame(world, entry, source, exit, arrival), arrival);
                }
            }
        } finally {
            GameTestPlayers.disconnect(player);
        }
        context.complete();
    }

    /**
     * Le joueur vient de faire passer le plan au point qui decide (ses pieds, ses yeux pour un cadre couche), au centre du
     * cadre, debout les pieds au bas du cadre ; il file a travers, regarde ou il va et a une chute en cours.
     */
    private static void crossAndCheck(TestContext context, ServerPlayerEntity player, BackroomsPortalEntity portal, Vec3d arrival) {
        PortalFacing source = portal.facingSource();
        PortalFacing exit = portal.facingDestination();
        String pair = source + " vers " + exit;
        Vec3d motion = source.normal.multiply(-SPEED);
        Vec3d decision = portal.getPos().subtract(source.normal.multiply(0.05));
        double feetY = source.normal.y == 0.0 ? portal.getY() - 1.0 : decision.y - portal.crossingHeight(player);
        float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
        float pitch = (float) Math.toDegrees(-Math.asin(motion.y / SPEED));
        player.refreshPositionAndAngles(decision.x, feetY, decision.z, yaw, pitch);
        player.setVelocity(motion);
        player.fallDistance = FALL;
        context.assertTrue(TeleportExecutor.execute(player, portal), pair + " : la traversee a lieu.");
        Vec3d push = player.getVelocity().subtract(exit.normal.multiply(SPEED));
        if (source.normal.y == 0.0 && exit.normal.y > 0.0) {
            // D'un cadre debout a un cadre au sol, on est pousse dans le sens de son regard pour retomber devant le cadre ; a
            // cette vitesse, sans saut ajoute (decision de l'auteur du 31 octobre 2026).
            double heading = Math.toRadians(player.getYaw());
            context.assertTrue(Math.abs(push.y) < EPSILON && push.length() > EPSILON && push.length() <= TeleportExecutor.MAX_EDGE_PUSH + EPSILON
                    && (-Math.sin(heading) * push.x + Math.cos(heading) * push.z) / push.length() > 1.0 - EPSILON,
                    pair + " : on file hors du cadre d'arrivee, pousse dans le sens du regard : " + player.getVelocity() + ".");
        } else {
            context.assertTrue(push.length() < EPSILON, pair + " : on file hors du cadre d'arrivee : " + player.getVelocity() + ".");
        }
        context.assertTrue(player.getRotationVector().dotProduct(exit.normal) > 0.999, pair + " : on regarde ou l'on va.");
        // Debout vers debout, le bas reste le bas sauf si un seul des deux cadres est retourne ; couche vers couche oppose, la
        // chute continue le long de la normale, retourne ou non.
        boolean keepsFalling = source.normal.y == 0.0 && exit.normal.y == 0.0 && source.upsideDown() == exit.upsideDown()
                || source.normal.y * exit.normal.y < 0.0;
        context.assertTrue(player.fallDistance == (keepsFalling ? FALL : 0.0f),
                pair + " : chute gardee seulement si le bas reste le bas : " + player.fallDistance + ".");
        double feet = player.getY();
        double top = feet + player.getHeight();
        if (exit.normal.y < 0.0) {
            context.assertTrue(top <= arrival.y, pair + " : tout le corps pend sous le plafond : " + player.getPos() + ".");
        } else if (exit.normal.y > 0.0) {
            context.assertTrue(feet >= arrival.y, pair + " : tout le corps est au-dessus du sol : " + player.getPos() + ".");
        } else {
            context.assertTrue(player.getPos().subtract(arrival).dotProduct(exit.normal) > 0.0 && feet >= arrival.y - 1.0 - EPSILON
                    && top <= arrival.y + 1.0 + EPSILON, pair + " : debout devant le cadre, dans sa hauteur : " + player.getPos() + ".");
        }
        context.assertTrue(portal.withinArrivalOpening(false, player.getX(), player.getY(), player.getZ()),
                pair + " : en face de l'ouverture d'arrivee : " + player.getPos() + ".");
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void anObjectCrossesEveryOrientationOnItsOwn(TestContext context) {
        ServerWorld world = context.getWorld();
        // Les six normales, en entree comme en sortie, un cadre sur deux retourne (le retour d'une arrivee tete en bas l'est).
        PortalFacing[] entries = {PortalFacing.NORTH, PortalFacing.SOUTH_UPSIDE_DOWN, PortalFacing.EAST, PortalFacing.WEST_UPSIDE_DOWN,
            PortalFacing.UP, PortalFacing.DOWN_UPSIDE_DOWN};
        PortalFacing[] exits = {PortalFacing.UP_UPSIDE_DOWN, PortalFacing.DOWN, PortalFacing.WEST_UPSIDE_DOWN, PortalFacing.NORTH,
            PortalFacing.EAST_UPSIDE_DOWN, PortalFacing.SOUTH};
        BackroomsPortalEntity[] portals = new BackroomsPortalEntity[entries.length];
        ItemEntity[] items = new ItemEntity[entries.length];
        for (int i = 0; i < entries.length; i++) {
            Vec3d entry = context.getAbsolute(new Vec3d(2.5 + 3.5 * i, 4.0, 4.5));
            portals[i] = frame(world, entry, entries[i], exits[i], context.getAbsolute(new Vec3d(2.5 + 3.5 * i, 4.0, 16.5)));
            world.spawnEntity(portals[i]);
            // L'objet part a six dixiemes du plan, son point de traversee au centre du cadre, et file droit dedans.
            Vec3d start = entry.add(entries[i].normal.multiply(0.6));
            items[i] = new ItemEntity(world, start.x, 0.0, start.z, new ItemStack(Items.FEATHER));
            double startY = entries[i].normal.y == 0.0 ? entry.y - 0.5 : start.y - portals[i].crossingHeight(items[i]);
            items[i].setPosition(start.x, startY, start.z);
            items[i].setNoGravity(true);
            items[i].setPickupDelayInfinite();
            items[i].setVelocity(entries[i].normal.multiply(-ITEM_SPEED));
            world.spawnEntity(items[i]);
        }
        context.runAtTick(12, () -> {
            try {
                for (int i = 0; i < entries.length; i++) {
                    BackroomsPortalEntity portal = portals[i];
                    ItemEntity item = items[i];
                    String pair = entries[i] + " vers " + exits[i];
                    Vec3d ahead = item.getPos().subtract(portal.destinationPos());
                    context.assertTrue(item.hasPortalCooldown() && ahead.dotProduct(exits[i].normal) > 0.0,
                            pair + " : l'objet a traverse seul et file devant l'arrivee : " + item.getPos() + ".");
                    // L'air freine un objet de deux pour cent par tick : seule la direction se compare.
                    context.assertTrue(item.getVelocity().normalize().dotProduct(exits[i].normal) > 0.999,
                            pair + " : sa vitesse est transposee : " + item.getVelocity() + ".");
                    context.assertTrue(portal.withinArrivalOpening(false, item.getX(), item.getY(), item.getZ()), pair + " : en face de l'ouverture d'arrivee.");
                }
            } finally {
                for (int i = 0; i < entries.length; i++) {
                    items[i].discard();
                    portals[i].discard();
                }
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 200)
    public void aLongFallTurnedSidewaysDoesNotHurtButAFallThatGoesOnDoes(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d entry = context.getAbsolute(ENTRY);
        Vec3d arrival = context.getAbsolute(ARRIVAL);
        BackroomsPortalEntity sideways = frame(world, entry, PortalFacing.UP, PortalFacing.NORTH, arrival);
        BackroomsPortalEntity onwards = frame(world, entry, PortalFacing.UP, PortalFacing.DOWN, arrival.add(-6.0, 0.0, 0.0));
        ServerPlayerEntity turned = GameTestPlayers.connect(context, GameMode.SURVIVAL, entry, 0.0f, 0.0f);
        ServerPlayerEntity falling = GameTestPlayers.connect(context, GameMode.SURVIVAL, entry, 0.0f, 0.0f);
        int[] ticks = {0};
        context.createTimedTaskRunner()
                // Un joueur tout juste connecte ne prend aucun degat pendant soixante ticks : on les laisse passer.
                .createAndAdd(() -> {
                    GameTestPlayers.tick(turned);
                    GameTestPlayers.tick(falling);
                    context.assertTrue(++ticks[0] >= JOIN_INVULNERABILITY_TICKS, "Les joueurs sont encore invulnerables.");
                })
                .createAndAddReported(() -> {
                    try {
                        land(context, turned, sideways);
                        land(context, falling, onwards);
                        context.assertTrue(turned.getHealth() == FULL_HEALTH,
                                "Tombe de vingt blocs dans un sol, sorti d'un mur a l'horizontale : aucun degat a l'atterrissage (" + turned.getHealth() + ").");
                        context.assertTrue(falling.getHealth() < FULL_HEALTH && falling.isAlive(),
                                "Tombe de vingt blocs dans un sol, sorti d'un plafond : il tombe encore, et l'atterrissage blesse (" + falling.getHealth() + ").");
                    } finally {
                        GameTestPlayers.disconnect(turned);
                        GameTestPlayers.disconnect(falling);
                    }
                })
                .completeIfSuccessful();
    }

    /** Le joueur, en pleine chute de vingt blocs, passe les yeux par le cadre au sol, puis touche terre de l'autre cote. */
    private static void land(TestContext context, ServerPlayerEntity player, BackroomsPortalEntity portal) {
        Vec3d decision = portal.getPos().subtract(0.0, 0.05, 0.0);
        player.refreshPositionAndAngles(decision.x, decision.y - portal.crossingHeight(player), decision.z, 0.0f, 90.0f);
        player.setVelocity(0.0, -1.5, 0.0);
        player.fallDistance = LONG_FALL;
        context.assertTrue(TeleportExecutor.execute(player, portal), "La traversee a lieu.");
        player.handleFall(0.0, -0.1, 0.0, true);
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void aLyingFrameAtTheEntryOrTheArrivalHasOnlyItsFrontFace(TestContext context) {
        ServerWorld world = context.getWorld();
        int front = BackroomsPortalBlockEntity.FRONT;
        int back = BackroomsPortalBlockEntity.BACK;
        BlockPos floorPos = new BlockPos(3, 4, 3);
        BlockPos wallToFloorPos = new BlockPos(9, 1, 3);
        BlockPos wallPos = new BlockPos(15, 1, 3);
        // Un bloc couche au sol, vers un plafond ; un bloc debout, vers un sol ; un bloc debout, vers un mur. Deux faces reglees.
        BackroomsPortalBlockEntity floor = place(context, floorPos, Direction.UP);
        BackroomsPortalBlockEntity wallToFloor = place(context, wallToFloorPos, Direction.NORTH);
        BackroomsPortalBlockEntity wall = place(context, wallPos, Direction.NORTH);
        floor.configureTarget(World.OVERWORLD, context.getAbsolute(new Vec3d(3.5, 6.5, 15.0)), PortalFacing.DOWN);
        wallToFloor.configureTarget(World.OVERWORLD, context.getAbsolute(new Vec3d(9.5, 1.5, 15.0)), PortalFacing.UP);
        wall.configureTarget(World.OVERWORLD, PortalArea.center(context.getAbsolutePos(new BlockPos(15, 1, 15)), PortalFacing.SOUTH,
                PortalSettings.DEFAULT), PortalFacing.SOUTH);
        try {
            context.assertTrue(floor.entryPortal(front) != null && floor.returnPortal(front) != null
                    && floor.entryPortal(back) == null && floor.returnPortal(back) == null,
                    "Couche au sol : la face avant seule, sans retour arriere (de dos, on viendrait du sol).");
            context.assertTrue(wallToFloor.entryPortal(front) != null && wallToFloor.returnPortal(front) != null
                    && wallToFloor.entryPortal(back) == null && wallToFloor.returnPortal(back) == null,
                    "Debout vers un sol : la face avant seule (la face arriere ressortirait sous le sol).");
            context.assertTrue(wall.entryPortal(back) != null && wall.returnPortal(back) != null,
                    "Debout vers un mur : les deux faces, comme reglees.");
        } finally {
            context.setBlockState(floorPos, Blocks.AIR);
            context.setBlockState(wallToFloorPos, Blocks.AIR);
            context.setBlockState(wallPos, Blocks.AIR);
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 40)
    public void anUpsideDownArrivalTurnsItsFrameOverAndTheTravellerWithIt(TestContext context) {
        ServerWorld world = context.getWorld();
        int front = BackroomsPortalBlockEntity.FRONT;
        int back = BackroomsPortalBlockEntity.BACK;
        BlockPos anchor = new BlockPos(3, 1, 3);
        BackroomsPortalBlockEntity block = place(context, anchor, Direction.NORTH,
                new PortalSettings(1, 2, true, true, false, false, PortalExit.STRAIGHT, 0, false, true));
        Vec3d arrival = PortalArea.center(context.getAbsolutePos(new BlockPos(3, 1, 15)), PortalFacing.SOUTH, PortalSettings.DEFAULT);
        block.configureTarget(World.OVERWORLD, arrival, PortalFacing.SOUTH);
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(ENTRY), 0.0f, 0.0f);
        try {
            BackroomsPortalEntity entry = (BackroomsPortalEntity) world.getEntity(block.entryPortal(front));
            BackroomsPortalEntity entryBack = (BackroomsPortalEntity) world.getEntity(block.entryPortal(back));
            BackroomsPortalEntity returnFront = (BackroomsPortalEntity) world.getEntity(block.returnPortal(front));
            BackroomsPortalEntity returnBack = (BackroomsPortalEntity) world.getEntity(block.returnPortal(back));
            context.assertTrue(entry.facingDestination() == PortalFacing.SOUTH_UPSIDE_DOWN && entryBack.facingDestination() == PortalFacing.NORTH_UPSIDE_DOWN,
                    "Chaque face mene au cadre d'arrivee retourne.");
            context.assertTrue(returnFront.facingSource() == PortalFacing.SOUTH_UPSIDE_DOWN && returnBack.facingSource() == PortalFacing.NORTH_UPSIDE_DOWN,
                    "Les retours sont poses retournes : on revient tete en bas aussi.");
            context.assertTrue(entry.destinationPos().squaredDistanceTo(arrival) < EPSILON && returnFront.getPos().squaredDistanceTo(arrival) < EPSILON,
                    "Le cadre d'arrivee ne bouge pas : son centre est celui de la pose droite.");
            // Le joueur passe le plan au pied du cadre, en regardant devant lui et vers le bas.
            Vec3d feet = entry.getPos().add(0.0, -1.0, 0.0).subtract(entry.facingSource().normal.multiply(0.05));
            player.refreshPositionAndAngles(feet.x, feet.y, feet.z, 0.0f, 30.0f);
            player.setVelocity(entry.facingSource().normal.multiply(-SPEED));
            context.assertTrue(TeleportExecutor.execute(player, entry), "La traversee a lieu.");
            context.assertTrue(Math.abs(player.getPitch() + 30.0f) < EPSILON && player.getRotationVector().z > 0.0,
                    "Tete en bas, ce qu'il regardait en bas est en haut, et il regarde toujours devant lui : " + player.getPitch() + ".");
            context.assertTrue(player.getY() >= arrival.y - 1.0 - EPSILON && player.getY() + player.getHeight() <= arrival.y + 1.0 + EPSILON
                    && player.getZ() > arrival.z, "Il ressort debout dans l'ouverture, devant le cadre : " + player.getPos() + ".");
        } finally {
            GameTestPlayers.disconnect(player);
            context.setBlockState(anchor, Blocks.AIR);
        }
        context.complete();
    }

    private static BackroomsPortalBlockEntity place(TestContext context, BlockPos pos, Direction front) {
        return place(context, pos, front, PortalSettings.DEFAULT);
    }

    private static BackroomsPortalBlockEntity place(TestContext context, BlockPos pos, Direction front, PortalSettings settings) {
        context.setBlockState(pos, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, front));
        BackroomsPortalBlockEntity block = context.getBlockEntity(pos);
        block.readComponents(wand(settings));
        return block;
    }

    /** Portail d'un bloc sur deux, pas encore dans le monde, d'une orientation a une autre, dans le monde du test. */
    private static BackroomsPortalEntity frame(ServerWorld world, Vec3d center, PortalFacing source, PortalFacing exit, Vec3d arrival) {
        return BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(source)
                .facingDestination(exit)
                .destinationDim(World.OVERWORLD)
                .destinationPos(arrival)
                .size(1.0f, 2.0f)
                .build();
    }

    /** Baguette de portail portant ces reglages. */
    private static ItemStack wand(PortalSettings settings) {
        ItemStack wand = new ItemStack(ModItems.PORTAL_WAND);
        wand.set(ModDataComponents.PORTAL_SETTINGS, settings);
        return wand;
    }
}
