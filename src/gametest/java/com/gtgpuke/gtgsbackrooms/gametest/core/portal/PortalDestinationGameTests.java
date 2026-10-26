package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.Optional;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalArea;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalMark;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalPlacement;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalWandSettingsPayload;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModItems;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestArrivals;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestServerWorlds;

/**
 * Tests en jeu de la destination choisie et de la sortie fixee : dans une dimension a plafond, l'arrivee sur le premier sol
 * sous le point de depart ou l'on tient debout, hors des fluides, ou au-dessus a defaut ; dans le vide de l'End, une
 * plateforme d'obsidienne, la place degagee, au-dessus d'un fluide, dans la hauteur du monde ; vers le Nether, les X/Z au
 * huitieme ; vers une sortie fixee, sur son bloc et dans la direction de son regard, ou tiree au sort
 * autour d'elle ; vers une dimension absente, rien. Les reglages recus d'un client oublient une destination inconnue et ne
 * fixent une sortie que la ou se tient le joueur ; la pose imposee par la baguette ignore le regard ; un bloc relu vers une
 * autre sortie fixee ou une autre destination cherche de nouveau son arrivee.
 */
public final class PortalDestinationGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-6;
    /** Colonnes du Nether preparees a la main, loin de tout, vingt blocs l'une de l'autre : plus que le rayon de recherche. */
    private static final int NETHER_X = 30_000;
    private static final int NETHER_Z = 30_000;
    private static final int COLUMN_STEP = 20;
    private static final int POCKET_Y = 60;
    private static final int START_Y = 70;
    private static final int HIGH_POCKET_Y = 90;
    private static final int NETHER_TOP = 127;
    /** Loin du centre de l'End, avant ses iles exterieures : le vide. */
    private static final int END_VOID = 500;
    /** Un autre point du vide de l'End, plus loin que le rayon de recherche du premier. */
    private static final int END_WATER = 560;
    /** Un troisieme, pour une hauteur demandee hors du monde. */
    private static final int END_SKY = 620;
    /** Un quatrieme, ou un bloc gene la plateforme. */
    private static final int END_STONE = 680;
    private static final int NETHER_SCALE = 8;
    /** Plus haut sol d'une plateforme dans l'End : sa hauteur logique (256 blocs), moins la place du corps et le sol. */
    private static final int END_PLATFORM_TOP = 253;
    /** Demi-cote, en blocs, de la place videe autour d'un point de l'End : la plateforme d'un cadre d'un bloc sur deux et sa marge. */
    private static final int END_CLEARANCE = 2;

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aDimensionWithACeilingArrivesOnTheFirstFloorBelowOutOfFluidsOrAbove(TestContext context) {
        ServerWorld nether = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.pregenerate(nether, new Vec3d(NETHER_X + 2 * COLUMN_STEP, START_Y, NETHER_Z), 4);
        // Trois colonnes pleines du bas au haut du Nether, chacune avec une poche de deux blocs : sous le depart, de lave,
        // au-dessus du depart.
        BlockPos below = column(nether, NETHER_X, POCKET_Y, false);
        BlockPos lava = column(nether, NETHER_X + COLUMN_STEP, POCKET_Y, true);
        BlockPos above = column(nether, NETHER_X + 2 * COLUMN_STEP, HIGH_POCKET_Y, false);
        PortalTargets.Target down = GameTestArrivals.arrive(nether, below.getX(), START_Y, below.getZ(), PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(down != null && down.dimension().equals(World.NETHER) && near(down.position(), centerOn(below, PortalFacing.SOUTH)),
                "Sous le point de depart, le premier sol ou l'on tient debout : " + position(down) + ".");
        PortalSettings tall = new PortalSettings(1, 3, true);
        PortalTargets.Target taller = GameTestArrivals.arrive(nether, below.getX(), START_Y, below.getZ(), PortalFacing.SOUTH, tall);
        context.assertTrue(!near(taller.position(), PortalArea.center(Vec3d.ofBottomCenter(below), PortalFacing.SOUTH, tall)),
                "Un cadre de trois blocs de haut ne tient pas dans une poche de deux : il arrive ailleurs : " + taller.position() + ".");
        PortalTargets.Target notLava = GameTestArrivals.arrive(nether, lava.getX(), START_Y, lava.getZ(), PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(notLava == null || !near(notLava.position(), centerOn(lava, PortalFacing.SOUTH)),
                "Une poche de lave ne compte pas : on arrive ailleurs : " + position(notLava) + ".");
        // Un cadre couche au sol ne couvre que sa couche : la lave a hauteur de tete, au-dessus de lui, l'ecarte aussi.
        BlockPos head = column(nether, NETHER_X + 4 * COLUMN_STEP, POCKET_Y, false);
        nether.setBlockState(head.up(), Blocks.LAVA.getDefaultState());
        PortalSettings flat = new PortalSettings(1, 1, true);
        PortalTargets.Target lyingUnderLava = GameTestArrivals.arrive(nether, head.getX(), START_Y, head.getZ(), PortalFacing.UP, flat);
        context.assertTrue(!near(lyingUnderLava.position(), PortalArea.center(Vec3d.ofBottomCenter(head), PortalFacing.UP, flat)),
                "De la lave a hauteur de tete au-dessus d'un cadre au sol : il arrive ailleurs : " + lyingUnderLava.position() + ".");
        PortalTargets.Target up = GameTestArrivals.arrive(nether, above.getX(), START_Y, above.getZ(), PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(up != null && near(up.position(), centerOn(above, PortalFacing.SOUTH)),
                "Rien sous le depart : le premier sol au-dessus : " + position(up) + ".");
        // On tient debout dans la poche au-dessus du depart, mais le haut d'un cadre de trois blocs y serait dans la lave.
        nether.setBlockState(above.up(2), Blocks.LAVA.getDefaultState());
        PortalTargets.Target tallUnderLava = GameTestArrivals.arrive(nether, above.getX(), START_Y, above.getZ(), PortalFacing.SOUTH, tall);
        context.assertTrue(!near(tallUnderLava.position(), PortalArea.center(Vec3d.ofBottomCenter(above), PortalFacing.SOUTH, tall)),
                "Le haut d'un cadre dans la lave au-dessus du depart : il arrive ailleurs : " + tallUnderLava.position() + ".");
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void withoutAnyFloorTheArrivalStandsOnAnObsidianPlatform(TestContext context) {
        ServerWorld end = context.getWorld().getServer().getWorld(World.END);
        context.assertTrue(end != null, "Le serveur de test a un End.");
        for (int x : new int[] {END_VOID, END_WATER, END_SKY, END_STONE}) {
            emptyAround(end, x, END_VOID);
        }
        PortalTargets.Target platform = GameTestArrivals.arrive(end, END_VOID, START_Y, END_VOID, PortalFacing.SOUTH, PortalSettings.DEFAULT);
        BlockPos feet = new BlockPos(END_VOID, START_Y, END_VOID);
        context.assertTrue(near(platform.position(), centerOn(feet, PortalFacing.SOUTH)), "Dans le vide, au point de depart : " + platform.position() + ".");
        context.assertTrue(end.getBlockState(feet.down()).isOf(Blocks.OBSIDIAN) && end.getBlockState(feet.down().east()).isOf(Blocks.OBSIDIAN),
                "Sur une plateforme d'obsidienne, plus large que le cadre.");
        // Un bloc de pierre la ou se posera le cadre d'une plateforme : on n'arrive jamais dans un bloc.
        BlockPos stone = new BlockPos(END_STONE, START_Y + 1, END_VOID);
        end.setBlockState(stone, Blocks.STONE.getDefaultState());
        PortalTargets.onPlatform(end, END_STONE, START_Y, END_VOID, PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(end.getBlockState(stone).isAir() && end.getBlockState(stone.down()).isAir() && end.getBlockState(stone.up()).isAir(),
                "La place du cadre et du corps est degagee, le bloc pose la compris.");
        // De l'eau au point de depart d'une plateforme : elle se pose au-dessus, pas dedans.
        end.setBlockState(new BlockPos(END_WATER, START_Y, END_VOID), Blocks.WATER.getDefaultState());
        PortalTargets.Target dry = PortalTargets.onPlatform(end, END_WATER, START_Y, END_VOID, PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(near(dry.position(), centerOn(new BlockPos(END_WATER, START_Y + 1, END_VOID), PortalFacing.SOUTH))
                && end.getBlockState(new BlockPos(END_WATER, START_Y, END_VOID)).isOf(Blocks.OBSIDIAN),
                "Au-dessus de l'eau, la plateforme a sa place : " + dry.position() + ".");
        PortalTargets.Target high = GameTestArrivals.arrive(end, END_SKY, Integer.MAX_VALUE, END_VOID, PortalFacing.SOUTH, PortalSettings.DEFAULT);
        context.assertTrue(near(high.position(), centerOn(new BlockPos(END_SKY, END_PLATFORM_TOP, END_VOID), PortalFacing.SOUTH)),
                "Une hauteur hors du monde est ramenee dans sa hauteur logique : " + high.position() + ".");
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 100)
    public void aChosenDestinationOrAFixedExitDecidesWhereThePortalLeads(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos anchor = context.getAbsolutePos(new BlockPos(4, 1, 4));
        // Un bloc de l'overworld dont l'image au huitieme tombe sur une colonne du Nether preparee a la main.
        ServerWorld netherWorld = GameTestServerWorlds.nether(context);
        GameTestServerWorlds.pregenerate(netherWorld, new Vec3d(NETHER_X + 3 * COLUMN_STEP, START_Y, NETHER_Z), 1);
        BlockPos column = column(netherWorld, NETHER_X + 3 * COLUMN_STEP, POCKET_Y, false);
        BlockPos scaled = new BlockPos(column.getX() * NETHER_SCALE, START_Y, column.getZ() * NETHER_SCALE);
        PortalSettings toNether = PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.empty());
        PortalTargets.Target nether = GameTestArrivals.resolve(server, World.OVERWORLD, scaled, PortalFacing.SOUTH, toNether, 1L);
        context.assertTrue(nether != null && nether.dimension().equals(World.NETHER) && near(nether.position(), centerOn(column, PortalFacing.SOUTH)),
                "Vers le Nether, les X/Z au huitieme, comme vanilla : " + position(nether) + ".");
        context.assertTrue(GameTestArrivals.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                PortalSettings.DEFAULT.withExit(Optional.of(GameTestServerWorlds.nowhere()), Optional.empty()), 1L) == null,
                "Vers une dimension absente, rien.");

        // Sortie fixee sur le sol de l'arene, regard vers l'est.
        BlockPos markPos = context.getAbsolutePos(new BlockPos(10, 0, 10));
        PortalMark mark = new PortalMark(World.OVERWORLD, markPos, Direction.EAST);
        PortalTargets.Target exact = GameTestArrivals.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.of(mark)), 1L);
        context.assertTrue(exact != null && exact.dimension().equals(World.OVERWORLD) && exact.facing() == PortalFacing.EAST
                && near(exact.position(), centerOn(markPos, PortalFacing.EAST)),
                "La sortie fixee l'emporte : sur son bloc, dans la direction du regard : " + position(exact) + ".");
        PortalTargets.Target westward = GameTestArrivals.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                settings(PortalExit.WEST, 0, Optional.of(mark)), 1L);
        context.assertTrue(westward != null && westward.facing() == PortalFacing.WEST, "Un sens de sortie impose l'emporte sur le regard.");
        int scatter = 5;
        Random draw = Random.create(7L);
        int dx = draw.nextBetween(-scatter, scatter);
        int dz = draw.nextBetween(-scatter, scatter);
        PortalTargets.Target around = GameTestArrivals.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                settings(PortalExit.STRAIGHT, scatter, Optional.of(mark)), 7L);
        context.assertTrue(around != null && Math.abs(around.position().x - (markPos.getX() + dx + 0.5)) < EPSILON
                && Math.abs(around.position().z - (markPos.getZ() + dz + 0.5)) < EPSILON,
                "Tiree au sort autour de la sortie fixee : " + position(around) + ".");
        context.assertTrue(GameTestArrivals.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH, PortalSettings.DEFAULT.withExit(Optional.empty(),
                Optional.of(new PortalMark(GameTestServerWorlds.nowhere(), markPos, Direction.EAST))), 1L) == null,
                "Une sortie fixee dans une dimension absente : rien.");
        // Chunk par chunk : une sortie fixee exacte n'attend rien (son chunk est charge par la resolution), une sortie tiree au
        // sort loin de tout s'arrete au premier chunk absent, une dimension absente ne donne rien, le Level 0 (calcul pur) non plus.
        context.assertTrue(!PortalTargets.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.of(mark)), Random.create(1L)).waits(),
                "Une sortie fixee exacte n'attend aucun chunk.");
        PortalMark farMark = new PortalMark(World.NETHER, new BlockPos(-60_000, 64, -60_000), Direction.EAST);
        PortalTargets.Search far = PortalTargets.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                settings(PortalExit.STRAIGHT, scatter, Optional.of(farMark)), Random.create(7L));
        context.assertTrue(far.waits() && far.target() == null && Math.abs(far.missingChunk().x - (-60_000 >> 4)) <= 2,
                "Tiree au sort loin de tout, la recherche s'arrete au premier chunk absent, pres de la marque : " + far.missingChunk() + ".");
        PortalTargets.Search nowhere = PortalTargets.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                settings(PortalExit.STRAIGHT, scatter, Optional.of(new PortalMark(GameTestServerWorlds.nowhere(), markPos, Direction.EAST))), Random.create(1L));
        context.assertTrue(!nowhere.waits() && nowhere.target() == null, "Une marque dans une dimension absente : rien, sans attendre.");
        context.assertTrue(!PortalTargets.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH, PortalSettings.DEFAULT, Random.create(1L)).waits(),
                "Vers le Level 0 (calcul pur), aucun chunk a attendre.");
        // Une sortie fixee exacte dans un chunk jamais genere attend ce chunk ; a ciel ouvert, la colonne de depart attend le sien.
        PortalMark farExact = new PortalMark(World.NETHER, new BlockPos(-52_000, 64, 20_000), Direction.EAST);
        PortalTargets.Search exactFar = PortalTargets.resolve(server, World.OVERWORLD, anchor, PortalFacing.SOUTH,
                PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.of(farExact)), Random.create(1L));
        context.assertTrue(exactFar.waits() && exactFar.missingChunk().equals(new ChunkPos(farExact.pos())),
                "Une sortie fixee exacte loin de tout attend son chunk : " + exactFar.missingChunk() + ".");
        PortalTargets.Search surface = PortalTargets.resolve(server, World.NETHER, new BlockPos(-7_000, 64, 5_000), PortalFacing.SOUTH,
                PortalSettings.DEFAULT.withExit(Optional.of(World.OVERWORLD), Optional.empty()), Random.create(1L));
        context.assertTrue(surface.waits() && surface.missingChunk().equals(new ChunkPos(-56_000 >> 4, 40_000 >> 4)),
                "A ciel ouvert, loin de tout, la colonne de depart (les X/Z fois huit) attend son chunk : " + surface.missingChunk() + ".");
        // Sous un plafond, le cadre d'une colonne au bord d'un chunk deborde sur le chunk d'a cote : il doit etre la aussi.
        ChunkPos corner = new ChunkPos(-2_000, 3_000);
        netherWorld.getChunk(corner.x, corner.z);
        PortalSettings wide = new PortalSettings(8, 3, true).withExit(Optional.of(World.NETHER), Optional.empty());
        BlockPos cornerAnchor = new BlockPos(corner.getStartX() * 8, 64, corner.getStartZ() * 8);
        PortalTargets.Search beside = PortalTargets.resolve(server, World.OVERWORLD, cornerAnchor, PortalFacing.NORTH, wide, Random.create(1L));
        // Tourne vers le nord, le cadre s'etend vers l'ouest : le chunk a l'ouest, pas celui que la colonne suivante de l'anneau
        // (au nord-ouest) demanderait si l'empreinte du cadre n'etait pas verifiee.
        context.assertTrue(beside.waits() && beside.missingChunk().equals(new ChunkPos(corner.x - 1, corner.z)),
                "La colonne de depart est chargee, son cadre de huit deborde sur le chunk voisin a l'ouest, absent : " + beside.missingChunk() + ".");
        netherWorld.getChunk(beside.missingChunk().x, beside.missingChunk().z);
        PortalTargets.Search further = PortalTargets.resolve(server, World.OVERWORLD, cornerAnchor, PortalFacing.NORTH, wide, Random.create(1L));
        context.assertTrue(!further.waits() || !further.missingChunk().equals(corner) && !further.missingChunk().equals(beside.missingChunk()),
                "Les deux chunks la, la recherche avance : " + further + ".");
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void theServerOnlyFixesAnExitWhereThePlayerStands(TestContext context) {
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(4.5, 0.0, 4.5)), 0.0f, 0.0f);
        try {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PORTAL_WAND));
            PortalMark here = PortalMark.at(player);
            send(player, PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.of(here)));
            context.assertTrue(held(player).mark().equals(Optional.of(here)) && held(player).destination().equals(Optional.of(World.NETHER)),
                    "Une sortie fixee la ou se tient le joueur est prise, avec la destination.");
            send(player, PortalSettings.DEFAULT.withExit(Optional.of(GameTestServerWorlds.nowhere()), Optional.of(here)));
            context.assertTrue(held(player).destination().isEmpty(), "Une destination que le serveur n'a pas est oubliee.");
            // Le joueur s'eloigne : la sortie deja fixee reste, une nouvelle loin de lui ou dans un autre monde est refusee.
            Vec3d away = context.getAbsolute(new Vec3d(20.5, 0.0, 20.5));
            player.refreshPositionAndAngles(away.x, away.y, away.z, 0.0f, 0.0f);
            send(player, PortalSettings.DEFAULT.withExit(Optional.empty(), Optional.of(here)));
            context.assertTrue(held(player).mark().equals(Optional.of(here)), "La sortie deja fixee reste, d'ou que le joueur renvoie ses reglages.");
            send(player, PortalSettings.DEFAULT.withExit(Optional.empty(), Optional.of(new PortalMark(World.OVERWORLD, here.pos().east(10), Direction.NORTH))));
            context.assertTrue(held(player).mark().equals(Optional.of(here)), "Une sortie nouvelle loin du joueur est refusee.");
            send(player, PortalSettings.DEFAULT.withExit(Optional.empty(), Optional.of(new PortalMark(World.NETHER, player.getBlockPos(), Direction.NORTH))));
            context.assertTrue(held(player).mark().equals(Optional.of(here)), "Une sortie nouvelle dans un autre monde que le sien est refusee.");
            send(player, PortalSettings.DEFAULT);
            context.assertTrue(held(player).mark().isEmpty(), "Sans sortie fixee : elle est oubliee.");
        } finally {
            GameTestPlayers.disconnect(player);
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void aFixedPlacementIgnoresTheLook(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos support = context.getAbsolutePos(new BlockPos(4, 0, 12));
        BlockPos anchor = support.up();
        world.setBlockState(support, Blocks.STONE.getDefaultState());
        // Le joueur regarde droit devant lui, vers le nord : selon le regard, le cadre serait debout.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.CREATIVE, context.getAbsolute(new Vec3d(4.5, 1.0, 15.5)), 180.0f, 0.0f);
        try {
            context.assertTrue(placed(player, support, PortalPlacement.FLOOR) == Direction.UP, "Pose au sol : couche, face au haut, meme sans regarder le sol.");
            context.assertTrue(placed(player, support, PortalPlacement.CEILING) == Direction.DOWN, "Pose au plafond : couche, face au bas.");
            player.setPitch(80.0f);
            context.assertTrue(placed(player, support, PortalPlacement.STANDING) == Direction.SOUTH,
                    "Pose debout : face au joueur, meme en regardant le sol.");
        } finally {
            GameTestPlayers.disconnect(player);
            world.setBlockState(anchor, Blocks.AIR.getDefaultState());
            world.setBlockState(support, Blocks.AIR.getDefaultState());
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA, tickLimit = 200)
    public void aBlockRereadTowardsAnotherExitSearchesItsArrivalAgain(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos first = context.getAbsolutePos(new BlockPos(15, 0, 15));
        BlockPos second = context.getAbsolutePos(new BlockPos(15, 0, 5));
        context.setBlockState(pos, ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
        BackroomsPortalBlockEntity block = context.getBlockEntity(pos);
        block.readComponents(wand(PortalSettings.DEFAULT.withExit(Optional.empty(), Optional.of(new PortalMark(World.OVERWORLD, first, Direction.SOUTH)))));
        context.createTimedTaskRunner()
                .createAndAdd(() -> context.assertTrue(leadsTo(world, block, centerOn(first, PortalFacing.SOUTH)), "Pas encore pose vers la sortie fixee."))
                .createAndAddReported(() -> reread(block, world, nbt -> nbt.put("Mark", markNbt(new PortalMark(World.OVERWORLD, second, Direction.SOUTH)))))
                .createAndAdd(() -> context.assertTrue(leadsTo(world, block, centerOn(second, PortalFacing.SOUTH)),
                        "Relu vers une autre sortie fixee, il n'y mene pas encore."))
                .createAndAddReported(() -> reread(block, world, nbt -> {
                    nbt.remove("Mark");
                    nbt.putString("Destination", World.OVERWORLD.getValue().toString());
                }))
                // Vers le monde normal depuis le monde normal : la surface a l'aplomb du bloc, et non plus la sortie fixee.
                .createAndAdd(() -> context.assertTrue(block.isConfigured() && portal(world, block) != null
                        && portal(world, block).destinationDim().equals(World.OVERWORLD) && !leadsTo(world, block, centerOn(second, PortalFacing.SOUTH)),
                        "Relu vers le monde normal, il n'y mene pas encore."))
                .createAndAddReported(() -> context.setBlockState(pos, Blocks.AIR))
                .completeIfSuccessful();
    }

    /**
     * Rend le vide a un point de l'End : un lancement precedent sur le meme monde (le dossier de run n'est vide que par
     * {@code clean}) y a laisse sa plateforme, que la recherche d'un sol trouverait des la colonne de depart.
     */
    private static void emptyAround(ServerWorld end, int x, int z) {
        BlockPos.Mutable cell = new BlockPos.Mutable();
        for (int cellX = x - END_CLEARANCE; cellX <= x + END_CLEARANCE; cellX++) {
            for (int cellZ = z - END_CLEARANCE; cellZ <= z + END_CLEARANCE; cellZ++) {
                for (int y = end.getBottomY(); y < end.getTopY(); y++) {
                    end.setBlockState(cell.set(cellX, y, cellZ), Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    /** Une colonne du Nether pleine du bas au haut, avec une poche de deux blocs (de lave en bas, ou d'air) a cette hauteur. */
    private static BlockPos column(ServerWorld world, int x, int pocketY, boolean lava) {
        BlockPos.Mutable cell = new BlockPos.Mutable();
        for (int y = world.getBottomY(); y <= NETHER_TOP; y++) {
            world.setBlockState(cell.set(x, y, NETHER_Z), Blocks.NETHERRACK.getDefaultState());
        }
        world.setBlockState(cell.set(x, pocketY, NETHER_Z), lava ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState());
        world.setBlockState(cell.set(x, pocketY + 1, NETHER_Z), Blocks.AIR.getDefaultState());
        return new BlockPos(x, pocketY, NETHER_Z);
    }

    /** Centre d'un cadre d'un bloc sur deux pose sur le bloc donne, dans cette orientation. */
    private static Vec3d centerOn(BlockPos feet, PortalFacing facing) {
        return PortalArea.center(Vec3d.ofBottomCenter(feet), facing, PortalSettings.DEFAULT);
    }

    private static PortalSettings settings(PortalExit exit, int scatter, Optional<PortalMark> mark) {
        return new PortalSettings(1, 2, true, true, false, false, exit, scatter, false, false, PortalPlacement.LOOK, Optional.empty(), mark);
    }

    private static boolean near(Vec3d point, Vec3d expected) {
        return point.squaredDistanceTo(expected) < EPSILON;
    }

    private static @Nullable Vec3d position(@Nullable PortalTargets.Target target) {
        return target == null ? null : target.position();
    }

    private static void send(ServerPlayerEntity player, PortalSettings settings) {
        GameTestPlayers.receive(player, new PortalWandSettingsPayload(Hand.MAIN_HAND, settings));
    }

    private static PortalSettings held(ServerPlayerEntity player) {
        return PortalSettings.of(player.getMainHandStack());
    }

    /** Le joueur pose avec une baguette a cette pose un bloc portail sur le dessus du support ; renvoie sa face avant, retire ensuite. */
    private static @Nullable Direction placed(ServerPlayerEntity player, BlockPos support, PortalPlacement placement) {
        player.setStackInHand(Hand.MAIN_HAND, wand(new PortalSettings(1, 2, true, true, false, false, PortalExit.STRAIGHT, 0, false, false, placement,
                Optional.empty(), Optional.empty())));
        Vec3d hit = Vec3d.ofCenter(support).add(0.0, 0.5, 0.0);
        ModItems.PORTAL_WAND.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, support, false)));
        ServerWorld world = player.getServerWorld();
        BlockPos anchor = support.up();
        Direction front = world.getBlockState(anchor).isOf(ModBlocks.BACKROOMS_PORTAL) ? world.getBlockState(anchor).get(BackroomsPortalBlock.FACING) : null;
        world.setBlockState(anchor, Blocks.AIR.getDefaultState());
        return front;
    }

    /** {@code true} si la face avant du bloc mene au cadre d'arrivee de ce centre. */
    private static boolean leadsTo(ServerWorld world, BackroomsPortalBlockEntity block, Vec3d center) {
        BackroomsPortalEntity portal = portal(world, block);
        return portal != null && portal.destinationPos().squaredDistanceTo(center) < EPSILON;
    }

    private static @Nullable BackroomsPortalEntity portal(ServerWorld world, BackroomsPortalBlockEntity block) {
        return block.entryPortal(BackroomsPortalBlockEntity.FRONT) == null ? null
                : (BackroomsPortalEntity) world.getEntity(block.entryPortal(BackroomsPortalBlockEntity.FRONT));
    }

    /** Relit le bloc depuis sa sauvegarde, retouchee. */
    private static void reread(BackroomsPortalBlockEntity block, ServerWorld world, Consumer<NbtCompound> change) {
        NbtCompound nbt = block.createNbt(world.getRegistryManager());
        change.accept(nbt);
        block.read(nbt, world.getRegistryManager());
    }

    private static NbtCompound markNbt(PortalMark mark) {
        return (NbtCompound) PortalMark.CODEC.encodeStart(NbtOps.INSTANCE, mark).getOrThrow();
    }

    /** Baguette de portail portant ces reglages. */
    private static ItemStack wand(PortalSettings settings) {
        ItemStack wand = new ItemStack(ModItems.PORTAL_WAND);
        wand.set(ModDataComponents.PORTAL_SETTINGS, settings);
        return wand;
    }
}
