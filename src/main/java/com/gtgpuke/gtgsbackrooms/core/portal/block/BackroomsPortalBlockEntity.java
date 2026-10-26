package com.gtgpuke.gtgsbackrooms.core.portal.block;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.ModConstants;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.BackroomsPortalEntity;
import com.gtgpuke.gtgsbackrooms.core.portal.entity.PortalFacing;
import com.gtgpuke.gtgsbackrooms.core.portal.sync.PortalSight;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlockEntities;
import com.gtgpuke.gtgsbackrooms.core.registry.ModDataComponents;

/**
 * Block entity du bloc portail : garde ses reglages, trouve sa destination, pose ses portails et les retire avec lui.
 *
 * <p>Un bloc pose hors du Level 0 mene au Level 0 ; pose dans le Level 0, il ramene a la surface. A deux faces (reglage
 * par defaut), le portail se voit et se traverse des deux cotes, comme un trou dans l'espace : chaque cote tient deux
 * portails dos a dos dans le meme plan, la face avant et la face arriere, et chaque face mene a la face correspondante
 * de l'autre cote. A une face, seule la face avant existe ; aller simple, rien n'est pose a l'arrivee. A sortie fugace,
 * la sortie (le retour) ne tient que sous le regard des joueurs : perdue de vue, elle disparait pour de bon. Tous ses
 * portails connaissent le bloc qui les possede : le registre des portails retire ceux dont le bloc n'existe plus.
 */
public final class BackroomsPortalBlockEntity extends BlockEntity {

    /** Delai avant de considerer un portail d'entree perdu, le temps que le chunk charge ses entities. */
    public static final int PORTAL_GRACE_TICKS = 40;
    /** Intervalle entre deux tentatives de resolution de la destination ; la premiere a lieu au premier tick. */
    public static final int TARGET_RETRY_INTERVAL_TICKS = 100;
    /** Intervalle de verification des portails de retour, dans l'autre dimension. */
    public static final int RETURN_CHECK_INTERVAL_TICKS = 200;
    /** Verifications consecutives sans portail de retour avant de le reposer. */
    public static final int RETURN_MISSING_CHECKS = 2;
    /** Indice de la face avant, celle qui regarde le joueur qui a pose le bloc. */
    public static final int FRONT = 0;
    /** Indice de la face arriere. */
    public static final int BACK = 1;
    private static final int FACES = 2;
    /** Duree du ticket qui charge l'arrivee, renouvele a chaque tick tant que le retour attend son chunk. */
    private static final int ARRIVAL_TICKET_TICKS = 100;
    /** Charge le chunk d'arrivee sans bloquer le serveur : le retour y est pose des qu'il est la. */
    private static final ChunkTicketType<ChunkPos> ARRIVAL_TICKET =
            ChunkTicketType.create("gtgs_backrooms_portal_arrival", Comparator.comparingLong(ChunkPos::toLong), ARRIVAL_TICKET_TICKS);
    /**
     * Charge, un apres l'autre, les chunks que la recherche d'arrivee lit, sans bloquer le serveur : la recherche s'arrete
     * au premier chunk qui n'est pas entier, le bloc le demande et rappelle a chaque tick jusqu'a ce qu'il le soit. Sans
     * lui, la recherche genererait jusqu'a seize colonnes autour du point de depart sur le thread serveur, d'un tiers de
     * seconde a trois secondes de gel pour tous les joueurs (bilan de performance, PERF 3.2). Un chunk a la fois : ceux que
     * la recherche synchrone lirait, ni plus ni moins.
     */
    private static final ChunkTicketType<ChunkPos> SEARCH_TICKET =
            ChunkTicketType.create("gtgs_backrooms_portal_search", Comparator.comparingLong(ChunkPos::toLong), ARRIVAL_TICKET_TICKS);

    /** Portails actives par le point d'entree du mod ; desactives, le bloc reste inerte, sans portail. */
    private static boolean portalsEnabled;

