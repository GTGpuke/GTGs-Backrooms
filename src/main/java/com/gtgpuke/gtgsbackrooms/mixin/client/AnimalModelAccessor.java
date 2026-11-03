package com.gtgpuke.gtgsbackrooms.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AnimalModel;

/** Les parties d'un modele en tete et corps, et ce qui les met a l'echelle d'un petit : l'ombre d'un corps reprend ses cubes. */
@Mixin(AnimalModel.class)
public interface AnimalModelAccessor {

    @Invoker("getHeadParts")
    Iterable<ModelPart> backrooms$getHeadParts();

    @Invoker("getBodyParts")
    Iterable<ModelPart> backrooms$getBodyParts();

    @Accessor("headScaled")
    boolean backrooms$isHeadScaled();

    @Accessor("childHeadYOffset")
    float backrooms$getChildHeadYOffset();

    @Accessor("childHeadZOffset")
    float backrooms$getChildHeadZOffset();

    @Accessor("invertedChildHeadScale")
    float backrooms$getInvertedChildHeadScale();

    @Accessor("invertedChildBodyScale")
    float backrooms$getInvertedChildBodyScale();

    @Accessor("childBodyYOffset")
    float backrooms$getChildBodyYOffset();
}
