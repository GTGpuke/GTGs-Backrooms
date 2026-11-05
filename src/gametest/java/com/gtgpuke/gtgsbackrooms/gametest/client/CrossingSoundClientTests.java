package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroArrival;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlock;
import com.gtgpuke.gtgsbackrooms.core.portal.block.BackroomsPortalBlockEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalMark;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalPlacement;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.client.sound.SoundAnchors;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundManagerAccessor;
import com.gtgpuke.gtgsbackrooms.mixin.client.SoundSystemAccessor;

/**
 * Le son d'un portail vers un autre endroit du meme niveau, a la traversee : avant le cadre, le tube de l'autre cote bourdonne au cadre,
 * et apres, celui qu'on vient de quitter bourdonne au cadre de retour, chacun sans s'interrompre (la meme source joue tout du long).
 */
final class CrossingSoundClientTests {

    /**
     * Distances de l'arrivee, a l'est, en blocs : un autre endroit du meme niveau, dans les chunks que le joueur recoit deja, puis au-dela,
     * ou l'arrivee n'est que dans le monde a part que le serveur lui envoie pour elle (son ailleurs).
     */
    private static final int NEAR = 60;
    private static final int FAR = 300;
    /** Un son entendu par un cadre proche joue a moins de cela de l'auditeur, en blocs ; a sa place, il serait a une soixantaine. */
    private static final double NEAR_LISTENER = 6.0;
    /** Ticks laisses aux revues du bourdonnement, toutes les dix ticks, pour trouver les deux tubes. */
    private static final int HUM_TICKS = 600;
    private static final Map<BlockPos, BlockState> REPLACED = new HashMap<>();
    private static BlockPos corridor;
    private static Vec3d hereNeon;
    private static Vec3d thereNeon;
    private static @Nullable SoundInstance hereHum;
    private static @Nullable SoundInstance thereHum;

