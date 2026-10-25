package com.gtgpuke.gtgsbackrooms.core.portal.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import com.gtgpuke.gtgsbackrooms.MinecraftTestBootstrap;
import com.gtgpuke.gtgsbackrooms.backrooms.BackroomsConstants;
import com.gtgpuke.gtgsbackrooms.backrooms.level0.block.LevelZeroWallpaperBlock;
import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalExit;
import com.gtgpuke.gtgsbackrooms.core.portal.block.PortalSettings;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;

@DisplayName("Payloads de synchronisation des portails")
class PortalPayloadsTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    private static <T> T roundTrip(PacketCodec<ByteBuf, T> codec, T value) {
        ByteBuf buffer = Unpooled.buffer();
        codec.encode(buffer, value);
        T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "Tout le tampon doit etre consomme.");
        return decoded;
    }

    @Test
    @DisplayName("L'annonce de dimension survit a un aller-retour reseau")
    void dimensionPayload() {
        RemoteDimensionPayload payload = new RemoteDimensionPayload(
                BackroomsConstants.LEVEL_ZERO_WORLD.getValue(), Identifier.of(ModConstants.MOD_ID, "backrooms_level_0_type"), 2);
        assertEquals(payload, roundTrip(RemoteDimensionPayload.CODEC, payload), "Aller-retour identique.");
        assertEquals(RemoteDimensionPayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("Le dechargement de chunk survit a un aller-retour reseau, coordonnees negatives comprises")
    void unloadPayload() {
        RemoteChunkUnloadPayload payload = new RemoteChunkUnloadPayload(1, new ChunkPos(-12, 7));
        assertEquals(payload, roundTrip(RemoteChunkUnloadPayload.CODEC, payload), "Aller-retour identique.");
        assertEquals(RemoteChunkUnloadPayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("La liberation d'une dimension survit a un aller-retour reseau avec son index")
    void dimensionReleaseRoundTrip() {
        RemoteDimensionReleasePayload payload = new RemoteDimensionReleasePayload(3);
        assertEquals(payload, roundTrip(RemoteDimensionReleasePayload.CODEC, payload), "Aller-retour identique.");
        assertEquals(RemoteDimensionReleasePayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("La mise a jour de bloc transporte l'etat complet, proprietes comprises")
    void blockUpdatePayload() {
        RemoteBlockUpdatePayload payload = new RemoteBlockUpdatePayload(
                1,
                new BlockPos(-3, 11, 44),
                ModBlocks.LEVEL_ZERO_WALLPAPER_ADAPTIVE.getDefaultState().with(LevelZeroWallpaperBlock.FACE_MASK, 9));
        RemoteBlockUpdatePayload decoded = roundTrip(RemoteBlockUpdatePayload.CODEC, payload);
        assertEquals(payload, decoded, "Aller-retour identique.");
        assertEquals(9, decoded.state().get(LevelZeroWallpaperBlock.FACE_MASK), "La propriete face_mask est conservee.");
        assertTrue(roundTrip(RemoteBlockUpdatePayload.CODEC,
                new RemoteBlockUpdatePayload(0, BlockPos.ORIGIN, Blocks.AIR.getDefaultState()))
                .state().isAir(), "L'air est transporte.");
        assertEquals(RemoteBlockUpdatePayload.ID, payload.getId(), "Identifiant du payload.");
    }

    private static <T> T roundTripPacket(PacketCodec<PacketByteBuf, T> codec, T value) {
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        codec.encode(buffer, value);
        T decoded = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "Tout le tampon doit etre consomme.");
        return decoded;
    }

    @Test
    @DisplayName("La teleportation par portail transporte dimension, type, position, regard et vitesse")
    void teleportPayload() {
        PortalTeleportPayload payload = new PortalTeleportPayload(
                BackroomsConstants.LEVEL_ZERO_WORLD.getValue(),
                Identifier.of(ModConstants.MOD_ID, "backrooms_level_0_type"),
                new Vec3d(-7.05, 65.0, 30.5), -90.0f, 12.5f, new Vec3d(0.1, -0.08, 0.0));
        assertEquals(payload, roundTripPacket(PortalTeleportPayload.CODEC, payload), "Aller-retour identique.");
        assertEquals(PortalTeleportPayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("L'annonce de franchissement transporte le portail et l'etat exact du joueur")
    void crossPayload() {
        PortalCrossPayload payload = new PortalCrossPayload(
                UUID.fromString("7b0e4c1a-5d2f-4e3b-9a8c-1f2e3d4c5b6a"), new Vec3d(1.5, 2.0, -3.25), new Vec3d(0.0, 0.42, 0.28), 45.0f, -10.0f);
        assertEquals(payload, roundTripPacket(PortalCrossPayload.CODEC, payload), "Aller-retour identique.");
        assertEquals(PortalCrossPayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("Le regard du client transporte les portails vus, et pas plus que le plafond")
    void sightPayload() {
        Identifier level0 = BackroomsConstants.LEVEL_ZERO_WORLD.getValue();
        PortalSightPayload payload = new PortalSightPayload(level0, List.of(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(payload, roundTripPacket(PortalSightPayload.CODEC, payload), "Aller-retour identique, dimension comprise.");
        assertEquals(new PortalSightPayload(level0, List.of()), roundTripPacket(PortalSightPayload.CODEC, new PortalSightPayload(level0, List.of())),
                "Rien de vu.");
        assertEquals(PortalSightPayload.ID, payload.getId(), "Identifiant du payload.");
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i <= PortalSightPayload.MAX_PORTALS; i++) {
            tooMany.add(UUID.randomUUID());
        }
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer());
        PortalSightPayload.CODEC.encode(buffer, new PortalSightPayload(level0, tooMany));
        assertThrows(RuntimeException.class, () -> PortalSightPayload.CODEC.decode(buffer), "Au-dela du plafond, le paquet est refuse.");
    }

    @Test
    @DisplayName("Un paquet d'entity vanilla enveloppe survit a un aller-retour reseau avec sa dimension")
    void entityPacketPayload() {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.of(Registries.REGISTRIES));
        RemoteEntityPacketPayload payload = new RemoteEntityPacketPayload(3, new EntityVelocityUpdateS2CPacket(42, new Vec3d(0.25, -0.5, 1.0)));
        RemoteEntityPacketPayload.CODEC.encode(buffer, payload);
        RemoteEntityPacketPayload decoded = RemoteEntityPacketPayload.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "Tout le tampon doit etre consomme.");
        assertEquals(payload.dimension(), decoded.dimension(), "Dimension conservee.");
        EntityVelocityUpdateS2CPacket packet = assertInstanceOf(EntityVelocityUpdateS2CPacket.class, decoded.packet(), "Meme type de paquet.");
        assertEquals(42, packet.getEntityId(), "Identifiant d'entity conserve.");
        assertEquals(0.25, packet.getVelocityX(), 1e-3, "Vitesse conservee (vanilla l'encode en 1/8000 et la decode en bloc par tick).");
        assertEquals(RemoteEntityPacketPayload.ID, payload.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("Les actions a travers un portail transportent portail, dimension et cible")
    void interactionPayloads() {
        UUID portal = UUID.fromString("0f1e2d3c-4b5a-4968-8776-655443322110");
        Identifier dimension = BackroomsConstants.LEVEL_ZERO_WORLD.getValue();
        PortalEntityActionPayload entityAction = new PortalEntityActionPayload(portal, dimension, 1234, PortalEntityActionPayload.Action.INTERACT);
        assertEquals(entityAction, roundTripPacket(PortalEntityActionPayload.CODEC, entityAction), "Aller-retour identique.");

        BlockHitResult hit = new BlockHitResult(new Vec3d(-7.0, 2.5, 157.25), Direction.WEST, new BlockPos(-7, 2, 157), false);
        PortalBlockActionPayload blockAction = new PortalBlockActionPayload(portal, dimension, hit, PortalBlockActionPayload.Action.START_MINING);
        PortalBlockActionPayload decoded = roundTripPacket(PortalBlockActionPayload.CODEC, blockAction);
        assertEquals(portal, decoded.portal(), "Portail conserve.");
        assertEquals(dimension, decoded.dimension(), "Dimension conservee.");
        assertEquals(hit.getBlockPos(), decoded.hit().getBlockPos(), "Bloc vise conserve.");
        assertEquals(Direction.WEST, decoded.hit().getSide(), "Face visee conservee.");
        assertEquals(PortalBlockActionPayload.Action.START_MINING, decoded.action(), "Action conservee.");

        RemoteBlockBreakingPayload breaking = new RemoteBlockBreakingPayload(dimension, new BlockPos(-7, 2, 157), -1);
        assertEquals(breaking, roundTripPacket(RemoteBlockBreakingPayload.CODEC, breaking), "L'effacement des fissures (-1) survit a l'octet signe.");
    }

    @Test
    @DisplayName("Les reglages de la baguette voyagent avec leur main, au format du composant")
    void wandSettingsPayload() {
        PortalSettings chosen = new PortalSettings(3, 5, false, false, true, false, PortalExit.CEILING, 40, true);
        PortalWandSettingsPayload main = new PortalWandSettingsPayload(Hand.MAIN_HAND, chosen);
        PortalWandSettingsPayload off = new PortalWandSettingsPayload(Hand.OFF_HAND, PortalSettings.DEFAULT);
        assertEquals(main, roundTrip(PortalWandSettingsPayload.CODEC, main), "Main principale et reglages conserves.");
        assertEquals(off, roundTrip(PortalWandSettingsPayload.CODEC, off), "Autre main conservee.");
        assertEquals(PortalWandSettingsPayload.ID, main.getId(), "Identifiant du payload.");
    }

    @Test
    @DisplayName("Chaque payload porte un identifiant distinct dans l'espace de noms du mod")
    void distinctIds() {
        Set<CustomPayload.Id<?>> ids = Set.of(RemoteDimensionPayload.ID, RemoteChunkPayload.ID, RemoteChunkUnloadPayload.ID,
                RemoteBlockUpdatePayload.ID, RemoteEntityPacketPayload.ID, RemoteBlockBreakingPayload.ID, PortalTeleportPayload.ID,
                PortalCrossPayload.ID, PortalEntityActionPayload.ID, PortalBlockActionPayload.ID, PortalSightPayload.ID,
                PortalWandSettingsPayload.ID);
        assertEquals(12, ids.size(), "Douze identifiants distincts.");
        for (CustomPayload.Id<?> id : ids) {
            assertEquals(ModConstants.MOD_ID, id.id().getNamespace(), "Espace de noms du mod pour " + id.id() + ".");
        }
    }

    @Test
    @DisplayName("Chaque payload d'interaction porte son identifiant")
    void interactionPayloadIds() {
        UUID portal = UUID.randomUUID();
        assertEquals(PortalEntityActionPayload.ID,
                new PortalEntityActionPayload(portal, BackroomsConstants.LEVEL_ZERO_WORLD.getValue(), 7, PortalEntityActionPayload.Action.ATTACK).getId(),
                "Action sur entity.");
        assertEquals(PortalBlockActionPayload.ID,
                new PortalBlockActionPayload(portal, BackroomsConstants.LEVEL_ZERO_WORLD.getValue(),
                        new BlockHitResult(new Vec3d(1.5, 2.0, 1.5), Direction.UP, new BlockPos(1, 1, 1), false),
                        PortalBlockActionPayload.Action.USE).getId(),
                "Action sur bloc.");
        assertEquals(RemoteBlockBreakingPayload.ID,
                new RemoteBlockBreakingPayload(BackroomsConstants.LEVEL_ZERO_WORLD.getValue(), new BlockPos(1, 1, 1), 3).getId(),
                "Progression de minage.");
    }
}
