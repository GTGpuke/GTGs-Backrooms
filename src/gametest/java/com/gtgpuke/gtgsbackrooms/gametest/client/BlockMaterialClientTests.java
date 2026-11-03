package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.client.BlockMaterials;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightBounces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * La matiere des blocs dans un vrai client : la table reprend les textures de matiere au pixel pres, le relief du terrain suit la
 * texture de normales et non la luminance, et en High comme en Ultra le sombre du motif du papier peint renvoie les dalles, jamais la
 * moquette, et jamais une dalle qu'un bloc cache.
 */
final class BlockMaterialClientTests {

    private static final Identifier WALLPAPER = Identifier.of(ModConstants.MOD_ID, "block/level_zero_wallpaper");
    private static final Identifier NO_MATERIAL = Identifier.of(ModConstants.MOD_ID, "block/level_zero_baseboard");
    /** Un pixel du rail au bord de la dalle de plafond, et un pixel de la dalle. */
    private static final int RAIL_PIXEL = 0;
    private static final int PANEL_PIXEL = 16;
    /** La dalle est a peine plus basse que son rail. */
    private static final int MIN_PANEL_HEIGHT = 190;
    /** Part de l'image que le reflet fait changer, le regard au plafond : le rail est mat. */
    private static final double MAX_CEILING_SHINE = 0.002;
    /** Ce dont un pixel du rail depasse ses voisins, a cette distance d'eux, et la part de l'image qui doit le faire : le rail se voit a tous les paliers. */
    private static final double RIDGE = 4.0;
    private static final int RIDGE_SPAN = 10;
    private static final double MIN_RIDGES = 0.12;
    /** Au-dessus de cette luminance, c'est une dalle. */
    private static final double MAX_RIDGE = 200.0;
    /** Part de l'image que le relief des rails fait changer : il se voit, a peine. */
    private static final double MIN_RAIL_RELIEF = 0.0003;
    private static final double MAX_RAIL_RELIEF = 0.03;
    /** Ecart de luminance qu'un reflet donne a un pixel, et que le grain du film n'atteint jamais. */
    private static final double SHINING = 10.0;
    /** Ecart de luminance qu'atteint le grain du film sur une part des pixels. */
    private static final double TOUCHED = 4.0;
    /** Part de l'image que le reflet atteint au moins, sur le haut d'un mur. */
    private static final double MIN_SHINE = 0.001;
    /** Part de l'image dont un sol mat peut s'ecarter de SHINING : aucune. */
    private static final double MAX_FLOOR_SHINE = 0.0;
    /** Part de l'image qu'un reflet touche au plus : seul le motif brille, et pas partout. */
    private static final double MAX_TOUCHED = 0.2;
    /** Ce qu'il reste au plus du reflet quand des blocs cachent les dalles. */
    private static final double MAX_HIDDEN_SHINE = 0.05;
    /** Lien minimal entre le relief d'une tuile et la pente de sa texture de normales. */
    private static final double MIN_RELIEF_LINK = 0.5;
    /** Ce que le vrai sens du relief doit gagner sur le sens horizontal a l'envers. */
    private static final double MIN_SENSE_GAP = 0.02;
    /** Ce que le contraste de la moquette doit perdre au moins sans son relief ni son epaisseur. */
    private static final double MIN_PILE = 1.15;
    /** Distance a un mur d'ou la moquette se voit en rasant, en blocs : le regard y entre le plus loin. */
    private static final double FOOT_DISTANCE = 5.0;
    /** Tangage qui met le pied du mur au centre de l'image, a cette distance. */
    private static final float FOOT_PITCH = 18.0f;
    /** Rangees de la bande de moquette regardee, sous le pied du mur. */
    private static final int FOOT_BAND_START = 2;
    private static final int FOOT_BAND_END = 26;
    /** Ce qu'un pixel de moquette vaut au moins de la mediane de sa bande, pour ne pas compter comme un trou de lumiere. */
    private static final double MIN_FOOT_LIGHT = 0.7;
    /** Part de la bande qui peut tomber sous ce seuil : les creux de la moquette, pas des tirets noirs. */
    private static final double MAX_DARK_FOOT = 0.02;
    private static final int QUIET_FRAMES = 40;
    private static final int SETTLE_TICKS = 2400;
    private static final int TILE = 32;
    private static final int SEARCH = 12;
    private static final float[] DIRECTIONS = {0.0f, 90.0f, 180.0f, 270.0f};
    private static final int[] FACES = {5, 0, 4, 1};
    private static final List<BlockPos> UNDER_NEONS = new ArrayList<>();
    private static int quiet;
    private static float wallYaw;
    private static float footYaw;
    private static Vec3d nearSpot;
    private static BlockPos wallCell;
    private static int wallFace;
    private static int[] before;
    private static int[] wallBefore;
    private static int[] ceilingBefore;
    private static double shine;
    private static double pile;

