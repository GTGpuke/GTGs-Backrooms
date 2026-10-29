package com.gtgpuke.gtgsbackrooms.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gl.VertexBuffer;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.LazyVertexBuffers;

/**
 * Tampons de sommets crees pendant la creation d'un renderer de vue : leurs objets GL (tampon de sommets, tampon
 * d'indices, tableau de sommets) ne sont reserves qu'au premier usage, et non a la construction. Un tampon qui n'a
 * jamais servi n'a rien a liberer, et sa fermeture n'appelle pas le pilote : sous Linux, vanilla vide chaque tampon
 * avant de le supprimer (il le lie, puis {@code glBufferData} a zero octet), et vider le tampon nul est une erreur
 * OpenGL ; une grille de sections rechargee en faisait des centaines de milliers (pilotes Mesa, tests du client).
 */
@Mixin(VertexBuffer.class)
public abstract class MixinVertexBuffer {

    @Shadow
    private int vertexBufferId;

    @Shadow
    private int indexBufferId;

    @Shadow
    private int vertexArrayId;

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenBuffers()I"))
    private int backrooms$lazyBuffer(Operation<Integer> original) {
        return LazyVertexBuffers.active() ? 0 : original.call();
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_glGenVertexArrays()I"))
    private int backrooms$lazyVertexArray(Operation<Integer> original) {
        return LazyVertexBuffers.active() ? 0 : original.call();
    }

    /** Fermeture d'un tampon cree en differe qui n'a jamais servi : rien a supprimer, il est seulement tenu pour ferme. */
    @Inject(method = "close", at = @At("HEAD"))
    private void backrooms$closeUnallocated(CallbackInfo ci) {
        if (this.vertexArrayId == 0) {
            this.vertexBufferId = -1;
            this.indexBufferId = -1;
            this.vertexArrayId = -1;
        }
    }

    /** Premier usage d'un tampon cree en differe : ses objets GL existent a partir d'ici. */
    @Inject(method = {"bind", "upload(Lnet/minecraft/client/render/BuiltBuffer;)V",
        "uploadIndexBuffer(Lnet/minecraft/client/util/BufferAllocator$CloseableBuffer;)V"}, at = @At("HEAD"))
    private void backrooms$allocateOnFirstUse(CallbackInfo ci) {
        if (this.vertexArrayId == 0) {
            this.vertexBufferId = GlStateManager._glGenBuffers();
            this.indexBufferId = GlStateManager._glGenBuffers();
            this.vertexArrayId = GlStateManager._glGenVertexArrays();
        }
    }
}
