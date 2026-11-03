package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.lwjgl.opengl.GL20;

import net.minecraft.block.Blocks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightBounces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookUniformUploader;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * La lumiere des paliers hauts, dans un vrai client : l'ombre d'un corps sur le terrain et le palier Ultra, tout ce qui part en
 * rayons. Un corps fait de l'ombre en High et en Ultra, pas en Low, et son ombre part avec lui. En Ultra, chaque surface a son
 * rebond, le sol et le plafond ne refletent rien, et apres un bloc pose les rebonds reviennent a ce que donne un recalcul complet.
 */
final class TracedLightClientTests {

    private static final Identifier AREA_LIGHT = Identifier.of(ModConstants.MOD_ID, "shaders/include/area_light.glsl");
    private static final String AREA_MAIN = """
            uniform vec4 gtgs_TestPoint[64];
            uniform vec4 gtgs_TestNormal[64];
            out vec4 fragColor;
            void main() {
                int i = int(gl_FragCoord.x);
                vec3 point = gtgs_TestPoint[i].xyz;
                vec3 toLight = clamp(point, vec3(0.0, 10.0, 0.0), vec3(1.0, 10.0, 1.0)) - point;
                fragColor = vec4(gtgs_received(toLight, vec3(0.0, -1.0, 0.0), gtgs_TestNormal[i].xyz, gtgs_TestPoint[i].w, gtgs_TestNormal[i].w), 0.0, 0.0, 1.0);
            }
            """;
    private static final float AREA_TOLERANCE = 2.0e-4f;
    /** La moitie basse de l'image, sous les pieds du corps : du sol, jamais le corps lui-meme. */
    private static final double FLOOR_TOP = 0.62;
    /** Assombri d'au moins ceci, un pixel du sol est dans l'ombre. */
    private static final double SHADED = 0.9;
    private static final double MIN_SHADOW_SHARE = 0.005;
    private static final double MAX_STRAY_SHARE = 0.0005;
    /** Part de leur moyenne dont les rebonds peuvent bouger pendant un calcul : un pilier n'en change que le voisinage. */
    private static final double BOUNCE_DRIFT = 0.01;
    /** Ce que couper les reflets peut changer au plus a une surface mate, en luminance moyenne. */
    private static final double MAX_MATTE_CHANGE = 0.5;
    /** Luminance moyenne d'un corps sous les dalles, au plus. */
    private static final double MAX_BODY = 150.0;
    /** Ce que le second rebond ajoute au moins au premier. */
    private static final double SECOND_BOUNCE = 1.02;
    /** Luminance d'un plafond eclaire par ses seuls rebonds : le fond du profil seul reste bien en dessous. */
    private static final double MIN_CEILING = 40.0;
    /** La lumiere qui a tourne dans une piece est plus rouge d'autant, et moins bleue d'autant, que celle d'une surface sous une dalle. */
    private static final double MIN_WARMER = 0.02;
    private static final double LIT_BAND = 12.0;
    /** Le plafond, presque blanc dans sa texture, doit toute sa couleur aux rebonds : ambre, sans quoi ils n'ont que celle des blocs. */
    private static final double MAX_CEILING_BLUE = 0.45;
    /** Sous les dalles, la lumiere d'une surface a sa limite : sans elle, le sol sature. */
    private static final double MAX_LIT_FLOOR = 190.0;
    /** Ce que le contraste local de l'image peut gagner au plus en quittant la lumiere tracee, a vue egale : les textures sont calmes partout. */
    private static final double CALMER = 1.25;
    private static final int QUIET_FRAMES = 40;
    private static final int SETTLE_TICKS = 2400;
    private static final BlockPos[] PILLAR = {new BlockPos(0, 1, 4), new BlockPos(0, 2, 4), new BlockPos(0, 3, 4)};
    private static int quiet;
    private static int[] before;
    private static Long2LongOpenHashMap bounces;
    private static Long2LongOpenHashMap direct;
    private static double bounceMean;
    private static double[] ceilingColor;
    private static double tracedDetail;
    private static VillagerEntity body;

