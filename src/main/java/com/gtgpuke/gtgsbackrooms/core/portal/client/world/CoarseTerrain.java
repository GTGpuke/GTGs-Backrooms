package com.gtgpuke.gtgsbackrooms.core.portal.client.world;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.joml.Matrix4f;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.CoarseSectionMesher;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.SectionGenerations;
import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewDetail;

/**
 * Terrain simplifie des vues allegees d'un renderer (LOD des blocs, {@link ViewDetail}) : chaque section dont les blocs
 * sont petits a l'ecran se dessine en carres de faces fusionnees de deux ou quatre blocs de cote
 * ({@link CoarseSectionMesher}), a la place de son maillage vanilla. Un carre porte la texture de la face de son etat,
 * etiree, sa teinte, l'ombrage de sa direction et, a chaque coin, la moyenne de la lumiere des blocs vides qui touchent ce
 * coin devant la face (l'eclairage lisse de vanilla, sans occlusion ambiante) ; il se dessine avec la couche solide et le
 * shader du terrain vanilla (brouillard, lightmap, decoupe au plan d'arrivee).
 *
 * <p>Seul un cube plein et opaque, au modele ordinaire et sans block entity, se simplifie ; une section qui contient
 * autre chose que de l'air et de tels cubes garde son maillage vanilla. Une section ne quitte la liste vanilla qu'une fois
 * son maillage simplifie construit, a jour de la derniere reconstruction demandee de la section
 * ({@link SectionGenerations}) : jamais de trou. Au plus {@value #BUILDS_PER_FRAME} constructions par image et par
 * renderer ; un maillage par niveau, garde tant qu'il sert, rendu apres {@value #UNUSED_MILLIS} ms sans usage. Thread
 * de rendu seulement.
 */
final class CoarseTerrain implements CoarseSectionMesher.Blocks, CoarseSectionMesher.Faces {