    private CrossingSoundClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(humCrosses("hum_crosses_with_the_player", NEAR), humCrosses("hum_crosses_far_away", FAR));
    }

    private static void set(ServerWorld world, BlockPos pos, BlockState state) {
        REPLACED.putIfAbsent(pos.toImmutable(), world.getBlockState(pos));
        world.setBlockState(pos, state);
    }

    /** Un couloir de trois blocs de large et quatre de haut, de fromZ a toZ (murs compris). */
    private static void corridor(ServerWorld level, BlockPos origin, int fromZ, int toZ) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    boolean inside = Math.abs(x) <= 1 && y >= 0 && y <= 3 && z > fromZ && z < toZ;
                    BlockState state = inside ? Blocks.AIR.getDefaultState() : y == -1 ? ModBlocks.LEVEL_ZERO_DAMP_CARPET.getDefaultState()
                            : y == 4 ? ModBlocks.LEVEL_ZERO_CEILING_TILE.getDefaultState() : ModBlocks.LEVEL_ZERO_WALLPAPER.getDefaultState();
                    set(level, origin.add(x, y, z), state);
                }
            }
        }
    }

    /** Le bourdonnement que le moteur de son joue pour le tube de ce bloc, s'il en joue un : sa vraie source est le centre du bloc. */
    private static @Nullable SoundInstance hum(MinecraftClient client, Vec3d neon) {
        SoundSystemAccessor system = (SoundSystemAccessor) ((SoundManagerAccessor) client.getSoundManager()).backrooms$getSoundSystem();
        for (SoundInstance sound : system.backrooms$getSources().keySet()) {
            SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(sound);
            if (sound.getId().toString().equals(LevelZeroClientTests.HUM) && anchor != null && anchor.source().squaredDistanceTo(neon) < 1.0e-4) {
                return sound;
            }
        }
        return null;
    }

    private static void hold(ClientTestContext context) {
        context.player().setPosition(corridor.getX() + 0.5, corridor.getY(), corridor.getZ() + 2.0);
        context.player().setVelocity(0.0, 0.0, 0.0);
        context.player().setYaw(0.0f);
        context.player().setPitch(0.0f);
    }

    /** Les deux bourdonnements jouent encore, les memes, et chacun sonne pres de l'auditeur : a sa place, ou au cadre par ou il passe. */
    private static void bothStillHumNearby(ClientTestContext context, String when) {
        MinecraftClient client = context.client();
        Vec3d listener = client.gameRenderer.getCamera().getPos();
        ClientTestContext.require(hereHum != null && thereHum != null, "Bourdonnements perdus avant " + when + ".");
        for (SoundInstance sound : new SoundInstance[] {hereHum, thereHum}) {
            ClientTestContext.require(client.getSoundManager().isPlaying(sound), "Le bourdonnement du tube " + (sound == hereHum ? "quitte" : "de l'arrivee")
                    + " s'est arrete " + when + ".");
            SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(sound);
            ClientTestContext.require(anchor != null, "Un bourdonnement a perdu sa vraie source " + when + ".");
            double distance = anchor.played().distanceTo(listener);
            ModConstants.LOGGER.info("[Test client] Tube en {} : joue a {} blocs de l'auditeur {}.", anchor.source(), String.format(Locale.ROOT, "%.1f", distance), when);
            ClientTestContext.require(distance < NEAR_LISTENER, String.format(Locale.ROOT,
                    "Le tube en %s sonne a %.1f blocs de l'auditeur %s : il ne passe plus par le cadre.", anchor.source(), distance, when));
        }
    }

    /**
     * Un couloir, un tube au plafond devant un portail aller-retour qui le bouche, vers un couloir du meme niveau, a cette distance, avec un
     * tube juste apres l'arrivee. Avant le cadre, le tube de l'autre cote bourdonne au cadre ; le joueur passe en marchant ; juste apres, et
     * apres trois revues du bourdonnement, les deux memes sons jouent encore, celui qu'on vient de quitter au cadre de retour. Sans cela,
     * le tube de l'autre cote n'etait pas entendu par le cadre (choisi du mauvais cote de l'arrivee, et jamais pour le meme niveau), et
     * celui qu'on quittait sonnait a sa place, a soixante blocs, puis s'arretait : une coupure a chaque passage. Au loin, les tubes de
     * l'arrivee ne sont que dans l'ailleurs du portail : les chercher dans le monde de sa seule dimension ne les trouve pas.
     */
    private static ClientScenario humCrosses(String name, int distance) {
        return ClientScenario.named(name)
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .run("couloirs, tubes, portail aller-retour vers le meme niveau", context -> {
                    corridor = context.player().getBlockPos();
                    BlockPos o = corridor;
                    context.onServer(server -> {
                        ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                        corridor(level, o, -3, 14);
                        set(level, o.add(0, 4, 3), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        PortalTargets.Target target = LevelZeroArrival.find(level, o.getX() + distance, o.getZ(), PortalFacing.SOUTH, new PortalSettings(3, 4, true));
                        ClientTestContext.require(target != null, "Aucune arrivee.");
                        BlockPos bottom = BlockPos.ofFloored(target.position().subtract(0.0, 2.0, 0.0));
                        corridor(level, bottom, -9, 12);
                        set(level, bottom.add(0, 4, 2), ModBlocks.LEVEL_ZERO_FLUORESCENT_LIGHT.getDefaultState());
                        hereNeon = Vec3d.ofCenter(o.add(0, 4, 3));
                        thereNeon = Vec3d.ofCenter(bottom.add(0, 4, 2));
                        PortalSettings settings = new PortalSettings(3, 4, true, true, false, false, PortalExit.STRAIGHT, 0, false, false, PortalPlacement.LOOK,
                                Optional.empty(), Optional.of(new PortalMark(level.getRegistryKey(), bottom, Direction.SOUTH)));
                        set(level, o.add(0, 0, 6), ModBlocks.BACKROOMS_PORTAL.getDefaultState().with(BackroomsPortalBlock.FACING, Direction.NORTH));
                        if (level.getBlockEntity(o.add(0, 0, 6)) instanceof BackroomsPortalBlockEntity block) {
                            block.readComponents(ClientTestPortals.wand(settings));
                        }
                        ClientTestContext.serverPlayer(server).teleport(level, o.getX() + 0.5, o.getY(), o.getZ() + 2.0, 0.0f, 0.0f);
                        ClientTestContext.serverPlayer(server).getAbilities().flying = false;
                        ClientTestContext.serverPlayer(server).sendAbilitiesUpdate();
                    });
                })
                .await("portail et retour chez le client", context -> ClientPortalIndex.allPortals(context.world()).size() >= 2, 1200)
                .await("les deux tubes bourdonnent", context -> {
                    hold(context);
                    hereHum = hum(context.client(), hereNeon);
                    thereHum = hum(context.client(), thereNeon);
                    return hereHum != null && thereHum != null;
                }, HUM_TICKS)
                .check("le tube de l'autre cote bourdonne au cadre", context -> {
                    SoundAnchors.Anchor anchor = SoundAnchors.anchorOf(thereHum);
                    ClientTestContext.require(anchor != null && anchor.arrival() != null, "Le tube de l'autre cote ne passe pas par le cadre.");
                    bothStillHumNearby(context, "avant le cadre");
                })
                .run("marche droit vers le cadre", context -> context.client().options.forwardKey.setPressed(true))
                .await("passe le cadre", context -> context.player().getX() > corridor.getX() + distance / 2.0, 200)
                .run("arret", context -> context.client().options.forwardKey.setPressed(false))
                .check("juste apres le passage", context -> bothStillHumNearby(context, "juste apres le passage"))
                .ticks(30)
                .check("apres trois revues du bourdonnement", context -> bothStillHumNearby(context, "apres trois revues"))
                .cleanup(context -> context.client().options.forwardKey.setPressed(false))
                .cleanup(context -> context.onServer(server -> {
                    ServerWorld level = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                    REPLACED.forEach(level::setBlockState);
                    REPLACED.clear();
                }))
                .cleanup(LevelZeroClientTests::backToOverworld);
    }
}
