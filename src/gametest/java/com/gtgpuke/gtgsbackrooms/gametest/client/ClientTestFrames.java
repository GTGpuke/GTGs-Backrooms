package com.gtgpuke.gtgsbackrooms.gametest.client;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;

import com.gtgpuke.gtgsbackrooms.mixin.client.WorldRendererAccessor;

/**
 * Images rendues par le client de test : leur compte, la capture du tampon principal a la fin d'une image (apres le rendu
 * complet, avant la suivante), demandee par une etape et lue au tick suivant, et l'enregistrement de l'ecart entre
 * chaque image et la precedente (une traversee sans couture ne se voit pas dans cet ecart).
 */
public final class ClientTestFrames {

    private static final List<FrameStep> RECORDED = new ArrayList<>();
    /** Distance sous laquelle une section non construite compte comme un trou proche (le brouillard du Level 0). */
    private static final double NEAR_HOLE_DISTANCE = 48.0;
    private static int count;
    private static boolean captureRequested;
    private static @Nullable NativeImage captured;
    private static boolean recording;
    private static @Nullable NativeImage previous;
    private static long lastFrameNanos;
    private static int chunkLoads;
    private static int chunkUnloads;
    private static int remoteChunkLoads;
    private static long lastCollections;

    private ClientTestFrames() {
        throw new UnsupportedOperationException("Classe utilitaire.");
    }

    /** Compte les chunks charges et decharges par le client, image par image (appele a l'initialisation du mod de test). */
    static void registerChunkCounters() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (world == MinecraftClient.getInstance().world) {
                chunkLoads++;
            } else {
                remoteChunkLoads++;
            }
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> chunkUnloads++);
    }

    /** A la fin de chaque image du client (mixin du mod de test). */
    public static void onFrameEnd(MinecraftClient client) {
        count++;
        long now = Util.getMeasuringTimeNano();
        double frameMillis = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) / 1.0e6;
        lastFrameNanos = now;
        int loads = chunkLoads;
        int unloads = chunkUnloads;
        int remoteLoads = remoteChunkLoads;
        long collections = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            collections += Math.max(0L, collector.getCollectionCount());
        }
        int collected = (int) (collections - lastCollections);
        lastCollections = collections;
        chunkLoads = 0;
        chunkUnloads = 0;
        remoteChunkLoads = 0;
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
            NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            if (previous != null) {
                int[] holes = holes(client);
                RECORDED.add(new FrameStep(Images.meanDifference(previous, image), client.world.getRegistryKey().getValue(), frameMillis,
                        client.worldRenderer.getCompletedChunkCount(), holes[0], holes[1], holes[2], client.world.getChunkManager().getLoadedChunkCount(),
                        loads, unloads, remoteLoads, collected));
                previous.close();
            }
            previous = image;
        }
    }

    /**
     * Sections que le renderer du joueur doit dessiner (graphe et frustum) sans les avoir encore construites, et qui ont des
     * blocs (une section d'air n'a rien a montrer) : des trous, tous, puis ceux a moins de {@link #NEAR_HOLE_DISTANCE}
     * blocs de la camera.
     */
    private static int[] holes(MinecraftClient client) {
        int unbuilt = 0;
        int near = 0;
        double nearest = Double.MAX_VALUE;
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        for (ChunkBuilder.BuiltChunk section : ((WorldRendererAccessor) client.worldRenderer).backrooms$getBuiltChunks()) {
            BlockPos origin = section.getOrigin();
            WorldChunk chunk = client.world.getChunkManager().getWorldChunk(origin.getX() >> 4, origin.getZ() >> 4);
            int index = chunk == null ? -1 : chunk.getSectionIndex(origin.getY());
            boolean hasBlocks = index >= 0 && index < chunk.getSectionArray().length && !chunk.getSectionArray()[index].isEmpty();
            if (hasBlocks && section.getData() == ChunkBuilder.ChunkData.EMPTY) {
                unbuilt++;
                double squared = section.getBoundingBox().squaredMagnitude(camera);
                nearest = Math.min(nearest, squared);
                if (squared < NEAR_HOLE_DISTANCE * NEAR_HOLE_DISTANCE) {
                    near++;
                }
            }
        }
        return new int[] {unbuilt, near, unbuilt == 0 ? -1 : (int) Math.sqrt(nearest)};
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
     * Ecart d'une image a la precedente, et ce qui l'accompagne.
     *
     * @param difference ecart moyen absolu par canal, de 0 a 255
     * @param world monde du joueur pendant cette image
     * @param frameMillis temps ecoule depuis la fin de l'image precedente
     * @param builtSections sections que le renderer du joueur a construites et dessine
     * @param holes sections a dessiner que le renderer du joueur n'a pas encore construites
     * @param nearHoles celles de ces sections a moins de 48 blocs de la camera
     * @param nearestHole distance a la camera, en blocs, de la plus proche de ces sections ; -1 s'il n'y en a pas
     * @param loadedChunks chunks charges dans le monde du joueur
     * @param chunkLoads chunks charges dans le monde du joueur pendant cette image (nouveaux ou remplaces)
     * @param chunkUnloads chunks decharges par le client pendant cette image, tous mondes confondus
     * @param remoteChunkLoads chunks charges dans les mondes distants pendant cette image
     * @param collections passages du ramasse-miettes pendant cette image
     */
    public record FrameStep(double difference, Identifier world, double frameMillis, int builtSections, int holes, int nearHoles, int nearestHole,
            int loadedChunks, int chunkLoads, int chunkUnloads, int remoteChunkLoads, int collections) {
    }
}