    private BlockMaterialClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(blockMaterials());
    }

    private static ClientScenario blockMaterials() {
        ClientScenario scenario = ClientScenario.named("block_materials")
                .run("palier High", LevelZeroClientTests::highTier)
                .run("joueur invisible", context -> invisible(context, true))
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .ticks(40)
                .await("lumiere au repos", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .check("la table reprend les textures de matiere", BlockMaterialClientTests::requireTable)
                .run("pres d'un mur", BlockMaterialClientTests::nearWall)
                .ticks(30)
                .await("repos pres du mur", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .check("le relief suit la texture de normales", BlockMaterialClientTests::requireReliefFollowsNormals);
        scenario = shining(scenario, "High");
        scenario = scenario
                .run("palier Ultra", context -> tier(context, LookQuality.ULTRA))
                .ticks(4)
                .await("Ultra au repos", BlockMaterialClientTests::settled, SETTLE_TICKS);
        scenario = shining(scenario, "Ultra");
        return scenario
                .run("a cinq blocs d'un mur", BlockMaterialClientTests::farFromWall)
                .ticks(30)
                .await("repos loin du mur", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .run("regard vers le pied du mur", context -> look(context, footYaw, FOOT_PITCH))
                .frames(8)
                .capture("la moquette reste eclairee au pied du mur", BlockMaterialClientTests::requireLitFoot)
                .run("retour pres du mur", BlockMaterialClientTests::backNearWall)
                .ticks(30)
                .await("repos de retour", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .run("regard vers la moquette", context -> look(context, wallYaw + 180.0f, 40.0f))
                .frames(8)
                .capture("la moquette a du volume", image -> pile = contrast(image))
                .run("relief coupe", context -> LookResources.applySettings(LookSettings.current().with(LookOption.RELIEF, false), context.client()))
                .ticks(4)
                .await("repos sans relief", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("sans relief, la moquette est plate", image -> {
                    double flat = contrast(image);
                    ModConstants.LOGGER.info("[Test client] Contraste de la moquette : {} avec son volume, {} sans.", String.format(Locale.ROOT, "%.2f", pile),
                            String.format(Locale.ROOT, "%.2f", flat));
                    ClientTestContext.require(pile >= flat * MIN_PILE, String.format(Locale.ROOT, "La moquette n'a pas de volume : %.2f contre %.2f.", pile, flat));
                })
                .run("regard rasant vers le plafond", context -> look(context, wallYaw + 180.0f, -30.0f))
                .frames(8)
                .capture("le plafond sans relief", image -> ceilingBefore = pixels(image))
                .run("relief remis", context -> LookResources.applySettings(LookSettings.current().with(LookOption.RELIEF, true), context.client()))
                .ticks(4)
                .await("repos avec relief", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("le rail depasse a peine de la dalle", image -> {
                    double rails = moved(image, ceilingBefore);
                    ModConstants.LOGGER.info("[Test client] Relief des rails : {} % de l'image.", percent(rails));
                    ClientTestContext.require(rails >= MIN_RAIL_RELIEF, "Le rail ne depasse pas de la dalle : " + percent(rails) + " % de l'image.");
                    ClientTestContext.require(rails <= MAX_RAIL_RELIEF, "Le relief du rail n'est plus leger : " + percent(rails) + " % de l'image.");
                })
                .run("des blocs sous les dalles", context -> setUnderNeons(context, true))
                .ticks(4)
                .await("la copie voit ces blocs", context -> LightCopy.copiedBlock(context.world(), UNDER_NEONS.get(0)) != 0, 100)
                .await("repos sous les blocs", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .run("regard vers le haut du mur", context -> look(context, wallYaw + 25.0f, -38.0f))
                .frames(8)
                .capture("le mur sous les blocs, reflets actifs", image -> before = pixels(image))
                .run("reflets coupes sous les blocs", context -> sheen(context, false))
                .ticks(4)
                .await("repos sans reflets sous les blocs", BlockMaterialClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("un bloc coupe le reflet de sa dalle", image -> {
                    double hidden = share(image, SHINING);
                    ModConstants.LOGGER.info("[Test client] Reflet sous les blocs : {} % de l'image, contre {} % a decouvert.", percent(hidden), percent(shine));
                    ClientTestContext.require(hidden <= shine * MAX_HIDDEN_SHINE, "Un reflet passe a travers un bloc : " + percent(hidden) + " %.");
                })
                .cleanup(context -> setUnderNeons(context, false))
                .cleanup(context -> invisible(context, false))
                .cleanup(context -> sheen(context, true))
                .cleanup(context -> LookResources.applySettings(LookSettings.current().with(LookOption.RELIEF, true), context.client()))
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Avec puis sans reflets : le haut d'un mur change, et le sol ne bouge pas. */
    private static ClientScenario shining(ClientScenario scenario, String tier) {
        return scenario
                .run("regard vers le sol, " + tier, context -> look(context, wallYaw + 180.0f, 50.0f))
                .frames(8)
                .capture("le sol avec reflets, " + tier, image -> before = pixels(image))
                .run("regard vers le haut du mur, " + tier, context -> look(context, wallYaw + 25.0f, -38.0f))
                .frames(8)
                .capture("le mur avec reflets, " + tier, image -> wallBefore = pixels(image))
                .run("regard vers le plafond, " + tier, context -> look(context, wallYaw + 180.0f, -55.0f))
                .frames(8)
                .capture("le plafond avec reflets, " + tier, image -> {
                    ceilingBefore = pixels(image);
                    double rails = ridges(image);
                    ModConstants.LOGGER.info("[Test client] Rails du plafond en {} : {} % de l'image.", tier, percent(rails));
                    ClientTestContext.require(rails >= MIN_RIDGES, "Le rail ne se voit pas en " + tier + " : " + percent(rails) + " % de l'image.");
                })
                .run("reflets coupes, " + tier, context -> sheen(context, false))
                .ticks(4)
                .await("repos sans reflets, " + tier, BlockMaterialClientTests::settled, SETTLE_TICKS)
                .run("regard vers le haut du mur sans reflets, " + tier, context -> look(context, wallYaw + 25.0f, -38.0f))
                .frames(8)
                .capture("seul le motif brillait, " + tier, image -> {
                    int[] floor = before;
                    before = wallBefore;
                    shine = share(image, SHINING);
                    double touched = share(image, TOUCHED);
                    before = floor;
                    ModConstants.LOGGER.info("[Test client] Reflet du mur en {} : {} % de l'image brille, {} % bouge.", tier, percent(shine), percent(touched));
                    ClientTestContext.require(shine >= MIN_SHINE, "Le motif ne brille pas en " + tier + " : " + percent(shine) + " % de l'image.");
                    ClientTestContext.require(touched <= MAX_TOUCHED, "Trop de mur brille en " + tier + " : " + percent(touched) + " % de l'image.");
                })
                .run("regard vers le plafond sans reflets, " + tier, context -> look(context, wallYaw + 180.0f, -55.0f))
                .frames(8)
                .capture("le rail du plafond est mat, " + tier, image -> {
                    double rails = moved(image, ceilingBefore);
                    ModConstants.LOGGER.info("[Test client] Reflet du plafond en {} : {} % de l'image.", tier, percent(rails));
                    ClientTestContext.require(rails <= MAX_CEILING_SHINE, "Le rail du plafond brille en " + tier + " : " + percent(rails) + " % de l'image.");
                })
                .run("regard vers le sol sans reflets, " + tier, context -> look(context, wallYaw + 180.0f, 50.0f))
                .frames(8)
                .capture("la moquette ne brille pas, " + tier, image -> {
                    double floor = share(image, SHINING);
                    ClientTestContext.require(floor <= MAX_FLOOR_SHINE, "La moquette brille en " + tier + " : " + percent(floor) + " % de l'image.");
                })
                .run("reflets remis, " + tier, context -> sheen(context, true))
                .ticks(4)
                .await("repos avec reflets, " + tier, BlockMaterialClientTests::settled, SETTLE_TICKS);
    }

    /**
     * Le joueur invisible ou non : invisible, il ne fait pas d'ombre. Celle de son corps suit le balancement de ses bras au repos, et d'une
     * image a l'autre, elle changerait le sol qu'on compare.
     */
    private static void invisible(ClientTestContext context, boolean hidden) {
        context.onServer(server -> ClientTestContext.serverPlayer(server).setInvisible(hidden));
    }

    private static boolean settled(ClientTestContext context) {
        quiet = LightSurfaces.pending() == null && LightBounces.settled() && LightCopy.idle(context.world()) ? quiet + 1 : 0;
        if (quiet >= QUIET_FRAMES) {
            quiet = 0;
            return true;
        }
        return false;
    }

    private static void tier(ClientTestContext context, LookQuality quality) {
        LookResources.applySettings(LookSettings.current().withQuality(quality.key()), context.client());
    }

    private static void sheen(ClientTestContext context, boolean active) {
        LookResources.applySettings(LookSettings.current().with(LookOption.SHEEN, active), context.client());
    }

    private static void look(ClientTestContext context, float yaw, float pitch) {
        context.player().setYaw(yaw);
        context.player().setPitch(pitch);
    }

    private static NativeImage file(MinecraftClient client, String name) {
        Identifier id = Identifier.of(ModConstants.MOD_ID, "textures/block/" + name + ".png");
        try (InputStream in = client.getResourceManager().getResourceOrThrow(id).getInputStream()) {
            return NativeImage.read(in);
        } catch (IOException exception) {
            throw new IllegalStateException("Texture illisible : " + id + ".", exception);
        }
    }

    private static void requireTable(ClientTestContext context) {
        SpriteAtlasTexture atlas = context.client().getBakedModelManager().getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        byte[] table = BlockMaterials.read();
        int width = BlockMaterials.width();
        Sprite wall = atlas.getSprite(WALLPAPER);
        try (NativeImage slopes = file(context.client(), "level_zero_wallpaper_n"); NativeImage properties = file(context.client(), "level_zero_wallpaper_s")) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int at = ((wall.getY() + y) * width + wall.getX() + x) * 4;
                    int slope = slopes.getColor(x, y);
                    int property = properties.getColor(x, y);
                    boolean same = table[at] == (byte) slope && table[at + 1] == (byte) (slope >> 8) && table[at + 2] == (byte) property && table[at + 3] == (byte) (slope >> 24);
                    ClientTestContext.require(same, "Pixel de matiere " + x + ", " + y + " different de ses textures.");
                }
            }
        }
        try (NativeImage slopes = file(context.client(), "level_zero_ceiling_tile_n")) {
            int rail = slopes.getColor(RAIL_PIXEL, PANEL_PIXEL) >>> 24;
            int panel = slopes.getColor(PANEL_PIXEL, PANEL_PIXEL) >>> 24;
            ClientTestContext.require(rail == 255 && panel < rail && panel >= MIN_PANEL_HEIGHT, "Le rail doit depasser a peine de la dalle : " + rail + " contre " + panel + ".");
        }
        Sprite plain = atlas.getSprite(NO_MATERIAL);
        for (int y = 0; y < plain.getContents().getHeight(); y++) {
            for (int x = 0; x < plain.getContents().getWidth(); x++) {
                int at = ((plain.getY() + y) * width + plain.getX() + x) * 4;
                boolean empty = table[at] == 0 && table[at + 1] == 0 && table[at + 2] == 0 && table[at + 3] == 0;
                ClientTestContext.require(empty, "Une texture sans fichier de matiere n'est pas a zero.");
            }
        }
    }

    /** Le mur le plus proche des quatre directions : le joueur vient a un bloc et demi de lui, et sa surface est retenue. */
    private static void nearWall(ClientTestContext context) {
        Vec3d eye = context.player().getEyePos();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < DIRECTIONS.length; i++) {
            double yaw = Math.toRadians(DIRECTIONS[i]);
            for (double d = 0.5; d < SEARCH * 2 && d < best; d += 0.1) {
                BlockPos pos = BlockPos.ofFloored(eye.x - Math.sin(yaw) * d, eye.y, eye.z + Math.cos(yaw) * d);
                if (context.world().getBlockState(pos).isOpaqueFullCube(context.world(), pos)) {
                    best = d;
                    wallYaw = DIRECTIONS[i];
                    wallFace = FACES[i];
                    wallCell = pos.add((int) Math.round(Math.sin(yaw)), 0, (int) -Math.round(Math.cos(yaw)));
                }
            }
        }
        ClientTestContext.require(best < Double.MAX_VALUE, "Aucun mur autour du joueur.");
        double yaw = Math.toRadians(wallYaw);
        Vec3d feet = context.player().getPos().add(-Math.sin(yaw) * (best - 1.5), 0.0, Math.cos(yaw) * (best - 1.5));
        float kept = wallYaw;
        context.onServer(server -> {
            ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            ClientTestContext.serverPlayer(server).teleport(levelZero, feet.x, feet.y, feet.z, kept, 0.0f);
        });
    }

    /** D'ou vient la lumiere d'une surface : la somme des directions de ses dalles, chacune selon sa distance. */
    private static Vec3d lightComesFrom(ClientTestContext context, BlockPos cell) {
        Block neon = Registries.BLOCK.get(LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).neons().block());
        Vec3d point = Vec3d.ofCenter(cell);
        Vec3d sum = Vec3d.ZERO;
        for (BlockPos pos : BlockPos.iterate(cell.add(-SEARCH, -2, -SEARCH), cell.add(SEARCH, 8, SEARCH))) {
            if (context.world().getBlockState(pos).isOf(neon)) {
                Vec3d to = Vec3d.ofCenter(pos).subtract(point);
                sum = sum.add(to.normalize().multiply(1.0 / (1.0 + to.lengthSquared() / 8.0)));
            }
        }
        return sum;
    }

    /**
     * Sur un mur, la pente qui regarde les dalles en recoit plus. La pente lue doit etre celle de la texture de normales, dans ses deux
     * vrais sens : deviner d'apres la luminance donnerait l'inverse sur l'encre, qui est sombre et en relief. Le test choisit, le long du
     * mur, la surface dont la lumiere vient le plus de cote, ou un sens horizontal a l'envers se verrait.
     */
    private static void requireReliefFollowsNormals(ClientTestContext context) {
        boolean mirrored = wallFace == 0 || wallFace == 5;
        Vec3d along = wallFace < 2 ? new Vec3d(0.0, 0.0, 1.0) : new Vec3d(1.0, 0.0, 0.0);
        BlockPos best = null;
        double bestSide = 0.0;
        Vec3d light = Vec3d.ZERO;
        for (int offset = -SEARCH; offset <= SEARCH; offset++) {
            BlockPos cell = wallCell.add((int) along.x * offset, 0, (int) along.z * offset);
            if (LightSurfaces.surfacePixels(cell.getX(), cell.getY(), cell.getZ(), wallFace) != null) {
                Vec3d from = lightComesFrom(context, cell);
                double side = Math.abs(from.dotProduct(along)) / Math.max(from.length(), 1.0e-3);
                if (side > bestSide) {
                    bestSide = side;
                    best = cell;
                    light = from;
                }
            }
        }
        ClientTestContext.require(best != null, "Le mur n'a pas de tuile.");
        byte[] pixels = LightSurfaces.surfacePixels(best.getX(), best.getY(), best.getZ(), wallFace);
        double[] relief = new double[TILE * TILE];
        double[] right = new double[TILE * TILE];
        double[] wrong = new double[TILE * TILE];
        double[] guessed = new double[TILE * TILE];
        try (NativeImage slopes = file(context.client(), "level_zero_wallpaper_n"); NativeImage block = file(context.client(), "level_zero_wallpaper")) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int i = y * TILE + x;
                    int textureX = mirrored ? TILE - 1 - x : x;
                    int textureY = TILE - 1 - y;
                    int color = slopes.getColor(textureX, textureY);
                    double tiltAlong = ((color & 0xFF) - 128.0) * (mirrored ? -1.0 : 1.0) * light.dotProduct(along);
                    double tiltUp = (128.0 - (color >> 8 & 0xFF)) * light.y;
                    relief[i] = pixels[i * 4 + 3] & 0xFF;
                    right[i] = tiltAlong + tiltUp;
                    wrong[i] = tiltUp - tiltAlong;
                    guessed[i] = block.getColor(textureX, textureY) >> 8 & 0xFF;
                }
            }
        }
        double link = correlation(relief, right);
        double mirroredLink = correlation(relief, wrong);
        ModConstants.LOGGER.info("[Test client] Lien du relief et de la pente : {}, sens horizontal a l'envers : {}, lumiere de cote : {}.",
                String.format(Locale.ROOT, "%.2f", link), String.format(Locale.ROOT, "%.2f", mirroredLink), String.format(Locale.ROOT, "%.2f", bestSide));
        ClientTestContext.require(link >= MIN_RELIEF_LINK, String.format(Locale.ROOT, "Le relief ne suit pas la texture de normales : %.2f.", link));
        ClientTestContext.require(link > mirroredLink + MIN_SENSE_GAP,
                String.format(Locale.ROOT, "Le sens horizontal du relief n'est pas prouve : %.2f contre %.2f.", link, mirroredLink));
        ClientTestContext.require(Math.abs(correlation(relief, guessed)) < link, "Le relief suit la luminance de la texture.");
    }

    private static double correlation(double[] a, double[] b) {
        double meanA = 0.0;
        double meanB = 0.0;
        for (int i = 0; i < a.length; i++) {
            meanA += a[i] / a.length;
            meanB += b[i] / b.length;
        }
        double cross = 0.0;
        double squareA = 0.0;
        double squareB = 0.0;
        for (int i = 0; i < a.length; i++) {
            cross += (a[i] - meanA) * (b[i] - meanB);
            squareA += (a[i] - meanA) * (a[i] - meanA);
            squareB += (b[i] - meanB) * (b[i] - meanB);
        }
        return cross / Math.sqrt(squareA * squareB);
    }

    /** Pose un bloc juste sous chaque dalle autour du joueur, ou les retire. */
    private static void setUnderNeons(ClientTestContext context, boolean present) {
        if (present) {
            UNDER_NEONS.clear();
            Identifier id = LookProfiles.of(BackroomsConstants.LEVEL_ZERO_WORLD).neons().block();
            Block neon = Registries.BLOCK.get(id);
            BlockPos center = context.player().getBlockPos();
            for (BlockPos pos : BlockPos.iterate(center.add(-SEARCH, -2, -SEARCH), center.add(SEARCH, 8, SEARCH))) {
                if (context.world().getBlockState(pos).isOf(neon)) {
                    UNDER_NEONS.add(pos.down());
                }
            }
            ClientTestContext.require(!UNDER_NEONS.isEmpty(), "Aucune dalle autour du joueur.");
        }
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            for (BlockPos pos : UNDER_NEONS) {
                world.setBlockState(pos, present ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
            }
        });
    }

    private static int[] pixels(NativeImage image) {
        int[] copy = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                copy[y * image.getWidth() + x] = image.getColor(x, y);
            }
        }
        return copy;
    }

    private static double luminance(int abgr) {
        return 0.299 * (abgr & 0xFF) + 0.587 * (abgr >> 8 & 0xFF) + 0.114 * (abgr >> 16 & 0xFF);
    }

    /** La part de l'image qui a perdu au moins ceci de luminance depuis la premiere capture. */
    private static double share(NativeImage image, double drop) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (luminance(before[y * image.getWidth() + x]) - luminance(image.getColor(x, y)) >= drop) {
                    count++;
                }
            }
        }
        return count / (double) (image.getWidth() * image.getHeight());
    }

    /** La part de l'image qui depasse ses voisins des deux cotes : les lignes claires, ni la lumiere qui varie lentement, ni les dalles. */
    private static double ridges(NativeImage image) {
        int count = 0;
        int area = 0;
        for (int y = RIDGE_SPAN; y < image.getHeight() - RIDGE_SPAN; y++) {
            for (int x = RIDGE_SPAN; x < image.getWidth() - RIDGE_SPAN; x++) {
                double here = luminance(image.getColor(x, y));
                double across = (luminance(image.getColor(x - RIDGE_SPAN, y)) + luminance(image.getColor(x + RIDGE_SPAN, y))) * 0.5;
                double along = (luminance(image.getColor(x, y - RIDGE_SPAN)) + luminance(image.getColor(x, y + RIDGE_SPAN))) * 0.5;
                if (here < MAX_RIDGE && (here - across >= RIDGE || here - along >= RIDGE)) {
                    count++;
                }
                area++;
            }
        }
        return count / (double) area;
    }

    /** La part de l'image dont la luminance a change d'au moins {@link #SHINING} depuis cette capture, dans un sens ou dans l'autre. */
    private static double moved(NativeImage image, int[] first) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (Math.abs(luminance(first[y * image.getWidth() + x]) - luminance(image.getColor(x, y))) >= SHINING) {
                    count++;
                }
            }
        }
        return count / (double) (image.getWidth() * image.getHeight());
    }

    /** La distance a un mur dans une direction, depuis les yeux du joueur, ou 0 s'il n'y en a pas assez pres. */
    private static double wallDistance(ClientTestContext context, double yaw) {
        Vec3d eye = context.player().getEyePos();
        for (double d = 0.5; d < SEARCH * 2; d += 0.1) {
            BlockPos pos = BlockPos.ofFloored(eye.x - Math.sin(yaw) * d, eye.y, eye.z + Math.cos(yaw) * d);
            if (context.world().getBlockState(pos).isOpaqueFullCube(context.world(), pos)) {
                return d;
            }
        }
        return 0.0;
    }

    /** Le joueur vient a cinq blocs d'un mur, avec de la moquette libre entre eux. */
    private static void farFromWall(ClientTestContext context) {
        nearSpot = context.player().getPos();
        float kept = -1.0f;
        double keptDistance = 0.0;
        for (float direction : DIRECTIONS) {
            double distance = wallDistance(context, Math.toRadians(direction));
            if (distance >= FOOT_DISTANCE) {
                kept = direction;
                keptDistance = distance;
                break;
            }
        }
        ClientTestContext.require(kept >= 0.0f, "Aucun mur a plus de cinq blocs autour du joueur.");
        footYaw = kept;
        double yaw = Math.toRadians(kept);
        Vec3d feet = nearSpot.add(-Math.sin(yaw) * (keptDistance - FOOT_DISTANCE), 0.0, Math.cos(yaw) * (keptDistance - FOOT_DISTANCE));
        moveTo(context, feet, kept);
    }

    private static void backNearWall(ClientTestContext context) {
        moveTo(context, nearSpot, wallYaw);
    }

    private static void moveTo(ClientTestContext context, Vec3d feet, float yaw) {
        context.onServer(server -> {
            ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            ClientTestContext.serverPlayer(server).teleport(levelZero, feet.x, feet.y, feet.z, yaw, 0.0f);
        });
    }

    /**
     * La moquette prend sa lumiere ou le regard s'arrete dans son epaisseur, sauf sous le mur qui la borde, ou sa surface n'a pas de tuile :
     * sa lumiere y tomberait a celle de la zone, en tirets au pied de la plinthe.
     */
    private static void requireLitFoot(NativeImage image) {
        int middle = image.getHeight() / 2;
        int width = image.getWidth();
        double[] band = new double[(FOOT_BAND_END - FOOT_BAND_START) * width / 2];
        int count = 0;
        for (int y = middle + FOOT_BAND_START; y < middle + FOOT_BAND_END; y++) {
            for (int x = width / 4; x < width * 3 / 4; x++) {
                band[count++] = luminance(image.getColor(x, y));
            }
        }
        double[] sorted = Arrays.copyOf(band, count);
        Arrays.sort(sorted);
        double floor = sorted[count / 2] * MIN_FOOT_LIGHT;
        int dark = 0;
        for (int i = 0; i < count; i++) {
            dark += band[i] < floor ? 1 : 0;
        }
        double share = dark / (double) count;
        ModConstants.LOGGER.info("[Test client] Pied du mur : {} % de pixels sombres.", percent(share));
        ClientTestContext.require(share <= MAX_DARK_FOOT, "Des trous de lumiere au pied du mur : " + percent(share) + " % de la bande.");
    }

    /** Le contraste local du milieu de l'image, en bas : le sol, sans le bruit du grain. */
    private static double contrast(NativeImage image) {
        double sum = 0.0;
        int count = 0;
        for (int y = image.getHeight() / 2; y < image.getHeight() * 7 / 8; y += 2) {
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                sum += Math.abs(luminance(image.getColor(x, y)) - luminance(image.getColor(x - 1, y)));
                count++;
            }
        }
        return sum / count;
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.3f", share * 100.0);
    }
}
