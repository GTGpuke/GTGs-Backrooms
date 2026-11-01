package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.NeonIndex;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;

/**
 * La lumiere suit les blocs, dans un vrai client. Le juge est le recalcul complet : apres un changement, chaque tuile doit etre
 * identique, au bit pres, a ce que donne un recalcul de tout le cache. Une ombre perimee, une ombre oubliee, une tuile calculee
 * sur de vieux blocs ou un bruit au calcul font echouer le test. Les blocs viennent du serveur : ils ne passent pas par le
 * meme chemin que la prediction du joueur.
 */
final class LightFollowsBlocksClientTests {

    /** Un pilier dans la salle de l'arrivee, de trois blocs : assez pour des ombres qui portent. */
    private static final BlockPos[] PILLAR = {new BlockPos(0, 1, 4), new BlockPos(0, 2, 4), new BlockPos(0, 3, 4)};
    private static final BlockPos TORCH = new BlockPos(2, 1, 2);
    private static final BlockPos NEON = new BlockPos(2, 2, 2);
    /** Part des tuiles qu'un pilier pose a le droit de recalculer : tout recalculer serait le bug qu'on voit a l'ecran. */
    private static final double MAX_SHARE = 0.25;
    private static final int QUIET_FRAMES = 40;
    private static final int SETTLE_TICKS = 2400;
    private static final int DARK = 16;
    private static final double BLACK_SHARE = 0.9;
    /** Les blocs poses pour fermer la piece, a retirer en sortant. */
    private static final List<BlockPos> CELL = new ArrayList<>();
    /** Un corps enferme avec le joueur : aucun neon ne doit l'atteindre a travers les murs. */
    private static VillagerEntity shut;
    private static int quiet;
    private static long bakedBefore;
    private static Long2LongOpenHashMap kept;

