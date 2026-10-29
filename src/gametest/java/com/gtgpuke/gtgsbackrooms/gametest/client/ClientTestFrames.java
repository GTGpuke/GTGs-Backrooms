package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;

/**
 * Images rendues par le client de test : leur compte, la capture du tampon principal a la fin d'une image (apres le rendu
 * complet, avant la suivante), demandee par une etape et lue au tick suivant, et l'enregistrement de l'ecart entre
 * chaque image et la precedente (une traversee sans couture ne se voit pas dans cet ecart).
 */
public final class ClientTestFrames {

    private static final List<FrameStep> RECORDED = new ArrayList<>();
    private static int count;
    private static boolean captureRequested;
    private static @Nullable NativeImage captured;
    private static boolean recording;
    private static @Nullable NativeImage previous;

    private ClientTestFrames() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** A la fin de chaque image du client (mixin du mod de test). */
    public static void onFrameEnd(MinecraftClient client) {
        count++;
        if (client.world == null) {
            return;
        }
        if (captureRequested) {
            captureRequested = false;
            if (captured != null) {
                captured.close();
            }
            captured = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
        }
        if (recording) {
            NativeImage now = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            if (previous != null) {
                RECORDED.add(new FrameStep(Images.meanDifference(previous, now), client.world.getRegistryKey().getValue()));
                previous.close();
            }
            previous = now;
        }
    }

    /** Nombre d'images rendues depuis le lancement. */
    public static int count() {
        return count;
    }

    static void requestCapture() {
        captureRequested = true;
    }

    /** L'image capturee, a liberer par l'appelant, ou {@code null} si elle n'est pas encore prise. */
    static @Nullable NativeImage takeCapture() {
        NativeImage image = captured;
        captured = null;
        return image;
    }

    /** Commence l'enregistrement des ecarts d'une image a la suivante. */
    public static void startRecording() {
        RECORDED.clear();
        recording = true;
    }

    /** Arrete l'enregistrement et rend les ecarts mesures, dans l'ordre des images. */
    public static List<FrameStep> stopRecording() {
        recording = false;
        if (previous != null) {
            previous.close();
            previous = null;
        }
        return new ArrayList<>(RECORDED);
    }

    /**
     * Ecart d'une image a la precedente.
     *
     * @param difference ecart moyen absolu par canal, de 0 a 255
     * @param world monde du joueur pendant cette image
     */
    public record FrameStep(double difference, Identifier world) {
    }
}
