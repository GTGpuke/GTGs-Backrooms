package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.core.audio.client.AcousticRouter;
import com.gtgpuke.gtgsbackrooms.core.audio.client.CueTracker;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalWayBack;
import com.gtgpuke.gtgsbackrooms.core.portal.client.interaction.RemoteTargeting;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewDetail;
import com.gtgpuke.gtgsbackrooms.core.portal.client.wand.PortalWandScreen;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.FrozenViews;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;
import com.gtgpuke.gtgsbackrooms.core.registry.ModSounds;

/**
 * Les fonctions des portails vues par un vrai client : paliers de detail de loin (vue allegee, image figee), entite qui
 * traverse, traversee en troisieme personne, clics a travers le cadre, ecran de la baguette, vue imbriquee vers un
 * troisieme monde, commande d'etat, cri qui perturbe les neons, son entendu a travers le cadre et son occlusion.
 */
final class PortalFeaturesClientTests {

    private static final PortalSettings LARGE = new PortalSettings(4, 4, true);
    /** Identifiant du disque joue dans le Level 0, tel que le diagnostic du moteur audio l'ecrit. */
    private static final String RECORD = "minecraft:music_disc.13";

    private PortalFeaturesClientTests() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    static List<ClientScenario> scenarios() {
        return List.of(detailTiersFromAfar(), entityCrossesTheFrame(), thirdPersonCrossing(), clicksThroughTheFrame(), wandScreen(),
                nestedViewAndStateCommand(), screamDisturbsNeons(), soundHeardThroughTheFrame(), occlusionThroughTheFrame());
    }

