package com.gtgpuke.gtgsbackrooms.core.render.client;

import java.nio.FloatBuffer;
import java.util.Collection;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.gtgpuke.gtgsbackrooms.core.render.light.LookLut;
import com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;

/**
 * Les LUT d'etalonnage des looks ({@link LookLut}) dans une seule texture 3D : un etage de {@value LookLut#SIZE} par
 * identifiant de look, l'identite pour vanilla et les identifiants sans profil. Le shader du monde et le post la lisent
 * par l'echantillonneur {@value #SAMPLER}, a l'etage de leur look. Demi-flottants : une teinte peut depasser 1.
 */
public final class LookLuts {

    /** Unite de texture de la LUT : les shaders vanilla n'en lient que trois, rien d'autre n'y lie de texture 3D. */
    public static final int UNIT = 7;
    /** Echantillonneur de la LUT dans les shaders patches et le post. */
    public static final String SAMPLER = "gtgs_LookLut";
    private static final int SLOTS = StencilLookMask.MAX_LOOK + 1;
    private static final int SLOT_VALUES = LookLut.SIZE * LookLut.SIZE * LookLut.SIZE * LookLut.CHANNELS;
    private static int texture;

    private LookLuts() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /**
     * Refait la texture d'apres les profils charges, au rechargement des ressources (thread de rendu).
     *
     * @param profiles profils des dimensions, chacun a l'etage de son identifiant
     */
    public static void rebuild(Collection<LookProfile> profiles) {
        float[] values = new float[SLOT_VALUES * SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            LookLut.fill(LookProfile.VANILLA, values, slot * SLOT_VALUES);
        }
        for (LookProfile profile : profiles) {
            LookLut.fill(profile, values, profile.id() * SLOT_VALUES);
        }
        FloatBuffer buffer = MemoryUtil.memAllocFloat(values.length);
        int previousUnit = GlStateManager._getActiveTexture();
        PixelUnpack.begin();
        try {
            buffer.put(values).flip();
            if (texture == 0) {
                texture = GL11.glGenTextures();
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
            GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGB16F, LookLut.SIZE, LookLut.SIZE, LookLut.SIZE * SLOTS, 0, GL11.GL_RGB, GL11.GL_FLOAT,
                    buffer);
        } finally {
            GlStateManager._activeTexture(previousUnit);
            PixelUnpack.end();
            MemoryUtil.memFree(buffer);
        }
    }

    /** Lie la LUT a son unite, au debut du rendu du monde : un rechargement ou un autre mod a pu changer cette unite. */
    public static void bind() {
        if (texture == 0) {
            return;
        }
        int previousUnit = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + UNIT);
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, texture);
        GlStateManager._activeTexture(previousUnit);
    }

    /** Texture de la LUT, 0 avant le premier chargement des profils (tests du client). */
    public static int texture() {
        return texture;
    }
}