    private LightFollowsBlocksClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(lightFollowsBlocks());
    }

    /** Plus rien a calculer, plus rien a cuire, depuis quelques images. */
    private static boolean settled(ClientTestContext context) {
        quiet = LightSurfaces.pending() == null && LightCopy.idle(context.world()) ? quiet + 1 : 0;
        return quiet >= QUIET_FRAMES;
    }

    private static void set(ClientTestContext context, BlockPos pos, BlockState state) {
        context.onServer(server -> ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(pos, state));
    }

    private static void setPillar(ClientTestContext context, BlockState state) {
        for (BlockPos pos : PILLAR) {
            set(context, pos, state);
        }
    }

    /** Compare le cache a un recalcul complet : chaque tuile doit revenir identique. */
    private static ClientScenario sameAsFullBake(ClientScenario scenario, String what) {
        return scenario
                .run("releve : " + what, context -> kept = LightSurfaces.fingerprints())
                .run("tout recalculer : " + what, context -> LightVolume.rebake())
                .ticks(4)
                .await("cache recalcule : " + what, LightFollowsBlocksClientTests::settled, SETTLE_TICKS)
                .check("identique au recalcul complet : " + what, context -> requireSame(what));
    }

    private static void requireSame(String what) {
        Long2LongOpenHashMap fresh = LightSurfaces.fingerprints();
        ClientTestContext.require(!fresh.isEmpty(), "Cache vide : " + what + ".");
        List<Long> differ = new ArrayList<>();
        for (Long2LongOpenHashMap.Entry entry : fresh.long2LongEntrySet()) {
            if (!kept.containsKey(entry.getLongKey()) || kept.get(entry.getLongKey()) != entry.getLongValue()) {
                differ.add(entry.getLongKey());
            }
        }
        ClientTestContext.require(differ.isEmpty() && fresh.size() == kept.size(), what + " : " + differ.size() + " surface(s) sur " + fresh.size()
                + " different du recalcul complet, " + kept.size() + " tenue(s) avant.");
    }

    private static ClientScenario lightFollowsBlocks() {
        ClientScenario scenario = ClientScenario.named("light_follows_blocks")
                .run("palier avec le cache de surfaces", LevelZeroClientTests::highTier)
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .ticks(40)
                .await("cache de surfaces au repos", LightFollowsBlocksClientTests::settled, SETTLE_TICKS);
        scenario = sameAsFullBake(scenario, "au repos");
        scenario = scenario
                .run("un pilier arrive du serveur", context -> {
                    bakedBefore = LightSurfaces.bakedTiles();
                    setPillar(context, Blocks.STONE.getDefaultState());
                })
                .await("la copie de la lumiere voit les blocs du serveur", context -> LightCopy.copiedBlock(context.world(), PILLAR[2]) != LightBlocks.AIR, 100)
                .await("cache au repos apres le pilier", LightFollowsBlocksClientTests::settled, SETTLE_TICKS)
                .check("un pilier ne recalcule qu'une partie du cache", context -> {
                    long recomputed = LightSurfaces.bakedTiles() - bakedBefore;
                    int held = LightSurfaces.fingerprints().size();
                    ModConstants.LOGGER.info("[Test client] Un pilier recalcule {} tuiles sur {}.", recomputed, held);
                    ClientTestContext.require(recomputed > 0 && recomputed < held * MAX_SHARE, recomputed + " tuiles recalculees sur " + held + " pour un pilier.");
                });
        scenario = sameAsFullBake(scenario, "pilier pose");
        scenario = scenario
                .run("le pilier part", context -> setPillar(context, Blocks.AIR.getDefaultState()))
                .await("la copie de la lumiere voit le pilier partir", context -> LightCopy.copiedBlock(context.world(), PILLAR[2]) == LightBlocks.AIR, 100)
                .await("cache au repos sans le pilier", LightFollowsBlocksClientTests::settled, SETTLE_TICKS);
        scenario = sameAsFullBake(scenario, "pilier retire");
        scenario = scenario
                .run("une torche : la lumiere la traverse", context -> {
                    bakedBefore = LightSurfaces.bakedTiles();
                    set(context, TORCH, Blocks.TORCH.getDefaultState());
                })
                .ticks(10)
                .await("cache au repos apres la torche", LightFollowsBlocksClientTests::settled, SETTLE_TICKS)
                .check("elle ne recalcule rien", context -> ClientTestContext.require(LightSurfaces.bakedTiles() == bakedBefore,
                        (LightSurfaces.bakedTiles() - bakedBefore) + " tuiles recalculees pour une torche."))
                .run("la torche part", context -> set(context, TORCH, Blocks.AIR.getDefaultState()))
                .run("un neon arrive du serveur", context -> set(context, NEON, ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState()))
                .await("l'index des neons voit les blocs du serveur", context -> neonSelected(context), 100)
                .run("le neon part", context -> set(context, NEON, Blocks.AIR.getDefaultState()))
                .await("cache au repos sans le neon", LightFollowsBlocksClientTests::settled, SETTLE_TICKS);
        scenario = sameAsFullBake(scenario, "neon retire");
        return scenario
                .run("dans une piece fermee", LightFollowsBlocksClientTests::closeCell)
                .ticks(20)
                .await("cache au repos dans la piece", LightFollowsBlocksClientTests::settled, SETTLE_TICKS)
                .frames(4)
                .capture("sans lumiere, c'est vraiment noir", LightFollowsBlocksClientTests::requireBlack)
                .cleanup(context -> {
                    context.onServer(server -> {
                        if (shut != null) {
                            shut.discard();
                            shut = null;
                        }
                    });
                    for (BlockPos pos : CELL) {
                        set(context, pos, Blocks.AIR.getDefaultState());
                    }
                    CELL.clear();
                    setPillar(context, Blocks.AIR.getDefaultState());
                    set(context, TORCH, Blocks.AIR.getDefaultState());
                    set(context, NEON, Blocks.AIR.getDefaultState());
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Ferme de pierre une piece de trois blocs de cote dans la salle de l'arrivee, plafond compris, et y met le joueur. */
    private static void closeCell(ClientTestContext context) {
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            for (int y = 1; y <= 3; y++) {
                for (int z = 2; z <= 6; z++) {
                    for (int x = -2; x <= 2; x++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        boolean border = y == 3 || x == -2 || x == 2 || z == 2 || z == 6;
                        if (border && world.getBlockState(pos).isAir()) {
                            world.setBlockState(pos, Blocks.STONE.getDefaultState());
                            CELL.add(pos);
                        }
                    }
                }
            }
            ClientTestContext.serverPlayer(server).teleport(world, 0.5, 1.0, 3.5, 0.0f, 0.0f);
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            villager.setAiDisabled(true);
            villager.refreshPositionAndAngles(0.5, 1.0, 5.2, 180.0f, 0.0f);
            world.spawnEntity(villager);
            shut = villager;
        });
    }

    private static boolean neonSelected(ClientTestContext context) {
        NeonSelection selection = new NeonSelection(4);
        NeonIndex.collect(context.world(), NEON.getX() + 0.5, NEON.getY() + 0.5, NEON.getZ() + 0.5, 1.0, selection);
        return selection.count() > 0;
    }

    private static void requireBlack(NativeImage image) {
        int dark = 0;
        int total = image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getColor(x, y);
                if (Math.max(abgr & 0xFF, Math.max(abgr >> 8 & 0xFF, abgr >> 16 & 0xFF)) <= DARK) {
                    dark++;
                }
            }
        }
        ClientTestContext.require(dark >= total * BLACK_SHARE, "Seulement " + dark * 100L / total + " % de l'image sont noirs dans une piece fermee.");
        double body = Images.luminance(Images.mean(image, 0.35, 0.2, 0.65, 0.8));
        ClientTestContext.require(body <= DARK, String.format(Locale.ROOT, "Un corps enferme recoit de la lumiere a travers les murs : luminance %.1f.", body));
    }
}
