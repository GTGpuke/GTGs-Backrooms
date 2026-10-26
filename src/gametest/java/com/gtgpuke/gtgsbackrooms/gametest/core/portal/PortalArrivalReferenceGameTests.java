package com.gtgpuke.gtgsbackrooms.gametest.core.portal;

import java.util.Locale;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalMark;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalPlacement;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.gametest.support.GameTestArrivals;

/**
 * Arrivees de reference (garde-fou G3 de {@code docs/revue/BILAN-CORRECTIONS.md}) : sur le terrain naturel du serveur de
 * test (graine 0), des cas fixes en coordonnees absolues, dont l'arrivee attendue a ete relevee sur le code de la 0.10.0 et
 * ne doit plus bouger. Un remaniement de la recherche d'arrivee (resolution asynchrone, point d'acces des destinations)
 * qui change l'un de ces resultats a change ce que voit le joueur.
 *
 * <p>Les cas evitent les zones que les autres tests preparent a la main (colonnes du Nether en 30000, points de l'End en
 * 500 a 680) et l'arene, dont l'origine varie d'un lot a l'autre.
 *
 * <p>Le terrain de chaque cas est genere d'abord, chunk par chunk dans un ordre fixe ({@link #prepare}) : sans cela, deux
 * mondes neufs de meme graine n'ont pas le meme terrain pres des bords de chunks, et une arrivee avec eux.
 */
public final class PortalArrivalReferenceGameTests implements FabricGameTest {

    private static final double EPSILON = 1.0e-6;
    /** Loin de tout : une zone neuve du Nether, que seul ce test genere. */
    private static final BlockPos NETHER_START = new BlockPos(-40_000, 70, 12_000);
    /** La surface de l'overworld, loin de l'arene. */
    private static final BlockPos SURFACE_START = new BlockPos(-40_000, 70, 12_000);
    /** Le vide de l'End, loin du centre et des autres tests. */
    private static final BlockPos END_START = new BlockPos(-40_000, 70, 12_000);
    private static final PortalMark MARK = new PortalMark(World.NETHER, new BlockPos(-40_123, 64, 12_321), Direction.EAST);
    /** Ou {@link PortalTargets#resolve} cherche dans le Nether depuis {@link #SURFACE_START} : les coordonnees divisees par huit. */
    private static final BlockPos NETHER_FROM_SURFACE = new BlockPos(-5_000, 70, 1_500);
    private static final int SCATTER = 7;
    /**
     * Rayon, en chunks, du terrain prepare autour de chaque cas : la recherche lit jusqu'a seize colonnes autour de son
     * depart (trois chunks avec le cadre), et charger un chunk entier demande les features de ses huit voisins.
     */
    private static final int PREPARED_RADIUS = 3;

    /**
     * Releve sur la 0.10.0 (commit d331d53 plus C77), serveur de test graine 0, terrain prepare par {@link #prepare} :
     * identique sur deux mondes neufs et sur un monde deja joue. Une arrivee se note "cas = dimension x y z orientation".
     * Ces valeurs ne se recopient qu'apres une decision de l'auteur, annoncee dans le CHANGELOG : elles sont l'oracle, pas
     * le resultat.
     */
    private static final String[] EXPECTED = {
        "nether_1x2_south = minecraft:the_nether -39999.5000 50.0000 12000.5000 SOUTH",
        "nether_2x3_east = minecraft:the_nether -39999.5000 50.5000 12000.0000 EAST",
        "nether_1x2_up = minecraft:the_nether -39999.5000 49.5000 12000.0000 UP",
        "surface_1x2_north = minecraft:overworld -39999.5000 -59.0000 12000.5000 NORTH",
        "end_void_1x2_south = minecraft:the_end -40000.5000 58.0000 12006.5000 SOUTH",
        "resolve_overworld_to_nether = minecraft:the_nether -4999.5000 59.0000 1500.5000 EAST",
        "resolve_mark_exact = minecraft:the_nether -40122.5000 66.0000 12321.5000 EAST",
        "resolve_mark_scatter_seed7 = minecraft:the_nether -40128.5000 77.0000 12328.5000 EAST",
    };

