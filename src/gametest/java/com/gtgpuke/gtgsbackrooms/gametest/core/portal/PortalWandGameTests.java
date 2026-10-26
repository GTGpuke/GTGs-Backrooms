package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SculkSensorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.SculkSensorPhase;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalWandSettingsPayload;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModItems;
import com.gtgpuke.gtgsbackrooms.core.registry.ModSounds;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestArrivals;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;

/**
 * Tests en jeu de la baguette de portail et du plan au bord du bloc : reglages recus du client poses sur la baguette tenue
 * (et seulement sur elle), pose d'un bloc portail regle sans user la baguette, avec son et evenement de pose, refus rendu
 * comme un echec, copie des reglages d'un portail vise, bloc casse qui ne rend rien ; plan au bord du
 * bloc sur la face arriere, cadre debout pose contre un mur, plan visable sur la face arriere, cadre couche a ras d'un sol
 * plein, arrivee a la surface a ras du sol.
 */
public final class PortalWandGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-6;
    private static final PortalSettings FLUSH = new PortalSettings(1, 2, true, true, false, false, PortalExit.STRAIGHT, 0, true);
    /** Ce que le test du capteur sculk a pose, retire apres son lot meme si son attente expire. */
    private static BlockPos[] sculkCleanup = new BlockPos[0];

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void theWandTakesTheSettingsChosenInItsScreen(TestContext context) {
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(4.5, 0.0, 4.5)), 0.0f, 0.0f);
        try {
            PortalSettings chosen = new PortalSettings(3, 4, false, false, true, false, PortalExit.FLOOR, 16, true);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PORTAL_WAND));
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.STICK));
            GameTestPlayers.receive(player, new PortalWandSettingsPayload(Hand.MAIN_HAND, chosen));
            context.assertTrue(PortalSettings.of(player.getMainHandStack()).equals(chosen), "La baguette tenue prend les reglages choisis.");
            GameTestPlayers.receive(player, new PortalWandSettingsPayload(Hand.OFF_HAND, PortalSettings.DEFAULT));
            context.assertTrue(player.getOffHandStack().isOf(Items.STICK) && !player.getOffHandStack().contains(ModDataComponents.PORTAL_SETTINGS),
                    "Une main sans baguette ne prend rien.");
            player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.PORTAL_WAND));
            GameTestPlayers.receive(player, new PortalWandSettingsPayload(Hand.OFF_HAND, chosen));
            context.assertTrue(PortalSettings.of(player.getOffHandStack()).equals(chosen), "La baguette de l'autre main aussi.");
        } finally {
            GameTestPlayers.disconnect(player);
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, batchId = "portal_wand_sculk")
    public void placingAPortalIsHeardBySculkSensors(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(4, 0, 4));
        BlockPos sensor = context.getAbsolutePos(new BlockPos(4, 1, 8));
        sculkCleanup = new BlockPos[] {support.up(), sensor, support};
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        world.setBlockState(sensor, Blocks.SCULK_SENSOR.getDefaultState());
        // Comme vanilla pose un bloc, la baguette emet l'evenement de pose : un capteur sculk a quatre blocs l'entend.
        context.assertTrue(SculkSensorBlock.getPhase(world.getBlockState(sensor)) == SculkSensorPhase.INACTIVE, "Le capteur attend, au calme.");
        context.assertTrue(ModItems.PORTAL_WAND.useOnBlock(new ItemPlacementContext(world, null, Hand.MAIN_HAND, new ItemStack(ModItems.PORTAL_WAND),
                new BlockHitResult(Vec3d.ofCenter(support).add(0.0, 0.5, 0.0), Direction.UP, support, false))).isAccepted(),
                "La baguette pose le portail.");
        context.createTimedTaskRunner()
                .createAndAdd(() -> {
                    if (SculkSensorBlock.getPhase(world.getBlockState(sensor)) == SculkSensorPhase.INACTIVE) {
                        throw new GameTestException("Le capteur sculk n'a pas encore entendu la pose du portail.");
                    }
                })
                .completeIfSuccessful();
    }

    /** Apres le lot du capteur, reussi ou non : le portail, le capteur et leur support sont retires. */
    @AfterBatch(batchId = "portal_wand_sculk")
    public void afterSculk(ServerWorld world) {
        for (BlockPos pos : sculkCleanup) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        }
        sculkCleanup = new BlockPos[0];
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void theWandPlacesAConfiguredPortalWithoutWearingOut(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(4, 0, 4));
        BlockPos anchor = support.up();
        BlockPos blockedSupport = context.getAbsolutePos(new BlockPos(10, 0, 4));
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        world.setBlockState(blockedSupport, Blocks.STONE.getDefaultState());
        world.setBlockState(blockedSupport.up(2), Blocks.STONE.getDefaultState());
        PortalSettings settings = new PortalSettings(2, 3, false, true, true, false, PortalExit.WEST, 8, false);
        // En survie, regard vers le nord : la face avant du portail regarde le joueur, au sud.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(4.5, 1.0, 7.5)), 180.0f, 0.0f);
        // Un temoin a cote entend le portail s'ouvrir ; celui qui pose l'entend chez lui (son client joue le clic).
        ServerPlayerEntity witness = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(7.5, 1.0, 7.5)), 180.0f, 0.0f);
        try {
            GameTestPlayers.sent(witness, PlaySoundS2CPacket.class);
            player.setStackInHand(Hand.MAIN_HAND, wand(settings));
            context.assertTrue(use(player, support).isAccepted(), "La baguette pose un bloc portail.");
            context.assertTrue(GameTestPlayers.sent(witness, PlaySoundS2CPacket.class).stream()
                    .anyMatch(packet -> packet.getSound().value() == ModSounds.PORTAL_WAND_PLACE), "Le temoin entend un portail s'ouvrir.");
            BlockState state = world.getBlockState(anchor);
            context.assertTrue(state.isOf(ModBlocks.BACKROOMS_PORTAL) && state.get(BackroomsPortalBlock.FACING) == Direction.SOUTH,
                    "Face avant vers le joueur.");
            context.assertTrue(world.getBlockEntity(anchor) instanceof BackroomsPortalBlockEntity placed && placed.settings().equals(settings),
                    "Le bloc prend les reglages de la baguette.");
            context.assertTrue(player.getMainHandStack().isOf(ModItems.PORTAL_WAND) && player.getMainHandStack().getCount() == 1
                    && PortalSettings.of(player.getMainHandStack()).equals(settings), "La baguette ne s'use pas, meme en survie.");
            // Une pierre dans le cadre : refus, sans rien poser ; un echec, pour que l'ecran de reglages ne s'ouvre pas.
            context.assertTrue(use(player, blockedSupport) == ActionResult.FAIL && world.getBlockState(blockedSupport.up()).isAir(),
                    "Un cadre qui traverserait un bloc est refuse, et la baguette le dit par un echec.");
            // La place visee est prise par un bloc plein (la face est d'un bloc, une pierre a cote) : un echec aussi.
            world.setBlockState(support.east(), Blocks.STONE.getDefaultState());
            context.assertTrue(use(player, support, Direction.EAST) == ActionResult.FAIL && world.getBlockState(support.east()).isOf(Blocks.STONE),
                    "Une place prise par un bloc plein est refusee.");
            world.setBlockState(support.east(), Blocks.AIR.getDefaultState());
            // Hors du monde (au-dessus de sa hauteur), rien ne se pose : un echec aussi. Le bloc vise est hors du monde, vide et
            // remplacable : vanilla pose a sa place, ou il ne peut rien ecrire.
            BlockPos top = new BlockPos(anchor.getX(), world.getTopY(), anchor.getZ());
            context.assertTrue(ModItems.PORTAL_WAND.useOnBlock(new ItemPlacementContext(world, null, Hand.MAIN_HAND, new ItemStack(ModItems.PORTAL_WAND),
                    new BlockHitResult(Vec3d.ofCenter(top).add(0.0, 0.5, 0.0), Direction.UP, top, false))) == ActionResult.FAIL,
                    "Au-dessus de la hauteur du monde, la pose est refusee.");

            // Accroupi sur un portail, la baguette prend ses reglages et le joueur en est prevenu ; sans joueur, sans message.
            GameTestPlayers.sent(player, GameMessageS2CPacket.class);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PORTAL_WAND));
            context.assertTrue(use(player, anchor, Direction.SOUTH).isAccepted() && PortalSettings.of(player.getMainHandStack()).equals(settings)
                    && world.getBlockState(anchor.south()).isAir(), "La baguette copie les reglages du portail vise, sans rien poser.");
            context.assertTrue(GameTestPlayers.sent(player, GameMessageS2CPacket.class).stream().anyMatch(GameMessageS2CPacket::overlay),
                    "Le joueur voit, au-dessus de sa barre d'objets, que les reglages sont copies.");
            // Comme vanilla, un clic sur un bloc donne le bloc vise (un contexte de pose donnerait la place devant lui).
            ItemStack unattended = new ItemStack(ModItems.PORTAL_WAND);
            ItemUsageContext unattendedClick = new ItemUsageContext(world, null, Hand.MAIN_HAND, unattended,
                    new BlockHitResult(Vec3d.ofCenter(anchor), Direction.SOUTH, anchor, false)) { };
            context.assertTrue(ModItems.PORTAL_WAND.useOnBlock(unattendedClick).isAccepted()
                    && PortalSettings.of(unattended).equals(settings), "Sans joueur, la copie se fait aussi.");

            // Casse, le bloc portail ne rend rien : on en cree un autre avec la baguette.
            world.breakBlock(anchor, true, player);
            context.assertTrue(world.getBlockState(anchor).isAir()
                    && world.getEntitiesByClass(ItemEntity.class, new Box(anchor).expand(3.0), entity -> true).isEmpty(),
                    "Le bloc portail casse ne rend aucun objet.");
        } finally {
            GameTestPlayers.disconnect(player);
            GameTestPlayers.disconnect(witness);
            world.setBlockState(anchor, Blocks.AIR.getDefaultState());
            world.setBlockState(support, Blocks.AIR.getDefaultState());
            world.setBlockState(blockedSupport, Blocks.AIR.getDefaultState());
            world.setBlockState(blockedSupport.up(2), Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void aPlaneOnTheBlockEdgeSitsOnTheBackFaceAgainstAWall(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(4, 0, 4));
        BlockPos anchor = support.up();
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        // Le portail regarde le sud : sa face arriere est au nord, contre le bloc d'en face.
        world.setBlockState(anchor.north(), Blocks.STONE.getDefaultState());
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(4.5, 1.0, 7.5)), 180.0f, 0.0f);
        BlockPos pillar = context.getAbsolutePos(new BlockPos(10, 3, 4));
        BlockPos lying = pillar.west();
        try {
            player.setStackInHand(Hand.MAIN_HAND, wand(FLUSH));
            // Le mur, derriere le plan, est cache a qui s'engage dans le cadre : il ne gene pas la pose.
            context.assertTrue(use(player, support).isAccepted(), "Au bord du bloc, un cadre debout se pose contre un mur.");
            BackroomsPortalBlockEntity standing = (BackroomsPortalBlockEntity) world.getBlockEntity(anchor);
            Vec3d center = standing.entryCenter();
            context.assertTrue(Math.abs(center.z - anchor.getZ()) < EPSILON && Math.abs(center.x - (anchor.getX() + 0.5)) < EPSILON,
                    "Le plan passe sur la face arriere du bloc, et non en son milieu : " + center + ".");
            BlockState state = world.getBlockState(anchor);
            player.setSneaking(true);
            VoxelShape outline = state.getOutlineShape(world, anchor, ShapeContext.of(player));
            context.assertTrue(outline.getMin(Direction.Axis.Z) == 0.0 && outline.getMax(Direction.Axis.Z) == 0.125 && outline.getMax(Direction.Axis.X) == 1.0,
                    "Le plan visable suit : contre la face arriere.");
            // Lu la ou n'est aucune block entity (un etat de bloc consulte ailleurs), le plan visable reste au milieu du bloc.
            VoxelShape elsewhere = state.getOutlineShape(world, anchor.east(3), ShapeContext.of(player));
            context.assertTrue(elsewhere.getMin(Direction.Axis.Z) == 0.4375 && elsewhere.getMax(Direction.Axis.Z) == 0.5625,
                    "Sans ses reglages, le plan visable est celui du milieu du bloc.");
            player.setSneaking(false);

            // Couche au sol (regard vers le bas, pilier clique par le cote) : le plan a ras du bas du bloc, sur un sol plein.
            world.setBlockState(pillar, Blocks.STONE.getDefaultState());
            world.setBlockState(lying.down(), Blocks.STONE.getDefaultState());
            player.setPitch(80.0f);
            context.assertTrue(use(player, pillar, Direction.WEST).isAccepted() && world.getBlockState(lying).get(BackroomsPortalBlock.FACING) == Direction.UP,
                    "Regard vers le bas : le cadre se couche.");
            BackroomsPortalBlockEntity floor = (BackroomsPortalBlockEntity) world.getBlockEntity(lying);
            context.assertTrue(Math.abs(floor.entryCenter().y - lying.getY()) < EPSILON, "Couche au sol, le plan est le bas du bloc : " + floor.entryCenter() + ".");
            player.setSneaking(true);
            VoxelShape flat = world.getBlockState(lying).getOutlineShape(world, lying, ShapeContext.of(player));
            context.assertTrue(flat.getMin(Direction.Axis.Y) == 0.0 && flat.getMax(Direction.Axis.Y) == 0.125, "Le plan visable est au ras du bas du bloc.");
            player.setSneaking(false);

            // A la surface, l'arrivee suit le meme plan : un cadre debout recule d'un demi-bloc, un cadre au sol est a ras du sol.
            PortalTargets.Target middle = GameTestArrivals.resolve(world.getServer(), BackroomsConstants.LEVEL_ZERO_WORLD, anchor, PortalFacing.SOUTH,
                    PortalSettings.DEFAULT, 0L);
            PortalTargets.Target edge = GameTestArrivals.resolve(world.getServer(), BackroomsConstants.LEVEL_ZERO_WORLD, anchor, PortalFacing.SOUTH,
                    FLUSH, 0L);
            context.assertTrue(middle != null && edge != null && edge.dimension().equals(World.OVERWORLD) && edge.facing() == PortalFacing.NORTH
                    && Math.abs(edge.position().z - (middle.position().z + 0.5)) < EPSILON && Math.abs(edge.position().y - middle.position().y) < EPSILON,
                    "Arrivee debout au bord du bloc : un demi-bloc derriere le milieu, du cote oppose a sa face avant.");
            PortalTargets.Target ground = GameTestArrivals.resolve(world.getServer(), BackroomsConstants.LEVEL_ZERO_WORLD, anchor, PortalFacing.SOUTH,
                    new PortalSettings(1, 2, true, true, false, false, PortalExit.FLOOR, 0, true), 0L);
            context.assertTrue(ground != null && ground.facing() == PortalFacing.UP && Math.abs(ground.position().y - (middle.position().y - 1.0)) < EPSILON,
                    "Arrivee au sol au bord du bloc : le plan est le sol lui-meme.");
        } finally {
            GameTestPlayers.disconnect(player);
            world.setBlockState(anchor, Blocks.AIR.getDefaultState());
            world.setBlockState(anchor.north(), Blocks.AIR.getDefaultState());
            world.setBlockState(support, Blocks.AIR.getDefaultState());
            world.setBlockState(lying, Blocks.AIR.getDefaultState());
            world.setBlockState(lying.down(), Blocks.AIR.getDefaultState());
            world.setBlockState(pillar, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    /** Le joueur utilise la baguette tenue sur le dessus d'un bloc. */
    private static ActionResult use(ServerPlayerEntity player, BlockPos support) {
        return use(player, support, Direction.UP);
    }

    /** Le joueur utilise la baguette tenue sur la face donnee d'un bloc, comme un clic droit. */
    private static ActionResult use(ServerPlayerEntity player, BlockPos support, Direction side) {
        Vec3d hit = Vec3d.ofCenter(support).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return ModItems.PORTAL_WAND.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(hit, side, support, false)));
    }

    /** Baguette de portail portant ces reglages. */
    private static ItemStack wand(PortalSettings settings) {
        ItemStack wand = new ItemStack(ModItems.PORTAL_WAND);
        wand.set(ModDataComponents.PORTAL_SETTINGS, settings);
        return wand;
    }
}
