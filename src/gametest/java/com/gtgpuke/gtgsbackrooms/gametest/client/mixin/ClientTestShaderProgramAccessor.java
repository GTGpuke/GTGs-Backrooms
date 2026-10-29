package com.gtgpuke.gtgsbackrooms.gametest.client.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gl.ShaderProgram;

/** Les textures posees sur les samplers d'un programme, que les tests du client comparent a celles demandees. */
@Mixin(ShaderProgram.class)
public interface ClientTestShaderProgramAccessor {

    @Accessor("samplers")
    Map<String, Object> gtgsTest$getSamplers();
}
