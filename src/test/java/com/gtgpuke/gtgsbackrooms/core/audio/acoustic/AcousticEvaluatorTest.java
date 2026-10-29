package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

@DisplayName("Acoustique par rayons sur une copie du monde")
class AcousticEvaluatorTest {

    private static final float EPSILON = 1.0e-6f;
    private static final AudioProfile.Occlusion RULE = new AudioProfile.Occlusion(0.6f, 0.25f, 0.85f);

    /** Un monde de test : des sections connues, vides, dans lesquelles on pose des blocs pleins. */
    private static final class World {

        private final SolidityGrid grid = new SolidityGrid();

        World known(int sectionX, int sectionY, int sectionZ) {
            this.grid.setSection(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ), new byte[SolidityWindow.SECTION_BLOCKS]);
            return this;
        }

        World full(int x, int y, int z) {
            return put(x, y, z, OcclusionTrace.FULL);
        }

        World partial(int x, int y, int z) {
            return put(x, y, z, OcclusionTrace.PARTIAL);
        }

        /** Un mur plein sur tout un plan d'une boite, de {@code from} a {@code to} inclus. */
        World box(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
            for (int x = fromX; x <= toX; x++) {
                for (int y = fromY; y <= toY; y++) {
                    for (int z = fromZ; z <= toZ; z++) {
                        if (x == fromX || x == toX || y == fromY || y == toY || z == fromZ || z == toZ) {
                            full(x, y, z);
                        }
                    }
                }
            }
            return this;
        }

