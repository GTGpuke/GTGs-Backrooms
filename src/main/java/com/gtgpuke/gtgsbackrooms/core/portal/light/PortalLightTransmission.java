package com.gtgpuke.gtgsbackrooms.core.portal.light;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.gtgpuke.gtgsbackrooms.core.perf.ModPerformanceMonitor;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntityRegistry;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;

/**
 * Lumiere des blocs a travers les portails qui la transmettent : chaque bloc du cadre devient une source virtuelle aussi
 * forte que la lumiere du bloc correspondant de l'autre cote, moins un niveau, comme si elle avait traverse un bloc d'air.
 *
 * <p>Les deux cotes d'une paire s'eclairent l'un l'autre : sans vraie source, chaque aller-retour coute deux niveaux, et
 * une lumiere retiree s'eteint en quelques passes. Seuls les portails ouverts et visibles laissent passer la lumiere. Le
 * meme calcul tourne pour chaque monde du serveur et pour chaque monde du client (le sien et ceux qu'il voit par les
 * portails), dont le moteur de lumiere recalcule seul ses blocs. Une source publiee mais pas encore appliquee (chunk qui
 * vient de charger) est signalee de nouveau au moteur a la passe suivante.
 */
public final class PortalLightTransmission {

    /** Intervalle entre deux passes, en ticks. */
    public static final int UPDATE_INTERVAL_TICKS = 2;
    private static final String PERF_LIGHT = "portal.light";
    /** Calcul des mondes du serveur. */
    public static final PortalLightTransmission SERVER =
            new PortalLightTransmission(PERF_LIGHT, (source, portal) -> ((ServerWorld) source).getServer().getWorld(portal.destinationDim()));

    private static final int INITIAL_CAPACITY = 16;
    /** Recul devant le plan ou se prend chaque bloc du cadre : le bloc que la face eclaire, plan au milieu ou au bord. */
    private static final double IN_FRONT = 0.25;
    private static boolean initialized;

    /** Section du moniteur de performance ou se compte une passe. */
    private final String perfSection;
    /** Monde d'arrivee d'un portail, vu depuis le monde du portail ; {@code null} s'il n'est pas disponible. */
    private final BiFunction<World, BackroomsPortalEntity, @Nullable World> worlds;
    private final Long2ByteOpenHashMap wanted = new Long2ByteOpenHashMap();
    private final BlockPos.Mutable cell = new BlockPos.Mutable();
    private final BlockPos.Mutable remoteCell = new BlockPos.Mutable();
    private long[] positions = new long[INITIAL_CAPACITY];
    private byte[] luminances = new byte[INITIAL_CAPACITY];
    private int size;
    /** Blocs du cadre des portails comptes pour la premiere fois, a signaler au moteur quelle que soit leur luminosite. */
    private long[] refreshed = new long[INITIAL_CAPACITY];
    private int refreshedSize;

    /**
     * Calcul pour un cote (serveur ou client), chacun avec ses tampons.
     *
     * @param perfSection section du moniteur de performance ou se compte une passe (constante)
     * @param worlds monde d'arrivee d'un portail, vu depuis le monde du portail : cherche par le portail, un endroit lointain du
     *     monde du joueur n'etant chez le client que dans son ailleurs
     */
    public PortalLightTransmission(String perfSection, BiFunction<World, BackroomsPortalEntity, @Nullable World> worlds) {
        this.perfSection = perfSection;
        this.worlds = worlds;
    }

