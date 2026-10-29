package com.gtgpuke.gtgsbackrooms.core.render.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.DimensionEffects;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfile;
import com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles;

/**
 * Effets de dimension d'un monde a look : pas de ciel ni de nuages, pas de lever ni de coucher de soleil dans le
 * brouillard, dont la couleur est celle du profil quand il en a une. Enregistres sous l'identifiant d'effets que le type
 * de dimension declare ; le profil, lui, se lit a chaque appel, donc se recharge avec les ressources.
 */
public final class LookDimensionEffects extends DimensionEffects {

    private final Identifier dimension;

    /**
     * @param dimension dimension dont le profil pilote ces effets
     */
    public LookDimensionEffects(Identifier dimension) {
        super(Float.NaN, false, DimensionEffects.SkyType.NONE, false, false);
        this.dimension = dimension;
    }

    @Override
    public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
        LookProfile.Fog fog = LookProfiles.of(this.dimension).fog();
        return fog.enabled() ? fog.color() : color;
    }

    @Override
    public boolean useThickFog(int camX, int camY) {
        return false;
    }

    @Override
    public float @Nullable [] getFogColorOverride(float skyAngle, float tickDelta) {
        return null;
    }
}