    private static final String NBT_WIDTH = "Width";
    private static final String NBT_HEIGHT = "Height";
    private static final String NBT_TRANSMITS_LIGHT = "TransmitsLight";
    private static final String NBT_DOUBLE_SIDED = "DoubleSided";
    private static final String NBT_ONE_WAY = "OneWay";
    private static final String NBT_FLEETING_EXIT = "FleetingExit";
    private static final String NBT_EXIT = "Exit";
    private static final String NBT_EXIT_SCATTER = "ExitScatter";
    private static final String NBT_FLUSH = "Flush";
    private static final String NBT_UPSIDE_DOWN = "UpsideDown";
    private static final String NBT_PLACEMENT = "Placement";
    private static final String NBT_DESTINATION = "Destination";
    private static final String NBT_MARK = "Mark";
    private static final String NBT_EXIT_LOST = "ExitLost";
    private static final String[] NBT_ENTRY_PORTALS = {"EntryFront", "EntryBack"};
    private static final String[] NBT_RETURN_PORTALS = {"ReturnFront", "ReturnBack"};
    private static final String NBT_DESTINATION_DIM = "DestDim";
    private static final String NBT_DESTINATION_X = "DestX";
    private static final String NBT_DESTINATION_Y = "DestY";
    private static final String NBT_DESTINATION_Z = "DestZ";
    private static final String NBT_DESTINATION_FACING = "DestFacing";
    /** Intervalle, en ticks, entre deux regards sur une sortie fugace. */
    private static final int EXIT_SIGHT_INTERVAL_TICKS = 4;
    /**
     * Ticks pendant lesquels plus personne ne doit voir une sortie fugace pour qu'elle disparaisse : une image isolee sans
     * elle, pendant une traversee, ne la perd pas (le client rapporte ce qui change au plus tous les quatre ticks), un
     * regard detourne une demi-seconde si. Juste apres une arrivee par elle, le joueur a en plus le temps de se retourner
     * (voir {@link PortalSight}). Le client cache la sortie a son joueur apres le meme delai.
     */
    public static final int EXIT_UNSEEN_TICKS = 10;

    private PortalSettings settings = PortalSettings.DEFAULT;
    /** Cadre d'entree en cache, lu a chaque tick : recalcule quand l'etat du bloc (tourne) ou les reglages changent. */
    private @Nullable Vec3d entryCenter;
    private @Nullable PortalFacing entryFacing;
    private @Nullable BlockState entryState;
    private @Nullable PortalSettings entrySettings;
    private final @Nullable UUID[] entryPortals = new UUID[FACES];
    private final @Nullable UUID[] returnPortals = new UUID[FACES];
    private final int[] returnMissingChecks = new int[FACES];
    /**
     * Retour a poser au prochain tick : il attend son chunk d'arrivee, demande sans bloquer, ou une copie n'a pas encore les
     * siens. Perdu a la sauvegarde, les verifications le reposent.
     */
    private final boolean[] returnAwaitingChunk = new boolean[FACES];
    private @Nullable RegistryKey<World> destinationDim;
    private @Nullable Vec3d destinationPos;
    private @Nullable PortalFacing destinationFacing;
    /** Sortie fugace perdue de vue : plus de retour jusqu'a ce qu'un joueur repasse l'entree, ou jusqu'a une nouvelle destination. */
    private boolean exitLost;
    /**
     * Sortie fugace deja vue (ou par laquelle un joueur est ressorti) depuis la pose de ses retours : avant, personne ne peut la
     * perdre de vue, elle attend. Oublie a la sauvegarde : relue, elle attend de nouveau un regard.
     */
    private boolean exitSeen;
    /** Heure du monde d'arrivee depuis laquelle personne ne voit la sortie fugace ; negative tant qu'on la voit. */
    private long exitUnseenSince = -1L;
    /** Ce bloc, dimension comprise : la sortie par laquelle un joueur arrive se reconnait a son bloc proprietaire. */
    private @Nullable GlobalPos exitOwner;
    private int ticksSinceLoad;
    /** Un chunk de la recherche d'arrivee se charge : un essai par tick, jusqu'a ce qu'elle aboutisse. */
    private boolean searchLoading;
    /**
     * Graine du tirage des X/Z de l'arrivee d'une recherche en cours, la meme d'un tick a l'autre : la recherche synchrone
     * ne tire qu'une fois, celle-ci reprend chaque tick avec le meme tirage. Tiree au premier essai, oubliee a la resolution.
     */
    private long searchSeed;
    private boolean searchSeeded;

