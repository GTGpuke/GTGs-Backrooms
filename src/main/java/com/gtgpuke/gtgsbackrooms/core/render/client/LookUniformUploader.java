package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.FogShape;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule;
import com.gtgpuke.gtgsbackrooms.core.render.light.bake.LightZone;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.quality.LookSettings;
import com.gtgpuke.gtgsbackrooms.core.render.state.LookState;

/**
 * Envoi du look courant ({@link LookState}) aux uniformes qu'un patch a ajoutes a un shader, a son activation. Les
 * emplacements sont resolus une fois par programme ({@link #resolve}) par {@code MixinShaderProgram}, qui n'appelle
 * {@link #upload} que si la version du look a change depuis son dernier envoi a ce programme.
 */
public final class LookUniformUploader {

    /** Identifiant du look, 0 pour vanilla : le shader rend alors la couleur telle quelle. */
    public static final String ID = "gtgs_LookId";
    /** Pas de la grille des texels par bloc (x, 0 pour une lumiere lisse), grain (y), vacillement (z), temps (w). */
    public static final String PARAMS = "gtgs_LookParams";
    /** Nombre de neons dans le bloc d'uniformes de la vue courante. */
    public static final String NEON_COUNT = "gtgs_NeonCount";
    /** Bloc entier de la camera de la vue. */
    public static final String CAMERA_BLOCK = "gtgs_CameraBlock";
    /** Reste de la camera de la vue dans son bloc. */
    public static final String CAMERA_FRACT = "gtgs_CameraFract";
    /** Tick du monde de la vue dans la periode de l'horloge commune (x), graine des neons de sa dimension (y). */
    public static final String NEON_CLOCK = "gtgs_NeonClock";
    /** Rectangle de la texture du neon dans l'atlas, vide sans jumeau du calendrier. */
    public static final String NEON_FACE = "gtgs_NeonFace";
    /** Parts des neons qui vacillent et morts. */
    public static final String NEON_SHARES = "gtgs_NeonShares";
    /** Bloc d'uniformes des neons, lie au point {@link NeonLights#BINDING}. */
    public static final String NEON_BLOCK = "gtgs_NeonBlock";
    /** 1 si la zone de lumiere cuite couvre la vue : le terrain la lit a la place de la lightmap. */
    public static final String BAKED_LIGHT = "gtgs_BakedLight";
    /** Y du premier bloc de la zone de lumiere cuite. */
    public static final String LIGHT_BASE = "gtgs_LightBase";
    /** Debut et fin du brouillard, forme (1 pour un cylindre), epaule des hautes lumieres. */
    public static final String FOG_RANGE = "gtgs_FogRange";
    /** Couleur du brouillard. */
    public static final String FOG_TINT = "gtgs_FogTint";
    /** 1 si le cache de surfaces porte la lumiere directe du terrain. */
    public static final String SURFACE_LIGHT = "gtgs_SurfaceLight";
    /** Force des halos autour des neons et de la brillance des matieres. */
    public static final String AIR = "gtgs_Air";
    /** Lumiere de fond du profil : ce que recoit une entite loin de tout neon. */
    public static final String AMBIENT = "gtgs_Ambient";
    /** Pied de la courbe, part du degrade sur la grille des texels, grain de pixels dans le noir. */
    public static final String TONE = "gtgs_Tone";
    /** Couleur de la lumiere d'une dalle, la plus forte composante a 1. */
    public static final String NEON_TINT = "gtgs_NeonTint";
    /** 1 si cette vue trace : rebonds de ses surfaces, et ses reflets voient leur dalle en entier. */
    public static final String TRACED = "gtgs_Traced";
    /** Part du contraste des textures gardee (x), exposition des surfaces (y) et part du direct (z), au palier du joueur. */
    public static final String LIGHT_LOOK = "gtgs_LightLook";
    /** 1 si les matieres ont leur epaisseur. */
    public static final String PILE = "gtgs_Pile";
    /** La piece moyenne de la dimension, au-dela du loin : par genre de surface, l'eclairement complet puis sa part indirecte. */
    public static final String FAR_ROOM = "gtgs_FarRoom";
    /** Atlas des blocs, relu quand le regard entre dans l'epaisseur d'une matiere. */
    private static final String BLOCK_ATLAS = "gtgs_BlockAtlas";
    /** Emplacements a resoudre par programme, dans cet ordre. */
    public static final int LOCATIONS = 21;
    private static final String[] NAMES = {ID, PARAMS, NEON_COUNT, CAMERA_BLOCK, CAMERA_FRACT, NEON_CLOCK, NEON_FACE, NEON_SHARES, BAKED_LIGHT, LIGHT_BASE,
        FOG_RANGE, FOG_TINT, SURFACE_LIGHT, AIR, AMBIENT, TONE, NEON_TINT, TRACED, LIGHT_LOOK, PILE, FAR_ROOM};
    private static final int AT_ID = 0;
    private static final int AT_PARAMS = 1;
    private static final int AT_NEON_COUNT = 2;
    private static final int AT_CAMERA_BLOCK = 3;
    private static final int AT_CAMERA_FRACT = 4;
    private static final int AT_NEON_CLOCK = 5;
    private static final int AT_NEON_FACE = 6;
    private static final int AT_NEON_SHARES = 7;
    private static final int AT_BAKED_LIGHT = 8;
    private static final int AT_LIGHT_BASE = 9;
    private static final int AT_FOG_RANGE = 10;
    private static final int AT_FOG_TINT = 11;
    private static final int AT_SURFACE_LIGHT = 12;
    private static final int AT_AIR = 13;
    private static final int AT_AMBIENT = 14;
    private static final int AT_TONE = 15;
    private static final int AT_NEON_TINT = 16;
    private static final int AT_TRACED = 17;
    private static final int AT_LIGHT_LOOK = 18;
    private static final int AT_PILE = 19;
    private static final int AT_FAR_ROOM = 20;
    /** Piece moyenne avant toute mesure : le shader garde alors le fond. */
    private static final float[] NO_ROOM = new float[LightZone.ROOM_VALUES];

