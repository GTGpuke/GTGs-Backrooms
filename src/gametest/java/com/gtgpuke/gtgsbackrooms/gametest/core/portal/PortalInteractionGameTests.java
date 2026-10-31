package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.border.WorldBorder;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalBlockActionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalEntityActionPayload;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteBlockBreakingPayload;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu de l'interaction a travers un portail : coups, utilisation d'entities, clics et minage sur les
 * blocs vus de l'autre cote, avec les refus attendus (hors portee, derriere le portail, portail inconnu).
 *
 * <p>Le portail regarde le sud en (1, 2, 1) ; le joueur se tient trois blocs au sud et regarde le nord. Sa
 * sortie regarde l'est en (6, 2, 6) : les cibles sont a l'est de la sortie, a portee du regard transpose.
 */
public final class PortalInteractionGameTests implements FabricGameTest {

    private static final BlockPos ENTRY = new BlockPos(1, 2, 1);
    private static final BlockPos EXIT = new BlockPos(6, 2, 6);
    private static final Vec3d PLAYER = new Vec3d(1.5, 1.0, 4.5);
    /** Bloc cible : a l'est de la sortie, a quatre blocs du regard transpose. */
    private static final BlockPos TARGET_BLOCK = new BlockPos(8, 1, 6);
    private static final Identifier OVERWORLD_ID = World.OVERWORLD.getValue();
    private static final BlockState FLOOR_LEVER = Blocks.LEVER.getDefaultState().with(WallMountedBlock.FACE, BlockFace.FLOOR);

