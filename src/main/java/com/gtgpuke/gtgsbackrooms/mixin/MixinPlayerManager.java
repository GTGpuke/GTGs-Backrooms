package com.gtgpuke.gtgsbackrooms.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConfig;
import com.gtgpuke.gtgsbackrooms.core.portal.interaction.RemoteInteractionHandler;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker;

/**
 * Sons et evenements de monde (bruit de pas, porte, bloc casse) d'une dimension : vanilla les envoie aux joueurs
 * proches dans cette dimension ; ils partent aussi a ceux qui la voient a travers un portail, et a l'auteur d'une action
 * a travers un cadre, que vanilla exclut. Le client decide par quel portail le son lui parvient.
 */
@Mixin(PlayerManager.class)
public abstract class MixinPlayerManager {

    @Inject(method = "sendToAround", at = @At("TAIL"))
    private void backrooms$relayToPortalViewers(
            @Nullable PlayerEntity player,
            double x,
            double y,
            double z,
            double distance,
            RegistryKey<World> worldKey,
            Packet<?> packet,
            CallbackInfo ci) {
        if (ModConfig.ENABLE_PORTAL_FEATURE) {
            RemoteInteractionHandler.sendWithheld(player, worldKey, packet);
            PortalViewTracker.relayToViewers(worldKey, x, z, packet);
        }
    }
}