    private LookUniformUploader() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Resout les emplacements des uniformes du look d'un programme, une fois, lie son bloc des neons a son point et son
     * echantillonneur de la LUT a son unite. Le programme est actif.
     *
     * @param program programme GL
     * @param locations tableau de {@value #LOCATIONS} emplacements, rempli (negatif pour un uniforme absent)
     */
    public static void resolve(int program, int[] locations) {
        for (int i = 0; i < LOCATIONS; i++) {
            locations[i] = GlUniform.getUniformLocation(program, NAMES[i]);
        }
        int block = GL31.glGetUniformBlockIndex(program, NEON_BLOCK);
        if (block != GL31.GL_INVALID_INDEX) {
            GL31.glUniformBlockBinding(program, block, NeonLights.BINDING);
        }
        int lut = GlUniform.getUniformLocation(program, LookLuts.SAMPLER);
        if (lut >= 0) {
            GL20.glUniform1i(lut, LookLuts.UNIT);
        }
        int zone = GlUniform.getUniformLocation(program, LightVolume.SAMPLER);
        if (zone >= 0) {
            GL20.glUniform1i(zone, LightVolume.UNIT);
        }
        int far = GlUniform.getUniformLocation(program, FarVolume.SAMPLER);
        if (far >= 0) {
            GL20.glUniform1i(far, FarVolume.UNIT);
        }
        int atlas = GlUniform.getUniformLocation(program, LightSurfaces.SAMPLER_ATLAS);
        if (atlas >= 0) {
            GL20.glUniform1i(atlas, LightSurfaces.UNIT_ATLAS);
        }
        int tiles = GlUniform.getUniformLocation(program, LightSurfaces.SAMPLER_TILES);
        if (tiles >= 0) {
            GL20.glUniform1i(tiles, LightSurfaces.UNIT_TILES);
        }
        int blocks = GlUniform.getUniformLocation(program, LightSurfaces.SAMPLER_BLOCKS);
        if (blocks >= 0) {
            GL20.glUniform1i(blocks, LightSurfaces.UNIT_BLOCKS);
        }
        int bounces = GlUniform.getUniformLocation(program, LightBounces.SAMPLER_BOUNCES);
        if (bounces >= 0) {
            GL20.glUniform1i(bounces, LightBounces.UNIT_BOUNCES);
        }
        int materials = GlUniform.getUniformLocation(program, LightBounces.SAMPLER_MATERIALS);
        if (materials >= 0) {
            GL20.glUniform1i(materials, LightBounces.UNIT_MATERIALS);
        }
        int surface = GlUniform.getUniformLocation(program, BlockMaterials.SAMPLER);
        if (surface >= 0) {
            GL20.glUniform1i(surface, BlockMaterials.UNIT);
        }
        int sprites = GlUniform.getUniformLocation(program, BLOCK_ATLAS);
        if (sprites >= 0) {
            GL20.glUniform1i(sprites, LightSurfaces.UNIT_SPRITES);
        }
    }

    /** {@code true} si le programme est patche (il a l'identifiant du look). */
    public static boolean patched(int[] locations) {
        return locations[AT_ID] >= 0;
    }

    /**
     * Envoie le look courant au programme actif.
     *
     * @param locations emplacements resolus par {@link #resolve}
     */
    public static void upload(int[] locations) {
        LookProfile look = LookState.current();
        // Un mod qui dessine le terrain par ses propres shaders : le look part dans le post, les shaders patches restent vanilla.
        boolean carried = LookState.shadersCarryLook() && !look.vanilla();
        GL20.glUniform1i(locations[AT_ID], carried ? look.id() : 0);
        boolean baked = carried && LightVolume.covers(look, LookState.cameraBlockX(), LookState.cameraBlockY(), LookState.cameraBlockZ());
        if (locations[AT_BAKED_LIGHT] >= 0) {
            GL20.glUniform1f(locations[AT_BAKED_LIGHT], baked ? 1.0f : 0.0f);
        }
        if (!carried) {
            return;
        }
        if (locations[AT_LIGHT_BASE] >= 0) {
            GL20.glUniform1i(locations[AT_LIGHT_BASE], LightVolume.baseY());
        }
        if (locations[AT_AIR] >= 0) {
            GL20.glUniform2f(locations[AT_AIR], look.bakedLight().haze(), look.bakedLight().sheen());
        }
        if (locations[AT_AMBIENT] >= 0) {
            Vec3d ambient = look.bakedLight().ambient();
            GL20.glUniform3f(locations[AT_AMBIENT], (float) ambient.x, (float) ambient.y, (float) ambient.z);
        }
        if (locations[AT_TONE] >= 0) {
            GL20.glUniform4f(locations[AT_TONE], look.bakedLight().toe(), look.bakedLight().pixelLight(), look.bakedLight().darkPixels(), look.bakedLight().lightLimit());
        }
        if (locations[AT_NEON_TINT] >= 0) {
            LookProfile.Neons neons = look.neons();
            float strongest = Math.max(Math.max(neons.red(), Math.max(neons.green(), neons.blue())), 1.0e-3f);
            GL20.glUniform3f(locations[AT_NEON_TINT], neons.red() / strongest, neons.green() / strongest, neons.blue() / strongest);
        }
        if (locations[AT_SURFACE_LIGHT] >= 0) {
            GL20.glUniform1f(locations[AT_SURFACE_LIGHT], baked && LightSurfaces.active() ? 1.0f : 0.0f);
        }
        // L'Ultra ne s'applique qu'a la vue du joueur.
        boolean traced = baked && LightSurfaces.active() && LightBounces.active() && LookState.depth() == 0;
        if (locations[AT_TRACED] >= 0) {
            GL20.glUniform1f(locations[AT_TRACED], traced ? 1.0f : 0.0f);
        }
        if (locations[AT_PILE] >= 0) {
            GL20.glUniform1f(locations[AT_PILE], baked && LightSurfaces.active() && look.bakedLight().relief() > 0.0f ? 1.0f : 0.0f);
        }
        if (locations[AT_FAR_ROOM] >= 0) {
            float[] room = FarVolume.room();
            GL20.glUniform3fv(locations[AT_FAR_ROOM], room == null ? NO_ROOM : room);
        }
        if (locations[AT_LIGHT_LOOK] >= 0) {
            LookProfile.BakedLight shaped = look.bakedLight();
            GL20.glUniform3f(locations[AT_LIGHT_LOOK], shaped.detail(), shaped.exposure(), shaped.direct());
        }
        if (locations[AT_FOG_RANGE] >= 0) {
            GL20.glUniform4f(locations[AT_FOG_RANGE], RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
                    RenderSystem.getShaderFogShape() == FogShape.CYLINDER ? 1.0f : 0.0f, look.bakedLight().shoulder());
        }
        if (locations[AT_FOG_TINT] >= 0) {
            float[] fog = RenderSystem.getShaderFogColor();
            GL20.glUniform4f(locations[AT_FOG_TINT], fog[0], fog[1], fog[2], fog[3]);
        }
        if (locations[AT_PARAMS] >= 0) {
            // Une lumiere tracee est lisse : la grille des texels n'est plus que celle des textures.
            GL20.glUniform4f(locations[AT_PARAMS], traced ? 0.0f : LookSettings.current().pixelShadows(), look.grain(), look.flicker(), LookState.time());
        }
        if (locations[AT_NEON_COUNT] >= 0) {
            GL20.glUniform1i(locations[AT_NEON_COUNT], LookState.neonCount());
        }
        if (locations[AT_CAMERA_BLOCK] >= 0) {
            GL20.glUniform3i(locations[AT_CAMERA_BLOCK], LookState.cameraBlockX(), LookState.cameraBlockY(), LookState.cameraBlockZ());
        }
        if (locations[AT_CAMERA_FRACT] >= 0) {
            GL20.glUniform3f(locations[AT_CAMERA_FRACT], LookState.cameraFractX(), LookState.cameraFractY(), LookState.cameraFractZ());
        }
        if (locations[AT_NEON_CLOCK] >= 0) {
            GL20.glUniform2i(locations[AT_NEON_CLOCK], LookState.tick(), NeonSchedule.seed(look.dimension()));
        }
        if (locations[AT_NEON_FACE] >= 0) {
            float[] face = NeonFaces.rect(look);
            GL20.glUniform4f(locations[AT_NEON_FACE], face[0], face[1], face[2], face[3]);
        }
        if (locations[AT_NEON_SHARES] >= 0) {
            GL20.glUniform3f(locations[AT_NEON_SHARES], look.neons().flickering(), look.neons().dead(), look.neons().blackout());
        }
    }
}
