package com.gtgpuke.gtgsbackrooms.core.portal.block;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroArrival;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Ciblage d'un bloc portail : ou il mene, et dans quel sens on en sort.
 *
 * <p>Par defaut (destination automatique), un portail pose hors du Level 0 mene au layer 0 du Level 0, pres des memes X/Z ;
 * pose dans le Level 0, il ramene a la surface du monde normal aux memes X/Z. Les reglages peuvent choisir la dimension
 * d'arrivee : les X/Z y suivent l'echelle de coordonnees des deux dimensions, comme vanilla (un huitieme dans le Nether).
 * Une sortie fixee ({@link PortalMark}) l'emporte : on arrive la ou elle a ete marquee. Par defaut, traverser ne fait pas
 * tourner : la face de sortie regarde a l'oppose de celle d'entree, et on continue dans la meme direction ; vers une
 * sortie fixee, on ressort dans la direction de son regard ; les reglages peuvent imposer un point cardinal de sortie, le
 * sol ou le plafond ({@link PortalExit}) et tirer au sort les X/Z de l'arrivee dans un rayon autour de son point de depart.
 *
 * <p>Dans le Level 0, c'est le Level 0 qui sait ou le cadre d'arrivee tient ({@link LevelZeroArrival}). Dans une
 * dimension a ciel ouvert, il se pose sur le haut reel du sol ; dans une dimension a plafond (le Nether), sur le premier
 * sol plein sous le point de depart ou il tient, hors des fluides. Sans sol pres du point de depart (le vide de l'End, un
 * lac de lave), il se pose au point de depart sur une plateforme d'obsidienne, la place du cadre et du corps degagee : on
 * n'arrive jamais dans des blocs, ni dans un fluide, ni au-dessus du vide.
 */
public final class PortalTargets {

    /** Hauteur, en blocs, dont un cadre d'arrivee au plafond pose a la surface est releve au-dessus du sol. */
    static final int CEILING_LIFT = 2;
    /** Rayon, en colonnes, de la recherche d'un sol autour du point de depart, hors du Level 0 : celui de vanilla pour ses portails. */
    static final int SEARCH_RADIUS = 16;
    /** Hauteur libre, en blocs, au-dessus d'un sol ou l'on arrive : de quoi s'y tenir debout. */
    private static final int STANDING_ROOM = 2;
    /** Marge, en blocs, de la plateforme et de la place degagee autour du cadre, d'un sol sans sol a portee. */
    private static final int PLATFORM_MARGIN = 1;

    /**
     * Destination d'un portail.
     *
     * @param dimension dimension d'arrivee
     * @param position centre du cadre d'arrivee
     * @param facing orientation du cadre d'arrivee, normale vers la sortie de sa face avant
     */
    public record Target(RegistryKey<World> dimension, Vec3d position, PortalFacing facing) {
    }

