package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.sound.Source;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.core.audio.client.AdjustedSource;

/**
 * Les ajustements de l'acoustique sur une source vanilla : chaque volume que vanilla pose (au depart, puis a chaque tick
 * pour un son qui bouge ou boucle) est multiplie par la part gardee (sans EFX, l'occlusion se rend par le volume), et
 * chaque position est decalee vers la direction d'ou le son semble venir (direction percue). Une part de 1 et un
 * decalage nul (le cas de toute source non traitee) ne changent rien. Thread du son seulement.
 */
@Mixin(Source.class)
public abstract class MixinSource implements AdjustedSource {

    @Shadow
    @Final
    private int pointer;

    /** Tampons de lecture de la position OpenAL : thread du son seulement, reutilises. */
    @Unique
    private static final float[] BACKROOMS$X = new float[1];
    @Unique
    private static final float[] BACKROOMS$Y = new float[1];
    @Unique
    private static final float[] BACKROOMS$Z = new float[1];

    @Unique
    private float backrooms$volumeScale = 1.0f;
    /** Dernier volume que vanilla a pose, avant la part gardee : la part se change sans le perdre, meme a zero. */
    @Unique
    private float backrooms$rawVolume = 1.0f;
    @Unique
    private double backrooms$offsetX;
    @Unique
    private double backrooms$offsetY;
    @Unique
    private double backrooms$offsetZ;

    @ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
    private float backrooms$scaleVolume(float volume) {
        this.backrooms$rawVolume = volume;
        return volume * this.backrooms$volumeScale;
    }

    @ModifyVariable(method = "setPosition", at = @At("HEAD"), argsOnly = true)
    private Vec3d backrooms$offsetPosition(Vec3d position) {
        if (this.backrooms$offsetX == 0.0 && this.backrooms$offsetY == 0.0 && this.backrooms$offsetZ == 0.0) {
            return position;
        }
        return position.add(this.backrooms$offsetX, this.backrooms$offsetY, this.backrooms$offsetZ);
    }

    @Override
    public void backrooms$setVolumeScale(float scale) {
        this.backrooms$volumeScale = scale;
        AL10.alSourcef(this.pointer, AL10.AL_GAIN, this.backrooms$rawVolume * scale);
    }

    @Override
    public void backrooms$setPositionOffset(double dx, double dy, double dz) {
        AL10.alGetSource3f(this.pointer, AL10.AL_POSITION, BACKROOMS$X, BACKROOMS$Y, BACKROOMS$Z);
        AL10.alSource3f(this.pointer, AL10.AL_POSITION, (float) (BACKROOMS$X[0] - this.backrooms$offsetX + dx),
                (float) (BACKROOMS$Y[0] - this.backrooms$offsetY + dy), (float) (BACKROOMS$Z[0] - this.backrooms$offsetZ + dz));
        this.backrooms$offsetX = dx;
        this.backrooms$offsetY = dy;
        this.backrooms$offsetZ = dz;
    }
}