    /** Block entity du bloc portail, creee par le bloc a la pose ou au chargement. */
    public BackroomsPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BACKROOMS_PORTAL, pos, state);
    }

    /** Active ou desactive les portails des blocs portail (point d'entree du mod, tests). */
    public static void setPortalsEnabled(boolean enabled) {
        portalsEnabled = enabled;
    }

    /** Reglages du portail : taille du cadre, lumiere, faces, retour, sortie fugace, sens de sortie, tirage au sort, plan et arrivee. */
    public PortalSettings settings() {
        return this.settings;
    }

    /**
     * Fixe la destination du portail et, dans un monde serveur, pose aussitot ses portails (une ou deux faces, avec ou sans
     * retour) ; ceux d'une destination precedente sont retires.
     *
     * @param destinationDim dimension d'arrivee
     * @param destinationPos centre du cadre d'arrivee
     * @param destinationFacing orientation de la face avant du cadre d'arrivee, normale vers sa sortie
     */
    public void configureTarget(RegistryKey<World> destinationDim, Vec3d destinationPos, PortalFacing destinationFacing) {
        discardPortals();
        this.exitLost = false;
        this.destinationDim = destinationDim;
        this.destinationPos = destinationPos;
        this.destinationFacing = destinationFacing;
        this.markDirty();
        if (this.world instanceof ServerWorld serverWorld) {
            for (int face = 0; face < FACES; face++) {
                if (hasFace(face)) {
                    syncEntryPortal(serverWorld, face);
                    if (!this.settings.oneWay()) {
                        spawnReturnPortal(serverWorld, face);
                    }
                }
            }
        }
    }

    /**
     * Tick serveur, portails actives : resout la destination si besoin, garde les portails d'entree conformes au bloc et a
     * ses reglages, et veille sur les retours. Une face que les reglages n'ont pas, un retour d'un portail aller simple ou
     * celui d'une sortie fugace perdue de vue, est retire.
     */
    public void serverTick() {
        if (!portalsEnabled || !(this.world instanceof ServerWorld serverWorld)) {
            return;
        }
        this.ticksSinceLoad++;
        if (!isConfigured()) {
            // Tant qu'un chunk de la recherche se charge, un essai par tick ; sinon un essai periodique.
            if (this.searchLoading || this.ticksSinceLoad % TARGET_RETRY_INTERVAL_TICKS == 1) {
                resolveTarget(serverWorld);
            }
            return;
        }
        boolean checkReturns = this.ticksSinceLoad % RETURN_CHECK_INTERVAL_TICKS == 0;
        for (int face = 0; face < FACES; face++) {
            if (!hasFace(face)) {
                discardEntryPortal(serverWorld, face);
                discardReturnPortal(serverWorld, face);
                continue;
            }
            syncEntryPortal(serverWorld, face);
            if (noReturn()) {
                discardReturnPortal(serverWorld, face);
            } else if (this.returnAwaitingChunk[face]) {
                spawnReturnPortal(serverWorld, face);
            } else {
                syncReturnPortal(serverWorld, face);
                if (checkReturns) {
                    checkReturnPortal(serverWorld, face);
                }
            }
        }
        if (this.settings.fleetingExit() && !noReturn() && this.ticksSinceLoad % EXIT_SIGHT_INTERVAL_TICKS == 0) {
            ServerWorld destination = serverWorld.getServer().getWorld(this.destinationDim);
            if (destination != null) {
                watchExit(serverWorld, destination);
            }
        }
    }

    /** {@code true} si le portail n'a rien a l'arrivee : aller simple, ou sortie fugace perdue de vue. */
    private boolean noReturn() {
        return this.settings.oneWay() || (this.settings.fleetingExit() && this.exitLost);
    }

    /**
     * Veille sur la sortie fugace, sur le seul regard des joueurs, ou qu'ils soient : tant que personne ne l'a vue (ni n'est
     * ressorti par elle), elle attend ; ensuite, plus personne ne la regardant {@value #EXIT_UNSEEN_TICKS} ticks d'affilee, elle
     * disparait.
     */
    private void watchExit(ServerWorld world, ServerWorld destination) {
        if (someoneSeesExit(destination)) {
            this.exitSeen = true;
            this.exitUnseenSince = -1L;
            return;
        }
        if (!this.exitSeen) {
            return;
        }
        long now = destination.getTime();
        if (this.exitUnseenSince < 0L) {
            this.exitUnseenSince = now;
        } else if (now - this.exitUnseenSince >= EXIT_UNSEEN_TICKS) {
            loseExit(world);
        }
    }

    /**
     * {@code true} si un joueur du monde d'arrivee voit la sortie fugace, d'apres ce que rapporte son client
     * ({@link PortalSight}, troisieme personne comprise), ou vient de ressortir par elle. La distance ne compte pas : un client qui
     * ne rapporte rien ne la voit pas. Un spectateur n'agit pas sur le monde : il ne la garde pas.
     */
    private boolean someoneSeesExit(ServerWorld destination) {
        if (this.exitOwner == null) {
            this.exitOwner = GlobalPos.create(this.world.getRegistryKey(), this.pos);
        }
        List<ServerPlayerEntity> players = destination.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            if (!player.isSpectator() && PortalSight.seesAny(player, this.returnPortals, this.exitOwner)) {
                return true;
            }
        }
        return false;
    }

    /** La sortie fugace n'a plus personne pour la voir : ses retours disparaissent, jusqu'au prochain joueur passe par l'entree. */
    private void loseExit(ServerWorld world) {
        this.exitLost = true;
        for (int face = 0; face < FACES; face++) {
            discardReturnPortal(world, face);
        }
        this.markDirty();
        ModConstants.LOGGER.debug("[Portal] Sortie fugace du bloc portail {} perdue de vue : elle disparait.", this.pos);
    }

    /**
     * Un joueur vient de passer ce portail. Passe par l'entree d'un bloc dont la sortie fugace a ete perdue de vue, il la
     * rouvre : ses retours reviennent aussitot derriere lui, neufs, et ne tiennent de nouveau que sous le regard. Un objet,
     * un mob ou un spectateur ne la rouvre pas.
     *
     * @param crossed portail passe ; une entree est dans le monde de son bloc, un retour ailleurs ou sous un autre identifiant
     */
    public static void onPlayerCrossed(BackroomsPortalEntity crossed) {
        GlobalPos owner = crossed.owner();
        if (owner == null) {
            return;
        }
        // Le passage se decide cote serveur : le portail est dans un monde serveur. Un passage est rare : l'Optional et la
        // lambda qui suivent sont admis.
        ServerWorld world = (ServerWorld) crossed.getWorld();
        world.getBlockEntity(owner.pos(), ModBlockEntities.BACKROOMS_PORTAL).ifPresent(block -> block.reopenExit(world, crossed.getUuid()));
    }

    /**
     * Rouvre la sortie fugace perdue de vue si le portail passe est l'une de ses entrees. Les retours sont poses aussitot :
     * le joueur parti, rien ne dit que ce bloc tiquera encore.
     */
    private void reopenExit(ServerWorld world, UUID crossed) {
        if (!isEntry(crossed) || !this.exitLost) {
            return;
        }
        this.exitLost = false;
        this.markDirty();
        for (int face = 0; face < FACES; face++) {
            if (hasFace(face)) {
                spawnReturnPortal(world, face);
            }
        }
        ModConstants.LOGGER.debug("[Portal] Sortie fugace du bloc portail {} rouverte par un joueur passe par l'entree.", this.pos);
    }

    /** {@code true} si ce portail est l'une des entrees de ce bloc. */
    private boolean isEntry(UUID portal) {
        for (int face = 0; face < FACES; face++) {
            if (portal.equals(this.entryPortals[face])) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code true} si les reglages donnent cette face au portail : toujours la face avant, l'arriere s'il a deux faces et
     * qu'aucun de ses cadres n'est couche. La face arriere d'un cadre au sol se prend depuis le sol, celle d'un cadre au
     * plafond ressort dans le plafond : un portail couche, a l'entree ou a l'arrivee, n'a que sa face avant.
     */
    private boolean hasFace(int face) {
        return face == FRONT || this.settings.doubleSided() && !lying();
    }

    /**
     * {@code true} si le cadre d'entree ou celui d'arrivee est couche, au sol ou au plafond. Lu seulement par un bloc qui a
     * sa destination : on ne pose, ne garde ni ne rouvre de portail sans elle.
     */
    private boolean lying() {
        return entryFacing().normal.y != 0.0 || this.destinationFacing.normal.y != 0.0;
    }

    /**
     * {@code true} si ce bloc a pose ce portail et le tient encore pour l'un des siens ; un portail qui se reclame de lui
     * sans y figurer (face ou retour retire par les reglages, dans un chunk alors decharge) n'a plus de raison d'etre.
     */
    public boolean owns(UUID portal) {
        for (int face = 0; face < FACES; face++) {
            if (portal.equals(this.entryPortals[face]) || portal.equals(this.returnPortals[face])) {
                return true;
            }
        }
        return false;
    }

    /** Retire tous ses portails ; un portail dont le chunk n'est pas charge se retirera seul plus tard. */
    public void discardPortals() {
        if (!(this.world instanceof ServerWorld serverWorld)) {
            return;
        }
        ServerWorld destination = this.destinationDim == null ? null : serverWorld.getServer().getWorld(this.destinationDim);
        for (int face = 0; face < FACES; face++) {
            discard(serverWorld, this.entryPortals[face]);
            if (destination != null) {
                discard(destination, this.returnPortals[face]);
            }
            this.entryPortals[face] = null;
            this.returnPortals[face] = null;
            this.returnAwaitingChunk[face] = false;
        }
        this.markDirty();
    }

    /** {@code true} des que le portail connait sa destination. */
    public boolean isConfigured() {
        return this.destinationDim != null && this.destinationPos != null && this.destinationFacing != null;
    }

    /** Portail d'entree d'une face ({@link #FRONT} ou {@link #BACK}), ou {@code null} tant qu'il n'est pas pose. */
    public @Nullable UUID entryPortal(int face) {
        return this.entryPortals[face];
    }

    /** Portail de retour d'une face, ou {@code null} s'il n'a pas pu etre pose. */
    public @Nullable UUID returnPortal(int face) {
        return this.returnPortals[face];
    }

    /** Centre du cadre d'entree : au milieu du bloc ancre, sur la taille reglee. */
    public Vec3d entryCenter() {
        refreshEntryFrame();
        return this.entryCenter;
    }

    /** Orientation de la face avant du cadre d'entree, normale vers le joueur qui a pose le bloc, ou vers le haut ou le bas (couche). */
    public PortalFacing entryFacing() {
        refreshEntryFrame();
        return this.entryFacing;
    }

    /** Recalcule le cadre d'entree si le bloc a tourne ou si ses reglages ont change depuis le dernier calcul. */
    private void refreshEntryFrame() {
        BlockState state = getCachedState();
        if (state != this.entryState || this.settings != this.entrySettings) {
            this.entryFacing = PortalFacing.fromDirection(state.get(BackroomsPortalBlock.FACING));
            this.entryCenter = PortalArea.center(this.pos, this.entryFacing, this.settings);
            this.entryState = state;
            this.entrySettings = this.settings;
        }
    }

    /**
     * Resout la destination chunk par chunk : la recherche lit les chunks charges et s'arrete au premier absent, que le bloc
     * demande par un ticket avant de rappeler au tick suivant, avec le meme tirage. L'arrivee ne depend pas de l'ordre
     * d'arrivee des chunks : seul le moment change, quelques ticks par chunk a generer vers une zone jamais visitee (vanilla
     * le genere sur ses threads de travail), au lieu d'un gel du serveur. Entre-temps, le cadre n'est pas encore un portail. Chaque essai
     * alloue sa recherche (tirage, resultat, chunk demande) : un essai par tick le temps de cette attente seulement, apres
     * la pose ou le chargement du bloc.
     */
    private void resolveTarget(ServerWorld world) {
        if (!this.searchSeeded) {
            this.searchSeed = world.getRandom().nextLong();
            this.searchSeeded = true;
        }
        PortalTargets.Search search = PortalTargets.resolve(world.getServer(), world.getRegistryKey(), this.pos, entryFacing(), this.settings,
                Random.create(this.searchSeed));
        if (search.waits()) {
            // Une recherche qui attend un chunk a trouve son monde : sans lui, elle ne rend rien et n'attend pas.
            ChunkPos missing = search.missingChunk();
            world.getServer().getWorld(PortalTargets.searchDimension(world.getRegistryKey(), this.settings)).getChunkManager()
                    .addTicket(SEARCH_TICKET, missing, 0, missing);
            this.searchLoading = true;
            return;
        }
        this.searchLoading = false;
        this.searchSeeded = false;
        PortalTargets.Target target = search.target();
        if (target == null) {
            ModConstants.LOGGER.debug("[Portal] Bloc portail {} sans destination pour l'instant.", this.pos);
            return;
        }
        configureTarget(target.dimension(), target.position(), target.facing());
    }

    /** {@code true} tant que le bloc attend qu'un chunk de sa recherche d'arrivee se charge. */
    public boolean isSearchLoading() {
        return this.searchLoading;
    }

    /** Orientation d'une face : la face avant suit l'orientation donnee, la face arriere regarde a l'oppose. */
    private static PortalFacing faceFacing(PortalFacing front, int face) {
        return face == FRONT ? front : front.opposite();
    }

    /**
     * Orientation d'une face du cadre d'arrivee pour ses portails : retournee si les reglages veulent une arrivee tete en
     * bas. Le cadre lui-meme, centre et pied, garde l'orientation droite ({@code destinationFacing}).
     */
    private PortalFacing arrivalFacing(int face) {
        PortalFacing facing = faceFacing(this.destinationFacing, face);
        return this.settings.upsideDown() ? facing.flipped() : facing;
    }

    /**
     * Le portail d'entree d'une face existe tant que le bloc connait sa destination. Un portail qui ne remplit plus le
     * cadre (bloc tourne, reglages changes) est remplace.
     */
    private void syncEntryPortal(ServerWorld world, int face) {
        UUID uuid = this.entryPortals[face];
        Entity entity = uuid == null ? null : world.getEntity(uuid);
        if (entity instanceof BackroomsPortalEntity portal) {
            if (matches(portal, entryCenter(), faceFacing(entryFacing(), face), arrivalFacing(face),
                    this.destinationDim, this.destinationPos, false)) {
                return;
            }
            portal.discard();
        } else if (uuid != null && this.ticksSinceLoad < PORTAL_GRACE_TICKS) {
            // Le chunk vient de charger : ses entities arrivent, on laisse au portail le temps d'apparaitre.
            return;
        }
        BackroomsPortalEntity portal = portal(world, entryCenter(), faceFacing(entryFacing(), face), arrivalFacing(face))
                .destinationDim(this.destinationDim)
                .destinationPos(this.destinationPos)
                .build();
        world.spawnEntity(portal);
        this.entryPortals[face] = portal.getUuid();
        this.markDirty();
    }

    /** Le portail de retour d'une face mene toujours au cadre d'entree, dans son orientation et a sa taille ; sinon il est repose. */
    private void syncReturnPortal(ServerWorld world, int face) {
        ServerWorld destination = world.getServer().getWorld(this.destinationDim);
        UUID uuid = this.returnPortals[face];
        Entity entity = destination == null || uuid == null ? null : destination.getEntity(uuid);
        if (entity instanceof BackroomsPortalEntity portal && !matches(portal, this.destinationPos,
                arrivalFacing(face), faceFacing(entryFacing(), face), world.getRegistryKey(), entryCenter(),
                this.settings.fleetingExit())) {
            portal.discard();
            spawnReturnPortal(world, face);
        }
    }

    /**
     * {@code true} si le portail est a sa place, dans ses orientations, vers sa destination, a la largeur et avec la lumiere
     * reglees, sortie fugace ou non. La hauteur se lit dans les centres : changee, elle deplace le cadre d'entree et la
     * destination des retours.
     */
    private boolean matches(
            BackroomsPortalEntity portal,
            Vec3d center,
            PortalFacing facing,
            PortalFacing exitFacing,
            RegistryKey<World> exitDimension,
            Vec3d exitCenter,
            boolean fleeting) {
        return portal.getPos().equals(center)
                && portal.facingSource() == facing
                && portal.facingDestination() == exitFacing
                && portal.destinationDim().equals(exitDimension)
                && portal.destinationPos().equals(exitCenter)
                && portal.width() == this.settings.width()
                && portal.transmitsLight() == this.settings.transmitsLight()
                && portal.fleeting() == fleeting;
    }

    /**
     * Pose le retour d'une face. Un chunk d'arrivee pas encore charge est demande par un ticket, sans bloquer le serveur
     * (une arrivee jamais generee, dans le Level 0 surtout, le figerait plus d'une seconde) : le retour est pose a un
     * prochain tick, des que le chunk est la, avant que quiconque puisse y arriver.
     */
    private void spawnReturnPortal(ServerWorld world, int face) {
        ServerWorld destination = world.getServer().getWorld(this.destinationDim);
        if (destination == null) {
            return;
        }
        int chunkX = ChunkSectionPos.getSectionCoord(this.destinationPos.x);
        int chunkZ = ChunkSectionPos.getSectionCoord(this.destinationPos.z);
        if (!destination.isChunkLoaded(chunkX, chunkZ)) {
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            destination.getChunkManager().addTicket(ARRIVAL_TICKET, chunk, 0, chunk);
            this.returnAwaitingChunk[face] = true;
            return;
        }
        this.returnAwaitingChunk[face] = false;
        BackroomsPortalEntity portal = portal(destination, this.destinationPos, arrivalFacing(face), faceFacing(entryFacing(), face))
                .destinationDim(world.getRegistryKey())
                .destinationPos(entryCenter())
                .fleeting(this.settings.fleetingExit())
                .build();
        destination.spawnEntity(portal);
        this.returnPortals[face] = portal.getUuid();
        this.returnMissingChecks[face] = 0;
        // Un retour neuf n'a encore ete vu par personne : il attend un regard.
        this.exitSeen = false;
        this.exitUnseenSince = -1L;
        this.markDirty();
    }

    /** Portail de ce bloc, a sa taille, avec sa lumiere, possede par lui ; il reste a lui donner sa destination. */
    private BackroomsPortalEntity.Builder portal(World world, Vec3d center, PortalFacing facing, PortalFacing exitFacing) {
        return BackroomsPortalEntity.builder(world)
                .position(center)
                .facingSource(facing)
                .facingDestination(exitFacing)
                .size(this.settings.width(), this.settings.height())
                .transmitsLight(this.settings.transmitsLight())
                .owner(this.world.getRegistryKey(), this.pos);
    }

    private void checkReturnPortal(ServerWorld world, int face) {
        ServerWorld destination = world.getServer().getWorld(this.destinationDim);
        if (destination == null) {
            return;
        }
        UUID uuid = this.returnPortals[face];
        if (uuid != null && destination.getEntity(uuid) != null) {
            this.returnMissingChecks[face] = 0;
            return;
        }
        // Sans chunk d'arrivee actif, impossible de savoir si le portail manque vraiment.
        if (!destination.shouldTickEntity(BlockPos.ofFloored(this.destinationPos))) {
            return;
        }
        if (++this.returnMissingChecks[face] >= RETURN_MISSING_CHECKS) {
            spawnReturnPortal(world, face);
        }
    }

    private void discardEntryPortal(ServerWorld world, int face) {
        if (this.entryPortals[face] != null) {
            discard(world, this.entryPortals[face]);
            this.entryPortals[face] = null;
            this.markDirty();
        }
    }

    /** Retire le retour d'une face ; dans un chunk decharge, il se retirera seul, son bloc ne le tenant plus pour sien. */
    private void discardReturnPortal(ServerWorld world, int face) {
        this.returnAwaitingChunk[face] = false;
        if (this.returnPortals[face] != null) {
            ServerWorld destination = world.getServer().getWorld(this.destinationDim);
            if (destination != null) {
                discard(destination, this.returnPortals[face]);
            }
            this.returnPortals[face] = null;
            this.markDirty();
        }
    }

    private static void discard(ServerWorld world, @Nullable UUID uuid) {
        if (uuid != null) {
            Entity portal = world.getEntity(uuid);
            if (portal != null) {
                portal.discard();
            }
        }
    }

    /** Reglages portes par la baguette qui pose le bloc. */
    @Override
    protected void readComponents(BlockEntity.ComponentsAccess components) {
        super.readComponents(components);
        this.settings = components.getOrDefault(ModDataComponents.PORTAL_SETTINGS, PortalSettings.DEFAULT);
    }

    /** Les clients ne recoivent que les reglages : la baguette, accroupi sur un portail, les copie de chez eux. */
    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        writeSettings(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    /** Reglages du portail, sous les cles de sa sauvegarde. */
    private void writeSettings(NbtCompound nbt) {
        nbt.putInt(NBT_WIDTH, this.settings.width());
        nbt.putInt(NBT_HEIGHT, this.settings.height());
        nbt.putBoolean(NBT_TRANSMITS_LIGHT, this.settings.transmitsLight());
        nbt.putBoolean(NBT_DOUBLE_SIDED, this.settings.doubleSided());
        nbt.putBoolean(NBT_ONE_WAY, this.settings.oneWay());
        nbt.putBoolean(NBT_FLEETING_EXIT, this.settings.fleetingExit());
        nbt.putString(NBT_EXIT, this.settings.exit().asString());
        nbt.putInt(NBT_EXIT_SCATTER, this.settings.exitScatter());
        nbt.putBoolean(NBT_FLUSH, this.settings.flush());
        nbt.putBoolean(NBT_UPSIDE_DOWN, this.settings.upsideDown());
        nbt.putString(NBT_PLACEMENT, this.settings.placement().asString());
        this.settings.destination().ifPresent(destination -> nbt.putString(NBT_DESTINATION, destination.getValue().toString()));
        this.settings.mark().flatMap(mark -> PortalMark.CODEC.encodeStart(NbtOps.INSTANCE, mark).result())
                .ifPresent(mark -> nbt.put(NBT_MARK, mark));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        PortalSettings previous = this.settings;
        // Relu dans un monde sans destination jusque-la : la copie d'un autre bloc (clone, structure posee). Ses portails sont
        // ceux de l'original, qu'elle retirerait en posant les siens : elle n'en garde aucun. Au chargement de son chunk, un
        // bloc n'a pas encore de monde ; relu configure (commande), il garde les siens.
        boolean copy = this.world != null && !isConfigured();
        boolean reread = this.world != null && isConfigured();
        this.settings = new PortalSettings(
                nbt.contains(NBT_WIDTH) ? nbt.getInt(NBT_WIDTH) : PortalSettings.DEFAULT.width(),
                nbt.contains(NBT_HEIGHT) ? nbt.getInt(NBT_HEIGHT) : PortalSettings.DEFAULT.height(),
                !nbt.contains(NBT_TRANSMITS_LIGHT) || nbt.getBoolean(NBT_TRANSMITS_LIGHT),
                !nbt.contains(NBT_DOUBLE_SIDED) || nbt.getBoolean(NBT_DOUBLE_SIDED),
                nbt.getBoolean(NBT_ONE_WAY),
                nbt.getBoolean(NBT_FLEETING_EXIT),
                PortalExit.byName(nbt.getString(NBT_EXIT)),
                nbt.getInt(NBT_EXIT_SCATTER),
                nbt.getBoolean(NBT_FLUSH),
                nbt.getBoolean(NBT_UPSIDE_DOWN),
                PortalPlacement.byName(nbt.getString(NBT_PLACEMENT)),
                readDestination(nbt),
                nbt.contains(NBT_MARK, NbtElement.COMPOUND_TYPE)
                        ? PortalMark.CODEC.parse(NbtOps.INSTANCE, nbt.get(NBT_MARK)).result()
                        : Optional.empty());
        // Une copie a sa propre sortie, toute neuve ; seule une sortie fugace se perd de vue.
        this.exitLost = !copy && this.settings.fleetingExit() && nbt.getBoolean(NBT_EXIT_LOST);
        for (int face = 0; face < FACES; face++) {
            this.entryPortals[face] = !copy && nbt.containsUuid(NBT_ENTRY_PORTALS[face]) ? nbt.getUuid(NBT_ENTRY_PORTALS[face]) : null;
            this.returnPortals[face] = !copy && nbt.containsUuid(NBT_RETURN_PORTALS[face]) ? nbt.getUuid(NBT_RETURN_PORTALS[face]) : null;
            this.returnMissingChecks[face] = 0;
            this.returnAwaitingChunk[face] = false;
        }
        // Identifier.tryParse("") renvoie "minecraft:" et non null : il faut tester la presence de la cle.
        Identifier destinationDimId = nbt.contains(NBT_DESTINATION_DIM, NbtElement.STRING_TYPE)
                ? Identifier.tryParse(nbt.getString(NBT_DESTINATION_DIM))
                : null;
        this.destinationDim = destinationDimId == null ? null : RegistryKey.of(RegistryKeys.WORLD, destinationDimId);
        this.destinationPos = nbt.contains(NBT_DESTINATION_X)
                ? new Vec3d(nbt.getDouble(NBT_DESTINATION_X), nbt.getDouble(NBT_DESTINATION_Y), nbt.getDouble(NBT_DESTINATION_Z))
                : null;
        try {
            this.destinationFacing = nbt.contains(NBT_DESTINATION_FACING)
                    ? PortalFacing.valueOf(nbt.getString(NBT_DESTINATION_FACING))
                    : null;
        } catch (IllegalArgumentException ignored) {
            this.destinationFacing = null;
        }
        // Une copie pose ses retours des son premier tick, comme un bloc qui vient de trouver sa destination.
        if (copy && isConfigured()) {
            Arrays.fill(this.returnAwaitingChunk, true);
        }
        // Relu configure a une autre taille ou un autre plan : le cadre d'arrivee garde son pied, et son centre suit comme celui
        // du cadre d'entree. Une copie garde la destination de l'original telle quelle.
        if (isConfigured() && reread && (previous.width() != this.settings.width() || previous.height() != this.settings.height()
                || previous.flush() != this.settings.flush())) {
            this.destinationPos = PortalArea.center(PortalArea.floor(this.destinationPos, this.destinationFacing, previous),
                    this.destinationFacing, this.settings);
        }
        // Relu configure avec un autre sens de sortie : un cadre d'arrivee debout tourne sur son pied vers le nouveau sens, et
        // ses portails, qui ne correspondent plus, sont reposes. Couche avant ou apres, son pied ne convient plus (un cadre au
        // plafond se pose sous le plafond, releve a la surface) : ses portails sont retires et l'arrivee se cherche de
        // nouveau au prochain tick, comme a la pose.
        if (isConfigured() && reread && previous.exit() != this.settings.exit()) {
            PortalFacing facing = this.settings.exit().arrivalFacing(entryFacing());
            if (facing.normal.y == 0.0 && this.destinationFacing.normal.y == 0.0) {
                this.destinationPos = PortalArea.center(PortalArea.floor(this.destinationPos, this.destinationFacing, this.settings), facing,
                        this.settings);
                this.destinationFacing = facing;
            } else {
                discardPortals();
                this.destinationPos = null;
                this.destinationFacing = null;
            }
        }
        // Relu configure vers une autre destination ou une autre sortie fixee : l'arrivee se cherche de nouveau, comme a la pose.
        if (isConfigured() && reread && (!previous.destination().equals(this.settings.destination())
                || !previous.mark().equals(this.settings.mark()))) {
            discardPortals();
            this.destinationPos = null;
            this.destinationFacing = null;
        }
        this.ticksSinceLoad = 0;
    }

    /** Dimension de destination choisie, sauvegardee par son identifiant ; vide pour la destination automatique. */
    private static Optional<RegistryKey<World>> readDestination(NbtCompound nbt) {
        Identifier id = nbt.contains(NBT_DESTINATION, NbtElement.STRING_TYPE) ? Identifier.tryParse(nbt.getString(NBT_DESTINATION)) : null;
        return id == null ? Optional.empty() : Optional.of(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        writeSettings(nbt);
        for (int face = 0; face < FACES; face++) {
            if (this.entryPortals[face] != null) {
                nbt.putUuid(NBT_ENTRY_PORTALS[face], this.entryPortals[face]);
            }
            if (this.returnPortals[face] != null) {
                nbt.putUuid(NBT_RETURN_PORTALS[face], this.returnPortals[face]);
            }
        }
        if (this.destinationDim != null) {
            nbt.putString(NBT_DESTINATION_DIM, this.destinationDim.getValue().toString());
        }
        if (this.destinationPos != null) {
            nbt.putDouble(NBT_DESTINATION_X, this.destinationPos.x);
            nbt.putDouble(NBT_DESTINATION_Y, this.destinationPos.y);
            nbt.putDouble(NBT_DESTINATION_Z, this.destinationPos.z);
        }
        if (this.destinationFacing != null) {
            nbt.putString(NBT_DESTINATION_FACING, this.destinationFacing.name());
        }
        if (this.exitLost) {
            nbt.putBoolean(NBT_EXIT_LOST, true);
        }
    }
}