    /** Vue de base : portail du Level 0 pose, joueur a {@code distance} blocs du plan, regard vers le cadre. */
    private static ClientScenario withPortal(String name, PortalSettings settings, double distance) {
        return ClientScenario.named(name)
                .run("portail et joueur", context -> {
                    ClientTestPortals.place(context, settings);
                    ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - distance, 0.0f, 0.0f);
                })
                .await("portail recu", ClientTestPortals::received, ClientTestPortals.PORTAL_TICKS)
                .await("vue du Level 0 prete", ClientTestPortals::viewReady, ClientTestPortals.PORTAL_TICKS)
                .frames(60);
    }

    private static ClientScenario detailTiersFromAfar() {
        return withPortal("portal_detail_tiers_from_afar", LARGE, 6.0)
                .check("pleine qualite de pres", context -> requireTier(context, ViewDetail.Tier.FULL))
                .capture("Level 0 dans le cadre de pres", image -> requireLevelZero(image, 0.03, "de pres"))
                .run("a trente blocs", context -> ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - 30.0, 0.0f, 0.0f))
                .frames(60)
                .await("vue allegee", context -> ClientTestPortals.clientPortal(context).detailTier() == ViewDetail.Tier.REDUCED.ordinal(), 400)
                .capture("Level 0 dans le cadre a trente blocs", image -> requireLevelZero(image, 0.008, "a trente blocs"))
                .run("a soixante blocs (capture de l'image figee)", context -> ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - 60.0, 0.0f, 0.0f))
                .frames(60)
                .capture("Level 0 dans le cadre a soixante blocs", image -> requireLevelZero(image, 0.006, "a soixante blocs"))
                .check("image figee prise pres du bord de la distance de rendu", context -> ClientTestContext.require(
                        FrozenViews.has(ClientTestPortals.clientPortal(context)), "Aucune image figee prise a soixante blocs."))
                .run("a quatre-vingt-dix blocs", context -> ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - 90.0, 0.0f, 0.0f))
                .frames(60)
                .capture("image figee dans le cadre", image -> requireLevelZero(image, 0.004, "a quatre-vingt-dix blocs"))
                .run("retour au cadre", context -> ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - 6.0, 0.0f, 0.0f))
                .await("pleine qualite retrouvee", context -> ClientTestPortals.clientPortal(context).detailTier() == ViewDetail.Tier.FULL.ordinal(), 400)
                .cleanup(ClientTestPortals::remove);
    }

    private static void requireTier(ClientTestContext context, ViewDetail.Tier tier) {
        int actual = ClientTestPortals.clientPortal(context).detailTier();
        ClientTestContext.require(actual == tier.ordinal(), "Palier " + ViewDetail.tierOf(actual) + " au lieu de " + tier + ".");
    }

    /** Le centre de l'image (le cadre, regarde de face) montre le Level 0 : teinte jaune. */
    private static void requireLevelZero(NativeImage image, double half, String when) {
        double[] frame = Images.mean(image, 0.5 - half, 0.5 - half, 0.5 + half, 0.5 + half);
        ClientTestContext.require(Images.yellowish(frame), "Le cadre ne montre pas le Level 0 " + when + " : " + Images.describe(frame) + ".");
    }

    private static ClientScenario entityCrossesTheFrame() {
        UUID[] stand = new UUID[1];
        return withPortal("entity_crosses_the_frame", PortalSettings.DEFAULT, 4.0)
                .run("diamant lance vers le cadre", context -> context.onServer(server -> {
                    ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
                    ItemEntity entity = new ItemEntity(overworld, 0.5, ClientTestPortals.FLOOR_Y + 1.0, ClientTestPortals.PLANE_Z - 2.0,
                            new ItemStack(Items.DIAMOND));
                    entity.setNoGravity(true);
                    entity.setPickupDelayInfinite();
                    entity.setVelocity(0.0, 0.0, 0.1);
                    overworld.spawnEntity(entity);
                    stand[0] = entity.getUuid();
                }))
                .await("diamant passe dans le Level 0", context -> context.fromServer(server ->
                        ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).getEntity(stand[0]) != null), 200)
                .await("copie de l'autre cote chez le client", context -> {
                    ClientWorld remote = RemoteWorldRegistry.worldOf(BackroomsConstants.LEVEL_ZERO_WORLD);
                    return remote != null && find(remote, stand[0]) != null && find(context.world(), stand[0]) == null;
                })
                .frames(20)
                .cleanup(context -> context.onServer(server -> {
                    for (ServerWorld world : server.getWorlds()) {
                        Entity entity = world.getEntity(stand[0]);
                        if (entity != null) {
                            entity.discard();
                        }
                    }
                }))
                .cleanup(ClientTestPortals::remove);
    }

    private static Entity find(ClientWorld world, UUID uuid) {
        for (Entity entity : world.getEntities()) {
            if (entity.getUuid().equals(uuid)) {
                return entity;
            }
        }
        return null;
    }

    private static ClientScenario thirdPersonCrossing() {
        return withPortal("third_person_crossing", PortalSettings.DEFAULT, 3.0)
                .run("troisieme personne, marche vers le cadre", context -> {
                    context.client().options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    context.client().options.forwardKey.setPressed(true);
                })
                .await("bascule dans le Level 0", LevelZeroClientTests::inLevelZero, 200)
                .ticks(10)
                .run("vue de face, retour a reculons", context -> {
                    context.client().options.forwardKey.setPressed(false);
                    context.client().options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                    context.client().options.backKey.setPressed(true);
                })
                .await("bascule dans l'Overworld", context -> context.client().world != null && context.client().world.getRegistryKey() == World.OVERWORLD, 200)
                .ticks(10)
                .cleanup(context -> {
                    context.client().options.forwardKey.setPressed(false);
                    context.client().options.backKey.setPressed(false);
                    context.client().options.setPerspective(Perspective.FIRST_PERSON);
                })
                .cleanup(ClientTestPortals::remove)
                .cleanup(LevelZeroClientTests::backToOverworld);
    }

    private static ClientScenario clicksThroughTheFrame() {
        BlockPos[] target = new BlockPos[1];
        return withPortal("clicks_through_the_frame", PortalSettings.DEFAULT, 2.0)
                .run("bloc d'or derriere la sortie", context -> {
                    BackroomsPortalEntity portal = ClientTestPortals.clientPortal(context);
                    Vec3d point = ClientTestPortals.pastExit(portal, 2.5).add(0.0, 0.5, 0.0);
                    target[0] = BlockPos.ofFloored(point);
                    context.onServer(server -> ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD)
                            .setBlockState(target[0], Blocks.GOLD_BLOCK.getDefaultState()));
                })
                .await("bloc vise a travers le cadre", context -> RemoteTargeting.blockHit() != null && RemoteTargeting.blockHit().getBlockPos().equals(target[0]), 400)
                .run("clic gauche", context -> press(context.client().options.attackKey))
                .await("bloc casse dans le Level 0", context -> context.fromServer(server -> ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD)
                        .getBlockState(target[0]).isAir()))
                .run("bloc d'or remis, pierre en main", context -> context.onServer(server -> {
                    ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).setBlockState(target[0], Blocks.GOLD_BLOCK.getDefaultState());
                    ClientTestContext.serverPlayer(server).setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STONE));
                }))
                .await("bloc vise de nouveau", context -> RemoteTargeting.blockHit() != null && RemoteTargeting.blockHit().getBlockPos().equals(target[0]), 400)
                .run("clic droit", context -> press(context.client().options.useKey))
                .await("pierre posee contre le bloc dans le Level 0", context -> context.fromServer(server -> {
                    ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                    BlockPos hit = target[0].offset(RemoteTargeting.blockHit() == null ? Direction.UP : RemoteTargeting.blockHit().getSide());
                    return levelZero.getBlockState(hit).isOf(Blocks.STONE);
                }))
                .run("main videe", context -> context.onServer(server -> ClientTestContext.serverPlayer(server).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY)))
                .await("main vide cote client", context -> context.player().getMainHandStack().isEmpty())
                .run("choix du bloc vise (clic molette)", context -> press(context.client().options.pickItemKey))
                .await("bloc vise a travers le cadre pris en main", context -> context.player().getMainHandStack().isOf(Items.STONE))
                .cleanup(context -> context.onServer(server -> {
                    ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                    levelZero.setBlockState(target[0], Blocks.AIR.getDefaultState());
                    for (Direction side : Direction.values()) {
                        if (levelZero.getBlockState(target[0].offset(side)).isOf(Blocks.STONE)) {
                            levelZero.setBlockState(target[0].offset(side), Blocks.AIR.getDefaultState());
                        }
                    }
                    ClientTestContext.serverPlayer(server).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
                }))
                .cleanup(ClientTestPortals::remove);
    }

    /** Un appui sur la touche, comme un clic de la souris : vanilla le traite au tick suivant. */
    private static void press(KeyBinding key) {
        KeyBinding.onKeyPressed(key.getDefaultKey());
    }

    private static ClientScenario wandScreen() {
        return ClientScenario.named("wand_screen")
                .run("baguette en main, regard vers le ciel", context -> {
                    LevelZeroClientTests.backToOverworld(context);
                    context.onServer(server -> {
                        ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
                        player.setStackInHand(Hand.MAIN_HAND, ClientTestPortals.wand(PortalSettings.DEFAULT));
                        player.setPitch(-90.0f);
                        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), -90.0f);
                    });
                })
                .await("baguette recue", context -> context.player().getMainHandStack().contains(ModDataComponents.PORTAL_SETTINGS) && context.player().getPitch() < -80.0f)
                .run("clic droit dans le vide", context -> press(context.client().options.useKey))
                .await("ecran des reglages ouvert", context -> context.client().currentScreen instanceof PortalWandScreen)
                .frames(5)
                .run("chaque reglage change une fois, sortie fixee puis oubliee", context -> {
                    PortalWandScreen screen = (PortalWandScreen) context.client().currentScreen;
                    for (Element element : List.copyOf(screen.children())) {
                        if (element instanceof ClickableWidget widget && !widget.getMessage().getString().equals(ScreenTexts.DONE.getString())) {
                            // Un clic : chaque reglage quitte sa valeur de depart (un second ramenerait les interrupteurs).
                            widget.mouseClicked(widget.getX() + widget.getWidth() * 0.75, widget.getY() + 1.0, 0);
                            if (widget.getClass() == ButtonWidget.class) {
                                // La sortie fixee : posee la ou se tient le joueur, puis oubliee.
                                widget.mouseClicked(widget.getX() + 1.0, widget.getY() + 1.0, 0);
                            }
                        }
                    }
                })
                .frames(5)
                .run("terminer", context -> {
                    PortalWandScreen screen = (PortalWandScreen) context.client().currentScreen;
                    for (Element element : List.copyOf(screen.children())) {
                        if (element instanceof ClickableWidget widget && widget.getMessage().getString().equals(ScreenTexts.DONE.getString())) {
                            widget.mouseClicked(widget.getX() + 1.0, widget.getY() + 1.0, 0);
                        }
                    }
                })
                .await("ecran ferme", context -> context.client().currentScreen == null)
                .await("reglages recus par le serveur", context -> context.fromServer(server -> !PortalSettings.DEFAULT.equals(
                        ClientTestContext.serverPlayer(server).getMainHandStack().get(ModDataComponents.PORTAL_SETTINGS))))
                .check("chaque reglage recu tel que choisi", context -> {
                    PortalSettings chosen = context.fromServer(server -> ClientTestContext.serverPlayer(server).getMainHandStack()
                            .get(ModDataComponents.PORTAL_SETTINGS));
                    PortalSettings initial = PortalSettings.DEFAULT;
                    ClientTestContext.require(chosen.width() != initial.width() && chosen.height() != initial.height(), "Taille du cadre inchangee : " + chosen + ".");
                    ClientTestContext.require(chosen.transmitsLight() != initial.transmitsLight(), "Lumiere transmise inchangee : " + chosen + ".");
                    ClientTestContext.require(chosen.doubleSided() != initial.doubleSided(), "Faces inchangees : " + chosen + ".");
                    ClientTestContext.require(chosen.placement() != initial.placement(), "Pose inchangee : " + chosen + ".");
                    ClientTestContext.require(PortalWayBack.of(chosen) != PortalWayBack.of(initial), "Retour inchange : " + chosen + ".");
                    ClientTestContext.require(chosen.exit() != initial.exit(), "Sortie inchangee : " + chosen + ".");
                    ClientTestContext.require(chosen.upsideDown() != initial.upsideDown(), "Arrivee inchangee : " + chosen + ".");
                    ClientTestContext.require(chosen.exitScatter() > initial.exitScatter(), "Dispersion inchangee : " + chosen + ".");
                    ClientTestContext.require(chosen.flush() != initial.flush(), "Plan du cadre inchange : " + chosen + ".");
                    ClientTestContext.require(chosen.destination().equals(Optional.of(World.OVERWORLD)),
                            "Destination fixee par la sortie oubliee : l'Overworld attendu, " + chosen.destination() + ".");
                    ClientTestContext.require(chosen.mark().isEmpty(), "La sortie fixee puis oubliee est restee : " + chosen.mark() + ".");
                })
                .cleanup(context -> {
                    if (context.client().currentScreen != null) {
                        context.client().setScreen(null);
                    }
                    context.onServer(server -> ClientTestContext.serverPlayer(server).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY));
                    LevelZeroClientTests.backToOverworld(context);
                });
    }

    private static ClientScenario nestedViewAndStateCommand() {
        UUID[] nested = new UUID[1];
        return withPortal("nested_view_and_state_command", PortalSettings.DEFAULT, 4.0)
                .run("portail vers l'End dans le Level 0, face au joueur", context -> context.onServer(server -> {
                    BackroomsPortalEntity entry = overworldPortal();
                    ServerWorld levelZero = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD);
                    Vec3d at = ClientTestPortals.pastExit(entry, 2.0);
                    BlockPos base = BlockPos.ofFloored(at.x, entry.destinationPos().y - 1.0, at.z);
                    levelZero.setBlockState(base, Blocks.AIR.getDefaultState());
                    levelZero.setBlockState(base.up(), Blocks.AIR.getDefaultState());
                    BackroomsPortalEntity portal = BackroomsPortalEntity.builder(levelZero)
                            .position(at)
                            .facingSource(entry.facingDestination().opposite())
                            .facingDestination(entry.facingDestination())
                            .destinationDim(World.END)
                            .destinationPos(new Vec3d(0.5, 70.0, 0.5))
                            .size(1.0f, 2.0f)
                            .build();
                    levelZero.spawnEntity(portal);
                    nested[0] = portal.getUuid();
                }))
                .await("End recu par le client (vue imbriquee)", context -> RemoteWorldRegistry.get(World.END) != null, ClientTestPortals.PORTAL_TICKS)
                .frames(60)
                .check("deux mondes distants tenus", context -> ClientTestContext.require(RemoteWorldRegistry.remoteWorldCount() >= 2,
                        "Mondes distants : " + RemoteWorldRegistry.remoteWorldCount() + "."))
                .check("commande d'etat de l'image", context -> {
                    FabricClientCommandSource source = (FabricClientCommandSource) context.client().getNetworkHandler().getCommandSource();
                    try {
                        int result = ClientCommandManager.getActiveDispatcher().execute("backrooms_state", source);
                        ClientTestContext.require(result == 1, "La commande d'etat a rendu " + result + ".");
                    } catch (CommandSyntaxException exception) {
                        throw new AssertionError("Commande d'etat refusee : " + exception.getMessage(), exception);
                    }
                })
                .cleanup(context -> context.onServer(server -> {
                    Entity portal = ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).getEntity(nested[0]);
                    if (portal != null) {
                        portal.discard();
                    }
                }))
                .cleanup(ClientTestPortals::remove);
    }

    /** La face avant du portail du bloc, cote serveur (a appeler sur le thread du serveur). */
    private static BackroomsPortalEntity overworldPortal() {
        for (BackroomsPortalEntity portal : BackroomsPortalEntityRegistry.all()) {
            if (portal.getWorld().getRegistryKey() == World.OVERWORLD && portal.facingSource() == PortalFacing.NORTH) {
                return portal;
            }
        }
        throw new AssertionError("Aucune face avant de portail dans l'Overworld cote serveur.");
    }

    private static ClientScenario screamDisturbsNeons() {
        return ClientScenario.named("scream_disturbs_neons")
                .run("entree dans le Level 0", LevelZeroClientTests::enterLevelZero)
                .await("monde du Level 0 cote client", LevelZeroClientTests::inLevelZero, 1200)
                .frames(20)
                .run("cri a deux blocs", context -> context.onServer(server -> {
                    ServerPlayerEntity player = ClientTestContext.serverPlayer(server);
                    player.getServerWorld().playSound(null, player.getX() + 2.0, player.getEyeY(), player.getZ(), ModSounds.HOWLER_SCREAM, SoundCategory.HOSTILE, 1.0f, 1.0f);
                }))
                .await("perturbation des neons en cours, cri route par le moteur audio", context -> disturbances() > 0
                        && routed().contains("entity.howler.scream"))
                .frames(30)
                .cleanup(LevelZeroClientTests::backToOverworld);
    }

    private static ClientScenario soundHeardThroughTheFrame() {
        Vec3d[] scream = new Vec3d[1];
        return withPortal("sound_heard_through_the_frame", PortalSettings.DEFAULT, 4.0)
                .run("cri derriere la sortie", context -> context.onServer(server -> {
                    BackroomsPortalEntity entry = overworldPortal();
                    Vec3d at = ClientTestPortals.pastExit(entry, 2.0);
                    scream[0] = at;
                    ClientTestContext.serverWorld(server, BackroomsConstants.LEVEL_ZERO_WORLD).playSound(null, at.x, at.y, at.z, ModSounds.HOWLER_SCREAM,
                            SoundCategory.HOSTILE, 1.0f, 1.0f);
                }))
                .await("cri du Level 0 entendu dans l'Overworld", context -> routed().contains("entity.howler.scream") && routed().contains(
                        BackroomsConstants.LEVEL_ZERO_WORLD.getValue().toString()))
                .ticks(5)
                .check("perturbation des neons a la vraie source du cri, dans le Level 0", context -> {
                    double[] at = firstDisturbance();
                    ClientTestContext.require(at != null, "Aucune perturbation pour le cri entendu au cadre.");
                    double distance = Math.sqrt(scream[0].squaredDistanceTo(at[0], at[1], at[2]));
                    ClientTestContext.require(distance < 1.5, "La perturbation n'est pas a la source du cri : a " + distance + " blocs.");
                })
                .frames(20)
                .cleanup(ClientTestPortals::remove);
    }

    /**
     * Occlusion d'un son du Level 0 entendu par le cadre depuis l'Overworld, ou le monde du joueur n'a pas de copie pour
     * le son : le trajet de la source au cadre se trace dans le Level 0, celui du cadre a l'auditeur dans l'Overworld,
     * depuis le point du cadre ou le son joue (jamais depuis les coordonnees de la source, qui n'ont pas de sens dans ce
     * monde-ci). Un cube de pierre pose dans l'Overworld aux coordonnees de la source ne compte donc pas ; un mur entre le
     * cadre et l'auditeur compte.
     */
    private static ClientScenario occlusionThroughTheFrame() {
        Vec3d[] source = new Vec3d[1];
        return withPortal("occlusion_through_the_frame", PortalSettings.DEFAULT, 4.0)
                .run("cube de pierre aux coordonnees de la source, dans l'Overworld", context -> context.onServer(server -> {
                    Vec3d at = ClientTestPortals.pastExit(overworldPortal(), 2.0);
                    source[0] = at;
                    // Le Level 0 est au-dessus de y = 0, le joueur sur le sol du monde plat (y = -60) : le cube est loin du trajet du cadre a lui.
                    ClientTestContext.require(at.y > ClientTestPortals.FLOOR_Y + 20.0, "Source du Level 0 trop basse pour le cube : " + at + ".");
                    fill(ClientTestContext.serverWorld(server, World.OVERWORLD), BlockPos.ofFloored(at), 1, Blocks.STONE);
                }))
                // Les blocs changes partent en fin de tick, un son tout de suite : le cube doit etre chez le client avant le disque.
                .await("cube recu par le client", context -> context.world().getBlockState(BlockPos.ofFloored(source[0]).down()).isOf(Blocks.STONE), 200)
                .run("disque joue dans le Level 0", context -> context.onServer(server -> ClientTestContext.serverWorld(server,
                        BackroomsConstants.LEVEL_ZERO_WORLD).playSound(null, source[0].x, source[0].y, source[0].z, SoundEvents.MUSIC_DISC_13.value(),
                        SoundCategory.RECORDS, 1.0f, 1.0f)))
                .await("disque du Level 0 route dans l'Overworld", context -> !Float.isNaN(occlusionOf(RECORD)), 200)
                .ticks(20)
                .check("rien entre le cadre et l'auditeur : aucune occlusion", context -> ClientTestContext.require(occlusionOf(RECORD) < 0.05f,
                        "Occlusion " + occlusionOf(RECORD) + " sans rien entre le cadre et l'auditeur : le trajet part-il de la source ?"))
                .run("mur entre le cadre et l'auditeur, auditeur deplace", context -> {
                    context.onServer(server -> fill(ClientTestContext.serverWorld(server, World.OVERWORLD), new BlockPos(0, -59, 2), 1, Blocks.STONE));
                    ClientTestPortals.standAt(context, 0.5, ClientTestPortals.PLANE_Z - 5.0, 0.0f, 0.0f);
                })
                .ticks(30)
                .check("mur entre le cadre et l'auditeur : le son est assourdi", context -> ClientTestContext.require(occlusionOf(RECORD) > 0.1f,
                        "Occlusion " + occlusionOf(RECORD) + " avec un mur entre le cadre et l'auditeur : son trajet n'est pas compte."))
                .cleanup(context -> {
                    context.client().getSoundManager().stopSounds(SoundEvents.MUSIC_DISC_13.value().getId(), SoundCategory.RECORDS);
                    context.onServer(server -> {
                        ServerWorld overworld = ClientTestContext.serverWorld(server, World.OVERWORLD);
                        fill(overworld, new BlockPos(0, -59, 2), 1, Blocks.AIR);
                        if (source[0] != null) {
                            fill(overworld, BlockPos.ofFloored(source[0]), 1, Blocks.AIR);
                        }
                    });
                    ClientTestPortals.remove(context);
                });
    }

    /** Un cube de {@code 2 * radius + 1} blocs de cote autour de {@code center}. */
    private static void fill(ServerWorld world, BlockPos center, int radius, Block block) {
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -radius, -radius), center.add(radius, radius, radius))) {
            world.setBlockState(pos, block.getDefaultState());
        }
    }

    /** Occlusion du son de cet identifiant dans le diagnostic du moteur audio, ou {@code NaN} s'il n'y est pas. */
    private static float occlusionOf(String id) {
        String text = routed();
        int line = text.indexOf("  " + id + " : ");
        if (line < 0) {
            return Float.NaN;
        }
        int start = text.indexOf(", occlusion ", line) + ", occlusion ".length();
        return Float.parseFloat(text.substring(start, text.indexOf(',', start)));
    }

    /** Position de la premiere perturbation en cours, lue dans le diagnostic du suivi des sons lies, ou {@code null}. */
    private static double @Nullable [] firstDisturbance() {
        StringBuilder out = new StringBuilder();
        CueTracker.describe(out);
        String text = out.toString();
        int start = text.indexOf(" en (");
        if (start < 0) {
            return null;
        }
        String[] parts = text.substring(start + " en (".length(), text.indexOf(')', start)).split(", ");
        return new double[] {Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])};
    }

    private static int disturbances() {
        StringBuilder out = new StringBuilder();
        CueTracker.describe(out);
        return ClientTestPortals.readCount(out, "Perturbations en cours : ");
    }

    private static String routed() {
        StringBuilder out = new StringBuilder();
        AcousticRouter.describe(out);
        return out.toString();
    }
}
