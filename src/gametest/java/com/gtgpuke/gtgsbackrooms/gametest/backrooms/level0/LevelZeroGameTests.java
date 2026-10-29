package com.gtgpuke.gtgsbackrooms.gametest.backrooms.level0;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroLayoutPayload;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroLayoutSync;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.block.LevelZeroWallpaperBlock;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.LevelZeroChunkGenerator;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroLayerStackLayout;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroVerticalSlice;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.LevelZeroSeedResolver;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestPlayers;

/**
 * Tests en jeu du Level 0, executes par {@code ./gradlew runGameTest} sur un vrai serveur.
 *
 * <p>Le serveur de test vanilla force le preset plat et ignore les dimensions de datapack : on
 * valide donc le JSON de la dimension via les registres dynamiques du serveur (type de dimension,
 * biome et generateur resolus depuis le datapack du mod), puis on genere le chunk d'origine avec
 * le generateur ainsi obtenu. Le mur adaptatif du Level 0, pose dans le monde du test, y recalcule ses faces.
 */
public final class LevelZeroGameTests implements FabricGameTest {

    private static final String DIMENSION_JSON =
            "/data/" + ModConstants.MOD_ID + "/dimension/" + BackroomsConstants.LEVEL_ZERO_DIMENSION + ".json";

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void levelZeroDimensionResolvesAndGeneratesPlayableOrigin(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        DimensionOptions dimension = decodeDimension(server);

        context.assertTrue(dimension.chunkGenerator() instanceof LevelZeroChunkGenerator,
                "Le generateur de la dimension doit etre le generateur du Level 0.");
        context.assertTrue(dimension.dimensionTypeEntry().getKey()
                        .map(key -> key.getValue().equals(Identifier.of(ModConstants.MOD_ID, "backrooms_level_0_type")))
                        .orElse(false),
                "Le type de dimension doit etre backrooms_level_0_type.");
        context.assertTrue(dimension.dimensionTypeEntry().value().minY() == LevelZeroLayerStackLayout.MIN_Y,
                "Le Y minimal du type de dimension suit la pile verticale.");
        context.assertTrue(dimension.dimensionTypeEntry().value().height() == LevelZeroLayerStackLayout.DIMENSION_HEIGHT,
                "La hauteur du type de dimension suit la pile verticale.");

        JsonElement encoded = DimensionOptions.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager()), dimension)
                .getOrThrow(message -> new GameTestException("Dimension Level 0 non reencodable : " + message));
        context.assertTrue(encoded.getAsJsonObject().getAsJsonObject("generator").has("biome"),
                "Le generateur reecrit son biome dans la dimension.");

        LevelZeroChunkGenerator generator = (LevelZeroChunkGenerator) dimension.chunkGenerator();
        HeightLimitView height = HeightLimitView.create(LevelZeroLayerStackLayout.MIN_Y, LevelZeroLayerStackLayout.DIMENSION_HEIGHT);
        NoiseConfig noiseConfig = NoiseConfig.create(
                ChunkGeneratorSettings.createMissingSettings(),
                server.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS),
                server.getOverworld().getSeed());
        ProtoChunk chunk = new ProtoChunk(ChunkPos.ORIGIN, UpgradeData.NO_UPGRADE_DATA, height,
                server.getRegistryManager().get(RegistryKeys.BIOME), null);
        generator.populateNoise(Blender.getNoBlending(), noiseConfig, null, chunk).join();

        for (LevelZeroVerticalSlice slice : LevelZeroLayerStackLayout.slices()) {
            BlockState floor = chunk.getBlockState(new BlockPos(1, slice.floorY(), 1));
            context.assertTrue(floor.isOf(ModBlocks.LEVEL_ZERO_DAMP_CARPET) || floor.isOf(ModBlocks.LEVEL_ZERO_DAMP_CARPET_AGED),
                    "Sol en moquette au layer " + slice.layerIndex() + ".");
            for (int y = slice.airMinY(); y <= slice.airMaxY(); y++) {
                context.assertTrue(chunk.getBlockState(new BlockPos(1, y, 1)).isAir(), "Air jouable en Y=" + y + ".");
            }
            BlockState ceiling = chunk.getBlockState(new BlockPos(1, slice.ceilingY(), 1));
            context.assertTrue(ceiling.isOf(ModBlocks.LEVEL_ZERO_CEILING_TILE) || ceiling.isOf(ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT),
                    "Plafond au layer " + slice.layerIndex() + ".");
            context.assertTrue(chunk.getBlockState(new BlockPos(1, slice.bedrockMinY(), 1)).isOf(Blocks.BEDROCK),
                    "Bedrock inter-layer au layer " + slice.layerIndex() + ".");
        }
        context.complete();
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void serverGivesTheLayoutSeedOfItsLevelZeroOnly(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        DimensionOptions dimension = decodeDimension(server);
        NoiseConfig noiseConfig = NoiseConfig.create(
                ChunkGeneratorSettings.createMissingSettings(),
                server.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS),
                server.getOverworld().getSeed());
        // A la connexion, le serveur de test n'a aucun Level 0 (il ignore les dimensions de datapack) : rien ne part.
        ServerPlayerEntity player = GameTestPlayers.connect(context, GameMode.SPECTATOR, context.getAbsolute(new Vec3d(2.5, 2.0, 2.5)), 0.0f, 0.0f);
        try {
            context.assertTrue(GameTestPlayers.sent(player, LevelZeroLayoutPayload.class).isEmpty(), "Aucun Level 0 sur ce serveur : aucune graine.");
            RegistryKey<World> levelZero = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ModConstants.MOD_ID, BackroomsConstants.LEVEL_ZERO_DIMENSION));
            context.assertTrue(LevelZeroLayoutSync.send(player, levelZero, dimension.chunkGenerator(), noiseConfig),
                    "Le generateur du Level 0 envoie sa graine.");
            context.assertFalse(LevelZeroLayoutSync.send(player, World.OVERWORLD, server.getOverworld().getChunkManager().getChunkGenerator(),
                    server.getOverworld().getChunkManager().getNoiseConfig()), "Un autre generateur n'envoie rien.");
            List<LevelZeroLayoutPayload> sent = GameTestPlayers.sent(player, LevelZeroLayoutPayload.class);
            context.assertTrue(sent.size() == 1, "Une seule graine partie : " + sent.size() + ".");
            context.assertTrue(sent.get(0).dimension().equals(levelZero.getValue()), "Celle du Level 0.");
            context.assertTrue(sent.get(0).layoutSeed() == LevelZeroSeedResolver.resolveLayoutSeed(noiseConfig),
                    "La graine du layout, que le client refait sans la graine du monde.");
        } finally {
            GameTestPlayers.disconnect(player);
        }
        context.complete();
    }

    private static DimensionOptions decodeDimension(MinecraftServer server) {
        JsonElement json;
        try (InputStream stream = LevelZeroGameTests.class.getResourceAsStream(DIMENSION_JSON)) {
            if (stream == null) {
                throw new GameTestException("Le fichier " + DIMENSION_JSON + " est absent du datapack.");
            }
            json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new GameTestException("Lecture impossible de " + DIMENSION_JSON + " : " + exception.getMessage() + ".");
        }
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
        return DimensionOptions.CODEC.parse(ops, json)
                .getOrThrow(message -> new GameTestException("Dimension Level 0 invalide : " + message));
    }

    @GameTest(templateName = GameTestPlayers.ARENA)
    public void adaptiveWallpaperRecomputesItsFacesWhenPlaced(TestContext context) {
        BlockPos wall = new BlockPos(2, 1, 2);
        BlockPos bare = new BlockPos(4, 1, 2);
        context.setBlockState(wall, ModBlocks.LEVEL_ZERO_WALLPAPER_ADAPTIVE.getDefaultState().with(LevelZeroWallpaperBlock.FACE_MASK, LevelZeroWallpaperBlock.FULL_MASK));
        // Pose avec le masque deja juste (nul hors du Level 0) : rien a recalculer.
        context.setBlockState(bare, ModBlocks.LEVEL_ZERO_WALLPAPER_ADAPTIVE.getDefaultState());
        context.runAtTick(2, () -> {
            BlockState state = context.getBlockState(wall);
            context.assertTrue(state.isOf(ModBlocks.LEVEL_ZERO_WALLPAPER_ADAPTIVE), "Le mur est pose.");
            context.assertTrue(state.get(LevelZeroWallpaperBlock.FACE_MASK) == 0, "Hors du Level 0, aucune face ne voit le biome rouge : masque recalcule a zero.");
            // Repose avec le bon masque : rien a recalculer.
            context.setBlockState(wall, state);
            context.assertTrue(context.getBlockState(wall).get(LevelZeroWallpaperBlock.FACE_MASK) == 0, "Le masque reste nul.");
            context.assertTrue(context.getBlockState(bare).get(LevelZeroWallpaperBlock.FACE_MASK) == 0, "Un masque deja juste est garde tel quel.");
            context.setBlockState(wall, Blocks.AIR.getDefaultState());
            context.setBlockState(bare, Blocks.AIR.getDefaultState());
            context.complete();
        });
    }
}
