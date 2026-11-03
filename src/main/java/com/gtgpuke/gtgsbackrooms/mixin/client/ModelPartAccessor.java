package com.gtgpuke.gtgsbackrooms.mixin.client;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.model.ModelPart;

/** Les cubes et les enfants d'une partie de modele : l'ombre d'un corps reprend ses cubes. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {

    @Accessor("cuboids")
    List<ModelPart.Cuboid> backrooms$getCuboids();

    @Accessor("children")
    Map<String, ModelPart> backrooms$getChildren();
}