    /** Branche le calcul sur le tick des mondes du serveur ; idempotent. */
    public static void init() {
        if (initialized) {
            return;
        }
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % UPDATE_INTERVAL_TICKS == 0) {
                SERVER.update(world, BackroomsPortalEntityRegistry.all());
            }
        });
        initialized = true;
    }

    /**
     * Recalcule les sources virtuelles d'un monde, les publie si elles ont change et signale au moteur de lumiere les blocs
     * dont la luminosite change ou n'est pas encore appliquee.
     *
     * @param world monde dont on recalcule les sources
     * @param portals portails a considerer ; ceux d'un autre monde sont ignores
     */
    @SuppressWarnings("try")
    public void update(World world, List<BackroomsPortalEntity> portals) {
        try (ModPerformanceMonitor.Scope ignored = ModPerformanceMonitor.scope(this.perfSection)) {
            this.wanted.clear();
            this.size = 0;
            this.refreshedSize = 0;
            for (int i = 0; i < portals.size(); i++) {
                BackroomsPortalEntity portal = portals.get(i);
                if (portal.getWorld() != world || !portal.transmitsLight() || !portal.visible() || !portal.teleportable()) {
                    continue;
                }
                World destination = this.worlds.apply(world, portal);
                if (destination != null) {
                    collect(portal, destination, portal.takeLightRefresh());
                }
            }
            VirtualLightView view = (VirtualLightView) world;
            VirtualLightSources current = view.backrooms$virtualLight();
            boolean changed = current.size() != this.size;
            for (int i = 0; i < this.size && !changed; i++) {
                changed = current.luminance(this.positions[i]) != this.wanted.get(this.positions[i]);
            }
            // Publier avant de signaler : le moteur du serveur, sur son thread, peut traiter un bloc signale tout de suite, et
            // il y lit la luminosite publiee.
            if (changed) {
                publish(view);
            }
            LightingProvider lighting = world.getChunkManager().getLightingProvider();
            // Sources retirees ou modifiees : le moteur les eteint ou les reduit.
            for (int i = 0; i < current.size(); i++) {
                long position = current.position(i);
                if (this.wanted.get(position) != current.luminanceAt(i)) {
                    lighting.checkBlock(this.cell.set(position));
                }
            }
            // Sources nouvelles, ou publiees mais pas encore appliquees ; une source modifiee l'a ete ci-dessus.
            for (int i = 0; i < this.size; i++) {
                long position = this.positions[i];
                int luminance = this.wanted.get(position);
                int known = current.luminance(position);
                if (known == 0 || known == luminance && world.getLightLevel(LightType.BLOCK, this.cell.set(position)) < luminance) {
                    lighting.checkBlock(this.cell.set(position));
                }
            }
            // Cadres des portails qui apparaissent : la lumiere sauvee avec leur chunk a pu venir d'une source eteinte depuis.
            for (int i = 0; i < this.refreshedSize; i++) {
                lighting.checkBlock(this.cell.set(this.refreshed[i]));
            }
        }
    }

    /**
     * Sources d'un portail : un bloc du cadre par bloc de sa taille, eclaire par le bloc correspondant de l'autre cote.
     * Un point du plan passe dans le plan d'arrivee, la largeur inversee, sans allocation. Chaque bloc est pris un quart de
     * bloc devant son plan : celui que la face eclaire, que le plan passe au milieu des blocs ou sur leur bord.
     *
     * @param refresh {@code true} si tout le cadre est a reverifier : un portail compte pour la premiere fois
     */
    private void collect(BackroomsPortalEntity portal, World destination, boolean refresh) {
        PortalFacing facing = portal.facingSource();
        PortalFacing exit = portal.facingDestination();
        Vec3d center = portal.getPos();
        Vec3d exitCenter = portal.destinationPos();
        int columns = Math.max(1, Math.round(portal.width()));
        int rows = Math.max(1, Math.round(portal.height()));
        for (int column = 0; column < columns; column++) {
            double w = column + 0.5 - columns / 2.0;
            for (int row = 0; row < rows; row++) {
                double h = row + 0.5 - rows / 2.0;
                this.remoteCell.set(
                        MathHelper.floor(exitCenter.x - exit.axisW.x * w + exit.axisH.x * h + exit.normal.x * IN_FRONT),
                        MathHelper.floor(exitCenter.y - exit.axisW.y * w + exit.axisH.y * h + exit.normal.y * IN_FRONT),
                        MathHelper.floor(exitCenter.z - exit.axisW.z * w + exit.axisH.z * h + exit.normal.z * IN_FRONT));
                int luminance = destination.getLightLevel(LightType.BLOCK, this.remoteCell) - 1;
                if (luminance <= 0 && !refresh) {
                    continue;
                }
                long position = BlockPos.asLong(
                        MathHelper.floor(center.x + facing.axisW.x * w + facing.axisH.x * h + facing.normal.x * IN_FRONT),
                        MathHelper.floor(center.y + facing.axisW.y * w + facing.axisH.y * h + facing.normal.y * IN_FRONT),
                        MathHelper.floor(center.z + facing.axisW.z * w + facing.axisH.z * h + facing.normal.z * IN_FRONT));
                if (refresh) {
                    refresh(position);
                }
                add(position, luminance);
            }
        }
    }

    /**
     * Garde la plus forte luminosite d'un bloc eclaire par plusieurs portails (les deux faces d'un meme cadre) ; la liste
     * garde l'ordre des positions, la table leur luminosite. Un bloc sans lumiere de l'autre cote n'est pas une source.
     */
    private void add(long position, int luminance) {
        int known = this.wanted.get(position);
        if (luminance <= known) {
            return;
        }
        if (known == 0) {
            if (this.size == this.positions.length) {
                this.positions = Arrays.copyOf(this.positions, this.size * 2);
                this.luminances = Arrays.copyOf(this.luminances, this.size * 2);
            }
            this.positions[this.size] = position;
            this.size++;
        }
        this.wanted.put(position, (byte) luminance);
    }

    /** Bloc du cadre d'un portail compte pour la premiere fois. */
    private void refresh(long position) {
        if (this.refreshedSize == this.refreshed.length) {
            this.refreshed = Arrays.copyOf(this.refreshed, this.refreshedSize * 2);
        }
        this.refreshed[this.refreshedSize] = position;
        this.refreshedSize++;
    }

    /** Publie les sources calculees ; le monde garde une copie, les tampons servent a la passe suivante. */
    private void publish(VirtualLightView view) {
        if (this.size == 0) {
            view.backrooms$setVirtualLight(VirtualLightSources.EMPTY);
            return;
        }
        for (int i = 0; i < this.size; i++) {
            this.luminances[i] = this.wanted.get(this.positions[i]);
        }
        view.backrooms$setVirtualLight(new VirtualLightSources(this.positions, this.luminances, this.size));
    }
}
