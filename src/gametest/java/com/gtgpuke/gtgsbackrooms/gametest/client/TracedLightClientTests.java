package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.lwjgl.opengl.GL20;

import net.minecraft.block.Blocks;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.render.client.EntityShadows;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightBounces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightCopy;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightSurfaces;
import com.gtgpuke.gtgsbackrooms.core.render.client.LightVolume;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookResources;
import com.gtgpuke.gtgsbackrooms.core.render.client.LookUniformUploader;
import com.gtgpuke.gtgsbackrooms.core.render.light.ShadowCasters;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookOption;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookQuality;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;

/**
 * La lumiere des paliers hauts, dans un vrai client : l'ombre d'un corps sur le terrain et le palier Ultra, tout ce qui part en
 * rayons. Un corps porte une ombre en High et en Ultra, pas en Low (seule son ombre de contact, a ses pieds), et son ombre part avec
 * lui ; dans le Level 0, elle remplace le disque d'ombre de vanilla, que l'Overworld garde. En Ultra, chaque surface a son rebond, le
 * sol et le plafond ne refletent rien, et apres un bloc pose les rebonds reviennent a ce que donne un recalcul complet.
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
    private static final Identifier BODY_BOX = Identifier.of(ModConstants.MOD_ID, "shaders/include/body_box.glsl");
    /** Cas des vecteurs de la boite, au plus : six tableaux de quatre valeurs par cas tiennent dans les uniformes d'un fragment. */
    private static final int BOX_CASES = 40;
    private static final String BOX_MAIN = """
            uniform vec4 gtgs_TestStart[40];
            uniform vec4 gtgs_TestToTarget[40];
            uniform vec4 gtgs_TestCenter[40];
            uniform vec4 gtgs_TestU[40];
            uniform vec4 gtgs_TestV[40];
            uniform vec4 gtgs_TestW[40];
            out vec4 fragColor;
            void main() {
                int i = int(gl_FragCoord.x);
                bool hit = i < 40 && gtgs_hitsBox(gtgs_TestStart[i].xyz, gtgs_TestToTarget[i].xyz, gtgs_TestCenter[i].xyz, gtgs_TestU[i].xyz, gtgs_TestV[i].xyz,
                        gtgs_TestW[i].xyz, gtgs_TestStart[i].w);
                fragColor = vec4(hit ? 1.0 : 0.0, 0.0, 0.0, 1.0);
            }
            """;
    /** La moitie basse de l'image, sous les pieds du corps : du sol, jamais le corps lui-meme. */
    private static final double FLOOR_TOP = 0.62;
    /** Assombri d'au moins ceci, un pixel du sol est dans l'ombre. */
    private static final double SHADED = 0.9;
    /** Ce que le grain du film change d'une image a l'autre, en luminance : un pixel sombre ne s'assombrit pas pour si peu. */
    private static final double GRAIN = 6.0;
    private static final double MIN_SHADOW_SHARE = 0.005;
    private static final double MAX_STRAY_SHARE = 0.0005;
    /**
     * Autour des pieds du corps, en parts de l'image : a quatre blocs devant le joueur, qui regarde 20 degres sous l'horizon, ils tombent
     * au milieu de l'image, au-dessus du sol mesure sous eux. C'est la que vanilla pose son disque d'ombre.
     */
    private static final double FEET_LEFT = 0.38;
    private static final double FEET_RIGHT = 0.62;
    private static final double FEET_TOP = 0.42;
    /**
     * Ce que le disque d'ombre de vanilla retire au moins a la lumiere autour des pieds, et ce qu'elle peut perdre sans lui. Mesure : 2,9 %
     * dans l'Overworld ; dans le Level 0, 0,01 % sans disque, 0,5 % avec (la lumiere de vanilla y est faible, et le disque pale).
     */
    private static final double MIN_DISC_DROP = 0.015;
    private static final double MAX_DISC_DROP = 0.0015;
    /** Cubes d'une statue d'armure aux bras visibles, au moins : tete, corps, bras, jambes, baguettes et base. */
    private static final int MIN_STAND_BOXES = 8;
    /** Haut de la tete d'une statue d'armure, et l'ecart permis a ses bords. */
    private static final double STAND_HEIGHT = 1.876;
    private static final double STAND_EDGE = 0.05;
    /** Ce que le bras tendu deplace au moins un cube, en blocs : un bras de douze pixels qui passe de baisse a tendu. */
    private static final double MIN_POSE_MOVE = 0.15;
    /**
     * Le second demi-axe d'un cube de villageois, au plus, en blocs : ses cubes ont au plus huit pixels de large (un quart de bloc de
     * demi-axe) ; le bord de son chapeau, une plaque de seize pixels de cote, mais transparente dans sa texture, en aurait un d'un
     * demi-bloc.
     */
    private static final double MAX_VILLAGER_WIDTH = 0.4;
    /** Le second demi-axe d'un cube, dans ses valeurs : apres le centre et le plus long. */
    private static final int SECOND_AXIS = 8;
    /** L'orientation donnee au joueur, en degres, et l'accord demande entre la largeur de son torse et le travers de son regard. */
    private static final float PLAYER_YAW = 30.0f;
    private static final double MIN_TORSO_ACROSS = 0.99;
    /** Le torse d'un joueur : quatre pixels de demi-largeur, a sa taille de 0,9375, a un bloc du sol environ. */
    private static final double TORSO_HALF_WIDTH = 0.234;
    private static final double TORSO_LOW = 0.75;
    private static final double TORSO_HIGH = 1.5;
    private static final double SIZE_EDGE = 0.01;
    private static final int STAND_BOTTOM = 1;
    private static final int STAND_TOP = 4;
    private static final int STAND_BOXES = 5;
    /** Part de l'image qu'assombrit au moins l'ombre de contact d'un corps proche, en Low. */
    private static final double MIN_CONTACT_SHARE = 0.001;
    /** Ecart de teinte (les trois composantes rapportees a leur moyenne) au-dela duquel un pixel montre autre chose : le corps lui-meme. */
    private static final double SAME_TINT = 0.12;
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
    private static int[] empty;
    private static final UUID[] STANDS = new UUID[2];
    private static UUID villager;
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
        return List.of(bodyShadows(), contactShadow(), shadowFollowsTheModel(), tracedLight());
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
        return arrival(ClientScenario.named(name), quality);
    }

    private static ClientScenario arrival(ClientScenario scenario, LookQuality quality) {
        return scenario
                .run("palier", context -> tier(context, quality))
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .await("terrain du Level 0 construit", context -> context.client().worldRenderer.getCompletedChunkCount() > 40, 1200)
                .ticks(40)
                .await("lumiere au repos", TracedLightClientTests::settled, SETTLE_TICKS);
    }

    private static void spawn(ClientTestContext context) {
        spawn(context, BackroomsConstants.LEVEL_ZERO_WORLD);
    }

    /** Un villageois immobile a quatre blocs devant le joueur, un peu sur sa droite. */
    private static void spawn(ClientTestContext context, RegistryKey<World> key) {
        Vec3d feet = context.player().getPos();
        double yaw = Math.toRadians(20.0);
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, key);
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
        return shadedShare(image, FLOOR_TOP);
    }

    /** La part assombrie depuis la premiere image, a partir de cette hauteur de l'image. */
    private static double shadedShare(NativeImage image, double fromTop) {
        int top = (int) (fromTop * image.getHeight());
        int shaded = 0;
        int total = 0;
        for (int y = top; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                total++;
                if (darker(image.getColor(x, y), before[y * image.getWidth() + x])) {
                    shaded++;
                }
            }
        }
        return shaded / (double) total;
    }

    /** La part de l'image assombrie depuis cette image d'avant sans changer de teinte : une ombre sur le decor, pas le corps qui le cache. */
    private static double shadowShare(NativeImage image, int[] reference) {
        int shaded = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int now = image.getColor(x, y);
                int was = reference[y * image.getWidth() + x];
                if (darker(now, was) && tint(now, was) < SAME_TINT) {
                    shaded++;
                }
            }
        }
        return shaded / (double) (image.getWidth() * image.getHeight());
    }

    /** Ce que l'image a perdu de lumiere autour des pieds du corps depuis l'image d'avant, en part de cette lumiere, hors du corps. */
    private static double feetDrop(NativeImage image) {
        double now = 0.0;
        double was = 0.0;
        for (int y = (int) (FEET_TOP * image.getHeight()); y < (int) (FLOOR_TOP * image.getHeight()); y++) {
            for (int x = (int) (FEET_LEFT * image.getWidth()); x < (int) (FEET_RIGHT * image.getWidth()); x++) {
                int at = y * image.getWidth() + x;
                if (tint(before[at], empty[at]) < SAME_TINT) {
                    now += luminance(image.getColor(x, y));
                    was += luminance(before[at]);
                }
            }
        }
        return 1.0 - now / was;
    }

    private static boolean darker(int now, int was) {
        double lit = luminance(was);
        return luminance(now) < Math.min(lit * SHADED, lit - GRAIN);
    }

    private static double tint(int now, int was) {
        double nowMean = ((now & 0xFF) + (now >> 8 & 0xFF) + (now >> 16 & 0xFF)) / 3.0 + 1.0;
        double wasMean = ((was & 0xFF) + (was >> 8 & 0xFF) + (was >> 16 & 0xFF)) / 3.0 + 1.0;
        double sum = 0.0;
        for (int shift = 0; shift <= 16; shift += 8) {
            sum += Math.abs((now >> shift & 0xFF) / nowMean - (was >> shift & 0xFF) / wasMean);
        }
        return sum;
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

    private static void entityShadows(ClientTestContext context, boolean shown) {
        context.client().options.getEntityShadows().setValue(shown);
    }

    /**
     * Un corps devant le joueur, l'option d'ombres des entites de vanilla coupee : il assombrit le decor de son ombre de contact dans le
     * Level 0, et seulement la ou il se dessine dans l'Overworld. Puis l'option : seul son disque peut assombrir l'image.
     */
    private static ClientScenario disc(ClientScenario scenario, String where, boolean kept) {
        return scenario
                .run("regard, " + where, context -> {
                    look(context, 20.0f, 20.0f);
                    entityShadows(context, false);
                })
                .frames(8)
                .capture("sans corps, " + where, image -> empty = pixels(image))
                .run("un corps arrive, " + where, context -> spawn(context, context.world().getRegistryKey()))
                .ticks(10)
                .frames(8)
                .capture(kept ? "aucune ombre de contact, " + where : "son ombre de contact, " + where, image -> {
                    before = pixels(image);
                    double share = shadowShare(image, empty);
                    ModConstants.LOGGER.info("[Test client] Ombre de contact, {} : {} % de l'image assombri.", where, String.format(Locale.ROOT, "%.3f", share * 100.0));
                    ClientTestContext.require(kept ? share <= MAX_STRAY_SHARE : share >= MIN_CONTACT_SHARE,
                            String.format(Locale.ROOT, "Ombre de contact %s, %s : %.3f %% de l'image assombri.", kept ? "dessinee" : "absente", where, share * 100.0));
                })
                .run("ombres d'entites, " + where, context -> entityShadows(context, true))
                .frames(8)
                .capture(kept ? "le disque de vanilla, " + where : "aucun disque de vanilla, " + where, image -> {
                    double drop = feetDrop(image);
                    ModConstants.LOGGER.info("[Test client] Disque d'ombre de vanilla, {} : {} % de lumiere en moins autour des pieds.", where,
                            String.format(Locale.ROOT, "%.2f", drop * 100.0));
                    ClientTestContext.require(kept ? drop >= MIN_DISC_DROP : Math.abs(drop) <= MAX_DISC_DROP,
                            String.format(Locale.ROOT, "Disque d'ombre de vanilla %s, %s : %.2f %% de lumiere en moins autour des pieds.", kept ? "absent" : "dessine", where,
                                    drop * 100.0));
                })
                .run("le corps part, " + where, TracedLightClientTests::removeBody)
                .ticks(10);
    }

    /**
     * L'ombre de contact d'un corps et le disque d'ombre de vanilla : l'Overworld n'a que le disque, le Level 0 n'a que l'ombre de
     * contact, jusqu'en Low, ou c'est la seule ombre des corps.
     */
    private static ClientScenario contactShadow() {
        ClientScenario scenario = ClientScenario.named("contact_shadow_replaces_disc")
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200);
        scenario = arrival(disc(scenario, "Overworld", true), LookQuality.LOW);
        return disc(scenario, "Level 0", false)
                .cleanup(TracedLightClientTests::removeBody)
                .cleanup(context -> {
                    entityShadows(context, true);
                    LevelZeroClientTests.defaultTier(context);
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    /** Les cubes de la forme d'un corps du client, ses pieds a l'origine. */
    private static ShadowCasters shapeOf(ClientTestContext context, UUID id) {
        ShadowCasters casters = new ShadowCasters();
        for (Entity entity : context.world().getEntities()) {
            if (entity instanceof LivingEntity living && living.getUuid().equals(id)) {
                EntityShadows.shape(living, 0.0, 0.0, 0.0, 1.0f, casters);
            }
        }
        ClientTestContext.require(casters.bodies() == 1, "Corps absent du client.");
        return casters;
    }

    private static boolean bodiesArrived(ClientTestContext context) {
        int found = 0;
        for (Entity entity : context.world().getEntities()) {
            UUID id = entity.getUuid();
            if (id.equals(STANDS[0]) || id.equals(STANDS[1]) || id.equals(villager)) {
                found++;
            }
        }
        return found == STANDS.length + 1;
    }

    private static double center(ShadowCasters shape, int box, int axis) {
        return shape.values()[ShadowCasters.BOUNDS_VALUES + box * ShadowCasters.BOX_VALUES + axis];
    }

    /** La plus grande distance d'un cube de la premiere forme au cube le plus proche de la seconde : ce que la pose a deplace. */
    private static double moved(ShadowCasters from, ShadowCasters to) {
        double farthest = 0.0;
        for (int k = 0; k < (int) from.values()[STAND_BOXES]; k++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < (int) to.values()[STAND_BOXES]; j++) {
                double dx = center(from, k, 0) - center(to, j, 0);
                double dy = center(from, k, 1) - center(to, j, 1);
                double dz = center(from, k, 2) - center(to, j, 2);
                nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            farthest = Math.max(farthest, nearest);
        }
        return farthest;
    }

    private static void spawnStands(ClientTestContext context) {
        Vec3d feet = context.player().getPos();
        context.onServer(server -> {
            ServerWorld world = ClientTestContext.serverWorld(server, World.OVERWORLD);
            for (int i = 0; i < STANDS.length; i++) {
                ArmorStandEntity stand = EntityType.ARMOR_STAND.create(world);
                NbtCompound arms = new NbtCompound();
                arms.putBoolean("ShowArms", true);
                stand.readCustomDataFromNbt(arms);
                if (i == 0) {
                    stand.setRightArmRotation(new EulerAngle(-90.0f, 0.0f, 0.0f));
                }
                stand.refreshPositionAndAngles(feet.x + (i == 0 ? 2.0 : -2.0), feet.y, feet.z + 3.0, 0.0f, 0.0f);
                world.spawnEntity(stand);
                STANDS[i] = stand.getUuid();
            }
            VillagerEntity resident = EntityType.VILLAGER.create(world);
            resident.setAiDisabled(true);
            resident.refreshPositionAndAngles(feet.x, feet.y, feet.z + 5.0, 0.0f, 0.0f);
            world.spawnEntity(resident);
            villager = resident.getUuid();
        });
    }

    private static void requireStandShapes(ClientTestContext context) {
        ShadowCasters raised = shapeOf(context, STANDS[0]);
        ShadowCasters lowered = shapeOf(context, STANDS[1]);
        float[] shape = raised.values();
        double move = moved(raised, lowered);
        ModConstants.LOGGER.info("[Test client] Forme d'une statue : {} cubes, de {} a {} ; bras tendu, un cube deplace de {} bloc.", (int) shape[STAND_BOXES],
                String.format(Locale.ROOT, "%.3f", shape[STAND_BOTTOM]), String.format(Locale.ROOT, "%.3f", shape[STAND_TOP]), String.format(Locale.ROOT, "%.3f", move));
        ClientTestContext.require(shape[STAND_BOXES] >= MIN_STAND_BOXES && lowered.values()[STAND_BOXES] == shape[STAND_BOXES],
                "Une statue n'a pas les cubes de son modele : " + (int) shape[STAND_BOXES] + " et " + (int) lowered.values()[STAND_BOXES] + ".");
        ClientTestContext.require(Math.abs(shape[STAND_BOTTOM]) < STAND_EDGE && Math.abs(shape[STAND_TOP] - STAND_HEIGHT) < STAND_EDGE,
                String.format(Locale.ROOT, "Statue de %.3f a %.3f au lieu de 0 a %.3f.", shape[STAND_BOTTOM], shape[STAND_TOP], STAND_HEIGHT));
        ClientTestContext.require(move > MIN_POSE_MOVE, String.format(Locale.ROOT, "Le bras tendu n'a deplace aucun cube : %.3f bloc.", move));
        ShadowCasters resident = shapeOf(context, villager);
        double widest = 0.0;
        for (int k = 0; k < (int) resident.values()[STAND_BOXES]; k++) {
            widest = Math.max(widest, Math.sqrt(center(resident, k, SECOND_AXIS) * center(resident, k, SECOND_AXIS)
                    + center(resident, k, SECOND_AXIS + 1) * center(resident, k, SECOND_AXIS + 1) + center(resident, k, SECOND_AXIS + 2) * center(resident, k, SECOND_AXIS + 2)));
        }
        ModConstants.LOGGER.info("[Test client] Forme d'un villageois : {} cubes, le plus large de {} bloc de demi-cote.", (int) resident.values()[STAND_BOXES],
                String.format(Locale.ROOT, "%.3f", widest));
        ClientTestContext.require(widest < MAX_VILLAGER_WIDTH, String.format(Locale.ROOT, "Le bord transparent du chapeau compte : un cube de %.3f bloc de demi-cote.", widest));
        requirePlayerTurned(context);
    }

    /**
     * Le joueur debout se tourne sans vanilla, qui relirait son nom a chaque image : tourne de 30 degres, la largeur de son torse est en
     * travers de son regard, comme vanilla le dessine.
     */
    private static void requirePlayerTurned(ClientTestContext context) {
        LivingEntity player = context.player();
        player.setYaw(PLAYER_YAW);
        player.setBodyYaw(PLAYER_YAW);
        player.prevBodyYaw = PLAYER_YAW;
        player.setHeadYaw(PLAYER_YAW);
        player.prevHeadYaw = PLAYER_YAW;
        ShadowCasters shape = new ShadowCasters();
        EntityShadows.shape(player, 0.0, 0.0, 0.0, 1.0f, shape);
        double angle = Math.toRadians(PLAYER_YAW);
        double across = -1.0;
        for (int k = 0; k < (int) shape.values()[STAND_BOXES]; k++) {
            double x = center(shape, k, SECOND_AXIS);
            double y = center(shape, k, SECOND_AXIS + 1);
            double z = center(shape, k, SECOND_AXIS + 2);
            double width = Math.sqrt(x * x + y * y + z * z);
            double height = center(shape, k, 1);
            if (Math.abs(width - TORSO_HALF_WIDTH) < SIZE_EDGE && height > TORSO_LOW && height < TORSO_HIGH) {
                across = Math.abs(x * Math.cos(angle) + z * Math.sin(angle)) / width;
            }
        }
        ModConstants.LOGGER.info("[Test client] Torse du joueur tourne de {} degres : largeur en travers du regard a {}.", PLAYER_YAW,
                String.format(Locale.ROOT, "%.4f", across));
        ClientTestContext.require(across > MIN_TORSO_ACROSS, String.format(Locale.ROOT, "Le torse du joueur n'est pas tourne comme lui : %.4f.", across));
    }

    /**
     * La forme d'un corps suit son modele et sa pose : deux statues d'armure, l'une le bras droit tendu devant elle, l'autre le bras
     * baisse, ont chacune les cubes du modele (pas une boite de collision), de la base plate au haut de la tete, et le bras tendu deplace
     * un cube de l'une par rapport a l'autre. Le bord du chapeau d'un villageois, transparent dans sa texture, n'en fait pas partie. Le
     * shader arrete un rayon a ces cubes comme le dit un programme independant ({@code body_box_vectors.json}).
     */
    private static ClientScenario shadowFollowsTheModel() {
        return ClientScenario.named("shadow_follows_the_model")
                .await("Overworld cote client", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 1200)
                .run("deux statues, bras tendu et bras baisse, et un villageois", TracedLightClientTests::spawnStands)
                .await("corps chez le client", TracedLightClientTests::bodiesArrived, 200)
                .frames(4)
                .run("leurs cubes", TracedLightClientTests::requireStandShapes)
                .check("le passage d'un rayon dans une boite suit les vecteurs communs", TracedLightClientTests::requireBodyBoxMatches)
                .cleanup(context -> context.onServer(server -> {
                    ServerWorld world = ClientTestContext.serverWorld(server, World.OVERWORLD);
                    for (UUID id : new UUID[] {STANDS[0], STANDS[1], villager}) {
                        Entity spawned = id == null ? null : world.getEntity(id);
                        if (spawned != null) {
                            spawned.discard();
                        }
                    }
                }));
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

    private static void vector(int program, String name, int i, JsonObject v, String key, float w) {
        JsonArray values = v.getAsJsonArray(key);
        GL20.glUniform4f(GL20.glGetUniformLocation(program, name + "[" + i + "]"), values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat(), w);
    }

    /** Le passage d'un rayon dans une boite, sur le processeur graphique, contre les vecteurs d'un programme independant. */
    private static void requireBodyBoxMatches(ClientTestContext context) {
        JsonArray vectors;
        try {
            vectors = JsonParser.parseString(Files.readString(Path.of(System.getProperty("gtgsbackrooms.clientTest.boxVectors")), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("vectors");
        } catch (IOException exception) {
            throw new IllegalStateException("Vecteurs de la boite illisibles.", exception);
        }
        ClientTestContext.require(vectors.size() <= BOX_CASES, "Trop de vecteurs de la boite : " + vectors.size() + ".");
        String source = "#version 150\n" + LookGradeClientTests.library(context.client(), BODY_BOX) + "\n" + BOX_MAIN;
        float[] pixels = LookGradeClientTests.render(source, 1, program -> {
            for (int i = 0; i < vectors.size(); i++) {
                JsonObject v = vectors.get(i).getAsJsonObject();
                vector(program, "gtgs_TestStart", i, v, "start", v.get("grow").getAsFloat());
                vector(program, "gtgs_TestToTarget", i, v, "toTarget", 0.0f);
                vector(program, "gtgs_TestCenter", i, v, "center", 0.0f);
                vector(program, "gtgs_TestU", i, v, "u", 0.0f);
                vector(program, "gtgs_TestV", i, v, "v", 0.0f);
                vector(program, "gtgs_TestW", i, v, "w", 0.0f);
            }
        });
        for (int i = 0; i < vectors.size(); i++) {
            boolean expected = vectors.get(i).getAsJsonObject().get("hits").getAsBoolean();
            ClientTestContext.require(pixels[i * 4] > 0.5f == expected, String.format(Locale.ROOT, "Boite, vecteur %d : %s au lieu de %s.", i, !expected, expected));
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
