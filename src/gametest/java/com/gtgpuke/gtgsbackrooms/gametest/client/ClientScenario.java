package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.texture.NativeImage;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;

/**
 * Un test du client : une suite d'etapes jouees tick apres tick par {@link ClientTestRunner}, dans le monde de test.
 * Une etape agit une fois puis attend sa condition, dans une limite de ticks ; une exception ou une condition qui
 * n'arrive pas fait echouer le test, dont les nettoyages sont joues avant le suivant.
 */
public final class ClientScenario {

    /** Attente par defaut d'une condition, en ticks (dix secondes). */
    public static final int DEFAULT_TIMEOUT = 200;
    /** Ticks laisses a une image dans une attente d'images : un rendu logiciel tombe sous deux images par seconde. */
    private static final int TICKS_PER_SLOW_FRAME = 20;
    private static final Consumer<ClientTestContext> NOTHING = context -> { };
    private static final Predicate<ClientTestContext> AT_ONCE = context -> true;

    private final String name;
    private final List<Step> steps = new ArrayList<>();
    private final List<Consumer<ClientTestContext>> cleanups = new ArrayList<>();

    private ClientScenario(String name) {
        this.name = name;
    }

    /** Un test nomme, sans etape. */
    public static ClientScenario named(String name) {
        return new ClientScenario(name);
    }

    /** Une action jouee une fois. */
    public ClientScenario run(String what, Consumer<ClientTestContext> action) {
        steps.add(new Step(what, action, AT_ONCE, DEFAULT_TIMEOUT));
        return this;
    }

    /** Une verification jouee une fois : elle leve {@link AssertionError} si ce qu'elle attend n'est pas la. */
    public ClientScenario check(String what, Consumer<ClientTestContext> assertion) {
        return run(what, assertion);
    }

    /** Attend une condition, au plus {@code timeoutTicks} ticks. */
    public ClientScenario await(String what, Predicate<ClientTestContext> condition, int timeoutTicks) {
        steps.add(new Step(what, NOTHING, condition, timeoutTicks));
        return this;
    }

    /** Attend une condition, au plus {@link #DEFAULT_TIMEOUT} ticks. */
    public ClientScenario await(String what, Predicate<ClientTestContext> condition) {
        return await(what, condition, DEFAULT_TIMEOUT);
    }

    /**
     * Attend que le client ait rendu {@code count} images de plus : jusqu'a vingt ticks par image, pour un rendu logiciel
     * lent (la CI dessine sans processeur graphique).
     */
    public ClientScenario frames(int count) {
        int[] target = new int[1];
        steps.add(new Step(count + " image(s)", context -> target[0] = ClientTestFrames.count() + count,
                context -> ClientTestFrames.count() >= target[0], Math.max(DEFAULT_TIMEOUT, TICKS_PER_SLOW_FRAME * count)));
        return this;
    }

    /** Attend {@code count} ticks du client. */
    public ClientScenario ticks(int count) {
        int[] left = new int[1];
        steps.add(new Step(count + " tick(s)", context -> left[0] = count, context -> left[0]-- <= 0, count + DEFAULT_TIMEOUT));
        return this;
    }

    /**
     * Capture l'image suivante du client (tampon principal, HUD masque) et la passe a {@code reader}, qui peut lever
     * {@link AssertionError} ; l'image est liberee ensuite. Une image qui fait echouer le test est gardee dans
     * {@code captures/} du dossier du client de test, pour la voir.
     */
    public ClientScenario capture(String what, Consumer<NativeImage> reader) {
        int index = steps.size();
        steps.add(new Step(what, context -> ClientTestFrames.requestCapture(), context -> {
            NativeImage image = ClientTestFrames.takeCapture();
            if (image == null) {
                return false;
            }
            try {
                reader.accept(image);
            } catch (AssertionError failure) {
                keep(image, name + "_" + index + ".png");
                throw failure;
            } finally {
                image.close();
            }
            return true;
        }, DEFAULT_TIMEOUT));
        return this;
    }

    private static void keep(NativeImage image, String file) {
        try {
            Path folder = Path.of("captures");
            Files.createDirectories(folder);
            image.writeTo(folder.resolve(file));
        } catch (IOException exception) {
            ModConstants.LOGGER.error("[Test client] Capture impossible a garder : {}.", file, exception);
        }
    }

    /** Nettoyage joue apres le test, qu'il reussisse ou non. */
    public ClientScenario cleanup(Consumer<ClientTestContext> action) {
        cleanups.add(action);
        return this;
    }

    /** Nom du test. */
    public String name() {
        return name;
    }

    List<Step> steps() {
        return steps;
    }

    List<Consumer<ClientTestContext>> cleanups() {
        return cleanups;
    }

    /** Une etape : son action, puis la condition qui la termine, dans une limite de ticks. */
    record Step(String what, Consumer<ClientTestContext> action, Predicate<ClientTestContext> until, int timeoutTicks) {
    }
}
