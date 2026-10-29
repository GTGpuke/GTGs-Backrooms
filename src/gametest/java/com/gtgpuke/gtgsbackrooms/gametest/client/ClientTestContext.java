package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * Ce qu'une etape d'un test du client peut toucher : le client, son serveur integre (commandes et code joues sur le
 * thread du serveur, l'appel attend leur fin) et le joueur des deux cotes.
 */
public final class ClientTestContext {

    private final MinecraftClient client;

    ClientTestContext(MinecraftClient client) {
        this.client = client;
    }

    /** Le client. */
    public MinecraftClient client() {
        return client;
    }

    /** Le joueur du client. */
    public ClientPlayerEntity player() {
        return Objects.requireNonNull(client.player, "Aucun joueur cote client.");
    }

    /** Le monde du joueur cote client. */
    public ClientWorld world() {
        return Objects.requireNonNull(client.world, "Aucun monde cote client.");
    }

    /** Le serveur integre. */
    public IntegratedServer server() {
        return Objects.requireNonNull(client.getServer(), "Aucun serveur integre.");
    }

    /** Joue {@code action} sur le thread du serveur et attend sa fin. */
    public void onServer(Consumer<IntegratedServer> action) {
        IntegratedServer server = server();
        server.submitAndJoin(() -> action.accept(server));
    }

    /** Calcule une valeur sur le thread du serveur et attend sa fin. */
    public <T> T fromServer(Function<IntegratedServer, T> function) {
        IntegratedServer server = server();
        AtomicReference<T> result = new AtomicReference<>();
        server.submitAndJoin(() -> result.set(function.apply(server)));
        return result.get();
    }

    /** Le monde serveur d'une dimension (a appeler sur le thread du serveur). */
    public static ServerWorld serverWorld(IntegratedServer server, RegistryKey<World> dimension) {
        return Objects.requireNonNull(server.getWorld(dimension), "Dimension absente du serveur : " + dimension.getValue() + ".");
    }

    /** Le joueur cote serveur (a appeler sur le thread du serveur). */
    public static ServerPlayerEntity serverPlayer(IntegratedServer server) {
        return Objects.requireNonNull(server.getPlayerManager().getPlayerList().isEmpty() ? null : server.getPlayerManager().getPlayerList().get(0),
                "Aucun joueur cote serveur.");
    }

    /** Leve {@link AssertionError} avec {@code message} si {@code condition} est fausse. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
