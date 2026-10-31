package com.gtgpuke.gtgsbackrooms.core.render.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;

import com.gtgpuke.gtgsbackrooms.core.render.light.BiomeColorSampling;

/**
 * Couleurs du brouillard et du ciel des biomes autour de la camera, a la place de {@code CubicSampler.sampleColor} dans
 * {@code BackgroundRenderer.render} et {@code ClientWorld.getSkyColor} : le meme resultat au bit pres, sans les centaines
 * de vecteurs que vanilla cree a chaque appel, a chaque image et a chaque vue de portail ({@link BiomeColorSampling}).
 * Les effets de dimension de vanilla et ceux d'un monde a look sont reproduits ; d'autres (ceux d'un autre mod), ou un
 * appel hors du fil de rendu, laissent vanilla calculer.
 */
public final class BiomeColors {

    /** Couleur du biome d'un point de la grille, brouillard ou ciel, dans le monde en cours (fil de rendu seulement). */
    private static final Biomes BIOMES = new Biomes();

    private BiomeColors() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Couleur du brouillard des biomes autour de {@code pos}, corrigee par les effets de la dimension, comme vanilla ; ou
     * {@code null} pour laisser vanilla la calculer.
     *
     * @param world monde rendu
     * @param pos centre de l'echantillonnage, dans la grille des biomes
     * @param tickDelta avancement de l'image dans le tick, dont vanilla tire la hauteur du soleil
     */
    public static @Nullable Vec3d fogColor(ClientWorld world, Vec3d pos, float tickDelta) {
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }
        DimensionEffects effects = world.getDimensionEffects();
        Class<?> type = effects.getClass();
        // Meme hauteur du soleil que vanilla, du meme monde au meme instant.
        float sun = MathHelper.clamp(MathHelper.cos(world.getSkyAngle(tickDelta) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
        if (type == DimensionEffects.Overworld.class) {
            return sample(world, pos, false, sun * 0.94F + 0.06F, sun * 0.94F + 0.06F, sun * 0.91F + 0.09F, null);
        }
        if (type == DimensionEffects.Nether.class) {
            return sample(world, pos, false, 1.0, 1.0, 1.0, null);
        }
        if (type == DimensionEffects.End.class) {
            return sample(world, pos, false, 0.15F, 0.15F, 0.15F, null);
        }
        if (type == LookDimensionEffects.class) {
            return sample(world, pos, false, 1.0, 1.0, 1.0, ((LookDimensionEffects) effects).fixedFogColor());
        }
        return null;
    }

    /** Couleur du ciel des biomes autour de {@code pos}, comme vanilla ; ou {@code null} pour la laisser a vanilla. */
    public static @Nullable Vec3d skyColor(ClientWorld world, Vec3d pos) {
        if (!RenderSystem.isOnRenderThread()) {
            return null;
        }
        return sample(world, pos, true, 1.0, 1.0, 1.0, null);
    }

    private static Vec3d sample(ClientWorld world, Vec3d pos, boolean sky, double scaleRed, double scaleGreen, double scaleBlue, @Nullable Vec3d fixed) {
        BIOMES.access = world.getBiomeAccess();
        BIOMES.sky = sky;
        try {
            return BiomeColorSampling.sample(pos, BIOMES, scaleRed, scaleGreen, scaleBlue, fixed);
        } finally {
            BIOMES.access = null;
        }
    }

    /** Couleur du biome d'un point de la grille des biomes : celle du brouillard, ou du ciel. */
    private static final class Biomes implements BiomeColorSampling.Source {

        private @Nullable BiomeAccess access;
        private boolean sky;

        @Override
        public int rgb(int x, int y, int z) {
            Biome biome = this.access.getBiomeForNoiseGen(x, y, z).value();
            return this.sky ? biome.getSkyColor() : biome.getFogColor();
        }
    }
}
