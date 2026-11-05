package com.gtgpuke.gtgsbackrooms.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;

/**
 * Particules diffusees par le serveur (coups critiques, explosions, fumee) et fissures d'un bloc qu'on mine : elles
 * partent aussi aux joueurs qui voient la dimension a travers un portail, et s'affichent dans la vue.
 */
@Mixin(ServerWorld.class)
public abstract class MixinServerWorld {

    @ModifyExpressionValue(
            method = "spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I",
            at = @At(value = "NEW", target = "Lnet/minecraft/network/packet/s2c/play/ParticleS2CPacket;"))
    private ParticleS2CPacket backrooms$relayToPortalViewers(ParticleS2CPacket packet) {
        if (ModConfig.ENABLE_PORTAL_FEATURE) {
            ServerWorld self = (ServerWorld) (Object) this;
            // Vanilla envoie ces particules a moins de 32 blocs, 512 pour celles de longue distance (sendToPlayerIfNearby).
            PortalViewTracker.relayToViewers(self.getRegistryKey(), packet.getX(), packet.getY(), packet.getZ(),
                    packet.isLongDistance() ? PortalViewTracker.LONG_PARTICLE_RANGE : PortalViewTracker.NEAR_RANGE, -1, false, packet);
        }
        return packet;
    }

    @Inject(method = "setBlockBreakingInfo", at = @At("TAIL"))
    private void backrooms$relayBreakingToPortalViewers(int entityId, BlockPos pos, int progress, CallbackInfo ci) {
        if (ModConfig.ENABLE_PORTAL_FEATURE && PortalViewTracker.watchedChunkCount() > 0) {
            ServerWorld self = (ServerWorld) (Object) this;
            // Vanilla envoie les fissures a moins de 32 blocs, sauf a celui qui mine.
            PortalViewTracker.relayToViewers(self.getRegistryKey(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PortalViewTracker.NEAR_RANGE,
                    entityId, false, new BlockBreakingProgressS2CPacket(entityId, pos, progress));
        }
    }
}