    private PortalTargets() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Destination d'un bloc portail : vers la sortie fixee si les reglages en ont une, sinon vers la dimension choisie, ou,
     * automatiquement, de tout portail vers le Level 0 et du Level 0 vers la surface de l'overworld.
     *
     * <p>Recherche chunk par chunk ({@link Search}) : elle ne lit que des chunks deja la et s'arrete au premier absent, que
     * le bloc charge par un ticket avant de rappeler au tick suivant, au lieu de le generer sur le thread serveur. Le
     * Level 0 (calcul pur) et une dimension absente n'attendent jamais. Un chunk compte comme la s'il est entier et
     * accessible ({@link #loaded}), pas des qu'un ticket lui donne son niveau. Rappelee avec le meme tirage une fois le
     * chunk la, elle reprend du debut et rend l'arrivee qu'elle aurait trouvee si tout avait ete charge d'emblee.
     *
     * @param server serveur dont on lit les mondes
     * @param dimension dimension du bloc
     * @param anchor position du bloc
     * @param entryFacing orientation de sa face avant
     * @param settings taille du cadre, sens de sortie, rayon de tirage au sort, destination et sortie fixee
     * @param random tirage des X/Z de l'arrivee, quand les reglages en veulent un : le meme a chaque essai d'une recherche
     * @return l'arrivee ({@code null} si la dimension cible est absente), ou le chunk a charger d'abord, dans la dimension
     *     {@link #searchDimension}
     */
    public static Search resolve(
            MinecraftServer server,
            RegistryKey<World> dimension,
            BlockPos anchor,
            PortalFacing entryFacing,
            PortalSettings settings,
            Random random) {
        PortalFacing exitFacing = settings.exit().arrivalFacing(entryFacing);
        Optional<PortalMark> mark = settings.mark();
        if (mark.isPresent()) {
            PortalFacing facing = settings.exit() == PortalExit.STRAIGHT ? mark.get().arrivalFacing() : exitFacing;
            return toMark(server, mark.get(), facing, settings, random);
        }
        RegistryKey<World> target = settings.destination().orElse(automatic(dimension));
        ServerWorld world = server.getWorld(target);
        if (world == null) {
            return new Search(null, null);
        }
        double scale = scale(server.getWorld(dimension), world);
        int x = MathHelper.floor(anchor.getX() * scale) + scatter(random, settings.exitScatter());
        int z = MathHelper.floor(anchor.getZ() * scale) + scatter(random, settings.exitScatter());
        if (target.equals(BackroomsConstants.LEVEL_ZERO_WORLD)) {
            return new Search(LevelZeroArrival.find(world, x, z, exitFacing, settings), null);
        }
        return scan(world, x, anchor.getY(), z, exitFacing, settings);
    }

    /**
     * Dimension ou la recherche d'arrivee lit ses chunks : celle de la sortie fixee, sinon la destination choisie, ou la
     * destination automatique.
     *
     * @param dimension dimension du bloc
     * @param settings destination et sortie fixee
     * @return dimension dont la recherche demande les chunks
     */
    public static RegistryKey<World> searchDimension(RegistryKey<World> dimension, PortalSettings settings) {
        return settings.mark().map(PortalMark::dimension).orElseGet(() -> settings.destination().orElse(automatic(dimension)));
    }

    /** Destination automatique : la surface de l'overworld depuis le Level 0, le Level 0 depuis toute autre dimension. */
    static RegistryKey<World> automatic(RegistryKey<World> dimension) {
        return dimension.equals(BackroomsConstants.LEVEL_ZERO_WORLD) ? World.OVERWORLD : BackroomsConstants.LEVEL_ZERO_WORLD;
    }

    /** Facteur des X/Z d'une dimension a l'autre, comme vanilla ; un quand la dimension de depart n'est pas chargee. */
    private static double scale(@Nullable ServerWorld from, ServerWorld to) {
        return from == null ? 1.0 : DimensionType.getCoordinateScaleFactor(from.getDimension(), to.getDimension());
    }

    /**
     * Arrivee a une sortie fixee : sur le bloc ou elle a ete marquee, ou, avec un rayon de tirage au sort, sur un sol pres
     * d'un point tire autour d'elle.
     */
    private static Search toMark(MinecraftServer server, PortalMark mark, PortalFacing facing, PortalSettings settings, Random random) {
        ServerWorld world = server.getWorld(mark.dimension());
        if (world == null) {
            return new Search(null, null);
        }
        BlockPos pos = mark.pos();
        if (settings.exitScatter() > 0) {
            return scan(world, pos.getX() + scatter(random, settings.exitScatter()), pos.getY(),
                    pos.getZ() + scatter(random, settings.exitScatter()), facing, settings);
        }
        ChunkPos missing = missingChunk(world, pos.getX(), pos.getZ(), null);
        if (missing != null) {
            return new Search(null, missing);
        }
        return new Search(target(world, pos.getX(), floorHeight(world, pos.getX(), pos.getY(), pos.getZ()), pos.getZ(), facing, settings), null);
    }

    /**
     * Resultat d'une recherche chunk par chunk ({@link #resolve}) : l'arrivee, ou le premier chunk absent (pas entier et
     * accessible) dans l'ordre de la recherche, qu'elle nomme au lieu de le generer. Le bloc le charge par un ticket et
     * rappelle au tick suivant ; les colonnes se relisent dans le meme ordre, et la premiere avec un sol ne depend pas du
     * moment ou chaque chunk est arrive. Les deux champs ne sont jamais donnes ensemble.
     *
     * @param target arrivee, ou {@code null} si un chunk manque
     * @param missingChunk chunk a charger d'abord, ou {@code null} si l'arrivee est trouvee
     */
    public record Search(@Nullable Target target, @Nullable ChunkPos missingChunk) {

        /** {@code true} si un chunk doit etre charge avant de conclure. */
        public boolean waits() {
            return this.missingChunk != null;
        }
    }

    /**
     * Arrivee hors du Level 0, la colonne de depart d'abord puis des anneaux de colonnes autour d'elle, jusqu'a
     * {@value #SEARCH_RADIUS} : sur le haut du sol dans une dimension a ciel ouvert (un vide sans sol ne compte pas), sur le
     * premier sol ou l'on tient debout sous le point de depart, le cadre entier hors des blocs et des fluides, dans une
     * dimension a plafond, ou au-dessus a defaut. Sans sol a portee, sur une plateforme au point de depart ({@link #onPlatform}).
     */
    private static Search scan(ServerWorld world, int x, int startY, int z, PortalFacing facing, PortalSettings settings) {
        boolean ceiling = world.getDimension().hasCeiling();
        // Sous un plafond, chaque colonne ou l'on tient fait verifier le cadre ({@link #frameFits}), dont les cellules, jusqu'a
        // huit blocs de la colonne, peuvent etre dans le chunk d'a cote : il doit etre la aussi.
        BlockBox frame = ceiling ? footprint(world, startY, facing, settings) : null;
        for (int ring = 0; ring <= SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int columnX = x + dx;
                    int columnZ = z + dz;
                    ChunkPos missing = missingChunk(world, columnX, columnZ, frame);
                    if (missing != null) {
                        return new Search(null, missing);
                    }
                    Double floor = ceiling ? caveFloor(world, columnX, startY, columnZ, facing, settings) : surfaceFloor(world, columnX, columnZ);
                    if (floor != null) {
                        return new Search(target(world, columnX, floor, columnZ, facing, settings), null);
                    }
                }
            }
        }
        // La plateforme et son cadre tiennent dans les colonnes lues (seize autour du depart) : leurs chunks sont tous la.
        return new Search(onPlatform(world, x, startY, z, facing, settings), null);
    }

    /**
     * Premier chunk absent parmi ceux qu'une colonne fait lire : le sien, et ceux de l'empreinte du cadre s'il y en a une.
     *
     * @param frame empreinte horizontale du cadre autour de la colonne, ou {@code null} si la colonne seule est lue
     * @return le chunk a charger d'abord, ou {@code null} s'ils sont tous la
     */
    private static @Nullable ChunkPos missingChunk(ServerWorld world, int x, int z, @Nullable BlockBox frame) {
        int minX = frame == null ? x : x + frame.getMinX();
        int maxX = frame == null ? x : x + frame.getMaxX();
        int minZ = frame == null ? z : z + frame.getMinZ();
        int maxZ = frame == null ? z : z + frame.getMaxZ();
        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                if (!loaded(world, chunkX, chunkZ)) {
                    return new ChunkPos(chunkX, chunkZ);
                }
            }
        }
        return null;
    }

    /**
     * {@code true} si le chunk est entier et accessible, sans rien charger ni attendre. Pas {@code World.isChunkLoaded} :
     * cote serveur, il repond vrai des qu'un ticket donne son niveau au chunk, avant toute generation, et la premiere
     * lecture d'un bloc genererait alors le chunk sur le thread serveur, le gel que la recherche chunk par chunk evite.
     */
    static boolean loaded(ServerWorld world, int chunkX, int chunkZ) {
        return world.getChunkManager().getWorldChunk(chunkX, chunkZ) != null;
    }

    /**
     * Empreinte horizontale du cadre autour de sa colonne, la meme pour toute colonne : les cellules que {@link #frameFits}
     * lit, en coordonnees relatives a la colonne.
     */
    private static BlockBox footprint(ServerWorld world, int startY, PortalFacing facing, PortalSettings settings) {
        Vec3d center = target(world, 0, startY, 0, facing, settings).position();
        return PortalArea.cells(PortalArea.anchor(center, facing, settings.width(), settings.height()), facing, settings);
    }

    /**
     * Arrivee sans sol a portee, repli de la recherche d'un sol : au point de depart, a sa hauteur ramenee dans la hauteur logique
     * du monde (et au-dessus d'un fluide), sur une plateforme d'obsidienne de {@value #PLATFORM_MARGIN} bloc de marge autour
     * du cadre, la place du cadre et du corps degagee de tout bloc et de tout fluide. Pose la plateforme dans le monde.
     *
     * @return destination, sur la plateforme
     */
    public static Target onPlatform(ServerWorld world, int x, int startY, int z, PortalFacing facing, PortalSettings settings) {
        int bottom = world.getBottomY() + 1;
        int top = world.getBottomY() + world.getDimension().logicalHeight() - STANDING_ROOM - 1;
        BlockPos.Mutable pos = new BlockPos.Mutable(x, MathHelper.clamp(startY, bottom, top), z);
        while (pos.getY() < top && !world.getFluidState(pos).isEmpty()) {
            pos.move(Direction.UP);
        }
        int floor = pos.getY();
        Target target = target(world, x, floor, z, facing, settings);
        BlockBox frame = PortalArea.cells(PortalArea.anchor(target.position(), facing, settings.width(), settings.height()), facing, settings);
        int ceiling = Math.max(frame.getMaxY(), floor + STANDING_ROOM);
        for (int cellX = frame.getMinX() - PLATFORM_MARGIN; cellX <= frame.getMaxX() + PLATFORM_MARGIN; cellX++) {
            for (int cellZ = frame.getMinZ() - PLATFORM_MARGIN; cellZ <= frame.getMaxZ() + PLATFORM_MARGIN; cellZ++) {
                world.setBlockState(pos.set(cellX, floor - 1, cellZ), Blocks.OBSIDIAN.getDefaultState());
                for (int cellY = floor; cellY <= ceiling; cellY++) {
                    world.setBlockState(pos.set(cellX, cellY, cellZ), Blocks.AIR.getDefaultState());
                }
            }
        }
        return target;
    }

    private static Target target(ServerWorld world, int x, double floor, int z, PortalFacing facing, PortalSettings settings) {
        Vec3d point = new Vec3d(x + 0.5, floor + ceilingLift(facing), z + 0.5);
        return new Target(world.getRegistryKey(), PortalArea.center(point, facing, settings), facing);
    }

    /** Haut reel du sol d'une colonne a ciel ouvert, ou {@code null} si elle n'a aucun bloc (le vide de l'End). */
    private static @Nullable Double surfaceFloor(ServerWorld world, int x, int z) {
        // World.getTopY renvoie le fond du monde si le chunk n'est pas charge : la recherche n'arrive ici qu'avec son chunk.
        int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        return surfaceY <= world.getBottomY() ? null : floorHeight(world, x, surfaceY, z);
    }

    /**
     * Sol d'une colonne d'une dimension a plafond : le premier bloc plein sous le point de depart (puis au-dessus) surmonte
     * de {@value #STANDING_ROOM} blocs libres, hors des fluides, ou le cadre entier tient sans bloc ni fluide ; {@code null}
     * s'il n'y en a aucun dans la hauteur logique.
     */
    private static @Nullable Double caveFloor(ServerWorld world, int x, int startY, int z, PortalFacing facing, PortalSettings settings) {
        int bottom = world.getBottomY() + 1;
        int top = world.getBottomY() + world.getDimension().logicalHeight() - STANDING_ROOM;
        int start = MathHelper.clamp(startY, bottom, top);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = start; y >= bottom; y--) {
            if (standsAt(world, pos, x, y, z) && frameFits(world, x, y, z, facing, settings)) {
                return (double) y;
            }
        }
        for (int y = start + 1; y <= top; y++) {
            if (standsAt(world, pos, x, y, z) && frameFits(world, x, y, z, facing, settings)) {
                return (double) y;
            }
        }
        return null;
    }

    /** {@code true} si le cadre d'arrivee pose sur ce sol tient tout entier hors des blocs et des fluides. */
    private static boolean frameFits(ServerWorld world, int x, int floor, int z, PortalFacing facing, PortalSettings settings) {
        Vec3d center = target(world, x, floor, z, facing, settings).position();
        return PortalArea.isClear(world, PortalArea.anchor(center, facing, settings.width(), settings.height()), facing, settings);
    }

    /** {@code true} si l'on tient debout aux pieds en (x, y, z) : un sol plein dessous, de la place libre au-dessus, sans fluide. */
    private static boolean standsAt(ServerWorld world, BlockPos.Mutable pos, int x, int y, int z) {
        if (!world.getBlockState(pos.set(x, y - 1, z)).isSideSolidFullSquare(world, pos, Direction.UP)) {
            return false;
        }
        for (int dy = 0; dy < STANDING_ROOM; dy++) {
            BlockState state = world.getBlockState(pos.set(x, y + dy, z));
            if (!state.isReplaceable() || !state.getFluidState().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Un cadre au plafond pose a la surface est releve de {@value #CEILING_LIFT} blocs : qui en sort pend sous son plan, les
     * pieds encore au-dessus du sol. Rien pour les autres orientations.
     */
    static int ceilingLift(PortalFacing facing) {
        return facing.normal.y < 0.0 ? CEILING_LIFT : 0;
    }

    /** Ecart tire au sort dans [-rayon, rayon], en blocs ; nul sans rayon. */
    static int scatter(Random random, int radius) {
        return radius <= 0 ? 0 : random.nextBetween(-radius, radius);
    }

    /**
     * Hauteur exacte du sol d'une colonne : le haut de la collision du bloc qu'on y foule. Une dalle, un chemin ou une
     * couche de neige ne font pas un bloc plein : le cadre d'arrivee se pose dessus, au demi-bloc pres, au lieu de
     * flotter au-dessus, et celui qui le traverse depuis un portail pose sur un bloc plein garde les pieds au sol.
     *
     * @param world monde lu
     * @param x coordonnee monde X de la colonne
     * @param surfaceY premier Y libre au-dessus des blocs qui bloquent le mouvement (heightmap {@code MOTION_BLOCKING})
     * @param z coordonnee monde Z de la colonne
     * @return hauteur du sol, en blocs
     */
    public static double floorHeight(BlockView world, int x, int surfaceY, int z) {
        BlockPos.Mutable pos = new BlockPos.Mutable(x, surfaceY, z);
        // Un tapis ou une couche de neige peuvent ne pas compter dans la heightmap, mais on marche dessus.
        VoxelShape above = world.getBlockState(pos).getCollisionShape(world, pos);
        if (!above.isEmpty()) {
            return surfaceY + above.getMax(Direction.Axis.Y);
        }
        pos.setY(surfaceY - 1);
        VoxelShape ground = world.getBlockState(pos).getCollisionShape(world, pos);
        return ground.isEmpty() ? surfaceY : surfaceY - 1 + ground.getMax(Direction.Axis.Y);
    }
}
