package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalIndex;
import com.gtgpuke.gtgsbackrooms.core.portal.client.world.RemoteWorldRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBakeSettings;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightBlocks;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.PortalOpenings;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Ce que la cuisson de la lumiere lit du monde client : autour de la camera dans un monde a lumiere cuite, ou autour de
 * l'arrivee du premier portail visible vers un tel monde, et autour de la camera de la vue du portail visible le plus proche quand
 * cette vue montre un endroit que la premiere zone ne couvre pas, les sections sont copiees sur le thread de rendu, les plus
 * proches d'abord et quelques-unes par tick, chaque etat de bloc distinct n'etant classe qu'une fois (matiere : albedo moyen
 * de sa texture, emission du neon du profil). Un bloc change pose sa matiere dans une nouvelle copie de sa section, tout de
 * suite : le cache de surfaces le voit des l'image suivante, et la section repart a cuire. Un bloc que la lumiere traverse
 * avant comme apres (une torche, une porte) ne change rien, et ne coute rien.
 */
public final class LightCopy {

    /** Sections copiees par tick, au plus. */
    private static final int BUDGET_PER_TICK = 8;
    /** Sections copiees au-dela de la zone : une section ne cuit qu'une fois ses voisines copiees. */
    private static final int COPY_RADIUS = LightZone.RADIUS + LightZone.COPY_MARGIN;
    /** Sections copiees pour le loin, apres celles de la zone : sa lumiere directe lit les blocs des voisines. */
    private static final int FAR_COPY_RADIUS = LightZone.FAR_RADIUS + LightZone.COPY_MARGIN;
    private static final int KEEP_RADIUS = FAR_COPY_RADIUS + 3;
    private static final int RETAIN_PERIOD_TICKS = 100;
    private static final int SECTION = 16;
    private static final float CHANNEL = 255.0f;
    private static final long NOWHERE = Long.MIN_VALUE;
    private static final Map<World, Target> TARGETS = new IdentityHashMap<>();
    /** La copie et la cuisson de la zone d'une vue, par monde : un autre endroit que la zone du joueur, meme dans son monde. */
    private static final Map<World, Target> VIEW_TARGETS = new IdentityHashMap<>();
    private static final Map<Identifier, float[]> ALBEDOS = new HashMap<>();
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();
    private static final float[] NO_SPRITE = new float[4];
    private static int ticks;
    private static @Nullable Target current;
    private static @Nullable Target view;

    /** La copie et la cuisson d'un monde. */
    private static final class Target {

        private final LightZone zone;
        private final Reference2ByteOpenHashMap<BlockState> materials = new Reference2ByteOpenHashMap<>();
        private final float[][] sprites = new float[256][];
        private int base = Integer.MIN_VALUE;
        /** La place de l'ancrage ou plus aucune section ne manquait : la chercher a chaque tick ferait une clef par section, pour rien. */
        private long copiedAt = NOWHERE;

        private Target(LightZone zone) {
            this.zone = zone;
        }
    }

    private LightCopy() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A appeler a l'initialisation du client : une colonne dechargee sort de la copie, un monde oublie aussi. */
    public static void register() {
        ClientChunkEvents.CHUNK_UNLOAD.register(LightCopy::unloadChunk);
        ClientChunkEvents.CHUNK_LOAD.register(LightCopy::unloadChunk);
        RemoteWorldRegistry.WORLD_FORGOTTEN.register(LightCopy::forget);
    }