    private TracedLightClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(bodyShadows(), tracedLight());
    }

    private static boolean settled(ClientTestContext context) {
        quiet = LightSurfaces.pending() == null && LightBounces.settled() && LightCopy.idle(context.world()) ? quiet + 1 : 0;
        if (quiet >= QUIET_FRAMES) {
            // Le compte repart de zero : sinon l'attente suivante passerait a sa premiere image, avant meme le debut du recalcul.
            quiet = 0;
            return true;
        }
        return false;
    }

    private static void tier(ClientTestContext context, LookQuality quality) {
        LookResources.applySettings(LookSettings.current().withQuality(quality.key()), context.client());
    }

    private static void look(ClientTestContext context, float yaw, float pitch) {
        context.player().setYaw(yaw);
        context.player().setPitch(pitch);
    }

    private static ClientScenario arrival(String name, LookQuality quality) {
        return ClientScenario.named(name)
                .run("palier", context -> tier(context, quality))
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .ticks(40)
                .await("lumiere au repos", TracedLightClientTests::settled, SETTLE_TICKS);
    }

    private static void spawn(ClientTestContext context) {
        Vec3d feet = context.player().getPos();
        double yaw = Math.toRadians(20.0);
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            VillagerEntity villager = EntityType.VILLAGER.create(world);
            villager.setAiDisabled(true);
            villager.refreshPositionAndAngles(feet.x - Math.sin(yaw) * 4.0, feet.y, feet.z + Math.cos(yaw) * 4.0, 200.0f, 0.0f);
            world.spawnEntity(villager);
            body = villager;
        });
    }

    private static void removeBody(ClientTestContext context) {
        context.onServer(server -> {
            if (body != null) {
                body.discard();
                body = null;
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

    /** La part du sol assombrie depuis la premiere image, dans le bas de l'image. */
    private static double shadedShare(NativeImage image) {
        int top = (int) (FLOOR_TOP * image.getHeight());
        int shaded = 0;
        int total = 0;
        for (int y = top; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                total++;
                if (luminance(image.getColor(x, y)) < luminance(before[y * image.getWidth() + x]) * SHADED) {
                    shaded++;
                }
            }
        }
        return shaded / (double) total;
    }

    private static void requireLitBody(NativeImage image) {
        double sum = 0.0;
        int changed = 0;
        for (int y = image.getHeight() / 8; y < image.getHeight() / 2; y++) {
            for (int x = image.getWidth() * 3 / 8; x < image.getWidth() * 5 / 8; x++) {
                int was = before[y * image.getWidth() + x];
                if (Math.abs(luminance(image.getColor(x, y)) - luminance(was)) > 24.0) {
                    sum += luminance(image.getColor(x, y));
                    changed++;
                }
            }
        }
        ClientTestContext.require(changed > 100, "Le corps n'est pas dans l'image (" + changed + " pixels).");
        ClientTestContext.require(sum / changed > 40.0, String.format(Locale.ROOT, "Le corps est noir sous les dalles : luminance moyenne %.1f.", sum / changed));
        ModConstants.LOGGER.info("[Test client] Lumiere du corps : {}.", String.format(Locale.ROOT, "%.1f", sum / changed));
        ClientTestContext.require(sum / changed < MAX_BODY, String.format(Locale.ROOT, "Le corps brule sous les dalles : luminance moyenne %.1f.", sum / changed));
    }

    private static ClientScenario bodyShadowed(ClientScenario scenario, String tier) {
        return scenario
                .run("regard vers le sol", context -> look(context, 20.0f, 20.0f))
                .frames(8)
                .capture("le sol sans corps, " + tier, image -> before = pixels(image))
                .run("un corps arrive", TracedLightClientTests::spawn)
                .ticks(10)
                .frames(8)
                .capture("son ombre sur le sol, " + tier, image -> {
                    double share = shadedShare(image);
                    ModConstants.LOGGER.info("[Test client] Ombre d'un corps en {} : {} % du sol assombri.", tier, String.format(Locale.ROOT, "%.2f", share * 100.0));
                    ClientTestContext.require(share >= MIN_SHADOW_SHARE, String.format(Locale.ROOT, "Pas d'ombre de corps en %s : %.3f %% du sol assombri.", tier, share * 100.0));
                    requireLitBody(image);
                })
                .run("le corps part", TracedLightClientTests::removeBody)
                .ticks(10)
                .frames(8)
                .capture("son ombre part avec lui, " + tier, image -> {
                    double share = shadedShare(image);
                    ClientTestContext.require(share <= MAX_STRAY_SHARE, String.format(Locale.ROOT, "Il reste %.3f %% du sol assombri en %s.", share * 100.0, tier));
                });
    }

    private static ClientScenario bodyShadows() {
        ClientScenario scenario = bodyShadowed(arrival("body_shadows", LookQuality.HIGH), "High");
        scenario = scenario
                .run("palier Ultra", context -> tier(context, LookQuality.ULTRA))
                .ticks(4)
                .await("Ultra au repos", TracedLightClientTests::settled, SETTLE_TICKS);
        scenario = bodyShadowed(scenario, "Ultra");
        return scenario
                .run("palier Low", context -> tier(context, LookQuality.LOW))
                .ticks(4)
                .await("Low au repos", TracedLightClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("le sol sans corps, Low", image -> before = pixels(image))
                .run("un corps arrive, Low", TracedLightClientTests::spawn)
                .ticks(10)
                .frames(8)
                .capture("aucune ombre de corps en Low", image -> {
                    double share = shadedShare(image);
                    ClientTestContext.require(share <= MAX_STRAY_SHARE, String.format(Locale.ROOT, "Ombre d'un corps en Low : %.3f %% du sol assombri.", share * 100.0));
                })
                .cleanup(TracedLightClientTests::removeBody)
                .cleanup(context -> {
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Le calcul d'une dalle, sur le processeur graphique, contre les vecteurs communs que Java suit aussi. */
    private static void requireAreaLightMatches(ClientTestContext context) {
        JsonArray vectors;
        try {
            vectors = JsonParser.parseString(Files.readString(Path.of(System.getProperty("gtgsbackrooms.clientTest.areaVectors")), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("vectors");
        } catch (IOException exception) {
            throw new IllegalStateException("Vecteurs de la dalle illisibles.", exception);
        }
        String source = "#version 150\n" + LookGradeClientTests.library(context.client(), AREA_LIGHT) + "\n" + AREA_MAIN;
        float[] pixels = LookGradeClientTests.render(source, 1, program -> {
            for (int i = 0; i < vectors.size(); i++) {
                JsonObject v = vectors.get(i).getAsJsonObject();
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestPoint[" + i + "]"), v.get("x").getAsFloat(), v.get("y").getAsFloat(), v.get("z").getAsFloat(),
                        v.get("reach").getAsFloat());
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "gtgs_TestNormal[" + i + "]"), v.get("nx").getAsFloat(), v.get("ny").getAsFloat(), v.get("nz").getAsFloat(),
                        v.get("cone").getAsFloat());
            }
        });
        for (int i = 0; i < vectors.size(); i++) {
            float expected = vectors.get(i).getAsJsonObject().get("received").getAsFloat();
            ClientTestContext.require(Math.abs(pixels[i * 4] - expected) <= AREA_TOLERANCE,
                    String.format(Locale.ROOT, "Vecteur %d : %.5f au lieu de %.5f.", i, pixels[i * 4], expected));
        }
    }

    private static void requireTraced(float expected) {
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        float value = GL20.glGetUniformf(program, GL20.glGetUniformLocation(program, LookUniformUploader.TRACED));
        ClientTestContext.require(value == expected, String.format(Locale.ROOT, "gtgs_Traced vaut %.0f au lieu de %.0f.", value, expected));
    }

    /** Une lumiere tracee est lisse : le shader n'a plus de grille des texels, alors que le reglage du joueur reste la sienne. */
    private static void requireNoGrid() {
        int program = GameRenderer.getRenderTypeSolidProgram().getGlRef();
        float[] params = new float[4];
        GL20.glGetUniformfv(program, GL20.glGetUniformLocation(program, LookUniformUploader.PARAMS), params);
        ClientTestContext.require(params[0] == 0.0f, String.format(Locale.ROOT, "Grille de %.0f pas sous la lumiere tracee.", params[0]));
        ClientTestContext.require(LookSettings.current().pixelShadows() > 0, "Le reglage du joueur a change.");
    }

    /** Le contraste local du haut de l'image, en parts de sa lumiere : ce qu'on voit du papier peint, quelle que soit l'exposition du palier. */
    private static double localContrast(NativeImage image) {
        double sum = 0.0;
        double light = 0.0;
        for (int y = image.getHeight() / 4; y < image.getHeight() / 2; y += 2) {
            for (int x = 1; x < image.getWidth(); x++) {
                double here = luminance(image.getColor(x, y));
                sum += Math.abs(here - luminance(image.getColor(x - 1, y)));
                light += here;
            }
        }
        return sum / Math.max(light, 1.0);
    }

    private static void requireBounces() {
        byte[] read = LightBounces.read();
        long sum = 0L;
        int ready = 0;
        for (int i = 0; i < read.length; i += 4) {
            if (read[i + 3] != 0) {
                ready++;
                sum += (read[i] & 0xFF) + (read[i + 1] & 0xFF) + (read[i + 2] & 0xFF);
            }
        }
        ClientTestContext.require(ready > 64 * 1000, "Seulement " + ready / 64 + " tuiles de rebond calculees.");
        ClientTestContext.require(sum / (3.0 * ready) > 8.0, String.format(Locale.ROOT, "Rebonds presque nuls : %.2f sur 255 en moyenne.", sum / (3.0 * ready)));
    }

    private static double meanBounce() {
        return mean(LightBounces.read());
    }

    private static double mean(byte[] read) {
        long sum = 0L;
        for (int i = 0; i < read.length; i += 4) {
            sum += read[i + 1] & 0xFF;
        }
        return sum * 4.0 / read.length;
    }

    /** Le second tour relit le premier : deux rebonds renvoient plus de lumiere qu'un seul. */
    private static void requireSecondBounce() {
        double one = mean(LightBounces.readFirstRound());
        double two = meanBounce();
        ModConstants.LOGGER.info("[Test client] Rebonds : {} au premier tour, {} au second.", String.format(Locale.ROOT, "%.3f", one), String.format(Locale.ROOT, "%.3f", two));
        ClientTestContext.require(one > 0.0 && two > one * SECOND_BOUNCE, String.format(Locale.ROOT, "Le second rebond n'ajoute rien : %.3f puis %.3f.", one, two));
    }

    /** La couleur moyenne de ce qui, dans l'image, est autour de cette luminance : ni les dalles, ni le fond des coins. */
    private static double[] litColor(NativeImage image, double around) {
        double[] sum = new double[3];
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int abgr = image.getColor(x, y);
                if (Math.abs(luminance(abgr) - around) <= LIT_BAND) {
                    sum[0] += abgr & 0xFF;
                    sum[1] += abgr >> 8 & 0xFF;
                    sum[2] += abgr >> 16 & 0xFF;
                    count++;
                }
            }
        }
        return new double[] {sum[0] / Math.max(count, 1), sum[1] / Math.max(count, 1), sum[2] / Math.max(count, 1)};
    }

    /** La teinte suit la lumiere : le plafond, qui ne vit que de rebonds, est plus ambre que le sol sous les dalles, ou toute lumiere blanchit. */
    private static void requireColorFollowsLight(NativeImage image) {
        double median = medianLuminance(image);
        double[] floor = litColor(image, median);
        double redGain = ceilingColor[0] / ceilingColor[1] - floor[0] / floor[1];
        double blueLoss = floor[2] / floor[1] - ceilingColor[2] / ceilingColor[1];
        ModConstants.LOGGER.info("[Test client] Plafond {}, sol {} : rouge {}, bleu {}.", Arrays.toString(ceilingColor), Arrays.toString(floor),
                String.format(Locale.ROOT, "%+.3f", redGain), String.format(Locale.ROOT, "%+.3f", blueLoss));
        ClientTestContext.require(ceilingColor[2] / ceilingColor[1] <= MAX_CEILING_BLUE,
                String.format(Locale.ROOT, "Le plafond n'est pas ambre : bleu %.3f du vert.", ceilingColor[2] / ceilingColor[1]));
        ClientTestContext.require(median <= MAX_LIT_FLOOR, String.format(Locale.ROOT, "Le sol sature sous les dalles : %.0f.", median));
        ClientTestContext.require(redGain >= MIN_WARMER && blueLoss >= MIN_WARMER,
                String.format(Locale.ROOT, "La lumiere des rebonds n'a pas sa couleur : rouge %+.3f, bleu %+.3f.", redGain, blueLoss));
    }

    /** La luminance mediane de l'image : une dalle dans le champ n'y pese rien. */
    private static double medianLuminance(NativeImage image) {
        int[] histogram = new int[256];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                histogram[(int) luminance(image.getColor(x, y))]++;
            }
        }
        int half = image.getWidth() * image.getHeight() / 2;
        int seen = 0;
        for (int level = 0; level < histogram.length; level++) {
            seen += histogram[level];
            if (seen >= half) {
                return level;
            }
        }
        return histogram.length - 1;
    }

    /** Un calcul en cours ne se voit pas : le premier tour ecrit a part, et le cache lu par le terrain garde ses valeurs. */
    private static void requireSteadyBounces() {
        double now = meanBounce();
        ClientTestContext.require(Math.abs(now - bounceMean) <= bounceMean * BOUNCE_DRIFT,
                String.format(Locale.ROOT, "Les rebonds bougent pendant le calcul : %.3f puis %.3f.", bounceMean, now));
    }

    /** Chaque rebond doit revenir identique au bit pres : poser un bloc suit ce que donnerait un recalcul complet. */
    private static void requireSameAsFullBake(String what) {
        int directDiffer = differing(LightSurfaces.fingerprints(), direct);
        Long2LongOpenHashMap fresh = LightSurfaces.bounceFingerprints();
        ClientTestContext.require(!fresh.isEmpty(), "Aucun rebond : " + what + ".");
        int differ = differing(fresh, bounces);
        ModConstants.LOGGER.info("[Test client] {} : {} rebond(s) et {} tuile(s) directe(s) sur {} different du recalcul complet.", what, differ, directDiffer, fresh.size());
        ClientTestContext.require(directDiffer == 0 && differ == 0, what + " : " + differ + " rebond(s) et " + directDiffer + " tuile(s) directe(s) sur " + fresh.size()
                + " different du recalcul complet.");
    }

    private static int differing(Long2LongOpenHashMap fresh, Long2LongOpenHashMap kept) {
        int differ = 0;
        for (Long2LongOpenHashMap.Entry entry : fresh.long2LongEntrySet()) {
            if (!kept.containsKey(entry.getLongKey()) || kept.get(entry.getLongKey()) != entry.getLongValue()) {
                differ++;
            }
        }
        return differ;
    }

    private static void setPillar(ClientTestContext context, boolean present) {
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
            for (BlockPos pos : PILLAR) {
                world.setBlockState(pos, present ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
            }
        });
    }

    /** L'ecart moyen de luminance d'une bande de l'image : signe, pour que le grain du film, qui change a chaque image, s'y annule. */
    private static double difference(NativeImage image, double top, double bottom) {
        double sum = 0.0;
        int count = 0;
        for (int y = (int) (top * image.getHeight()); y < (int) (bottom * image.getHeight()); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                sum += luminance(before[y * image.getWidth() + x]) - luminance(image.getColor(x, y));
                count++;
            }
        }
        return sum / count;
    }

    private static ClientScenario tracedLight() {
        return arrival("traced_light", LookQuality.ULTRA)
                .check("la dalle du shader suit les vecteurs communs", TracedLightClientTests::requireAreaLightMatches)
                .frames(2)
                .check("la vue du joueur trace", context -> requireTraced(1.0f))
                .check("la lumiere tracee n'a pas de grille", context -> requireNoGrid())
                .check("chaque surface a son rebond", context -> requireBounces())
                .check("deux rebonds valent plus qu'un", context -> requireSecondBounce())
                .run("regard vers le plafond", context -> look(context, 200.0f, -80.0f))
                .frames(8)
                .capture("le plafond ne vit que de ses rebonds", image -> {
                    double ceiling = medianLuminance(image);
                    ModConstants.LOGGER.info("[Test client] Plafond en rebonds seuls : luminance {}.", String.format(Locale.ROOT, "%.0f", ceiling));
                    ClientTestContext.require(ceiling >= MIN_CEILING, String.format(Locale.ROOT, "Le terrain ne lit pas les rebonds : plafond a %.0f.", ceiling));
                    ceilingColor = litColor(image, ceiling);
                })
                .run("regard droit devant", context -> look(context, 200.0f, 0.0f))
                .frames(8)
                .capture("le sol avec les reflets", image -> {
                    before = pixels(image);
                    requireColorFollowsLight(image);
                    tracedDetail = localContrast(image);
                })
                .run("reflets coupes", context -> LookResources.applySettings(LookSettings.current().with(LookOption.SHEEN, false), context.client()))
                .ticks(4)
                .await("repos sans reflets", TracedLightClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("ni le sol ni le plafond ne refletent rien", image -> {
                    double floor = difference(image, 0.7, 1.0);
                    double ceiling = difference(image, 0.0, 0.3);
                    ModConstants.LOGGER.info("[Test client] Reflets coupes : sol {}, plafond {}.", String.format(Locale.ROOT, "%.2f", floor),
                            String.format(Locale.ROOT, "%.2f", ceiling));
                    ClientTestContext.require(Math.abs(floor) < MAX_MATTE_CHANGE, String.format(Locale.ROOT, "La moquette reflete : %.2f.", floor));
                    ClientTestContext.require(Math.abs(ceiling) < MAX_MATTE_CHANGE, String.format(Locale.ROOT, "Le plafond reflete : %.2f.", ceiling));
                })
                .run("reflets remis", context -> LookResources.applySettings(LookSettings.current().with(LookOption.SHEEN, true), context.client()))
                .ticks(4)
                .await("repos avec reflets", TracedLightClientTests::settled, SETTLE_TICKS)
                .run("releve des rebonds avant le pilier", context -> bounceMean = meanBounce())
                .run("un pilier arrive", context -> setPillar(context, true))
                .await("la copie voit le pilier", context -> LightCopy.copiedBlock(context.world(), PILLAR[2]) != 0, 100)
                .await("le premier tour commence", context -> LightBounces.inFirstRound(), SETTLE_TICKS)
                .frames(20)
                .check("rien ne retombe pendant le calcul", context -> requireSteadyBounces())
                .ticks(4)
                .await("rebonds au repos apres le pilier", TracedLightClientTests::settled, SETTLE_TICKS)
                .run("releve des rebonds", context -> {
                    bounces = LightSurfaces.bounceFingerprints();
                    direct = LightSurfaces.fingerprints();
                })
                .run("tout recalculer", context -> LightVolume.rebake())
                .ticks(4)
                .await("recalcul au repos", TracedLightClientTests::settled, SETTLE_TICKS)
                .check("les rebonds suivent le bloc pose", context -> requireSameAsFullBake("pilier pose"))
                .run("palier High", context -> tier(context, LookQuality.HIGH))
                .ticks(4)
                .await("High au repos", TracedLightClientTests::settled, SETTLE_TICKS)
                .frames(8)
                .capture("les textures sont aussi calmes en High", image -> {
                    double high = localContrast(image);
                    ModConstants.LOGGER.info("[Test client] Contraste des textures : Ultra {}, High {}.", String.format(Locale.ROOT, "%.4f", tracedDetail),
                            String.format(Locale.ROOT, "%.4f", high));
                    ClientTestContext.require(high < tracedDetail * CALMER, String.format(Locale.ROOT, "Textures plus marquees en High qu'en Ultra : %.4f contre %.4f.", high,
                            tracedDetail));
                })
                .cleanup(context -> {
                    setPillar(context, false);
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }
}
