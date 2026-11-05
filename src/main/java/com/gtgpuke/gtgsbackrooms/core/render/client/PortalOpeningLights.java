package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSelection;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Les portails du monde d'une zone, pour la lumiere ({@link PortalOpenings}) : seuls comptent ceux vers un monde a lumiere cuite, la
 * lumiere vanilla d'un autre monde n'entrant jamais dans la lumiere du mod. Le cadre d'un tel portail arrete la lumiere de son monde ;
 * celui d'un portail ouvert qui la laisse passer est un passage, avec la transformation du portail et la zone qui cuit la lumiere de
 * l'autre cote, et les neons de la-bas, proches de son arrivee et visibles d'elle, eclairent par lui, reportes de ce cote. Un portail
 * vers un monde sans lumiere cuite (l'Overworld) n'est rien pour elle. Un portail aller simple n'a pas de cadre de retour : pour la
 * lumiere, son arrivee en tient lieu ({@link #returnPassages}), sinon ce qu'on voit par l'entree n'aurait rien de la lumiere de ce
 * cote (un sol noir des le plan). Recalcules a chaque tick, sans allocation ; la zone ne recuit que si quelque chose a change.
 */
public final class PortalOpeningLights {

    private static final int INITIAL_ELEMENTS = 16;
    /** Neons de l'autre cote reportes par portail, les plus proches de son arrivee. */
    private static final int NEONS_PER_PORTAL = 24;
    /** Au-dela de la portee d'une dalle, ce qu'il faut de plus pour trouver les neons qui eclairent encore par le cadre. */
    private static final double SEARCH_MARGIN = 2.0;
    /** Ecart au plan d'arrivee sous lequel un neon n'est pas devant lui. */
    private static final double FRONT_MARGIN = 0.05;
    /** Cases parcourues au plus entre un neon et l'arrivee. */
    private static final int MAX_STEPS = 64;
    /** Points de l'arrivee d'ou un neon doit se voir : son centre et le milieu de ses quatre quarts. */
    private static final int FRAME_POINTS = 5;
    private static final double QUARTER = 0.25;
    /** Ecart, en blocs, sous lequel un portail est le cadre de retour d'une arrivee. */
    private static final double FRAME_MATCH = 0.5;
    private static final double[] CENTER = new double[3];
    private static final double[] HALF_WIDTH = new double[3];
    private static final double[] HALF_HEIGHT = new double[3];
    private static final double[] ROTATION = new double[9];
    private static final double[] ARRIVAL = new double[3];
    private static final float[] NEON_COLOR = new float[3];
    private static final float[] CORNERS = new float[12];
    private static final BlockPos.Mutable CELL = new BlockPos.Mutable();
    private static final NeonSelection SELECTION = new NeonSelection(NEONS_PER_PORTAL);
    private static float[] values = new float[PortalOpenings.VALUES * INITIAL_ELEMENTS];
    private static LightZone[] zones = new LightZone[INITIAL_ELEMENTS];
    private static int count;

    private PortalOpeningLights() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Les portails d'un monde, poses sur sa zone s'ils ont change (thread de rendu).
     *
     * @param world monde de la zone
     * @param zone zone de lumiere cuite de ce monde
     */
    static void update(ClientWorld world, LightZone zone) {
        LightBakeSettings settings = zone.settings();
        count = 0;
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            PortalFacing facing = portal.facingSource();
            int face = PortalOpenings.face(facing.normal.x, facing.normal.y, facing.normal.z);
            if (!portal.visible() || face < 0 || !LookProfiles.of(portal.destinationDim()).bakedLight().any()) {
                continue;
            }
            World destination = portal.transmitsLight() && portal.teleportable() ? RemoteWorldRegistry.worldOf(portal) : null;
            Vec3d arrival = portal.destinationPos();
            LightZone beyond = destination == null ? null : LightCopy.zoneCovering(destination, arrival.x, arrival.y, arrival.z);
            if (destination != null && beyond == null) {
                // Aucune zone ne cuit la lumiere de l'autre cote : le portail ne compte pas, plutot qu'un mur sombre.
                continue;
            }
            grow(1);
            Vec3d center = portal.getPos();
            CENTER[0] = center.x;
            CENTER[1] = center.y;
            CENTER[2] = center.z;
            half(HALF_WIDTH, facing.axisW, portal.width());
            half(HALF_HEIGHT, facing.axisH, portal.height());
            if (destination == null) {
                zones[count] = null;
                PortalOpenings.writeWall(values, count++, CENTER, HALF_WIDTH, HALF_HEIGHT, face);
                continue;
            }
            rotation(facing, portal.facingDestination(), ROTATION);
            ARRIVAL[0] = arrival.x;
            ARRIVAL[1] = arrival.y;
            ARRIVAL[2] = arrival.z;
            int passage = count;
            zones[count] = beyond;
            PortalOpenings.writePassage(values, count++, CENTER, HALF_WIDTH, HALF_HEIGHT, face, ROTATION, ARRIVAL);
            neonsSeenThrough(destination, arrival, portal.facingDestination(), center, facing, portal.width(), portal.height(), passage, settings.reach());
        }
        returnPassages(world, world, zone, settings.reach());
        ClientWorld player = MinecraftClient.getInstance().world;
        if (player != null && player != world) {
            returnPassages(player, world, zone, settings.reach());
        }
        if (!zone.openings().sameAs(values, zones, count)) {
            zone.openings(PortalOpenings.of(values, zones, count));
        }
        // Le tableau n'est qu'un brouillon, que les ouvertures copient : il ne retient pas les zones de l'autre cote apres la partie.
        Arrays.fill(zones, 0, count, null);
    }

    /**
     * Ce que devient un vecteur passe d'un cote d'un portail a l'autre, ligne par ligne : la transformation de
     * {@link PortalFacing#transformVector}, sans allocation. De l'arrivee vers l'entree, les deux faces echangees.
     */
    static void rotation(PortalFacing source, PortalFacing destination, double[] out) {
        for (int row = 0; row < 3; row++) {
            double dw = component(destination.axisW, row);
            double dh = component(destination.axisH, row);
            double dn = component(destination.normal, row);
            for (int column = 0; column < 3; column++) {
                out[row * 3 + column] = -dw * component(source.axisW, column) + dh * component(source.axisH, column) - dn * component(source.normal, column);
            }
        }
    }

    private static double component(Vec3d vector, int axis) {
        return axis == 0 ? vector.x : axis == 1 ? vector.y : vector.z;
    }

    private static void grow(int more) {
        if ((count + more) * PortalOpenings.VALUES > values.length) {
            values = Arrays.copyOf(values, Math.max(values.length * 2, (count + more) * PortalOpenings.VALUES));
        }
        if (count + more > zones.length) {
            zones = Arrays.copyOf(zones, Math.max(zones.length * 2, count + more));
        }
    }

    private static void half(double[] out, Vec3d axis, float size) {
        out[0] = axis.x * size * 0.5;
        out[1] = axis.y * size * 0.5;
        out[2] = axis.z * size * 0.5;
    }

    /**
     * Les neons de l'autre cote qui eclairent par un passage : devant son arrivee la-bas, a portee d'une dalle, allumes au calendrier,
     * leur face libre et rien d'opaque entre elle et l'arrivee. Chacun est reporte de ce cote, derriere le cadre.
     *
     * @param from monde de l'arrivee, celui des neons
     * @param arrival point d'arrivee dans ce monde
     * @param exit orientation de l'arrivee, normale vers le cote de sortie
     * @param center centre du cadre de ce cote
     * @param frame orientation du cadre de ce cote
     * @param width largeur du cadre
     * @param height hauteur du cadre
     * @param passage rang du passage dans la liste
     * @param reach portee d'une dalle
     */
    private static void neonsSeenThrough(World from, Vec3d arrival, PortalFacing exit, Vec3d center, PortalFacing frame, float width, float height, int passage,
            float reach) {
        LookProfile look = LookProfiles.of(from.getRegistryKey());
        LookProfile.Neons neons = look.neons();
        LookProfile.BakedLight baked = look.bakedLight();
        if (!neons.any() || !baked.any()) {
            return;
        }
        Identifier dimension = from.getRegistryKey().getValue();
        int seed = NeonSchedule.seed(dimension);
        NEON_COLOR[0] = neons.red() * baked.emission();
        NEON_COLOR[1] = neons.green() * baked.emission();
        NEON_COLOR[2] = neons.blue() * baked.emission();
        double radius = reach + SEARCH_MARGIN + Math.max(width, height) * 0.5;
        NeonIndex.collect(from, arrival.x, arrival.y, arrival.z, radius, SELECTION);
        for (int i = 0; i < SELECTION.count(); i++) {
            long packed = SELECTION.position(i);
            int x = BlockPos.unpackLongX(packed);
            int y = BlockPos.unpackLongY(packed);
            int z = BlockPos.unpackLongZ(packed);
            // La face du bas du neon, celle qui eclaire, comme la cuisson la prend.
            double faceX = x + 0.5;
            double faceY = y;
            double faceZ = z + 0.5;
            double ahead = (faceX - arrival.x) * exit.normal.x + (faceY - arrival.y) * exit.normal.y + (faceZ - arrival.z) * exit.normal.z;
            if (ahead < FRONT_MARGIN || NeonSchedule.blackout(x, z, seed, neons.blackout())
                    || NeonSchedule.kind(NeonSchedule.character(x, y, z, seed), neons.flickering(), neons.dead()) == NeonSchedule.DEAD
                    || opaque(from, x, y - 1, z) || !seenFromFrame(from, arrival, exit, width, height, faceX, faceY - FRONT_MARGIN, faceZ)) {
                continue;
            }
            PortalOpenings.neonFace(CORNERS, x, y, z, baked.size());
            for (int corner = 0; corner < 4; corner++) {
                double cx = CORNERS[corner * 3];
                double cy = CORNERS[corner * 3 + 1];
                double cz = CORNERS[corner * 3 + 2];
                for (int axis = 0; axis < 3; axis++) {
                    CORNERS[corner * 3 + axis] = (float) PortalFacing.transformCoordinate(exit, arrival, frame, center, cx, cy, cz, axis);
                }
            }
            // La face qui eclaire, reportee : l'image du bas, d'apres l'image de deux points.
            double downX = PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY - 1.0, faceZ, 0)
                    - PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY, faceZ, 0);
            double downY = PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY - 1.0, faceZ, 1)
                    - PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY, faceZ, 1);
            double downZ = PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY - 1.0, faceZ, 2)
                    - PortalFacing.transformCoordinate(exit, arrival, frame, center, faceX, faceY, faceZ, 2);
            int face = PortalOpenings.face(Math.round(downX), Math.round(downY), Math.round(downZ));
            if (face < 0) {
                continue;
            }
            grow(1);
            zones[count] = null;
            PortalOpenings.writeVirtual(values, count++, CORNERS, face, NEON_COLOR, passage);
        }
    }

    /**
     * {@code true} si rien d'opaque ne cache le neon de l'arrivee : de son centre ou du milieu d'un de ses quarts. Un neon que le centre ne
     * voit pas peut encore eclairer par un bord du cadre.
     */
    private static boolean seenFromFrame(World from, Vec3d arrival, PortalFacing exit, float frameWidth, float frameHeight, double x, double y, double z) {
        double width = frameWidth * QUARTER;
        double height = frameHeight * QUARTER;
        for (int point = 0; point < FRAME_POINTS; point++) {
            double w = point == 0 ? 0.0 : (point & 1) == 0 ? -width : width;
            double h = point == 0 ? 0.0 : point < 3 ? -height : height;
            if (clear(from, x, y, z, arrival.x + exit.axisW.x * w + exit.axisH.x * h + exit.normal.x * FRONT_MARGIN,
                    arrival.y + exit.axisW.y * w + exit.axisH.y * h + exit.normal.y * FRONT_MARGIN,
                    arrival.z + exit.axisW.z * w + exit.axisH.z * h + exit.normal.z * FRONT_MARGIN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Les portails de {@code source} qui arrivent dans {@code world} sans cadre de retour (aller simple, sortie fugace hors de vue) : pour la
     * lumiere, leur arrivee est un passage vers leur entree, comme le serait un cadre de retour, avec la transformation inverse du portail,
     * la zone qui cuit la lumiere autour de l'entree, et les neons de la-bas reportes derriere l'arrivee. Ce qu'on voit par l'entree a
     * ainsi la lumiere de ce cote. Un cadre de retour present (aller-retour, sortie fugace vue) le fait lui-meme.
     */
    private static void returnPassages(World source, World world, LightZone zone, float reach) {
        if (!LookProfiles.of(source.getRegistryKey()).bakedLight().any()) {
            return;
        }
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(source);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            Vec3d arrival = portal.destinationPos();
            PortalFacing exit = portal.facingDestination();
            int face = PortalOpenings.face(exit.normal.x, exit.normal.y, exit.normal.z);
            if (!portal.visible() || !portal.teleportable() || !portal.transmitsLight() || face < 0 || !portal.destinationDim().equals(world.getRegistryKey())
                    || !zone.covers(arrival.x, arrival.y, arrival.z) || hasFrameAt(world, arrival)) {
                continue;
            }
            Vec3d entry = portal.getPos();
            LightZone beyond = LightCopy.zoneCovering(source, entry.x, entry.y, entry.z);
            if (beyond == null) {
                continue;
            }
            grow(1);
            CENTER[0] = arrival.x;
            CENTER[1] = arrival.y;
            CENTER[2] = arrival.z;
            half(HALF_WIDTH, exit.axisW, portal.width());
            half(HALF_HEIGHT, exit.axisH, portal.height());
            rotation(exit, portal.facingSource(), ROTATION);
            ARRIVAL[0] = entry.x;
            ARRIVAL[1] = entry.y;
            ARRIVAL[2] = entry.z;
            int passage = count;
            zones[count] = beyond;
            PortalOpenings.writePassage(values, count++, CENTER, HALF_WIDTH, HALF_HEIGHT, face, ROTATION, ARRIVAL);
            neonsSeenThrough(source, entry, portal.facingSource(), arrival, exit, portal.width(), portal.height(), passage, reach);
        }
    }

    /** {@code true} si un portail de ce monde a son centre a cet endroit : un cadre de retour. */
    private static boolean hasFrameAt(World world, Vec3d at) {
        List<BackroomsPortalEntity> portals = ClientPortalIndex.allPortals(world);
        for (int i = 0; i < portals.size(); i++) {
            if (portals.get(i).getPos().squaredDistanceTo(at) < FRAME_MATCH * FRAME_MATCH) {
                return true;
            }
        }
        return false;
    }

    private static boolean opaque(World world, int x, int y, int z) {
        CELL.set(x, y, z);
        return world.getBlockState(CELL).isOpaqueFullCube(world, CELL);
    }

    /** {@code true} si rien d'opaque ne coupe le segment, case par case, sans allocation. */
    private static boolean clear(World world, double x0, double y0, double z0, double x1, double y1, double z1) {
        int x = MathHelper.floor(x0);
        int y = MathHelper.floor(y0);
        int z = MathHelper.floor(z0);
        int endX = MathHelper.floor(x1);
        int endY = MathHelper.floor(y1);
        int endZ = MathHelper.floor(z1);
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double deltaX = dx == 0.0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double deltaY = dy == 0.0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double deltaZ = dz == 0.0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        double nextX = dx > 0.0 ? (x + 1 - x0) * deltaX : dx < 0.0 ? (x0 - x) * deltaX : Double.MAX_VALUE;
        double nextY = dy > 0.0 ? (y + 1 - y0) * deltaY : dy < 0.0 ? (y0 - y) * deltaY : Double.MAX_VALUE;
        double nextZ = dz > 0.0 ? (z + 1 - z0) * deltaZ : dz < 0.0 ? (z0 - z) * deltaZ : Double.MAX_VALUE;
        for (int step = 0; step < MAX_STEPS && (x != endX || y != endY || z != endZ); step++) {
            if (nextX <= nextY && nextX <= nextZ) {
                x += dx > 0.0 ? 1 : -1;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                y += dy > 0.0 ? 1 : -1;
                nextY += deltaY;
            } else {
                z += dz > 0.0 ? 1 : -1;
                nextZ += deltaZ;
            }
            if (opaque(world, x, y, z)) {
                return false;
            }
        }
        return true;
    }
}
