package com.gtgpuke.gtgsbackrooms;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Les classes utilitaires et de registre du mod ne s'instancient pas : classe finale, constructeur prive qui
 * refuse l'appel. Le test les enumere toutes pour qu'un oubli se voie.
 */
@DisplayName("Classes utilitaires")
class UtilityClassesTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    static Stream<String> utilityClasses() {
        return Stream.of(
                "com.gtgpuke.gtgsbackrooms.core.ModConstants",
                "com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants",
                "com.gtgpuke.gtgsbackrooms.core.ModConfig",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModNetworking",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModItems",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModSounds",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroArrival",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.LevelZeroLayoutSync",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModChunkGenerators",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModBlockEntities",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModEntities",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModCreativeTab",
                "com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents",
                "com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor",
                "com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfiles",
                "com.gtgpuke.gtgsbackrooms.core.audio.acoustic.OcclusionTrace",
                "com.gtgpuke.gtgsbackrooms.core.audio.acoustic.EmissionDimension",
                "com.gtgpuke.gtgsbackrooms.core.audio.acoustic.GeneratedAcoustics",
                "com.gtgpuke.gtgsbackrooms.core.audio.acoustic.PerceivedDirection",
                "com.gtgpuke.gtgsbackrooms.core.audio.acoustic.ReverbPresets",
                "com.gtgpuke.gtgsbackrooms.core.audio.cue.CameraShake",
                "com.gtgpuke.gtgsbackrooms.core.audio.cue.CueClock",
                "com.gtgpuke.gtgsbackrooms.core.audio.cue.CueRegistry",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroCoords",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.coord.LevelZeroLayerStackLayout",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroCellConnections",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroCellMicroPattern",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroLargeRooms",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroLightPattern",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroTieBreak",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.layout.LevelZeroTopology",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.LevelZeroSeedResolver",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.noise.StageRandom",
                "com.gtgpuke.gtgsbackrooms.backrooms.level0.generation.write.LevelZeroDebugPalette",
                "com.gtgpuke.gtgsbackrooms.core.portal.block.PortalArea",
                "com.gtgpuke.gtgsbackrooms.core.portal.block.PortalTargets",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.world.BuildBuffers",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.world.ClientPortalLight",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.sound.PortalSound",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.LazyVertexBuffers",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ObliqueProjection",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalClipping",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalRenderState",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.CrossingRoll",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.CoarseSectionMesher",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.FrameOcclusion",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.FrozenImage",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewDetail",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.PortalViewTransform",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderClipInjector",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ShaderPackPortalRenderer",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ThirdPersonPortal",
                "com.gtgpuke.gtgsbackrooms.core.portal.client.view.ViewRenderStart",
                "com.gtgpuke.gtgsbackrooms.core.render.patch.PatchedVertexStages",
                "com.gtgpuke.gtgsbackrooms.core.render.patch.ShaderPatcher",
                "com.gtgpuke.gtgsbackrooms.core.render.profile.LookProfiles",
                "com.gtgpuke.gtgsbackrooms.core.render.state.LookState",
                "com.gtgpuke.gtgsbackrooms.core.render.light.DimensionLightmap",
                "com.gtgpuke.gtgsbackrooms.core.render.light.DimensionFog",
                "com.gtgpuke.gtgsbackrooms.core.render.light.NeonSchedule",
                "com.gtgpuke.gtgsbackrooms.core.render.post.StencilLookMask",
                "com.gtgpuke.gtgsbackrooms.core.portal.debug.DebugPortalCommand",
                "com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry",
                "com.gtgpuke.gtgsbackrooms.core.portal.interaction.RemoteInteractionHandler",
                "com.gtgpuke.gtgsbackrooms.core.portal.interaction.RemoteMiningTracker",
                "com.gtgpuke.gtgsbackrooms.core.portal.propagation.PortalPursuit",
                "com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalNetwork",
                "com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalSight",
                "com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalViewTracker",
                "com.gtgpuke.gtgsbackrooms.core.portal.sync.RemoteChunkWindow",
                "com.gtgpuke.gtgsbackrooms.core.portal.transition.ArrivalGuard",
                "com.gtgpuke.gtgsbackrooms.core.portal.transition.DestinationLoader",
                "com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalCollision",
                "com.gtgpuke.gtgsbackrooms.core.portal.transition.PortalRelevance",
                "com.gtgpuke.gtgsbackrooms.core.portal.transition.TeleportExecutor");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("utilityClasses")
    @DisplayName("Classe finale, constructeur prive qui refuse l'instanciation")
    void cannotBeInstantiated(String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        assertTrue(Modifier.isFinal(type.getModifiers()), "La classe est finale.");
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertTrue(constructors.length == 1 && Modifier.isPrivate(constructors[0].getModifiers()), "Un seul constructeur, prive.");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance, "L'appel echoue.");
        assertInstanceOf(UnsupportedOperationException.class, failure.getCause(), "Refus explicite.");
    }
}
