package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * Monde ou vanilla a cree un joueur client, garde dans un champ final que vanilla ne relit pas : un joueur qui change de
 * monde par un portail garderait vivant le monde ou il est apparu, meme apres que le serveur l'a fait liberer.
 */
@Mixin(AbstractClientPlayerEntity.class)
public interface AbstractClientPlayerEntityAccessor {

    @Mutable
    @Accessor("clientWorld")
    void backrooms$setClientWorld(ClientWorld world);
}
