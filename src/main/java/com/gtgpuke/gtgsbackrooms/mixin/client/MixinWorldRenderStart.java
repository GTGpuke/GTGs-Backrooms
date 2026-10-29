package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.WorldRenderer;

import com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewRenderStart;

/**
 * Arrete {@code WorldRenderer.render} a sa premiere instruction pendant un debut de rendu a blanc d'un renderer de vue
 * ({@link ViewRenderStart}). Le point d'arret est la premiere instruction vanilla, pas la tete de la methode : les
 * handlers que d'autres mods inserent en tete ont donc tous tourne (quelle que soit leur priorite) quand celui-ci rend
 * la main. Priorite au-dessus de celle par defaut : applique apres ces mods, le retour anticipe qu'il ajoute n'est pas
 * vu par leurs injections en fin de methode ({@code @At("RETURN")}), qui defont ce que la tete a pose.
 */
@Mixin(value = WorldRenderer.class, priority = 1100)
public abstract class MixinWorldRenderStart {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getTickManager()Lnet/minecraft/world/tick/TickManager;",
            ordinal = 0), cancellable = true)
    private void backrooms$firstInstruction(CallbackInfo ci) {
        if (ViewRenderStart.isStarting()) {
            ci.cancel();
        }
    }
}