    private static final int BUILDS_PER_FRAME = 2;
    /** Maillage sans usage depuis ce delai, en millisecondes : rendu. */
    private static final long UNUSED_MILLIS = 10000L;
    /** Intervalle entre deux passages du menage, en millisecondes. */
    private static final long SWEEP_MILLIS = 1000L;
    private static final int INITIAL_BUFFER_SIZE = 1 << 16;
    private static final int LEVELS = ViewDetail.COARSE_LEVEL;
    private static final Direction[] DIRECTIONS = Direction.values();
    /** Coins de chaque face, dans l'ordre trigonometrique vu de dehors (x, y, z par coin), par direction (bas, haut, nord, sud, ouest, est). */
    private static final int[][] CORNERS = {
        {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
        {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
        {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0},
        {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
        {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
        {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1},
    };

    /**
     * Section simplifiee : niveau courant, et par niveau un maillage, la reconstruction de la section dont il est a jour et
     * son nombre de faces ({@link CoarseSectionMesher#UNREPRESENTABLE} : la section garde son maillage vanilla).
     */
    private static final class Section {
        final int x;
        final int y;
        final int z;
        int level = ViewDetail.COARSE_LEVEL;
        long usedAt;
        final VertexBuffer[] buffers = new VertexBuffer[LEVELS];
        final int[] generations = {-1, -1};
        final int[] faces = new int[LEVELS];

        Section(BlockPos origin) {
            this.x = origin.getX();
            this.y = origin.getY();
            this.z = origin.getZ();
        }

        void close() {
            for (int i = 0; i < LEVELS; i++) {
                if (this.buffers[i] != null) {
                    this.buffers[i].close();
                    this.buffers[i] = null;
                }
            }
        }
    }

    /** Texture d'une face d'un etat : sprite et indice de teinte (-1 sans teinte). */
    private record FaceTexture(Sprite sprite, int tintIndex) {
    }

    private final ClientWorld world;
    private final Long2ObjectOpenHashMap<Section> sections = new Long2ObjectOpenHashMap<>();
    /** Sections simplifiees de la vue en cours de dessin, au niveau a dessiner. */
    private final ObjectArrayList<Section> drawn = new ObjectArrayList<>();
    private final ObjectArrayList<VertexBuffer> drawnBuffers = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<BlockState> keys = new Reference2IntOpenHashMap<>();
    private final List<BlockState> states = new ArrayList<>();
    private final List<FaceTexture[]> textures = new ArrayList<>();
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private final Random random = Random.create(0L);
    private final BufferAllocator allocator = new BufferAllocator(INITIAL_BUFFER_SIZE);
    private BufferBuilder builder;
    private int originX;
    private int originY;
    private int originZ;
    private long lastSweep;

    CoarseTerrain(ClientWorld world) {
        this.world = world;
    }

    /**
     * Retire de la liste vanilla les sections a simplifier pour cette vue, dont le maillage simplifie est pret, et les
     * retient pour {@link #draw} ; construit au plus {@value #BUILDS_PER_FRAME} maillages manquants.
     *
     * @param builtChunks sections que la vue va dessiner, choisies par son parcours
     * @param cameraPos camera de la vue
     * @param verticalScale facteur vertical de la projection
     * @param screenHeight hauteur de l'image, en pixels
     */
    void select(ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks, Vec3d cameraPos, double verticalScale, int screenHeight) {
        long now = Util.getMeasuringTimeMs();
        this.drawn.clear();
        this.drawnBuffers.clear();
        int builds = 0;
        for (int i = builtChunks.size() - 1; i >= 0; i--) {
            ChunkBuilder.BuiltChunk chunk = builtChunks.get(i);
            BlockPos origin = chunk.getOrigin();
            double pixels = ViewDetail.blockPixels(distanceToSection(cameraPos, origin), verticalScale, screenHeight);
            long key = ChunkSectionPos.asLong(origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4);
            Section section = this.sections.get(key);
            int level = ViewDetail.sectionLevel(section == null ? ViewDetail.COARSE_LEVEL : section.level, pixels);
            if (section == null) {
                if (level == ViewDetail.FULL_LEVEL) {
                    continue;
                }
                section = new Section(origin);
                this.sections.put(key, section);
            }
            section.level = level;
            if (level == ViewDetail.FULL_LEVEL) {
                continue;
            }
            section.usedAt = now;
            int slot = level - 1;
            int generation = ((SectionGenerations) chunk).backrooms$requested();
            if (section.generations[slot] != generation && builds < BUILDS_PER_FRAME) {
                build(section, slot, generation);
                builds++;
            }
            if (section.generations[slot] == generation && section.faces[slot] != CoarseSectionMesher.UNREPRESENTABLE) {
                builtChunks.remove(i);
                if (section.faces[slot] > 0) {
                    this.drawn.add(section);
                    this.drawnBuffers.add(section.buffers[slot]);
                }
            }
        }
        if (now - this.lastSweep >= SWEEP_MILLIS) {
            this.lastSweep = now;
            sweep(now);
        }
    }

    /** La vue en cours se dessine en pleine qualite : aucune section simplifiee. */
    void clearSelection() {
        this.drawn.clear();
        this.drawnBuffers.clear();
    }

    /** Dessine les sections simplifiees de la vue en cours, comme vanilla dessine une couche de terrain. */
    void draw(Vec3d cameraPos, Matrix4f positionMatrix, Matrix4f projection) {
        if (this.drawn.isEmpty()) {
            return;
        }
        RenderLayer layer = RenderLayer.getSolid();
        layer.startDrawing();
        try {
            ShaderProgram shader = RenderSystem.getShader();
            if (shader == null) {
                return;
            }
            shader.initializeUniforms(VertexFormat.DrawMode.QUADS, positionMatrix, projection, MinecraftClient.getInstance().getWindow());
            shader.bind();
            GlUniform offset = shader.chunkOffset;
            for (int i = 0; i < this.drawn.size(); i++) {
                Section section = this.drawn.get(i);
                if (offset != null) {
                    offset.set((float) (section.x - cameraPos.x), (float) (section.y - cameraPos.y), (float) (section.z - cameraPos.z));
                    offset.upload();
                }
                VertexBuffer buffer = this.drawnBuffers.get(i);
                buffer.bind();
                buffer.draw();
            }
            if (offset != null) {
                offset.set(0.0f, 0.0f, 0.0f);
            }
            shader.unbind();
            VertexBuffer.unbind();
        } finally {
            layer.endDrawing();
        }
    }

    /** Rend tous les maillages : le renderer est jete ou devient le principal. */
    void close() {
        ObjectIterator<Long2ObjectMap.Entry<Section>> iterator = this.sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            iterator.next().getValue().close();
        }
        this.sections.clear();
        this.drawn.clear();
        this.drawnBuffers.clear();
    }

    /** Rend les maillages des sections qui n'ont plus servi depuis {@value #UNUSED_MILLIS} ms. */
    private void sweep(long now) {
        ObjectIterator<Long2ObjectMap.Entry<Section>> iterator = this.sections.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Section section = iterator.next().getValue();
            if (now - section.usedAt > UNUSED_MILLIS) {
                section.close();
                iterator.remove();
            }
        }
    }

    private void build(Section section, int slot, int generation) {
        this.originX = section.x;
        this.originY = section.y;
        this.originZ = section.z;
        this.builder = new BufferBuilder(this.allocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
        int faces = CoarseSectionMesher.mesh(section.x, section.y, section.z, 2 << slot, this, this);
        BuiltBuffer built = this.builder.endNullable();
        this.builder = null;
        section.faces[slot] = faces;
        section.generations[slot] = generation;
        if (built == null) {
            return;
        }
        if (section.buffers[slot] == null) {
            section.buffers[slot] = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        section.buffers[slot].bind();
        section.buffers[slot].upload(built);
        VertexBuffer.unbind();
    }

    /** Distance de la camera au point le plus proche de la section. */
    private static double distanceToSection(Vec3d cameraPos, BlockPos origin) {
        double dx = Math.max(0.0, Math.max(origin.getX() - cameraPos.x, cameraPos.x - (origin.getX() + 16)));
        double dy = Math.max(0.0, Math.max(origin.getY() - cameraPos.y, cameraPos.y - (origin.getY() + 16)));
        double dz = Math.max(0.0, Math.max(origin.getZ() - cameraPos.z, cameraPos.z - (origin.getZ() + 16)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Override
    public int key(int x, int y, int z) {
        BlockState state = this.world.getBlockState(this.cursor.set(x, y, z));
        if (state.isAir()) {
            return 0;
        }
        if (state.getRenderType() != BlockRenderType.MODEL || state.hasBlockEntity() || !state.isOpaqueFullCube(this.world, this.cursor)) {
            return CoarseSectionMesher.UNREPRESENTABLE;
        }
        int key = this.keys.getInt(state);
        if (key == 0) {
            this.states.add(state);
            this.textures.add(new FaceTexture[DIRECTIONS.length]);
            key = this.states.size();
            this.keys.put(state, key);
        }
        return key;
    }

    @Override
    public void face(int direction, int x, int y, int z, int size, int key) {
        BlockState state = this.states.get(key - 1);
        Direction facing = DIRECTIONS[direction];
        // Etendue de la face sur chaque axe, en blocs : un bloc d'epaisseur le long de sa normale.
        int sizeX = facing.getOffsetX() != 0 ? 1 : size;
        int sizeY = facing.getOffsetY() != 0 ? 1 : size;
        int sizeZ = facing.getOffsetZ() != 0 ? 1 : size;
        FaceTexture texture = texture(key, state, facing);
        int color = 0xFFFFFF;
        if (texture.tintIndex() >= 0) {
            this.cursor.set(this.originX + x + sizeX / 2, this.originY + y + sizeY / 2, this.originZ + z + sizeZ / 2);
            color = MinecraftClient.getInstance().getBlockColors().getColor(state, this.world, this.cursor, texture.tintIndex());
        }
        float shade = this.world.getBrightness(facing, true);
        int argb = 0xFF000000
                | (int) (((color >> 16) & 0xFF) * shade) << 16
                | (int) (((color >> 8) & 0xFF) * shade) << 8
                | (int) ((color & 0xFF) * shade);
        Sprite sprite = texture.sprite();
        int[] corners = CORNERS[direction];
        for (int corner = 0; corner < 4; corner++) {
            int cx = corners[corner * 3];
            int cy = corners[corner * 3 + 1];
            int cz = corners[corner * 3 + 2];
            int vx = x + cx * sizeX;
            int vy = y + cy * sizeY;
            int vz = z + cz * sizeZ;
            // Coordonnees de texture : le long de la face, la hauteur vers le haut du sprite sur les cotes.
            float s = facing.getAxis() == Direction.Axis.X ? cz : cx;
            float t = facing.getAxis() == Direction.Axis.Y ? cz : cy;
            float u = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * s;
            float v = facing.getAxis() == Direction.Axis.Y
                    ? sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * t
                    : sprite.getMaxV() - (sprite.getMaxV() - sprite.getMinV()) * t;
            this.builder.vertex(vx, vy, vz)
                    .color(argb)
                    .texture(u, v)
                    .light(cornerLight(this.originX + vx, this.originY + vy, this.originZ + vz, facing))
                    .normal(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ());
        }
    }

    /**
     * Lumiere d'un coin de face : la moyenne, lumiere des blocs et du ciel a part, des blocs non pleins qui touchent ce coin
     * dans la couche juste devant la face (quatre au plus), comme l'eclairage lisse de vanilla sans son occlusion ambiante.
     * Deux carres voisins calculent la meme lumiere a leur coin commun : pas de marche entre eux.
     *
     * @param vx coin, en coordonnees monde
     * @param vy coin, en coordonnees monde
     * @param vz coin, en coordonnees monde
     */
    private int cornerLight(int vx, int vy, int vz, Direction facing) {
        // Le long de la normale, la seule tranche devant la face ; dans le plan, les deux blocs de part et d'autre du coin.
        int minX = vx - 1 + Math.max(facing.getOffsetX(), 0);
        int maxX = vx + Math.min(facing.getOffsetX(), 0);
        int minY = vy - 1 + Math.max(facing.getOffsetY(), 0);
        int maxY = vy + Math.min(facing.getOffsetY(), 0);
        int minZ = vz - 1 + Math.max(facing.getOffsetZ(), 0);
        int maxZ = vz + Math.min(facing.getOffsetZ(), 0);
        int block = 0;
        int sky = 0;
        int count = 0;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    BlockState state = this.world.getBlockState(this.cursor.set(bx, by, bz));
                    if (state.isOpaqueFullCube(this.world, this.cursor)) {
                        continue;
                    }
                    int packed = WorldRenderer.getLightmapCoordinates(this.world, state, this.cursor);
                    block += LightmapTextureManager.getBlockLightCoordinates(packed);
                    sky += LightmapTextureManager.getSkyLightCoordinates(packed);
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0;
        }
        // Coordonnees de lightmap : seize unites par niveau, la moyenne garde ses fractions comme l'eclairage lisse.
        return block * 16 / count | (sky * 16 / count) << 16;
    }

    /**
     * Texture d'une face d'un etat : le premier quad du modele sur cette face, a defaut le premier sans face, a defaut le
     * sprite de particules (fluides) avec la teinte de l'etat. Gardee par etat et par direction.
     */
    private FaceTexture texture(int key, BlockState state, Direction facing) {
        FaceTexture[] faces = this.textures.get(key - 1);
        FaceTexture texture = faces[facing.ordinal()];
        if (texture != null) {
            return texture;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBlockRenderManager().getModel(state);
        List<BakedQuad> quads = model.getQuads(state, facing, this.random);
        if (quads.isEmpty()) {
            quads = model.getQuads(state, null, this.random);
        }
        if (quads.isEmpty()) {
            texture = new FaceTexture(client.getBlockRenderManager().getModels().getModelParticleSprite(state), 0);
        } else {
            BakedQuad quad = quads.get(0);
            texture = new FaceTexture(quad.getSprite(), quad.hasColor() ? quad.getColorIndex() : -1);
        }
        faces[facing.ordinal()] = texture;
        return texture;
    }
}