    /** Chaque tick : choisit ou cuire, puis copie les sections qui manquent. */
    public static void onClientTick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ModConfig.ENABLE_LOOK || !ModConfig.LOOK_BAKED_LIGHT || world == null) {
            return;
        }
        ClientWorld target = world;
        Vec3d anchor = client.gameRenderer.getCamera().getPos();
        if (!LookProfiles.of(world.getRegistryKey()).bakedLight().any()) {
            target = null;
            // Depuis un monde sans lumiere cuite : l'arrivee du premier portail visible vers un monde qui en a une.
            List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
            for (int i = 0; i < portals.size() && target == null; i++) {
                BackroomsPortalEntity portal = portals.get(i);
                RegistryKey<World> destination = portal.destinationDim();
                if (portal.visible() && portal.teleportable() && LookProfiles.of(destination).bakedLight().any()) {
                    target = RemoteWorldRegistry.worldOf(portal);
                    anchor = portal.destinationPos();
                }
            }
        }
        if (target == null) {
            LightVolume.target(null, null, 0, 0, 0, 0);
            LightWorker.target(null);
            EntityShadows.clear();
            clearView();
            return;
        }
        follow(target, anchor);
        followView(world, client.gameRenderer.getCamera().getPos());
    }

    /** La copie et la cuisson d'un monde dans une table, creees a sa premiere rencontre. */
    private static Target targetOf(Map<World, Target> targets, ClientWorld world) {
        Target target = targets.get(world);
        if (target == null) {
            RegistryKey<World> key = world.getRegistryKey();
            LookProfile profile = LookProfiles.of(key);
            target = new Target(new LightZone(new LightBlocks(), LightBakeSettings.of(profile, key.getValue()), world.getBottomSectionCoord(),
                    world.getTopSectionCoord()));
            targets.put(world, target);
        }
        return target;
    }

    private static void follow(ClientWorld world, Vec3d anchor) {
        Target target = targetOf(TARGETS, world);
        int sectionX = ChunkSectionPos.getSectionCoord(anchor.x);
        int sectionZ = ChunkSectionPos.getSectionCoord(anchor.z);
        int base = LightZone.baseFor(target.base, (int) Math.floor(anchor.y));
        target.base = base;
        target.zone.anchor(sectionX, base, sectionZ);
        LightVolume.target(target.zone, world.getRegistryKey().getValue(), sectionX, base, sectionZ, (int) Math.floor(anchor.y));
        current = target;
        LightWorker.target(target.zone);
        EntityShadows.tick(world, anchor.x, anchor.y, anchor.z);
        PortalOpeningLights.update(world, target.zone);
        copyAround(world, target, sectionX, base, sectionZ, LightSurfaces.PLAYER);
        if (++ticks % RETAIN_PERIOD_TICKS == 0) {
            target.zone.blocks().retainAround(sectionX, base, sectionZ, KEEP_RADIUS);
        }
    }

    /**
     * La zone d'une vue : celle du portail visible le plus proche de la camera, vers un monde a lumiere cuite, dont la camera de la
     * vue est hors de la zone du joueur (une autre dimension, ou le meme monde, loin). Elle suit la camera de la vue, comme celle du
     * joueur suit la sienne ; sans tel portail, il n'y en a pas.
     */
    private static void followView(ClientWorld world, Vec3d camera) {
        BackroomsPortalEntity nearest = null;
        double best = Double.MAX_VALUE;
        List<BackroomsPortalEntity> portals = ClientPortalIndex.portals(world);
        for (int i = 0; i < portals.size(); i++) {
            BackroomsPortalEntity portal = portals.get(i);
            RegistryKey<World> destination = portal.destinationDim();
            double distance = portal.getPos().squaredDistanceTo(camera);
            if (distance < best && portal.visible() && portal.teleportable() && LookProfiles.of(destination).bakedLight().any()
                    && !LightVolume.playerCovers(destination.getValue(), viewBlock(portal, camera, 0), viewBlock(portal, camera, 1), viewBlock(portal, camera, 2))) {
                nearest = portal;
                best = distance;
            }
        }
        ClientWorld seen = nearest == null ? null : RemoteWorldRegistry.worldOf(nearest);
        if (seen == null) {
            clearView();
            return;
        }
        double x = viewCoordinate(nearest, camera, 0);
        double y = viewCoordinate(nearest, camera, 1);
        double z = viewCoordinate(nearest, camera, 2);
        Target target = targetOf(VIEW_TARGETS, seen);
        int sectionX = ChunkSectionPos.getSectionCoord(x);
        int sectionZ = ChunkSectionPos.getSectionCoord(z);
        int base = LightZone.baseFor(target.base, (int) Math.floor(y));
        target.base = base;
        target.zone.anchor(sectionX, base, sectionZ);
        LightVolume.targetView(target.zone, seen.getRegistryKey().getValue(), sectionX, base, sectionZ, (int) Math.floor(y));
        view = target;
        LightWorker.targetView(target.zone);
        EntityShadows.tickView(seen, x, y, z);
        PortalOpeningLights.update(seen, target.zone);
        copyAround(seen, target, sectionX, base, sectionZ, LightSurfaces.VIEW);
        if (ticks % RETAIN_PERIOD_TICKS == 0) {
            target.zone.blocks().retainAround(sectionX, base, sectionZ, KEEP_RADIUS);
        }
    }

    /** Une coordonnee de la camera de la vue d'un portail : l'image de la camera du joueur de l'autre cote, sans allocation. */
    private static double viewCoordinate(BackroomsPortalEntity portal, Vec3d camera, int axis) {
        return PortalFacing.transformCoordinate(portal.facingSource(), portal.getPos(), portal.facingDestination(), portal.destinationPos(), camera.x,
                camera.y, camera.z, axis);
    }

    private static int viewBlock(BackroomsPortalEntity portal, Vec3d camera, int axis) {
        return (int) Math.floor(viewCoordinate(portal, camera, axis));
    }

    /** Plus de zone de vue. */
    private static void clearView() {
        if (view != null) {
            view = null;
            LightVolume.clearView();
            LightWorker.targetView(null);
            EntityShadows.clearView();
        }
    }

    /** Copie les sections manquantes, anneau par anneau depuis la section d'ancrage ; celles de la zone vont a son cache de surfaces. */
    private static void copyAround(ClientWorld world, Target target, int sectionX, int base, int sectionZ, LightSurfaces surfaces) {
        long here = ChunkSectionPos.asLong(sectionX, base, sectionZ);
        if (target.copiedAt == here) {
            return;
        }
        int budget = BUDGET_PER_TICK;
        LightBlocks blocks = target.zone.blocks();
        int bottom = Math.max(base - LightZone.COPY_MARGIN, world.getBottomSectionCoord());
        int top = Math.min(base + LightZone.HEIGHT + LightZone.COPY_MARGIN, world.getTopSectionCoord());
        for (int radius = 0; radius <= FAR_COPY_RADIUS; radius++) {
            for (int z = sectionZ - radius; z <= sectionZ + radius; z++) {
                for (int x = sectionX - radius; x <= sectionX + radius; x++) {
                    if (Math.max(Math.abs(x - sectionX), Math.abs(z - sectionZ)) != radius) {
                        continue;
                    }
                    for (int y = bottom; y < top; y++) {
                        long key = ChunkSectionPos.asLong(x, y, z);
                        if (blocks.section(key) != null) {
                            continue;
                        }
                        WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
                        // Une colonne que le client n'a pas (petite distance de vue) ne fait pas attendre la cuisson.
                        blocks.setSection(key, chunk == null ? LightBlocks.UNLOADED : copy(world, target, chunk, y));
                        // La grille des blocs et le cache de surfaces ne tiennent que la zone : une section du loin y prendrait la place d'une autre.
                        if (radius <= COPY_RADIUS) {
                            surfaces.onSectionCopied(key, base);
                        }
                        target.zone.onChanged(key);
                        if (--budget == 0) {
                            return;
                        }
                    }
                }
            }
        }
        target.copiedAt = here;
    }

    /** Les matieres d'une section : un tableau neuf, jamais modifie ensuite, ou la section vide partagee. */
    private static byte[] copy(ClientWorld world, Target target, WorldChunk chunk, int sectionY) {
        ChunkSection section = chunk.getSection(chunk.sectionCoordToIndex(sectionY));
        if (section.isEmpty()) {
            return LightBlocks.EMPTY;
        }
        // Une allocation par section copiee : la copie est relue par le thread de cuisson, jamais recyclee.
        byte[] blocks = new byte[LightBlocks.SECTION_BLOCKS];
        ChunkPos pos = chunk.getPos();
        int originX = pos.getStartX();
        int originY = ChunkSectionPos.getBlockCoord(sectionY);
        int originZ = pos.getStartZ();
        for (int z = 0; z < SECTION; z++) {
            for (int y = 0; y < SECTION; y++) {
                for (int x = 0; x < SECTION; x++) {
                    BlockState state = section.getBlockState(x, y, z);
                    blocks[z << 8 | y << 4 | x] = material(world, target, state, originX + x, originY + y, originZ + z);
                }
            }
        }
        return blocks;
    }

    /** La matiere d'un etat de bloc, classe a sa premiere rencontre (sa forme ne depend pas de sa place ici). */
    private static byte material(ClientWorld world, Target target, BlockState state, int x, int y, int z) {
        if (state.isAir()) {
            return LightBlocks.AIR;
        }
        if (target.materials.containsKey(state)) {
            return target.materials.getByte(state);
        }
        byte material = LightBlocks.AIR;
        if (state.isOpaqueFullCube(world, CURSOR.set(x, y, z))) {
            float[] albedo = albedo(state);
            Identifier neon = LookProfiles.of(world.getRegistryKey()).neons().block();
            material = target.zone.blocks().addMaterial(albedo[0], albedo[1], albedo[2], Registries.BLOCK.getId(state.getBlock()).equals(neon));
            Sprite sprite = MinecraftClient.getInstance().getBakedModelManager().getBlockModels().getModelParticleSprite(state);
            target.sprites[material & 0xFF] = new float[] {sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV()};
        }
        target.materials.put(state, material);
        return material;
    }

    /** Couleur moyenne de la texture d'un bloc, lue une fois par texture : ce que sa surface renvoie de la lumiere. */
    private static float[] albedo(BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        Sprite sprite = client.getBakedModelManager().getBlockModels().getModelParticleSprite(state);
        Identifier id = sprite.getContents().getId();
        float[] albedo = ALBEDOS.get(id);
        if (albedo == null) {
            albedo = read(client, id);
            ALBEDOS.put(id, albedo);
        }
        return albedo;
    }

    private static float[] read(MinecraftClient client, Identifier sprite) {
        float[] albedo = {0.5f, 0.5f, 0.5f};
        Identifier file = sprite.withPath(path -> "textures/" + path + ".png");
        Optional<Resource> resource = client.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            return albedo;
        }
        try (InputStream in = resource.get().getInputStream(); NativeImage image = NativeImage.read(in)) {
            float red = 0.0f;
            float green = 0.0f;
            float blue = 0.0f;
            int pixels = image.getWidth() * image.getHeight();
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getColor(x, y);
                    red += abgr & 0xFF;
                    green += abgr >> 8 & 0xFF;
                    blue += abgr >> 16 & 0xFF;
                }
            }
            albedo[0] = red / pixels / CHANNEL;
            albedo[1] = green / pixels / CHANNEL;
            albedo[2] = blue / pixels / CHANNEL;
        } catch (IOException exception) {
            ModConstants.LOGGER.warn("[Look] Texture {} illisible : albedo moyen.", file);
        }
        return albedo;
    }

    /**
     * Un bloc change dans un monde client : s'il change quelque chose pour la lumiere, sa section copiee le recoit, et ses
     * surfaces et ses ombres suivent.
     *
     * @param world monde du bloc
     * @param pos position du bloc
     * @param state nouvel etat du bloc
     */
    public static void onBlockChanged(ClientWorld world, BlockPos pos, BlockState state) {
        Target target = TARGETS.get(world);
        if (target != null) {
            blockChanged(world, target, pos, state);
        }
        Target seen = VIEW_TARGETS.get(world);
        if (seen != null) {
            blockChanged(world, seen, pos, state);
        }
    }

    private static void blockChanged(ClientWorld world, Target target, BlockPos pos, BlockState state) {
        LightBlocks blocks = target.zone.blocks();
        long key = ChunkSectionPos.asLong(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getY()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));
        byte[] section = blocks.section(key);
        if (section == null) {
            return;
        }
        int index = LightBlocks.index(pos.getX(), pos.getY(), pos.getZ());
        byte before = section[index];
        byte material = material(world, target, state, pos.getX(), pos.getY(), pos.getZ());
        if (material == before) {
            return;
        }
        // Une section posee n'est jamais modifiee en place : le thread de cuisson la lit.
        byte[] next = section.clone();
        next[index] = material;
        blocks.setSection(key, next);
        long stamp = target.zone.onChanged(key);
        LightSurfaces surfaces = target == current ? LightSurfaces.PLAYER : target == view ? LightSurfaces.VIEW : null;
        if (surfaces != null) {
            boolean[] emissive = blocks.emissive();
            surfaces.onBlockChanged(pos, emissive[material & 0xFF] || emissive[before & 0xFF], stamp);
        }
    }

    /**
     * {@code true} si les blocs de ce monde se construisent sans l'ombrage vanilla par face : la lumiere cuite suit la vraie
     * orientation de chaque surface. Lu par les fils de construction des sections.
     */
    public static boolean flatFaces(World world) {
        return ModConfig.ENABLE_LOOK && ModConfig.LOOK_BAKED_LIGHT && LookProfiles.of(world.getRegistryKey()).bakedLight().any();
    }

    /** Une colonne qui part ou qui arrive : ce qu'on en savait est perime, ses sections sont a recopier. */
    private static void unloadChunk(ClientWorld world, WorldChunk chunk) {
        Target target = TARGETS.get(world);
        if (target != null) {
            unloadChunk(world, target, chunk);
        }
        Target seen = VIEW_TARGETS.get(world);
        if (seen != null) {
            unloadChunk(world, seen, chunk);
        }
    }

    private static void unloadChunk(ClientWorld world, Target target, WorldChunk chunk) {
        ChunkPos pos = chunk.getPos();
        for (int sectionY = world.getBottomSectionCoord(); sectionY < world.getTopSectionCoord(); sectionY++) {
            target.zone.blocks().removeSection(ChunkSectionPos.asLong(pos.x, sectionY, pos.z));
        }
        target.copiedAt = NOWHERE;
    }

    /**
     * Monde oublie : sa copie aussi. La zone liee au joueur ou a la vue ne repart de zero que si c'etait la sienne : oublier un autre
     * monde videait les textures de zone et faisait recuire toute la vue.
     */
    private static void forget(World world) {
        Target target = TARGETS.remove(world);
        if (target != null) {
            target.zone.retire();
        }
        if (target != null && target == current) {
            current = null;
            LightVolume.target(null, null, 0, 0, 0, 0);
            LightWorker.target(null);
            EntityShadows.clear();
        }
        Target seen = VIEW_TARGETS.remove(world);
        if (seen != null) {
            seen.zone.retire();
        }
        if (seen != null && seen == view) {
            clearView();
        }
    }

    /** Les reglages du joueur ont change : une zone cuite d'apres les anciens repart de zero, sinon ses rebonds restent ceux de l'ancien palier. */
    public static void refreshSettings() {
        for (World world : List.copyOf(TARGETS.keySet())) {
            RegistryKey<World> key = world.getRegistryKey();
            if (!LightBakeSettings.of(LookProfiles.of(key), key.getValue()).equals(TARGETS.get(world).zone.settings())) {
                forget(world);
            }
        }
        for (World world : List.copyOf(VIEW_TARGETS.keySet())) {
            RegistryKey<World> key = world.getRegistryKey();
            if (!LightBakeSettings.of(LookProfiles.of(key), key.getValue()).equals(VIEW_TARGETS.get(world).zone.settings())) {
                forget(world);
            }
        }
    }

    /** Les ouvertures des portails que compte la zone d'un monde, celle du joueur ou celle d'une vue ; aucune sans zone. */
    public static PortalOpenings openings(World world) {
        Target target = boundTarget(world);
        return target == null ? PortalOpenings.NONE : target.zone.openings();
    }

    /**
     * La zone d'un monde : celle du joueur ou celle d'une vue, et, si ce monde a les deux (le joueur hors des Backrooms, sa zone ancree a
     * l'arrivee d'un portail, celle de la vue a sa camera), celle que lit la vue en cours de dessin ({@link LightVolume#playerBound}).
     */
    private static @Nullable Target boundTarget(World world) {
        Target player = TARGETS.get(world);
        Target seen = VIEW_TARGETS.get(world);
        if (player != null && seen != null) {
            return LightVolume.playerBound() ? player : seen;
        }
        return player != null ? player : seen;
    }

    /**
     * La zone qui cuit la lumiere d'un monde en ce point : celle du joueur ou celle de la vue, {@code null} si aucune ne le couvre. C'est
     * elle que lisent les rayons qui passent un portail vers ce point. Une zone qui ne cuit plus (un monde quitte) ne compte pas.
     *
     * @param world monde du point
     * @param x point
     * @param y point
     * @param z point
     */
    static @Nullable LightZone zoneCovering(World world, double x, double y, double z) {
        Target target = TARGETS.get(world);
        if (target != null && target == current && target.zone.covers(x, y, z)) {
            return target.zone;
        }
        Target seen = VIEW_TARGETS.get(world);
        return seen != null && seen == view && seen.zone.covers(x, y, z) ? seen.zone : null;
    }

    /** La lumiere qui sort des passages de la zone d'un monde ({@link #boundTarget}), {@code null} sans zone ni mesure. */
    static LightZone.@Nullable PassageLight passageLight(World world) {
        Target target = boundTarget(world);
        return target == null ? null : target.zone.passageLight();
    }

    /** Les reglages de la zone d'un monde ({@link #boundTarget}), {@code null} sans zone. */
    static @Nullable LightBakeSettings zoneSettings(World world) {
        Target target = boundTarget(world);
        return target == null ? null : target.zone.settings();
    }

    /** Les reglages de la zone d'un monde (tests du client). */
    public static @Nullable LightBakeSettings settings(World world) {
        Target target = TARGETS.get(world);
        return target == null ? null : target.zone.settings();
    }

    /** Fin de partie, ou profils recharges : plus rien n'est copie ni cuit. */
    public static void clear() {
        for (Target target : TARGETS.values()) {
            target.zone.retire();
        }
        for (Target target : VIEW_TARGETS.values()) {
            target.zone.retire();
        }
        TARGETS.clear();
        VIEW_TARGETS.clear();
        ALBEDOS.clear();
        current = null;
        view = null;
        LightVolume.clear();
        LightWorker.close();
        EntityShadows.clear();
    }

    /**
     * Rectangle de la texture d'une matiere dans l'atlas des blocs, vide si elle n'en a pas. La zone du joueur et celle d'une vue
     * numerotent chacune leurs matieres dans l'ordre ou elles les rencontrent : lue dans la table de l'autre, une tuile de la vue
     * prenait le relief d'une autre texture.
     *
     * @param ofView {@code true} pour une matiere de la zone d'une vue, {@code false} pour celle du joueur
     * @param material matiere dans cette zone
     */
    static float[] sprite(boolean ofView, int material) {
        Target target = ofView ? view : current;
        float[] sprite = target == null ? null : target.sprites[material];
        return sprite == null ? NO_SPRITE : sprite;
    }

    /** La matiere d'un bloc dans la copie d'un monde, ou {@link LightBlocks#UNKNOWN} (tests du client). */
    public static byte copiedBlock(World world, BlockPos pos) {
        Target target = TARGETS.get(world);
        return target == null ? LightBlocks.UNKNOWN : target.zone.blocks().block(pos.getX(), pos.getY(), pos.getZ());
    }

    /** {@code true} si la zone d'une vue existe et que sa cuisson est au repos (tests du client). */
    public static boolean viewIdle() {
        Target target = view;
        return target != null && target.zone.idle();
    }

    /** {@code true} si la cuisson de ce monde est au repos (tests du client). */
    public static boolean idle(World world) {
        Target target = TARGETS.get(world);
        return target != null && target.zone.idle();
    }

    /** Sections copiees du monde cible (diagnostic), ou 0. */
    public static int copiedSections(@Nullable World world) {
        Target target = TARGETS.get(world);
        return target == null ? 0 : target.zone.blocks().sectionCount();
    }
}
