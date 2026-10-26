package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState;

/**
 * Vue allegee d'un portail (palier de detail) : ses particules ne se dessinent pas, quel que soit le rendu qui les
 * demande (le dessin d'une vue, ou le rendu complet de son renderer sous un pack de shaders). Elles continuent de vivre.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManager {

    @Inject(method = "renderParticles", at = @At("HEAD"), cancellable = true)
    private void backrooms$noParticlesInReducedView(LightmapTextureManager lightmap, Camera camera, float tickDelta, CallbackInfo ci) {
        if (PortalRenderState.reducedView()) {
            ci.cancel();
        }
    }
}