    @GameTest(templateName = "gtgs_backrooms_gametest:test_arena", tickLimit = 400)
    public void referenceArrivalsDoNotChange(TestContext context) {
        MinecraftServer server = context.getWorld().getServer();
        ServerWorld nether = server.getWorld(World.NETHER);
        ServerWorld end = server.getWorld(World.END);
        context.assertTrue(nether != null && end != null, "Le serveur de test a un Nether et un End.");
        ServerWorld overworld = context.getWorld();
        long before = System.nanoTime();
        prepare(nether, NETHER_START);
        prepare(overworld, SURFACE_START);
        prepare(end, END_START);
        prepare(nether, NETHER_FROM_SURFACE);
        prepare(nether, MARK.pos());
        ModConstants.LOGGER.info("[G3] Terrain prepare en {} ms.", (System.nanoTime() - before) / 1_000_000L);
        PortalSettings tall = new PortalSettings(2, 3, true);
        PortalSettings scattered = new PortalSettings(1, 2, true, true, false, false, PortalExit.STRAIGHT, SCATTER, false, false, PortalPlacement.LOOK,
                Optional.empty(), Optional.of(MARK));
        String[] actual = {
            describe("nether_1x2_south", GameTestArrivals.arrive(nether, NETHER_START.getX(), NETHER_START.getY(), NETHER_START.getZ(), PortalFacing.SOUTH,
                    PortalSettings.DEFAULT)),
            describe("nether_2x3_east", GameTestArrivals.arrive(nether, NETHER_START.getX(), NETHER_START.getY(), NETHER_START.getZ(), PortalFacing.EAST, tall)),
            describe("nether_1x2_up", GameTestArrivals.arrive(nether, NETHER_START.getX(), NETHER_START.getY(), NETHER_START.getZ(), PortalFacing.UP,
                    PortalSettings.DEFAULT)),
            describe("surface_1x2_north", GameTestArrivals.arrive(overworld, SURFACE_START.getX(), SURFACE_START.getY(), SURFACE_START.getZ(),
                    PortalFacing.NORTH, PortalSettings.DEFAULT)),
            describe("end_void_1x2_south", GameTestArrivals.arrive(end, END_START.getX(), END_START.getY(), END_START.getZ(), PortalFacing.SOUTH,
                    PortalSettings.DEFAULT)),
            describe("resolve_overworld_to_nether", GameTestArrivals.resolve(server, World.OVERWORLD, SURFACE_START, PortalFacing.WEST,
                    PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.empty()), 1L)),
            describe("resolve_mark_exact", GameTestArrivals.resolve(server, World.OVERWORLD, SURFACE_START, PortalFacing.SOUTH,
                    PortalSettings.DEFAULT.withExit(Optional.of(World.NETHER), Optional.of(MARK)), 1L)),
            describe("resolve_mark_scatter_seed7", GameTestArrivals.resolve(server, World.OVERWORLD, SURFACE_START, PortalFacing.SOUTH, scattered,
                    7L)),
        };
        for (String line : actual) {
            ModConstants.LOGGER.info("[G3] {}", line);
        }
        context.assertTrue(actual.length == EXPECTED.length, "Autant de cas que de references.");
        for (int i = 0; i < actual.length; i++) {
            context.assertTrue(actual[i].equals(EXPECTED[i]), "Arrivee de reference changee : attendu " + EXPECTED[i] + ", obtenu " + actual[i] + ".");
        }
        context.complete();
    }

    /**
     * Genere les features des chunks autour d'une colonne, un chunk apres l'autre dans un ordre fixe. Charger un chunk
     * entier demande les features de ses huit voisins, que vanilla pose sur ses threads de travail dans un ordre qui change
     * d'un lancement a l'autre ; une feature qui deborde sur le chunk d'a cote (colonnes et taches de basalte des deltas,
     * arbres) tombe donc sur un terrain qui varie entre deux mondes neufs de meme graine, et l'arrivee avec elle (releve :
     * a la meme graine, une colonne tantot pleine de basalte, tantot creuse). Un chunk demande seul au statut des features
     * ne fait courir que les siennes : l'ordre est celui de cette boucle, le terrain le meme a chaque monde neuf. Sur un
     * monde deja joue, les chunks reviennent du disque tels quels.
     */
    private static void prepare(ServerWorld world, BlockPos around) {
        int centerX = ChunkSectionPos.getSectionCoord(around.getX());
        int centerZ = ChunkSectionPos.getSectionCoord(around.getZ());
        for (int chunkX = centerX - PREPARED_RADIUS; chunkX <= centerX + PREPARED_RADIUS; chunkX++) {
            for (int chunkZ = centerZ - PREPARED_RADIUS; chunkZ <= centerZ + PREPARED_RADIUS; chunkZ++) {
                world.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true);
            }
        }
    }

    private static String describe(String name, @Nullable PortalTargets.Target target) {
        if (target == null) {
            return name + " = aucune";
        }
        Vec3d p = target.position();
        return String.format(Locale.ROOT, "%s = %s %.4f %.4f %.4f %s", name, target.dimension().getValue(), p.x, p.y, p.z, target.facing());
    }
}