    private static BackroomsPortalEntity portal(TestContext context, boolean visible) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(ENTRY)))
                .facingSource(PortalFacing.SOUTH)
                .facingDestination(PortalFacing.EAST)
                .destinationDim(World.OVERWORLD)
                .destinationPos(Vec3d.ofCenter(context.getAbsolutePos(EXIT)))
                .size(1.0f, 2.0f)
                .visible(visible)
                .build();
        world.spawnEntity(portal);
        return portal;
    }

    private static ServerPlayerEntity player(TestContext context, GameMode mode) {
        return GameTestPlayers.connect(context, mode, context.getAbsolute(PLAYER), 180.0f, 0.0f);
    }

    private static BlockHitResult hitOn(TestContext context, BlockPos relative) {
        BlockPos pos = context.getAbsolutePos(relative);
        return new BlockHitResult(Vec3d.ofCenter(pos).add(-0.5, 0.0, 0.0), Direction.WEST, pos, false);
    }

    private static void blockAction(ServerPlayerEntity player, BackroomsPortalEntity portal, BlockHitResult hit, PortalBlockActionPayload.Action action) {
        GameTestPlayers.receive(player, new PortalBlockActionPayload(portal.getUuid(), OVERWORLD_ID, hit, action));
    }

    private static void entityAction(ServerPlayerEntity player, BackroomsPortalEntity portal, int entityId, PortalEntityActionPayload.Action action) {
        GameTestPlayers.receive(player, new PortalEntityActionPayload(portal.getUuid(), OVERWORLD_ID, entityId, action));
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void entitiesSeenThroughThePortalCanBeHitAndUsed(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        ServerPlayerEntity player = player(context, GameMode.CREATIVE);
        ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
        Vec3d standPos = context.getAbsolute(new Vec3d(7.5, 1.0, 6.5));
        stand.refreshPositionAndAngles(standPos.x, standPos.y, standPos.z, 0.0f, 0.0f);
        world.spawnEntity(stand);
        SheepEntity sheep = EntityType.SHEEP.create(world);
        Vec3d sheepPos = context.getAbsolute(new Vec3d(7.5, 1.0, 5.5));
        sheep.refreshPositionAndAngles(sheepPos.x, sheepPos.y, sheepPos.z, 0.0f, 0.0f);
        sheep.setSheared(false);
        sheep.setAiDisabled(true);
        world.spawnEntity(sheep);
        ArmorStandEntity farStand = EntityType.ARMOR_STAND.create(world);
        Vec3d farPos = context.getAbsolute(new Vec3d(7.5, 1.0, 20.5));
        farStand.refreshPositionAndAngles(farPos.x, farPos.y, farPos.z, 0.0f, 0.0f);
        world.spawnEntity(farStand);
        PigEntity pig = EntityType.PIG.create(world);
        Vec3d pigPos = context.getAbsolute(new Vec3d(7.5, 1.0, 7.5));
        pig.refreshPositionAndAngles(pigPos.x, pigPos.y, pigPos.z, 0.0f, 0.0f);
        pig.setAiDisabled(true);
        world.spawnEntity(pig);
        ItemEntity drop = new ItemEntity(world, pigPos.x, pigPos.y + 0.5, pigPos.z + 1.0, new ItemStack(Items.APPLE));
        drop.setNoGravity(true);
        world.spawnEntity(drop);

        // Dix ticks du joueur : son coup est charge, le premier sera fort et le suivant faible.
        for (int tick = 1; tick <= 10; tick++) {
            context.runAtTick(tick, () -> GameTestPlayers.tick(player));
        }
        context.runAtTick(12, () -> {
            try {
                // Refus : portail inconnu, cible absente, le joueur lui-meme, le portail, cible hors portee, objet non frappable.
                GameTestPlayers.receive(player, new PortalEntityActionPayload(UUID.randomUUID(), OVERWORLD_ID, stand.getId(), PortalEntityActionPayload.Action.ATTACK));
                entityAction(player, portal, 999_999, PortalEntityActionPayload.Action.ATTACK);
                entityAction(player, portal, player.getId(), PortalEntityActionPayload.Action.ATTACK);
                entityAction(player, portal, portal.getId(), PortalEntityActionPayload.Action.ATTACK);
                entityAction(player, portal, farStand.getId(), PortalEntityActionPayload.Action.ATTACK);
                entityAction(player, portal, drop.getId(), PortalEntityActionPayload.Action.ATTACK);
                context.assertTrue(farStand.isAlive() && stand.isAlive() && portal.isAlive() && drop.isAlive(), "Les cibles refusees sont intactes.");
                // Utilisation : des cisailles sur le mouton. Un cochon selle emmenerait le joueur de l'autre cote : refuse.
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
                entityAction(player, portal, sheep.getId(), PortalEntityActionPayload.Action.INTERACT);
                context.assertTrue(sheep.isSheared(), "Le mouton est tondu a travers le portail.");
                pig.saddle(new ItemStack(Items.SADDLE), null);
                entityAction(player, portal, pig.getId(), PortalEntityActionPayload.Action.INTERACT);
                context.assertTrue(!player.hasVehicle(), "Un cochon selle ne s'enfourche pas a travers le portail.");
                // Le support d'armure n'a pas d'utilisation par ce chemin, l'objet au sol n'est pas une creature : rien ne se passe.
                entityAction(player, portal, stand.getId(), PortalEntityActionPayload.Action.INTERACT);
                entityAction(player, portal, drop.getId(), PortalEntityActionPayload.Action.INTERACT);
                // Coup charge sur le mouton, puis coup faible immediat sur le support d'armure, qui casse.
                float sheepHealth = sheep.getHealth();
                entityAction(player, portal, sheep.getId(), PortalEntityActionPayload.Action.ATTACK);
                context.assertTrue(sheep.getHealth() < sheepHealth, "Le mouton a pris le coup.");
                entityAction(player, portal, stand.getId(), PortalEntityActionPayload.Action.ATTACK);
                context.assertTrue(!stand.isAlive(), "Le support d'armure est frappe a travers le portail.");
                context.assertTrue(player.getPos().isInRange(context.getAbsolute(PLAYER), 1.0e-6), "Le joueur est revenu a sa place.");
                // Un spectateur qui frappe suivrait la creature (camera vanilla), teleporte dans son monde : il est refuse.
                player.changeGameMode(GameMode.SPECTATOR);
                entityAction(player, portal, sheep.getId(), PortalEntityActionPayload.Action.ATTACK);
                context.assertTrue(player.getCameraEntity() == player && player.getPos().isInRange(context.getAbsolute(PLAYER), 1.0e-6),
                        "Un spectateur ne suit pas la creature a travers le portail : " + player.getPos() + ".");
            } finally {
                GameTestPlayers.disconnect(player);
                sheep.discard();
                pig.discard();
                drop.discard();
                farStand.discard();
                stand.discard();
                portal.discard();
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void requestsThatDoNotHoldAreIgnored(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        BackroomsPortalEntity hidden = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(3, 2, 1))))
                .facingSource(PortalFacing.SOUTH).facingDestination(PortalFacing.EAST)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(context.getAbsolutePos(EXIT)))
                .size(1.0f, 2.0f).visible(false).build();
        world.spawnEntity(hidden);
        BackroomsPortalEntity broken = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(5, 2, 1))))
                .facingSource(PortalFacing.SOUTH).facingDestination(PortalFacing.EAST)
                .destinationDim(GameTestServerWorlds.nowhere()).destinationPos(Vec3d.ofCenter(context.getAbsolutePos(EXIT)))
                .size(1.0f, 2.0f).build();
        world.spawnEntity(broken);
        // Visible mais ferme : rien ne le traverse.
        BackroomsPortalEntity closed = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(7, 2, 1))))
                .facingSource(PortalFacing.SOUTH).facingDestination(PortalFacing.EAST)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(context.getAbsolutePos(EXIT)))
                .size(1.0f, 2.0f).teleportable(false).build();
        world.spawnEntity(closed);
        ServerPlayerEntity player = player(context, GameMode.CREATIVE);
        BlockPos target = context.getAbsolutePos(TARGET_BLOCK);
        world.setBlockState(target, Blocks.DIRT.getDefaultState());
        BlockHitResult hit = hitOn(context, TARGET_BLOCK);

        context.runAtTick(2, () -> {
            try {
                GameTestPlayers.receive(player, new PortalBlockActionPayload(UUID.randomUUID(), OVERWORLD_ID, hit, PortalBlockActionPayload.Action.START_MINING));
                GameTestPlayers.receive(player, new PortalBlockActionPayload(hidden.getUuid(), OVERWORLD_ID, hit, PortalBlockActionPayload.Action.START_MINING));
                GameTestPlayers.receive(player, new PortalBlockActionPayload(closed.getUuid(), OVERWORLD_ID, hit, PortalBlockActionPayload.Action.START_MINING));
                GameTestPlayers.receive(player, new PortalBlockActionPayload(portal.getUuid(), World.END.getValue(), hit, PortalBlockActionPayload.Action.START_MINING));
                GameTestPlayers.receive(player,
                        new PortalBlockActionPayload(broken.getUuid(), GameTestServerWorlds.nowhere().getValue(), hit, PortalBlockActionPayload.Action.START_MINING));
                // Hors portee de bloc : la cible est a l'autre bout du monde vu ; en creatif, elle casserait d'un coup.
                BlockPos farTarget = context.getAbsolutePos(new BlockPos(20, 1, 6));
                world.setBlockState(farTarget, Blocks.DIRT.getDefaultState());
                blockAction(player, portal, hitOn(context, new BlockPos(20, 1, 6)), PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(target).isOf(Blocks.DIRT) && world.getBlockState(farTarget).isOf(Blocks.DIRT),
                        "Portail inconnu, invisible, ferme, mauvaise dimension ou hors portee : rien ne se casse.");
                world.setBlockState(farTarget, Blocks.AIR.getDefaultState());
                // Hors de la bordure du monde, le bloc n'est pas modifiable : la bordure est resserree sur le joueur le temps du clic.
                WorldBorder border = world.getWorldBorder();
                double borderSize = border.getSize();
                double borderX = border.getCenterX();
                double borderZ = border.getCenterZ();
                border.setCenter(player.getX(), player.getZ());
                border.setSize(2.0);
                try {
                    blockAction(player, portal, hit, PortalBlockActionPayload.Action.START_MINING);
                } finally {
                    border.setCenter(borderX, borderZ);
                    border.setSize(borderSize);
                }
                context.assertTrue(world.getBlockState(target).isOf(Blocks.DIRT), "Hors de la bordure du monde : rien ne se casse.");
                // Derriere le portail, puis trop loin de lui.
                Vec3d behind = context.getAbsolute(new Vec3d(1.5, 1.0, -1.5));
                player.refreshPositionAndAngles(behind.x, behind.y, behind.z, 0.0f, 0.0f);
                blockAction(player, portal, hit, PortalBlockActionPayload.Action.START_MINING);
                Vec3d far = context.getAbsolute(new Vec3d(1.5, 1.0, 12.5));
                player.refreshPositionAndAngles(far.x, far.y, far.z, 180.0f, 0.0f);
                blockAction(player, portal, hit, PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(target).isOf(Blocks.DIRT), "Derriere le portail ou trop loin : rien ne se casse.");
            } finally {
                GameTestPlayers.disconnect(player);
                hidden.discard();
                broken.discard();
                closed.discard();
                portal.discard();
                world.setBlockState(target, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void nothingIsReachedThroughAFrameFarToTheSide(TestContext context) {
        ServerWorld world = context.getWorld();
        // Un passage sans rotation : le regard ressort a cote de la sortie, du meme ecart qu'a cote de l'entree.
        BackroomsPortalEntity portal = BackroomsPortalEntity.builder(world)
                .position(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(12, 2, 1))))
                .facingSource(PortalFacing.SOUTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(World.OVERWORLD).destinationPos(Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(12, 2, 12))))
                .size(1.0f, 2.0f).build();
        world.spawnEntity(portal);
        // A un bloc du plan, mais a huit blocs a cote du cadre ; le bloc vise est la ou son regard ressortirait.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(20.5, 1.0, 2.5)), 180.0f, 0.0f);
        BlockPos target = BlockPos.ofFloored(portal.toDestination(player.getEyePos()));
        world.setBlockState(target, Blocks.DIRT.getDefaultState());
        context.runAtTick(2, () -> {
            try {
                GameTestPlayers.receive(player, new PortalBlockActionPayload(portal.getUuid(), OVERWORLD_ID,
                        new BlockHitResult(Vec3d.ofCenter(target), Direction.NORTH, target, false), PortalBlockActionPayload.Action.START_MINING));
                context.assertTrue(world.getBlockState(target).isOf(Blocks.DIRT),
                        "Pres du plan mais loin a cote du cadre, on ne vise rien par lui : rien ne se casse.");
            } finally {
                GameTestPlayers.disconnect(player);
                portal.discard();
                world.setBlockState(target, Blocks.AIR.getDefaultState());
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void blocksSeenThroughThePortalCanBeUsed(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        ServerPlayerEntity creative = player(context, GameMode.CREATIVE);
        BlockPos lever = context.getAbsolutePos(TARGET_BLOCK);
        BlockPos chest = context.getAbsolutePos(TARGET_BLOCK.south());
        BlockPos ground = context.getAbsolutePos(TARGET_BLOCK.north());
        // Un levier au sol, sur une pierre : sans support il tomberait au premier tick.
        world.setBlockState(lever.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(lever, FLOOR_LEVER);
        world.setBlockState(chest, Blocks.CHEST.getDefaultState());
        world.setBlockState(ground, Blocks.STONE.getDefaultState());

        context.runAtTick(2, () -> {
            try {
                // Un levier repond au clic.
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(lever).get(LeverBlock.POWERED), "Le levier est bascule a travers le portail.");
                // Un coffre ne s'ouvre pas : son ecran se fermerait aussitot. Mais l'objet en main s'utilise sur lui.
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
                GameTestPlayers.sent(creative, PlaySoundS2CPacket.class);
                blockAction(creative, portal, new BlockHitResult(Vec3d.ofCenter(chest).add(0.0, 0.5, 0.0), Direction.UP, chest, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(chest.up()).isOf(Blocks.COBBLESTONE), "La pierre est posee sur le coffre, en creatif.");
                // Vanilla ne lui envoie pas le son de la pose (son client l'aurait predit) : a travers le cadre, il le recoit.
                context.assertTrue(GameTestPlayers.sent(creative, PlaySoundS2CPacket.class).stream()
                        .anyMatch(sound -> sound.getSound().value() == SoundEvents.BLOCK_STONE_PLACE), "Le son de la pierre posee parvient a celui qui la pose.");
                context.assertTrue(creative.getMainHandStack().getCount() == 1, "La pile n'est pas consommee en creatif.");
                // Accroupi avec un objet en main : le bloc n'est pas sollicite, seul l'objet l'est.
                creative.setSneaking(true);
                world.setBlockState(lever, FLOOR_LEVER);
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.USE);
                context.assertTrue(!world.getBlockState(lever).get(LeverBlock.POWERED), "Accroupi, le levier n'est pas bascule.");
                creative.setSneaking(false);
                // Main principale vide, main secondaire garnie : la torche d'ame de la main secondaire se pose.
                creative.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                creative.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SOUL_TORCH));
                blockAction(creative, portal, new BlockHitResult(Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(ground.up()).isOf(Blocks.SOUL_TORCH), "La torche de la main secondaire est posee.");
                creative.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
                world.setBlockState(ground.up(), Blocks.AIR.getDefaultState());
                // Un baton n'a aucun effet sur la pierre : aucune main n'aboutit.
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
                blockAction(creative, portal, new BlockHitResult(Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(ground.up()).isAir(), "Un baton ne fait rien a la pierre.");
                // Mains vides et accroupi, le bloc est tout de meme sollicite : le bloc musical joue la note suivante.
                BlockPos note = context.getAbsolutePos(TARGET_BLOCK.north(2));
                world.setBlockState(note, Blocks.NOTE_BLOCK.getDefaultState());
                creative.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                creative.setSneaking(true);
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK.north(2)), PortalBlockActionPayload.Action.USE);
                creative.setSneaking(false);
                context.assertTrue(world.getBlockState(note).get(NoteBlock.NOTE) == 1, "Le bloc musical a joue sa note suivante.");
                // Une tete sur le dessus d'un bloc musical : le bloc decline l'interaction par defaut, l'objet se pose quand meme.
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SKELETON_SKULL));
                blockAction(creative, portal, new BlockHitResult(Vec3d.ofCenter(note).add(0.0, 0.5, 0.0), Direction.UP, note, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(note.up()).isOf(Blocks.SKELETON_SKULL), "La tete est posee sur le bloc musical.");
                world.setBlockState(note.up(), Blocks.AIR.getDefaultState());
                world.setBlockState(note, Blocks.AIR.getDefaultState());
                // Objet en recharge : ignore.
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TORCH));
                creative.getItemCooldownManager().set(Items.TORCH, 100);
                blockAction(creative, portal, new BlockHitResult(Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(ground.up()).isAir(), "Un objet en recharge ne se pose pas.");
                // Un briquet sur de la TNT : le bloc repond a l'objet, la TNT s'amorce.
                BlockPos tnt = context.getAbsolutePos(TARGET_BLOCK.up());
                world.setBlockState(tnt, Blocks.TNT.getDefaultState());
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK.up()), PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(tnt).isAir(), "La TNT est amorcee a travers le portail.");
                world.getEntitiesByClass(TntEntity.class, new Box(tnt).expand(2.0), entity -> true).forEach(TntEntity::discard);
                // Un spectateur n'agit sur rien.
                creative.changeGameMode(GameMode.SPECTATOR);
                world.setBlockState(lever, FLOOR_LEVER);
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.USE);
                context.assertTrue(!world.getBlockState(lever).get(LeverBlock.POWERED), "Un spectateur ne bascule rien.");
            } finally {
                GameTestPlayers.disconnect(creative);
                portal.discard();
            }
        });
        context.runAtTick(4, () -> {
            BackroomsPortalEntity again = portal(context, true);
            ServerPlayerEntity survival = player(context, GameMode.SURVIVAL);
            try {
                survival.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.TORCH, 5));
                blockAction(survival, again, new BlockHitResult(Vec3d.ofCenter(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false),
                        PortalBlockActionPayload.Action.USE);
                context.assertTrue(world.getBlockState(ground.up()).isOf(Blocks.TORCH), "La torche est posee en survie.");
                context.assertTrue(survival.getMainHandStack().getCount() == 4, "La pile est consommee en survie.");
            } finally {
                GameTestPlayers.disconnect(survival);
                again.discard();
                for (BlockPos pos : new BlockPos[] {lever, lever.down(), chest, chest.up(), ground, ground.up()}) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 60)
    public void aDoorSeenThroughThePortalOpensAndCloses(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        ServerPlayerEntity creative = player(context, GameMode.CREATIVE);
        BlockPos lower = context.getAbsolutePos(TARGET_BLOCK);
        BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.WEST);
        // Les deux moities sur un bloc plein : sans lui, la porte tomberait a la premiere mise a jour de voisinage.
        world.setBlockState(lower.down(), Blocks.STONE.getDefaultState());
        world.setBlockState(lower, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        world.setBlockState(lower.up(), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        try {
            blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.USE);
            context.assertTrue(world.getBlockState(lower).get(DoorBlock.OPEN) && world.getBlockState(lower.up()).get(DoorBlock.OPEN),
                    "La porte s'ouvre a travers le portail, ses deux moities ensemble.");
            blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.USE);
            context.assertTrue(!world.getBlockState(lower).get(DoorBlock.OPEN), "Et se referme.");
        } finally {
            GameTestPlayers.disconnect(creative);
            portal.discard();
            world.setBlockState(lower.up(), Blocks.AIR.getDefaultState());
            world.setBlockState(lower, Blocks.AIR.getDefaultState());
            world.setBlockState(lower.down(), Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 180)
    public void blocksSeenThroughThePortalCanBeMined(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        BlockPos netherrack = context.getAbsolutePos(TARGET_BLOCK.north(2));
        BlockPos dirt = context.getAbsolutePos(TARGET_BLOCK);
        BlockPos stone = context.getAbsolutePos(TARGET_BLOCK.north());
        BlockPos gold = context.getAbsolutePos(TARGET_BLOCK.south());
        BlockPos command = context.getAbsolutePos(TARGET_BLOCK.up());
        world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
        world.setBlockState(stone, Blocks.STONE.getDefaultState());
        world.setBlockState(gold, Blocks.GOLD_BLOCK.getDefaultState());
        world.setBlockState(command, Blocks.COMMAND_BLOCK.getDefaultState());
        // Un evenement de casse refuse ce seul bloc d'or, le temps du test : l'ecouteur, global et permanent, s'eteint ensuite.
        boolean[] refusing = {true};
        PlayerBlockBreakEvents.BEFORE.register((eventWorld, eventPlayer, pos, state, blockEntity) -> !(refusing[0] && pos.equals(gold)));
        ServerPlayerEntity creative = player(context, GameMode.CREATIVE);

        context.runAtTick(2, () -> {
            try {
                // En creatif : casse immediate, sauf avec une epee, sur un bloc de commande ou si un evenement s'y oppose.
                creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(dirt).isOf(Blocks.DIRT), "Une epee ne mine pas en creatif.");
                creative.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK.up()), PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(command).isOf(Blocks.COMMAND_BLOCK), "Un bloc de commande resiste sans droits d'operateur.");
                // Operateur en creatif : le bloc de commande se casse.
                world.getServer().getPlayerManager().getOpList().add(new OperatorEntry(creative.getGameProfile(), 4, false));
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK.up()), PortalBlockActionPayload.Action.START_MINING);
                world.getServer().getPlayerManager().getOpList().remove(creative.getGameProfile());
                context.assertTrue(world.getBlockState(command).isAir(), "Un operateur casse le bloc de commande a travers le portail.");
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK.south()), PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(gold).isOf(Blocks.GOLD_BLOCK), "L'evenement de casse peut refuser.");
                GameTestPlayers.sent(creative, WorldEventS2CPacket.class);
                blockAction(creative, portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
                context.assertTrue(world.getBlockState(dirt).isAir(), "La terre est cassee d'un coup en creatif.");
                context.assertTrue(GameTestPlayers.sent(creative, WorldEventS2CPacket.class).stream()
                        .anyMatch(event -> event.getEventId() == WorldEvents.BLOCK_BROKEN && event.getPos().equals(dirt)),
                        "Le son et les eclats de la casse parviennent au mineur.");
                world.syncWorldEvent(creative, WorldEvents.BLOCK_BROKEN, dirt, Block.getRawIdFromState(Blocks.DIRT.getDefaultState()));
                context.assertTrue(GameTestPlayers.sent(creative, WorldEventS2CPacket.class).isEmpty(),
                        "Hors d'une action a travers un cadre, vanilla l'exclut toujours de ses propres effets.");
            } finally {
                GameTestPlayers.disconnect(creative);
            }
        });
        ServerPlayerEntity[] survival = new ServerPlayerEntity[1];
        context.runAtTick(4, () -> {
            world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
            survival[0] = player(context, GameMode.SURVIVAL);
            // Miner de l'air n'engage aucun minage : aucune fissure ne sera envoyee pour cette place.
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK.up(2)), PortalBlockActionPayload.Action.START_MINING);
            // En survie : abandon immediat (aucune fissure a effacer), puis progression sur plusieurs ticks, abandon et reprise.
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.ABORT_MINING);
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(8, () -> {
            BlockPos air = context.getAbsolutePos(TARGET_BLOCK.up(2));
            List<RemoteBlockBreakingPayload> cracks = GameTestPlayers.sent(survival[0], RemoteBlockBreakingPayload.class);
            context.assertTrue(cracks.stream().anyMatch(crack -> crack.pos().equals(dirt)), "La terre se fissure chez le mineur.");
            context.assertTrue(cracks.stream().noneMatch(crack -> crack.pos().equals(air)), "L'air, lui, ne se fissure pas.");
            context.assertTrue(world.getBlockState(dirt).isOf(Blocks.DIRT), "La terre tient encore apres quatre ticks a mains nues.");
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.ABORT_MINING);
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(40, () -> {
            context.assertTrue(world.getBlockState(dirt).isAir(), "La terre finit par ceder en survie.");
            // Le minage d'une pierre s'interrompt si le joueur s'eloigne du portail ou passe en aventure.
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK.north()), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(44, () -> {
            Vec3d far = context.getAbsolute(new Vec3d(1.5, 1.0, 12.5));
            survival[0].refreshPositionAndAngles(far.x, far.y, far.z, 180.0f, 0.0f);
        });
        context.runAtTick(50, () -> {
            context.assertTrue(cleared(survival[0], stone), "Hors portee du portail, le minage s'arrete : ses fissures s'effacent.");
            Vec3d home = context.getAbsolute(PLAYER);
            survival[0].refreshPositionAndAngles(home.x, home.y, home.z, 180.0f, 0.0f);
            // Un bloc qui disparait en cours de minage (autre joueur, explosion) libere le mineur.
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK.south()), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(53, () -> world.setBlockState(gold, Blocks.AIR.getDefaultState()));
        context.runAtTick(56, () -> {
            context.assertTrue(cleared(survival[0], gold), "Un bloc disparu en cours de minage libere le mineur : ses fissures s'effacent.");
            // La netherrack se casse a la main mais ne se recolte pas : le bloc disparait sans rien lacher.
            world.setBlockState(netherrack, Blocks.NETHERRACK.getDefaultState());
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK.north(2)), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(110, () -> {
            context.assertTrue(world.getBlockState(netherrack).isAir(), "La netherrack finit par ceder a la main.");
            context.assertTrue(world.getEntitiesByClass(ItemEntity.class, new Box(netherrack).expand(2.0), entity -> entity.getStack().isOf(Items.NETHERRACK)).isEmpty(),
                    "Sans l'outil requis, rien n'est recolte.");
            // Le portail reste a portee mais plus le bloc : le mineur recule de deux blocs.
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK.north()), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(113, () -> {
            Vec3d back = context.getAbsolute(PLAYER).add(0.0, 0.0, 2.0);
            survival[0].refreshPositionAndAngles(back.x, back.y, back.z, 180.0f, 0.0f);
        });
        context.runAtTick(120, () -> {
            context.assertTrue(cleared(survival[0], stone), "Hors portee du bloc, le minage s'arrete : ses fissures s'effacent.");
            Vec3d home = context.getAbsolute(PLAYER);
            survival[0].refreshPositionAndAngles(home.x, home.y, home.z, 180.0f, 0.0f);
            survival[0].changeGameMode(GameMode.ADVENTURE);
            world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
            blockAction(survival[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
        });
        context.runAtTick(160, () -> {
            try {
                context.assertTrue(world.getBlockState(dirt).isOf(Blocks.DIRT), "En aventure, la terre fissuree ne se casse pas.");
            } finally {
                refusing[0] = false;
                GameTestPlayers.disconnect(survival[0]);
                portal.discard();
                for (BlockPos pos : new BlockPos[] {dirt, stone, gold, command, netherrack}) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
            context.complete();
        });
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 120)
    public void aMinerWhoLeavesOrDiesLeavesNoCracks(TestContext context) {
        ServerWorld world = context.getWorld();
        BackroomsPortalEntity portal = portal(context, true);
        BlockPos dirt = context.getAbsolutePos(TARGET_BLOCK);
        world.setBlockState(dirt, Blocks.DIRT.getDefaultState());
        // Un temoin pres du bloc, de ce cote-ci : vanilla lui envoie les fissures que posent les autres.
        ServerPlayerEntity witness = GameTestPlayers.connect(context, GameMode.SURVIVAL, context.getAbsolute(new Vec3d(10.5, 1.0, 8.5)), 0.0f, 0.0f);
        ServerPlayerEntity[] miner = {player(context, GameMode.SURVIVAL)};
        int[] minerId = new int[1];
        long[] since = new long[1];
        List<BlockBreakingProgressS2CPacket> seen = new ArrayList<>();
        context.createTimedTaskRunner()
                .createAndAddReported(() -> {
                    minerId[0] = miner[0].getId();
                    blockAction(miner[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
                })
                .createAndAdd(() -> awaitCracks(witness, seen, minerId[0], dirt, true))
                .createAndAddReported(() -> GameTestPlayers.disconnect(miner[0]))
                .createAndAdd(() -> awaitCracks(witness, seen, minerId[0], dirt, false))
                // Un second mineur meurt en plein minage.
                .createAndAddReported(() -> {
                    miner[0] = player(context, GameMode.SURVIVAL);
                    minerId[0] = miner[0].getId();
                    seen.clear();
                    blockAction(miner[0], portal, hitOn(context, TARGET_BLOCK), PortalBlockActionPayload.Action.START_MINING);
                })
                .createAndAdd(() -> awaitCracks(witness, seen, minerId[0], dirt, true))
                .createAndAddReported(() -> miner[0].kill())
                .createAndAdd(() -> awaitCracks(witness, seen, minerId[0], dirt, false))
                .createAndAddReported(() -> since[0] = context.getTick())
                // Plus que le temps qu'il faut pour casser de la terre a la main.
                .createAndAdd(() -> {
                    if (context.getTick() < since[0] + 30) {
                        throw new GameTestException("Le temps de casser la terre a la main n'est pas encore passe.");
                    }
                })
                .createAndAddReported(() -> {
                    try {
                        context.assertTrue(world.getBlockState(dirt).isOf(Blocks.DIRT), "Mort, il ne mine plus : la terre tient.");
                    } finally {
                        GameTestPlayers.disconnect(miner[0]);
                        GameTestPlayers.disconnect(witness);
                        portal.discard();
                        world.setBlockState(dirt, Blocks.AIR.getDefaultState());
                    }
                })
                .completeIfSuccessful();
    }

    /** Leve tant que le temoin n'a pas recu les fissures du mineur sur ce bloc : posees ({@code shown}), ou effacees. */
    private static void awaitCracks(ServerPlayerEntity witness, List<BlockBreakingProgressS2CPacket> seen, int minerId, BlockPos pos, boolean shown) {
        seen.addAll(GameTestPlayers.sent(witness, BlockBreakingProgressS2CPacket.class));
        if (seen.stream().noneMatch(crack -> crack.getEntityId() == minerId && crack.getPos().equals(pos) && crack.getProgress() >= 0 == shown)) {
            throw new GameTestException(shown ? "Le temoin ne voit pas encore les fissures du mineur." : "Les fissures du mineur ne sont pas encore effacees.");
        }
    }

    /** {@code true} si le mineur a recu l'effacement des fissures de ce bloc depuis la derniere lecture : le minage a cesse. */
    private static boolean cleared(ServerPlayerEntity miner, BlockPos pos) {
        return GameTestPlayers.sent(miner, RemoteBlockBreakingPayload.class).stream().anyMatch(crack -> crack.pos().equals(pos) && crack.stage() < 0);
    }
}
