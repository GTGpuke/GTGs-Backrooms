package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

/** La pose d'un joueur (accroupi, bras, calques de la peau), posee avant son modele : l'ombre d'un joueur le pose de la meme facon. */
@Mixin(PlayerEntityRenderer.class)
public interface PlayerEntityRendererAccessor {

    @Invoker("setModelPose")
    void backrooms$setModelPose(AbstractClientPlayerEntity player);
}
