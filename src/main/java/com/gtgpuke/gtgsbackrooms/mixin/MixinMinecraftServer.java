package com.gtgpuke.gtgsbackrooms.mixin;

import java.util.function.BooleanSupplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;

/**
 * La fermeture d'un monde finit toujours. Vanilla y fait tourner le dechargement des chunks avec une condition toujours vraie : il
 * vide sa file sans rendre la main. Un chunk repris pendant qu'il se dechargeait, encore retenu par un chargement, y remet sa tache a
 * chaque passage, et ce chargement attend justement le fil du serveur, qui ne sort plus de cette boucle : la partie reste figee sur la
 * sauvegarde. Revenir sur ses pas juste avant de quitter suffit, et les allers-retours par un portail le rendent plus probable. Chaque
 * tour de fermeture borne donc ce temps, puis laisse au fil du serveur le temps de ses taches, que vanilla compte depuis le debut du
 * tour : il n'en resterait rien.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    /** Temps laisse au dechargement d'un monde a chaque tour de fermeture. */
    @Unique
    private static final long BACKROOMS$UNLOAD_NANOS = 5_000_000L;
    /** Temps laisse aux taches du fil du serveur a chaque tour de fermeture. */
    @Unique
    private static final long BACKROOMS$TASKS_NANOS = 1_000_000L;

    @Shadow
    private long tickStartTimeNanos;

    @ModifyArg(method = "shutdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;tick(Ljava/util/function/BooleanSupplier;Z)V"))
    private BooleanSupplier backrooms$boundedUnload(BooleanSupplier always) {
        long end = Util.getMeasuringTimeNano() + BACKROOMS$UNLOAD_NANOS;
        return () -> Util.getMeasuringTimeNano() < end;
    }

    @Inject(method = "shutdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;runTasksTillTickEnd()V"))
    private void backrooms$timeForTasks(CallbackInfo ci) {
        this.tickStartTimeNanos = Util.getMeasuringTimeNano() + BACKROOMS$TASKS_NANOS;
    }
}
