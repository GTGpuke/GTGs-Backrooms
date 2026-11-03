package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.ModelPart;

/** Les faces d'un cube de modele, avec leur place dans la texture : un cube transparent ne fait pas d'ombre. */
@Mixin(ModelPart.Cuboid.class)
public interface CuboidAccessor {

    @Accessor("sides")
    ModelPart.Quad[] backrooms$getSides();
}