        /** Un sol plein a cette hauteur, sur un carre de sections, posees entieres (connues). */
        World floor(int y, int fromSectionX, int toSectionX, int fromSectionZ, int toSectionZ) {
            for (int sectionX = fromSectionX; sectionX <= toSectionX; sectionX++) {
                for (int sectionZ = fromSectionZ; sectionZ <= toSectionZ; sectionZ++) {
                    byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            blocks[SolidityWindow.blockIndex(x, y, z)] = OcclusionTrace.FULL;
                        }
                    }
                    this.grid.setSection(ChunkSectionPos.asLong(sectionX, y >> 4, sectionZ), blocks);
                }
            }
            return this;
        }

        SolidityWindow window(int x, int y, int z) {
            SolidityWindow window = new SolidityWindow();
            this.grid.window(x >> 4, y >> 4, z >> 4, window);
            return window;
        }

        private World put(int x, int y, int z, int solidity) {
            long key = ChunkSectionPos.asLong(x >> 4, y >> 4, z >> 4);
            byte[] blocks = new byte[SolidityWindow.SECTION_BLOCKS];
            SolidityWindow window = window(x, y, z);
            if (window.known(x, y, z)) {
                for (int i = 0; i < SolidityWindow.SECTION_BLOCKS; i++) {
                    blocks[i] = (byte) window.at(x & ~15 | i & 15, y & ~15 | i >> 8 & 15, z & ~15 | i >> 4 & 15);
                }
            }
            blocks[SolidityWindow.blockIndex(x, y, z)] = (byte) solidity;
            this.grid.setSection(key, blocks);
            return this;
        }
    }

    @Test
    @DisplayName("Dans une salle fermee, tous les rayons rencontrent un mur : espace ferme, taille de la salle")
    void closedRoomIsClosed() {
        World world = new World().known(0, 0, 0).box(2, 2, 2, 12, 8, 12);
        AcousticResult result = AcousticEvaluator.of(AudioQuality.FULL).reverb(world.window(7, 5, 7), 7.5, 5.5, 7.5);
        assertEquals(0.0f, result.openness(), EPSILON, "Aucun rayon ne sort.");
        assertEquals(0.0f, result.unknown(), EPSILON, "Aucun rayon perdu.");
        assertTrue(result.reliable(), "Un resultat fiable.");
        assertTrue(result.roomSize() > 2.0f && result.roomSize() < 10.0f, "Le libre parcours est celui de la salle : " + result.roomSize() + ".");
        assertEquals(0.0f, result.occlusion(), EPSILON, "Les rayons ne disent rien de l'occlusion.");
        AcousticResult light = AcousticEvaluator.of(AudioQuality.LIGHT).reverb(world.window(7, 5, 7), 7.5, 5.5, 7.5);
        assertEquals(0.0f, light.openness(), EPSILON, "En qualite legere aussi.");
        assertTrue(ReverbPresets.index(result) < 4, "Un reglage de salle fermee.");
    }

    @Test
    @DisplayName("En plein air connu, tous les rayons sortent ; hors du monde copie, ils sont perdus et le resultat ne vaut rien")
    void openAirIsOpen() {
        World world = new World();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    world.known(x, y, z);
                }
            }
        }
        AcousticEvaluator evaluator = new AcousticEvaluator(16, 2, 1);
        AcousticResult open = evaluator.reverb(world.window(8, 8, 8), 8.5, 8.5, 8.5);
        assertEquals(1.0f, open.openness(), EPSILON, "Tout sort.");
        assertEquals(0.0f, open.unknown(), EPSILON, "Rien de perdu.");
        assertEquals((float) AcousticEvaluator.MAX_RAY, open.roomSize(), EPSILON, "Sans mur, la taille est la portee des rayons.");
        AcousticResult unknown = evaluator.reverb(new World().window(8, 8, 8), 8.5, 8.5, 8.5);
        assertEquals(1.0f, unknown.unknown(), EPSILON, "Sorti du monde copie des le premier pas : tous perdus.");
        assertEquals(0.0f, unknown.openness(), EPSILON, "Rien a dire de l'ouverture.");
        assertFalse(unknown.reliable(), "Un resultat qui ne vaut rien.");
        assertTrue(ReverbPresets.index(open) >= 4, "Un reglage d'espace ouvert.");
        World half = new World().known(0, 0, 0);
        AcousticResult halfKnown = evaluator.reverb(half.window(8, 8, 8), 8.5, 8.5, 8.5);
        assertEquals(1.0f, halfKnown.unknown(), EPSILON, "Une seule section connue : tous les rayons en sortent avant la portee.");
    }

    @Test
    @DisplayName("Un mur d'un seul cote : les rayons qui le touchent rebondissent, les autres sortent, l'espace est a demi ouvert")
    void oneWallIsHalfOpen() {
        World world = new World();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -1; y <= 3; y++) {
                    world.known(x, y, z);
                }
            }
        }
        world.floor(4, -3, 3, -3, 3);
        AcousticResult result = new AcousticEvaluator(32, 3, 1).reverb(world.window(8, 8, 8), 8.5, 8.5, 8.5);
        assertTrue(result.reliable(), "Le monde copie va plus loin que les rayons : rien de perdu (" + result.unknown() + ").");
        assertTrue(result.openness() > 0.3f && result.openness() < 0.8f, "Une part des rayons sort : " + result.openness() + ".");
        assertTrue(result.roomSize() > 3.0f, "Les rayons vers le sol font quelques blocs avant lui : " + result.roomSize() + ".");
    }

    @Test
    @DisplayName("Un cadre de portail dans un mur de la salle laisse sortir les rayons qui le traversent, transposes de l'autre cote")
    void raysContinueThroughPortals() {
        World room = new World().known(0, 0, 0).box(2, 2, 2, 12, 8, 12);
        World outside = new World();
        for (int x = 3; x <= 9; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 3; z <= 9; z++) {
                    outside.known(x, y, z);
                }
            }
        }
        PortalPlanes planes = new PortalPlanes();
        Vec3d arrival = new Vec3d(100.5, 65.0, 100.5);
        // Un cadre de trois sur trois contre le mur nord de la salle (z = 2), vu de l'interieur : son cote d'entree regarde le sud.
        planes.add(PortalFacing.SOUTH, new Vec3d(7.5, 5.5, 3.01), 3.0f, 3.0f, PortalFacing.NORTH, arrival, outside.grid);
        SolidityWindow[] arrivals = {outside.window(100, 65, 100), null, null, null};
        AcousticEvaluator evaluator = new AcousticEvaluator(32, 3, 1);
        AcousticResult closed = evaluator.reverb(room.window(7, 5, 7), 7.5, 5.5, 7.5);
        AcousticResult opened = evaluator.reverb(room.window(7, 5, 7), 7.5, 5.5, 7.5, planes, arrivals);
        assertEquals(0.0f, closed.openness(), EPSILON, "Sans cadre, la salle est fermee.");
        assertTrue(opened.openness() > 0.0f && opened.openness() < 0.5f, "Les rayons qui passent le cadre sortent en plein air : " + opened.openness() + ".");
        assertTrue(opened.reliable(), "Rien de perdu : l'autre cote est copie.");
        World closedOutside = new World().known(6, 4, 6).box(96, 64, 96, 108, 74, 108);
        SolidityWindow[] closedArrivals = {closedOutside.window(100, 65, 100), null, null, null};
        planes.clear();
        planes.add(PortalFacing.SOUTH, new Vec3d(7.5, 5.5, 3.01), 3.0f, 3.0f, PortalFacing.NORTH, arrival, closedOutside.grid);
        AcousticResult twoRooms = evaluator.reverb(room.window(7, 5, 7), 7.5, 5.5, 7.5, planes, closedArrivals);
        assertEquals(0.0f, twoRooms.openness(), EPSILON, "Un cadre vers une autre salle fermee : toujours ferme.");
        assertTrue(twoRooms.reliable(), "Et rien de perdu de l'autre cote.");
        assertTrue(twoRooms.roomSize() > 2.0f && twoRooms.roomSize() < 12.0f, "Les rayons passes rebondissent dans l'autre salle : " + twoRooms.roomSize() + ".");
        RayTrace trace = new RayTrace(1024);
        evaluator.record(trace);
        evaluator.reverb(room.window(7, 5, 7), 7.5, 5.5, 7.5, planes, closedArrivals);
        boolean throughFrame = false;
        for (int i = 0; i < trace.count(); i++) {
            throughFrame |= trace.kind(i) == RayTrace.PORTAL;
        }
        assertTrue(throughFrame, "Un segment enregistre s'arrete au cadre.");
    }

    @Test
    @DisplayName("Un mur entre la source et l'auditeur bouche le chemin direct ; en qualite complete, un detour par une ouverture passe")
    void detoursFindOpenings() {
        World world = new World().known(0, 0, 0);
        for (int y = 0; y <= 15; y++) {
            for (int z = 0; z <= 15; z++) {
                if (z != 9) {
                    world.full(8, y, z);
                }
            }
        }
        SolidityWindow window = world.window(8, 8, 8);
        AcousticEvaluator light = new AcousticEvaluator(16, 2, 1);
        AcousticEvaluator full = new AcousticEvaluator(16, 2, 5);
        assertEquals(0.6f, light.occlusion(window, 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON, "Le mur, et rien d'autre en qualite legere.");
        float detour = full.occlusion(window, 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE);
        assertEquals(AcousticEvaluator.DETOUR_COST, detour, EPSILON, "Le detour par z + 2 passe l'ouverture de biais : seul son cout reste.");
        assertEquals(0.0f, full.occlusion(window, 4.5, 5.5, 8.5, 6.5, 5.5, 8.5, RULE), EPSILON, "Rien a contourner quand le direct est libre.");
        assertEquals(0.0, full.waypointX(), 1.0e-9, "Le direct l'emporte : pas de point de passage.");
        full.occlusion(window, 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE);
        assertEquals(0.6f, full.directOcclusion(), EPSILON, "Le direct, avant les detours.");
        assertEquals(0.0, full.waypointX(), 1.0e-9, "Le detour gagnant passe par z + 2...");
        assertEquals(0.0, full.waypointY(), 1.0e-9, "...a la meme hauteur...");
        assertEquals(2.0, full.waypointZ(), 1.0e-9, "...du cote de l'ouverture.");
    }

    @Test
    @DisplayName("Les segments des rayons et des chemins s'enregistrent sur demande, chacun avec ce qui l'a arrete")
    void raysAreRecorded() {
        World world = new World().known(0, 0, 0).box(2, 2, 2, 12, 8, 12);
        AcousticEvaluator rays = new AcousticEvaluator(8, 1, 5);
        RayTrace trace = new RayTrace(64);
        rays.record(trace);
        rays.reverb(world.window(7, 5, 7), 7.5, 5.5, 7.5);
        assertEquals(16, trace.count(), "Huit rayons, deux segments chacun (un rebond).");
        for (int i = 0; i < trace.count(); i++) {
            assertEquals(RayTrace.REFLECT, trace.kind(i), "Salle fermee : chaque segment finit sur un mur.");
        }
        assertEquals(7.5f, trace.coordinate(0, 0), EPSILON, "Le premier segment part de la source.");
        trace.clear();
        World open = new World();
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    open.known(x, y, z);
                }
            }
        }
        rays.reverb(open.window(8, 8, 8), 8.5, 8.5, 8.5);
        assertEquals(RayTrace.ESCAPE, trace.kind(0), "En plein air, le rayon sort.");
        trace.clear();
        rays.reverb(new World().known(0, 0, 0).window(8, 8, 8), 8.5, 8.5, 8.5);
        assertEquals(RayTrace.LOST, trace.kind(0), "Hors de la copie, le rayon est perdu.");
        trace.clear();
        rays.occlusion(world.window(7, 5, 7), 7.5, 5.5, 7.5, 7.5, 5.5, 9.5, RULE);
        assertEquals(RayTrace.OCCLUSION, trace.kind(0), "Le chemin direct.");
        assertEquals(0.0f, trace.value(0), EPSILON, "Libre.");
        trace.clear();
        rays.occlusion(world.window(7, 5, 7), 7.5, 5.5, 7.5, 14.5, 5.5, 7.5, RULE);
        assertTrue(trace.count() > 1, "Le direct bouche, puis les detours essayes.");
        rays.record(null);
        trace.clear();
        rays.reverb(world.window(7, 5, 7), 7.5, 5.5, 7.5);
        rays.occlusion(world.window(7, 5, 7), 7.5, 5.5, 7.5, 14.5, 5.5, 7.5, RULE);
        assertEquals(0, trace.count(), "Sans enregistrement demande, rien.");
    }

    @Test
    @DisplayName("Un detour qui part dans un mur ne compte pas, et un detour qui reste bouche ne fait pas mieux que le direct")
    void blockedDetoursAreIgnored() {
        World world = new World().known(0, 0, 0);
        for (int y = 0; y <= 15; y++) {
            for (int z = 0; z <= 15; z++) {
                world.full(8, y, z);
            }
        }
        world.full(6, 5, 8).full(2, 5, 8).full(4, 5, 10).full(4, 5, 6);
        SolidityWindow window = world.window(8, 8, 8);
        AcousticEvaluator full = new AcousticEvaluator(16, 2, 9);
        assertEquals(RULE.max(), full.occlusion(window, 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON,
                "Les quatre points de passage sont dans un bloc : le direct reste (le bloc du detour + x est aussi sur son chemin : plafond).");
        World open = new World().known(0, 0, 0);
        for (int y = 0; y <= 15; y++) {
            for (int z = 0; z <= 15; z++) {
                open.full(8, y, z);
            }
        }
        assertEquals(0.6f, full.occlusion(open.window(8, 8, 8), 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON,
                "Un mur sans ouverture : chaque detour le traverse aussi, avec son cout en plus.");
        for (int y = 0; y <= 15; y++) {
            for (int z = 0; z <= 15; z++) {
                open.full(9, y, z);
            }
        }
        assertEquals(RULE.max(), full.occlusion(open.window(8, 8, 8), 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON,
                "Un mur epais : le plafond, detours compris.");
        World partial = new World().known(0, 0, 0).partial(8, 5, 8);
        assertEquals(AcousticEvaluator.DETOUR_COST, full.occlusion(partial.window(8, 8, 8), 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON,
                "Un seul bloc partiel sur le direct : le son le contourne, au prix du detour.");
        assertEquals(0.25f, new AcousticEvaluator(16, 2, 1).occlusion(partial.window(8, 8, 8), 4.5, 5.5, 8.5, 12.5, 5.5, 8.5, RULE), EPSILON,
                "Sans detour, le bloc partiel compte.");
    }

    @Test
    @DisplayName("Les chemins demandes au-dela des quatre detours sont ignores, et un chemin seul n'a aucun detour")
    void pathCountIsBounded() {
        World world = new World().known(0, 0, 0).box(0, 0, 0, 15, 15, 15);
        SolidityWindow window = world.window(8, 8, 8);
        assertEquals(0.0f, new AcousticEvaluator(8, 1, 0).occlusion(window, 2.5, 8.5, 8.5, 13.5, 8.5, 8.5, RULE), EPSILON,
                "Dans la boite vide, le direct est libre, avec ou sans detour.");
        // Un bloc partiel sur le direct : les detours sont essayes, et leur nombre compte.
        SolidityWindow blocked = world.partial(8, 8, 8).window(8, 8, 8);
        float five = new AcousticEvaluator(8, 1, 5).occlusion(blocked, 2.5, 8.5, 8.5, 13.5, 8.5, 8.5, RULE);
        assertEquals(AcousticEvaluator.DETOUR_COST, five, EPSILON, "Cinq chemins : un detour contourne le bloc partiel, a son cout.");
        assertEquals(five, new AcousticEvaluator(8, 1, 9).occlusion(blocked, 2.5, 8.5, 8.5, 13.5, 8.5, 8.5, RULE), EPSILON,
                "Neuf chemins valent cinq : quatre detours au plus.");
        assertEquals(0.25f, new AcousticEvaluator(8, 1, 1).occlusion(blocked, 2.5, 8.5, 8.5, 13.5, 8.5, 8.5, RULE), EPSILON,
                "Un chemin seul : aucun detour, le bloc partiel compte.");
    }
}
