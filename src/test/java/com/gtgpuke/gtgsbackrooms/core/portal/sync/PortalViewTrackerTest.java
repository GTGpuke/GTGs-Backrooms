package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ChunkFilter;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.transition.DestinationLoader;

@DisplayName("Paquets relayes aux joueurs qui regardent une dimension")
class PortalViewTrackerTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    @Test
    @DisplayName("Sons, sons d'entity, evenements de monde, particules et fissures passent ; le reste concerne les joueurs presents")
    void relayedPackets() {
        assertTrue(PortalViewTracker.isRelayed(new PlaySoundS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS,
                0.0, 64.0, 0.0, 1.0f, 1.0f, 0L)), "Un son a une position.");
        assertTrue(PortalViewTracker.isRelayed(new PlaySoundFromEntityS2CPacket(SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.BLOCKS,
                new ItemEntity(EntityType.ITEM, null), 1.0f, 1.0f, 0L)), "Un son porte par une entity.");
        assertTrue(PortalViewTracker.isRelayed(new WorldEventS2CPacket(WorldEvents.BLOCK_BROKEN, BlockPos.ORIGIN,
                Block.getRawIdFromState(Blocks.STONE.getDefaultState()), false)), "Un evenement de monde.");
        assertTrue(PortalViewTracker.isRelayed(new ParticleS2CPacket(ParticleTypes.FLAME, false, 0.0, 64.0, 0.0, 0.0f, 0.0f, 0.0f, 0.0f, 1)),
                "Des particules.");
        assertTrue(PortalViewTracker.isRelayed(new BlockBreakingProgressS2CPacket(42, BlockPos.ORIGIN, 3)), "Les fissures d'un bloc mine.");
        assertFalse(PortalViewTracker.isRelayed(new GameMessageS2CPacket(Text.literal("Bonjour."), false)), "Un message de chat.");
    }

    @Test
    @DisplayName("Un portail vu a travers un autre ouvre son arrivee s'il est visible, dans la fenetre de son monde, et mene hors du monde du joueur")
    void portalsSeenThroughAnother() {
        Vec3d position = new Vec3d(20.5, 70.0, 36.5);
        long chunk = ChunkPos.toLong(ChunkSectionPos.getSectionCoord(position.x), ChunkSectionPos.getSectionCoord(position.z));
        LongSet window = new LongOpenHashSet();
        window.add(chunk);
        BackroomsPortalEntity toEnd = portal(position, World.END, true);
        assertTrue(PortalViewTracker.seenThroughAnother(toEnd, World.OVERWORLD, window), "Dans la fenetre, visible, vers un troisieme monde.");
        assertFalse(PortalViewTracker.seenThroughAnother(toEnd, World.OVERWORLD, null), "Son monde n'est pas synchronise pour ce joueur.");
        assertFalse(PortalViewTracker.seenThroughAnother(toEnd, World.OVERWORLD, new LongOpenHashSet()), "Hors de la fenetre de son monde.");
        assertFalse(PortalViewTracker.seenThroughAnother(portal(position, World.END, false), World.OVERWORLD, window), "Invisible.");
        assertFalse(PortalViewTracker.seenThroughAnother(portal(position, World.OVERWORLD, true), World.OVERWORLD, window),
                "Vers le monde du joueur : le client le tient deja.");
    }

    @Test
    @DisplayName("Pres d'un cadre, la fenetre couvre ce que vanilla enverrait au joueur, dans la carte du client")
    void windowNearAFrame() {
        int near = DestinationLoader.syncRadius(true);
        assertEquals(9, PortalViewTracker.nearRadius(8, 10, 0), "La distance de vue du joueur et l'anneau qui la borde.");
        assertEquals(7, PortalViewTracker.nearRadius(12, 6, 0), "Bornee par la distance de vue du serveur.");
        assertEquals(near, PortalViewTracker.nearRadius(2, 10, 0), "Jamais moins que la fenetre pres du portail.");
        int fitting = (RemoteChunkWindow.MAP_RADIUS - 4) / 2;
        assertEquals(fitting, PortalViewTracker.nearRadius(32, 32, 4), "Dans la carte du client autour du foyer.");
        assertTrue(RemoteChunkWindow.fitsAround(0, 0, 4, 0, fitting), "Une fenetre a ce rayon tient autour du foyer.");
    }

    @Test
    @DisplayName("Une arrivee du monde du joueur a sa fenetre des que celle-ci deborde de ce que vanilla lui envoie")
    void arrivalBeyondVanilla() {
        ChunkFilter vanilla = ChunkFilter.cylindrical(new ChunkPos(0, 0), 8);
        assertFalse(PortalViewTracker.beyondVanilla(vanilla, 0, 0, 3), "Fenetre autour du joueur, dans sa distance de vue : vanilla l'envoie.");
        assertFalse(PortalViewTracker.beyondVanilla(vanilla, 5, 0, 3), "Son bord sur l'axe, encore dessine.");
        assertTrue(PortalViewTracker.beyondVanilla(vanilla, 7, 0, 3), "Son bord dans l'anneau, que le client ne dessine pas.");
        assertTrue(PortalViewTracker.beyondVanilla(vanilla, 0, -7, 3), "Pareil de l'autre cote.");
        assertTrue(PortalViewTracker.beyondVanilla(vanilla, 4, 4, 3), "Un coin hors du disque suffit.");
        assertTrue(PortalViewTracker.beyondVanilla(vanilla, -4, 4, 3), "Quel que soit le coin.");
        assertTrue(PortalViewTracker.beyondVanilla(vanilla, -4, -4, 3), "Quel que soit le coin.");
        assertTrue(PortalViewTracker.beyondVanilla(ChunkFilter.cylindrical(new ChunkPos(0, 0), 2), 0, 0, 3),
                "Une distance de vue plus courte que la fenetre ne la couvre jamais.");
    }

    @Test
    @DisplayName("Un portail visible a une fenetre vers une autre dimension, ou loin dans le monde du joueur")
    void relevantPortals() {
        ChunkFilter vanilla = ChunkFilter.cylindrical(new ChunkPos(0, 0), 8);
        Vec3d position = new Vec3d(0.5, 65.0, 0.5);
        assertTrue(PortalViewTracker.relevant(portal(position, World.END, true), World.OVERWORLD, vanilla), "Vers une autre dimension.");
        assertFalse(PortalViewTracker.relevant(portal(position, World.END, false), World.OVERWORLD, vanilla), "Invisible, jamais.");
        assertFalse(PortalViewTracker.relevant(portal(position, World.OVERWORLD, true), World.OVERWORLD, vanilla),
                "Vers son propre monde, tout pres : vanilla envoie deja toute la fenetre.");
        assertTrue(PortalViewTracker.relevant(portal(position, World.OVERWORLD, true), World.OVERWORLD, ChunkFilter.cylindrical(new ChunkPos(40, 0), 8)),
                "Vers son propre monde, loin du joueur : l'ailleurs a sa fenetre.");
    }

    private static BackroomsPortalEntity portal(Vec3d position, RegistryKey<World> destination, boolean visible) {
        return BackroomsPortalEntity.builder(null).position(position).facingSource(PortalFacing.NORTH).facingDestination(PortalFacing.SOUTH)
                .destinationDim(destination).destinationPos(new Vec3d(0.5, 65.0, 0.5)).size(1.0f, 2.0f).visible(visible).build();
    }
}
