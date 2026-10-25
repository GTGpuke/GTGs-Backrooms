package com.gtgpuke.gtgsbackrooms.core.portal.entity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.gtgpuke.gtgsbackrooms.core.registry.ModBlockEntities;
import com.gtgpuke.gtgsbackrooms.core.registry.ModBlocks;
import com.gtgpuke.gtgsbackrooms.core.registry.ModEntities;

/**
 * Entity persistante qui porte un portail : ou il est, ou il mene, et a quel bloc portail il appartient.
 *
 * <p>Les donnees utiles au client (orientations, destination, taille, visibilite, ouverture, passage de la lumiere,
 * sortie fugace) passent par le {@link DataTracker} : le client predit les traversees et les collisions, et eclaire l'autre cote, avec le
 * meme etat que le serveur. Seul le bloc proprietaire reste cote serveur. Un portail ne bouge jamais, ne subit aucune
 * physique et ne peut pas etre frappe.
 */
public class BackroomsPortalEntity extends Entity {

    private static final String NBT_FACING_SOURCE = "FacingSource";
    private static final String NBT_FACING_DESTINATION = "FacingDestination";
    private static final String NBT_DESTINATION_DIM = "DestinationDim";
    private static final String NBT_DESTINATION_X = "DestinationX";
    private static final String NBT_DESTINATION_Y = "DestinationY";
    private static final String NBT_DESTINATION_Z = "DestinationZ";
    private static final String NBT_WIDTH = "Width";
    private static final String NBT_HEIGHT = "Height";
    private static final String NBT_TELEPORTABLE = "Teleportable";
    private static final String NBT_VISIBLE = "Visible";
    private static final String NBT_TRANSMITS_LIGHT = "TransmitsLight";
    private static final String NBT_FLEETING = "Fleeting";
    private static final String NBT_OWNER_DIM = "OwnerDim";
    private static final String NBT_OWNER_POS = "OwnerPos";

    /** Demi-epaisseur de la boite de detection autour du plan du portail. */
    private static final double DETECTION_HALF_THICKNESS = 0.25;
    /** Marge autour de la boite de detection ou chercher les entities qui ont pu traverser ce tick : la vitesse maximale des projectiles. */
    private static final double CROSSING_SEARCH_MARGIN = 3.0;
    /** Distance, au carre, en dessous de laquelle deux centres de cadre sont le meme point. */
    private static final double SAME_CENTER_EPSILON_SQUARED = 1.0e-6;
    private static final float MIN_SIZE = 0.1f;
    /** Orientations par ordinal, sans la copie de {@code values()} a chaque lecture. */
    private static final PortalFacing[] FACINGS = PortalFacing.values();

    // Donnees synchronisees ; celles que les tests rejouent par identifiant reseau sont visibles du paquet.
    static final TrackedData<Integer> TRACKED_FACING_SOURCE =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<Integer> TRACKED_FACING_DESTINATION =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<String> TRACKED_DESTINATION_DIM =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.STRING);
    // Coordonnees d'arrivee en double exact (bits dans un long) : un float perd le centimetre loin de l'origine.
    static final TrackedData<Long> TRACKED_DESTINATION_X =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> TRACKED_DESTINATION_Y =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Long> TRACKED_DESTINATION_Z =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final TrackedData<Float> TRACKED_WIDTH =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> TRACKED_HEIGHT =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> TRACKED_VISIBLE =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    static final TrackedData<Boolean> TRACKED_TELEPORTABLE =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> TRACKED_TRANSMITS_LIGHT =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> TRACKED_FLEETING =
            DataTracker.registerData(BackroomsPortalEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** Bloc proprietaire (dimension et position), ou {@code null} pour un portail libre. */
    private @Nullable GlobalPos owner;
    /** Boite de detection calculee a la demande ; invalidee quand la position change. */
    private @Nullable Box detectionBox;
    /** Centre d'arrivee lu dans le tracker, en cache : les transformations de vue le lisent a chaque image. */
    private @Nullable Vec3d destinationPosCache;
    /** Dimension d'arrivee lue dans le tracker, en cache : synchronisation et mondes distants la lisent a chaque tick. */
    private @Nullable RegistryKey<World> destinationDimCache;
    private @Nullable Box crossingBox;
    private @Nullable Vec3d detectionBoxPos;
    /** Cadre pas encore reverifie par le calcul de la lumiere transmise (voir {@link #takeLightRefresh}). */
    private boolean lightRefreshPending = true;
    /** Boite autour du plan d'arrivee, en cache : recalculee quand le centre d'arrivee change. */
    private @Nullable Box arrivalBox;
    private @Nullable Box arrivalCrossingBox;
    private @Nullable Vec3d arrivalBoxPos;
    /** Cote client, pour une sortie fugace : dernier tick du client ou son joueur la regardait. */
    private long seenAt = Long.MIN_VALUE / 2;
    /** Cote client : sortie fugace que le joueur ne regarde plus ; pour lui, elle n'existe plus. */
    private boolean hiddenFromPlayer;

    /**
     * Portail vide, tel que le cree le registre d'entities au chargement ou a la reception ; le reste arrive par le NBT
     * ou le tracker. Un portail neuf passe par {@link #builder(World)}.
     */
    public BackroomsPortalEntity(EntityType<? extends BackroomsPortalEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.intersectionChecked = false;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(TRACKED_FACING_SOURCE, PortalFacing.NORTH.ordinal());
        builder.add(TRACKED_FACING_DESTINATION, PortalFacing.SOUTH.ordinal());
        builder.add(TRACKED_DESTINATION_DIM, World.OVERWORLD.getValue().toString());
        builder.add(TRACKED_DESTINATION_X, Double.doubleToRawLongBits(0.0));
        builder.add(TRACKED_DESTINATION_Y, Double.doubleToRawLongBits(0.0));
        builder.add(TRACKED_DESTINATION_Z, Double.doubleToRawLongBits(0.0));
        builder.add(TRACKED_WIDTH, 1.0f);
        builder.add(TRACKED_HEIGHT, 2.0f);
        builder.add(TRACKED_VISIBLE, true);
        builder.add(TRACKED_TELEPORTABLE, true);
        builder.add(TRACKED_TRANSMITS_LIGHT, false);
        builder.add(TRACKED_FLEETING, false);
    }

    /**
     * {@code true} si le bloc proprietaire a disparu alors que son chunk est charge (ou que sa dimension n'existe plus) ;
     * un chunk decharge ne tranche pas, et un portail libre n'est jamais orphelin.
     */
    public boolean isOrphan(MinecraftServer server) {
        if (this.owner == null) {
            return false;
        }
        ServerWorld ownerWorld = server.getWorld(this.owner.dimension());
        if (ownerWorld == null) {
            return true;
        }
        if (!ownerWorld.isChunkLoaded(ChunkPos.toLong(this.owner.pos()))) {
            return false;
        }
        // Le bloc est la mais ne tient plus ce portail pour sien : face ou retour retire par ses reglages, ou remplace.
        return !ownerWorld.getBlockState(this.owner.pos()).isOf(ModBlocks.BACKROOMS_PORTAL)
                || !ownerWorld.getBlockEntity(this.owner.pos(), ModBlockEntities.BACKROOMS_PORTAL).map(block -> block.owns(this.getUuid())).orElse(true);
    }

    /** Orientation du portail dans son monde, normale vers le cote d'entree. */
    public PortalFacing facingSource() {
        return safeFacing(this.dataTracker.get(TRACKED_FACING_SOURCE));
    }

    /** Orientation du portail d'arrivee, normale vers le cote de sortie. */
    public PortalFacing facingDestination() {
        return safeFacing(this.dataTracker.get(TRACKED_FACING_DESTINATION));
    }

    /** Dimension d'arrivee ; l'overworld si la valeur synchronisee est illisible. */
    public RegistryKey<World> destinationDim() {
        RegistryKey<World> cached = this.destinationDimCache;
        if (cached == null) {
            Identifier id = Identifier.tryParse(this.dataTracker.get(TRACKED_DESTINATION_DIM));
            cached = id == null ? World.OVERWORLD : RegistryKey.of(RegistryKeys.WORLD, id);
            this.destinationDimCache = cached;
        }
        return cached;
    }

    /** Centre du portail d'arrivee. */
    public Vec3d destinationPos() {
        Vec3d cached = this.destinationPosCache;
        if (cached == null) {
            cached = new Vec3d(
                    Double.longBitsToDouble(this.dataTracker.get(TRACKED_DESTINATION_X)),
                    Double.longBitsToDouble(this.dataTracker.get(TRACKED_DESTINATION_Y)),
                    Double.longBitsToDouble(this.dataTracker.get(TRACKED_DESTINATION_Z)));
            this.destinationPosCache = cached;
        }
        return cached;
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (TRACKED_DESTINATION_X.equals(data) || TRACKED_DESTINATION_Y.equals(data) || TRACKED_DESTINATION_Z.equals(data)) {
            this.destinationPosCache = null;
        } else if (TRACKED_DESTINATION_DIM.equals(data)) {
            this.destinationDimCache = null;
        } else if (TRACKED_WIDTH.equals(data) || TRACKED_HEIGHT.equals(data)
                || TRACKED_FACING_SOURCE.equals(data) || TRACKED_FACING_DESTINATION.equals(data)) {
            // Taille ou orientation relues, ou recues par le client apres l'apparition : les boites se recalculent.
            this.detectionBox = null;
            this.arrivalBox = null;
        }
    }

    /** Largeur du cadre, en blocs. */
    public float width() {
        return this.dataTracker.get(TRACKED_WIDTH);
    }

    /** Hauteur du cadre, en blocs. */
    public float height() {
        return this.dataTracker.get(TRACKED_HEIGHT);
    }

    /** {@code true} si le portail est ouvert : on le traverse et, engage dans le cadre, on heurte l'autre cote. */
    public boolean teleportable() {
        return this.dataTracker.get(TRACKED_TELEPORTABLE);
    }

    /** Ouvre ou ferme le portail ; les clients qui le suivent le recoivent par le tracker. */
    public void setTeleportable(boolean teleportable) {
        this.dataTracker.set(TRACKED_TELEPORTABLE, teleportable);
    }

    /** {@code true} si le portail montre l'autre cote ; invisible, il ne fait que teleporter. */
    public boolean visible() {
        return this.dataTracker.get(TRACKED_VISIBLE);
    }

    /** {@code true} si la lumiere des blocs passe d'un cote a l'autre du portail. */
    public boolean transmitsLight() {
        return this.dataTracker.get(TRACKED_TRANSMITS_LIGHT);
    }

    /**
     * {@code true} si le portail est la sortie fugace d'un bloc portail : il n'existe, pour chaque joueur, que tant que
     * celui-ci le regarde.
     */
    public boolean fleeting() {
        return this.dataTracker.get(TRACKED_FLEETING);
    }

    /** Cote client : le joueur regarde cette sortie fugace a ce tick du client. */
    public void noteSeen(long tick) {
        this.seenAt = tick;
    }

    /** Cote client : dernier tick du client ou le joueur regardait cette sortie fugace. */
    public long seenAt() {
        return this.seenAt;
    }

    /** Cote client : {@code true} si, pour le joueur, cette sortie fugace n'existe plus le temps qu'il ne la regarde pas. */
    public boolean hiddenFromPlayer() {
        return this.hiddenFromPlayer;
    }

    /** Cote client : cache ou montre cette sortie fugace au joueur. */
    public void setHiddenFromPlayer(boolean hidden) {
        this.hiddenFromPlayer = hidden;
    }

    /** Image d'un point de ce cote dans le monde d'arrivee. */
    public Vec3d toDestination(Vec3d point) {
        return PortalFacing.transformPoint(facingSource(), getPos(), facingDestination(), destinationPos(), point);
    }

    /** Image de ce cote d'un point du monde d'arrivee : l'inverse de {@link #toDestination(Vec3d)}. */
    public Vec3d fromDestination(Vec3d point) {
        return PortalFacing.transformPoint(facingDestination(), destinationPos(), facingSource(), getPos(), point);
    }

    /** Bloc proprietaire, ou {@code null} pour un portail libre. */
    public @Nullable GlobalPos owner() {
        return this.owner;
    }

    /** Boite autour du plan du portail (largeur, hauteur, un demi-bloc d'epaisseur), en coordonnees monde. */
    public Box detectionBox() {
        Vec3d center = getPos();
        if (this.detectionBox == null || this.detectionBoxPos != center) {
            this.detectionBox = frameBox(center, facingSource());
            this.crossingBox = this.detectionBox.expand(CROSSING_SEARCH_MARGIN);
            this.detectionBoxPos = center;
        }
        return this.detectionBox;
    }

    /** Boite autour du plan d'arrivee, comme {@link #detectionBox()} autour du cadre : on y cherche ce qui sort du portail. */
    public Box arrivalBox() {
        Vec3d center = destinationPos();
        if (this.arrivalBox == null || this.arrivalBoxPos != center) {
            this.arrivalBox = frameBox(center, facingDestination());
            this.arrivalCrossingBox = this.arrivalBox.expand(CROSSING_SEARCH_MARGIN);
            this.arrivalBoxPos = center;
        }
        return this.arrivalBox;
    }

    /** Boite d'arrivee elargie de trois blocs, comme {@link #crossingBox()} : on y cherche ce qui sort du portail. */
    public Box arrivalCrossingBox() {
        arrivalBox();
        return this.arrivalCrossingBox;
    }

    /** Cadre a la taille du portail, centre et oriente comme donne, d'un demi-bloc d'epaisseur. */
    private Box frameBox(Vec3d center, PortalFacing facing) {
        Vec3d w = facing.axisW.multiply(width() / 2.0);
        Vec3d h = facing.axisH.multiply(height() / 2.0);
        Vec3d n = facing.normal.multiply(DETECTION_HALF_THICKNESS);
        return new Box(center.subtract(w).subtract(h).subtract(n), center.add(w).add(h).add(n));
    }

    /** Boite de detection elargie de trois blocs : les entities qui s'y trouvent peuvent avoir traverse ce tick. */
    public Box crossingBox() {
        detectionBox();
        return this.crossingBox;
    }

    /** Deplace le centre d'arrivee ; les clients qui suivent le portail le recoivent par le tracker. */
    void setDestinationPos(Vec3d destination) {
        this.dataTracker.set(TRACKED_DESTINATION_X, Double.doubleToRawLongBits(destination.x));
        this.dataTracker.set(TRACKED_DESTINATION_Y, Double.doubleToRawLongBits(destination.y));
        this.dataTracker.set(TRACKED_DESTINATION_Z, Double.doubleToRawLongBits(destination.z));
    }

    /**
     * {@code true} si un point tombe dans le cadre, projete sur son plan : teste dans le repere du portail (largeur,
     * hauteur), sa distance au plan n'y compte pas.
     */
    public boolean frameContains(double x, double y, double z) {
        PortalFacing facing = facingSource();
        double dx = x - getX();
        double dy = y - getY();
        double dz = z - getZ();
        double w = dx * facing.axisW.x + dy * facing.axisW.y + dz * facing.axisW.z;
        double h = dx * facing.axisH.x + dy * facing.axisH.y + dz * facing.axisH.z;
        return Math.abs(w) <= width() / 2.0 && Math.abs(h) <= height() / 2.0;
    }

    /**
     * {@code true} si un point est en face de l'ouverture, dans la largeur du cadre : une entity dont le centre passe le
     * plan ici entre dans le portail ; a cote, elle le contourne, meme si son epaule frole le bord. Pour un cadre couche,
     * dont la hauteur est horizontale elle aussi, dans sa hauteur aussi : qui arrive de cote n'est pas au-dessus (ou
     * au-dessous) de l'ouverture tant qu'il n'a pas franchi son bord. Un cadre debout laisse passer un point a toute
     * hauteur : les pieds d'un mob ; pour un joueur, dont la tete doit passer, {@link #withinOpening(Entity, double,
     * double, double)}.
     */
    public boolean withinFrameWidth(double x, double y, double z) {
        return facesOpening(facingSource(), getPos(), width(), height(), x, y, z);
    }

    /**
     * {@code true} si le point qui decide de la traversee d'une entity est en face de l'ouverture : dans sa largeur, et dans
     * sa hauteur aussi pour un joueur (ses yeux doivent passer l'ouverture, un cadre trop bas l'arrete) ou un cadre couche.
     */
    public boolean withinOpening(Entity entity, double x, double y, double z) {
        return withinOpening(entity instanceof PlayerEntity, x, y, z);
    }

    /**
     * Comme {@link #withinOpening(Entity, double, double, double)}, la hauteur verifiee d'office si {@code fullHeight}.
     *
     * @param fullHeight {@code true} pour verifier la hauteur meme sur un cadre debout : le point d'un joueur
     */
    public boolean withinOpening(boolean fullHeight, double x, double y, double z) {
        return facesOpening(facingSource(), getPos(), width(), height(), x, y, z, fullHeight);
    }

    /** Comme {@link #withinOpening(Entity, double, double, double)}, pour le cadre d'arrivee. */
    public boolean withinArrivalOpening(Entity entity, double x, double y, double z) {
        return withinArrivalOpening(entity instanceof PlayerEntity, x, y, z);
    }

    /**
     * Comme {@link #withinOpening(boolean, double, double, double)}, pour le cadre d'arrivee : un point du monde d'arrivee
     * en face de son ouverture.
     */
    public boolean withinArrivalOpening(boolean fullHeight, double x, double y, double z) {
        return facesOpening(facingDestination(), destinationPos(), width(), height(), x, y, z, fullHeight);
    }

    /**
     * {@code true} si un point est en face de l'ouverture d'un cadre donne : dans sa largeur, et dans sa hauteur aussi pour
     * un cadre couche (voir {@link #withinFrameWidth}).
     *
     * @param facing orientation du cadre
     * @param center centre du cadre
     * @param width largeur du cadre, en blocs
     * @param height hauteur du cadre, en blocs
     */
    public static boolean facesOpening(PortalFacing facing, Vec3d center, float width, float height, double x, double y, double z) {
        return facesOpening(facing, center, width, height, x, y, z, false);
    }

    /**
     * Comme {@link #facesOpening(PortalFacing, Vec3d, float, float, double, double, double)}, la hauteur verifiee d'office
     * si {@code fullHeight}, meme sur un cadre debout.
     */
    public static boolean facesOpening(PortalFacing facing, Vec3d center, float width, float height, double x, double y, double z, boolean fullHeight) {
        double dx = x - center.x;
        double dy = y - center.y;
        double dz = z - center.z;
        if (Math.abs(dx * facing.axisW.x + dy * facing.axisW.y + dz * facing.axisW.z) > width / 2.0) {
            return false;
        }
        return !fullHeight && facing.normal.y == 0.0 || Math.abs(dx * facing.axisH.x + dy * facing.axisH.y + dz * facing.axisH.z) <= height / 2.0;
    }

    /**
     * {@code true} si une boite, decalee, est engagee dans le cadre d'entree, a cheval sur son plan : une entity qui
     * entre dans le portail et n'est pas encore passee tout entiere, a la position ou une image la montre, entre deux
     * ticks. La partie deja passee se dessine dans la vue, transposee.
     *
     * @param box boite de l'entity, dans le monde du portail
     * @param margin marge ajoutee a la boite de tous cotes : le modele d'une entity deborde de sa boite de collision
     * @param shiftX decalage de la position affichee sur celle du tick, X
     * @param shiftY decalage de la position affichee sur celle du tick, Y
     * @param shiftZ decalage de la position affichee sur celle du tick, Z
     */
    public boolean sourceFrameStraddles(Box box, double margin, double shiftX, double shiftY, double shiftZ) {
        return frameStraddles(box, margin, shiftX, shiftY, shiftZ, getPos(), facingSource());
    }

    /**
     * {@code true} si une boite, decalee, est engagee dans le cadre d'arrivee, a cheval sur son plan : une entity qui sort
     * du portail et n'est pas encore passee tout entiere, a la position ou une image la montre, entre deux ticks.
     *
     * @param box boite de l'entity, dans le monde d'arrivee
     * @param margin marge ajoutee a la boite de tous cotes : le modele d'une entity deborde de sa boite de collision
     * @param shiftX decalage de la position affichee sur celle du tick, X
     * @param shiftY decalage de la position affichee sur celle du tick, Y
     * @param shiftZ decalage de la position affichee sur celle du tick, Z
     * @return {@code true} si la boite traverse le plan d'arrivee dans le cadre ; qui ne fait qu'en toucher un bord n'y est pas
     */
    public boolean arrivalFrameStraddles(Box box, double margin, double shiftX, double shiftY, double shiftZ) {
        return frameStraddles(box, margin, shiftX, shiftY, shiftZ, destinationPos(), facingDestination());
    }

    /** {@code true} si la boite, elargie de la marge et decalee, traverse le plan de ce cadre dans sa largeur et sa hauteur. */
    private boolean frameStraddles(
            Box box, double margin, double shiftX, double shiftY, double shiftZ, Vec3d center, PortalFacing facing) {
        double cx = (box.minX + box.maxX) / 2.0 + shiftX - center.x;
        double cy = (box.minY + box.maxY) / 2.0 + shiftY - center.y;
        double cz = (box.minZ + box.maxZ) / 2.0 + shiftZ - center.z;
        double hx = (box.maxX - box.minX) / 2.0 + margin;
        double hy = (box.maxY - box.minY) / 2.0 + margin;
        double hz = (box.maxZ - box.minZ) / 2.0 + margin;
        return Math.abs(dot(facing.normal, cx, cy, cz)) < extent(facing.normal, hx, hy, hz)
                && Math.abs(dot(facing.axisW, cx, cy, cz)) < width() / 2.0 + extent(facing.axisW, hx, hy, hz)
                && Math.abs(dot(facing.axisH, cx, cy, cz)) < height() / 2.0 + extent(facing.axisH, hx, hy, hz);
    }

    private static double dot(Vec3d axis, double x, double y, double z) {
        return axis.x * x + axis.y * y + axis.z * z;
    }

    /** Demi-etendue d'une boite le long d'un axe. */
    private static double extent(Vec3d axis, double halfX, double halfY, double halfZ) {
        return Math.abs(axis.x) * halfX + Math.abs(axis.y) * halfY + Math.abs(axis.z) * halfZ;
    }

    /** Carre de la distance d'un point au cadre d'entree, rectangle plein : nulle sur l'ouverture, sans allocation. */
    public double squaredDistanceToFrame(double x, double y, double z) {
        PortalFacing facing = facingSource();
        double dx = x - getX();
        double dy = y - getY();
        double dz = z - getZ();
        double n = dot(facing.normal, dx, dy, dz);
        double outW = Math.max(0.0, Math.abs(dot(facing.axisW, dx, dy, dz)) - width() / 2.0);
        double outH = Math.max(0.0, Math.abs(dot(facing.axisH, dx, dy, dz)) - height() / 2.0);
        return n * n + outW * outW + outH * outH;
    }

    /**
     * Point du cadre le plus proche d'un point de l'espace : sa projection sur le plan, ramenee dans le cadre. C'est par
     * la que passe un son qui traverse le portail.
     */
    public Vec3d nearestFramePoint(double x, double y, double z) {
        PortalFacing facing = facingSource();
        double dx = x - getX();
        double dy = y - getY();
        double dz = z - getZ();
        double halfWidth = width() / 2.0;
        double halfHeight = height() / 2.0;
        double w = MathHelper.clamp(dx * facing.axisW.x + dy * facing.axisW.y + dz * facing.axisW.z, -halfWidth, halfWidth);
        double h = MathHelper.clamp(dx * facing.axisH.x + dy * facing.axisH.y + dz * facing.axisH.z, -halfHeight, halfHeight);
        return new Vec3d(
                getX() + facing.axisW.x * w + facing.axisH.x * h,
                getY() + facing.axisW.y * w + facing.axisH.y * h,
                getZ() + facing.axisW.z * w + facing.axisH.z * h);
    }

    /** Distance signee d'un point au plan du portail : positive du cote d'entree. */
    public double signedDistance(Vec3d point) {
        return signedDistance(point.x, point.y, point.z);
    }

    /** {@code true} si le centre du cadre est ce point : une face posee sur le cadre d'arrivee d'un autre portail. */
    public boolean centeredAt(Vec3d point) {
        return getPos().squaredDistanceTo(point) < SAME_CENTER_EPSILON_SQUARED;
    }

    /**
     * Hauteur, au-dessus des pieds, du point d'une entity qui decide de sa traversee de ce portail. Pour un joueur, ses
     * yeux quel que soit le cadre : la vue bascule avec la camera, qui ne passe jamais le plan avant elle, et ce sont sa
     * tete et ses epaules qui doivent passer l'ouverture, pas ses pieds (un cadre trop bas l'arrete au front, un cadre
     * sureleve se passe la tete dedans ; decision de l'auteur du 25 octobre 2026). Pour les autres entities, leurs yeux
     * pour un cadre couche (au sol, on y tombe la tete la derniere ; au plafond, on y monte la tete la premiere), leurs
     * pieds pour un cadre debout (on y entre de front, les yeux au-dessus des pieds).
     */
    public double crossingHeight(Entity entity) {
        return crossingHeight(entity, facingSource());
    }

    /** Meme hauteur pour un cadre de l'orientation donnee : celle de l'arrivee pour le retour. */
    private static double crossingHeight(Entity entity, PortalFacing facing) {
        return entity instanceof PlayerEntity || facing.normal.y != 0.0 ? entity.getEyeY() - entity.getY() : 0.0;
    }

    /**
     * {@code true} si le point qui decide de la traversee d'une entity ({@link #crossingHeight}), ses pieds en (x, y, z), est
     * devant le plan et en face de l'ouverture : elle n'a pas encore traverse, et ce qui d'elle touche le cadre y entre.
     * Le rendu qui la coupe au plan en decide comme la traversee : sur un cadre au sol, les pieds d'une entity passent le
     * plan bien avant ses yeux.
     */
    public boolean crossingPointFaces(Entity entity, double x, double y, double z) {
        double crossingY = y + crossingHeight(entity);
        return signedDistance(x, crossingY, z) > 0.0 && withinOpening(entity, x, crossingY, z);
    }

    /**
     * Comme {@link #crossingPointFaces}, pour le cadre d'arrivee, que le retour de ce portail traverse en sens inverse : le
     * point qui decide de cette traversee (les yeux si l'arrivee est couchee), ses pieds en (x, y, z), est du cote de la
     * sortie et en face de l'ouverture d'arrivee.
     */
    public boolean crossingPointFacesArrival(Entity entity, double x, double y, double z) {
        PortalFacing arrival = facingDestination();
        double crossingY = y + crossingHeight(entity, arrival);
        Vec3d center = destinationPos();
        return (x - center.x) * arrival.normal.x + (crossingY - center.y) * arrival.normal.y + (z - center.z) * arrival.normal.z > 0.0
                && withinArrivalOpening(entity, x, crossingY, z);
    }

    /** Variante sans allocation de {@link #signedDistance(Vec3d)}. */
    public double signedDistance(double x, double y, double z) {
        Vec3d normal = facingSource().normal;
        return (x - getX()) * normal.x + (y - getY()) * normal.y + (z - getZ()) * normal.z;
    }

    /**
     * {@code true} si un point qui se deplace ainsi entre par cette face : il va contre sa normale, et sa trajectoire passe
     * le plan en face de l'ouverture, comme pour la traversee (voir {@link #withinFrameWidth}), au plus tard dans
     * {@code maxTicks} ticks, ou l'a passe au tick precedent. Les deux faces d'un bloc portail sont dans le meme plan, dos a
     * dos : une seule est franchie dans un sens donne, la plus proche ne dit pas laquelle. Un point immobile, qui longe le
     * plan ou qui passe a cote du cadre n'en franchit aucune.
     *
     * @param maxTicks ticks au plus avant que le point n'atteigne le plan
     */
    public boolean enteredBy(double x, double y, double z, double moveX, double moveY, double moveZ, int maxTicks) {
        Vec3d normal = facingSource().normal;
        double along = moveX * normal.x + moveY * normal.y + moveZ * normal.z;
        if (along >= 0.0) {
            return false;
        }
        double ticks = signedDistance(x, y, z) / -along;
        return ticks >= -1.0 && ticks <= maxTicks && withinFrameWidth(x + moveX * ticks, y + moveY * ticks, z + moveZ * ticks);
    }

    /**
     * {@code true} au premier calcul de la lumiere transmise qui compte cette copie du portail, {@code false} ensuite : son
     * cadre est alors reverifie en entier, la lumiere sauvee avec son chunk pouvant venir d'une source eteinte depuis.
     */
    public boolean takeLightRefresh() {
        boolean pending = this.lightRefreshPending;
        this.lightRefreshPending = false;
        return pending;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        String destinationDim = nbt.getString(NBT_DESTINATION_DIM);
        if (destinationDim.isBlank()) {
            destinationDim = World.OVERWORLD.getValue().toString();
        }
        this.dataTracker.set(TRACKED_FACING_SOURCE, readFacing(nbt, NBT_FACING_SOURCE, PortalFacing.NORTH).ordinal());
        this.dataTracker.set(TRACKED_FACING_DESTINATION, readFacing(nbt, NBT_FACING_DESTINATION, PortalFacing.SOUTH).ordinal());
        this.dataTracker.set(TRACKED_DESTINATION_DIM, destinationDim);
        setDestinationPos(new Vec3d(nbt.getDouble(NBT_DESTINATION_X), nbt.getDouble(NBT_DESTINATION_Y), nbt.getDouble(NBT_DESTINATION_Z)));
        this.dataTracker.set(TRACKED_WIDTH, Math.max(MIN_SIZE, nbt.getFloat(NBT_WIDTH)));
        this.dataTracker.set(TRACKED_HEIGHT, Math.max(MIN_SIZE, nbt.getFloat(NBT_HEIGHT)));
        this.dataTracker.set(TRACKED_VISIBLE, !nbt.contains(NBT_VISIBLE) || nbt.getBoolean(NBT_VISIBLE));
        this.dataTracker.set(TRACKED_TELEPORTABLE, !nbt.contains(NBT_TELEPORTABLE) || nbt.getBoolean(NBT_TELEPORTABLE));
        this.dataTracker.set(TRACKED_TRANSMITS_LIGHT, nbt.getBoolean(NBT_TRANSMITS_LIGHT));
        this.dataTracker.set(TRACKED_FLEETING, nbt.getBoolean(NBT_FLEETING));

        // Identifier.tryParse("") renvoie "minecraft:" et non null : on teste la presence des cles.
        Identifier ownerDimId = nbt.contains(NBT_OWNER_DIM, NbtElement.STRING_TYPE)
                ? Identifier.tryParse(nbt.getString(NBT_OWNER_DIM))
                : null;
        this.owner = ownerDimId != null && nbt.contains(NBT_OWNER_POS, NbtElement.LONG_TYPE)
                ? GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, ownerDimId), BlockPos.fromLong(nbt.getLong(NBT_OWNER_POS)))
                : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString(NBT_FACING_SOURCE, facingSource().name());
        nbt.putString(NBT_FACING_DESTINATION, facingDestination().name());
        nbt.putString(NBT_DESTINATION_DIM, this.dataTracker.get(TRACKED_DESTINATION_DIM));
        Vec3d destination = destinationPos();
        nbt.putDouble(NBT_DESTINATION_X, destination.x);
        nbt.putDouble(NBT_DESTINATION_Y, destination.y);
        nbt.putDouble(NBT_DESTINATION_Z, destination.z);
        nbt.putFloat(NBT_WIDTH, width());
        nbt.putFloat(NBT_HEIGHT, height());
        nbt.putBoolean(NBT_TELEPORTABLE, teleportable());
        nbt.putBoolean(NBT_VISIBLE, visible());
        nbt.putBoolean(NBT_TRANSMITS_LIGHT, transmitsLight());
        nbt.putBoolean(NBT_FLEETING, fleeting());
        if (this.owner != null) {
            nbt.putString(NBT_OWNER_DIM, this.owner.dimension().getValue().toString());
            nbt.putLong(NBT_OWNER_POS, this.owner.pos().asLong());
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
        return new EntitySpawnS2CPacket(this, entityTrackerEntry);
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) {
        return visible();
    }

    /** Nouveau portail dans un monde ; position, orientations et destination sont obligatoires. */
    public static Builder builder(World world) {
        return new Builder(world);
    }

    /** Orientation d'un ordinal recu du reseau ; le nord s'il est hors bornes. */
    private static PortalFacing safeFacing(int ordinal) {
        return ordinal < 0 || ordinal >= FACINGS.length ? PortalFacing.NORTH : FACINGS[ordinal];
    }

    private static PortalFacing readFacing(NbtCompound nbt, String key, PortalFacing fallback) {
        try {
            return PortalFacing.valueOf(nbt.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    /** Construction d'un portail complet ; refuse une configuration incomplete. */
    public static final class Builder {
        private final World world;
        private Vec3d position;
        private PortalFacing facingSource;
        private PortalFacing facingDestination;
        private RegistryKey<World> destinationDim;
        private Vec3d destinationPos;
        private float width = 1.0f;
        private float height = 2.0f;
        private boolean teleportable = true;
        private boolean visible = true;
        private boolean transmitsLight;
        private boolean fleeting;
        private @Nullable GlobalPos owner;

        private Builder(World world) {
            this.world = world;
        }

        /** Centre du cadre. */
        public Builder position(Vec3d position) {
            this.position = position;
            return this;
        }

        /** Orientation du portail, normale vers le cote d'entree. */
        public Builder facingSource(PortalFacing facingSource) {
            this.facingSource = facingSource;
            return this;
        }

        /** Orientation du portail d'arrivee, normale vers le cote de sortie. */
        public Builder facingDestination(PortalFacing facingDestination) {
            this.facingDestination = facingDestination;
            return this;
        }

        /** Dimension d'arrivee. */
        public Builder destinationDim(RegistryKey<World> destinationDim) {
            this.destinationDim = destinationDim;
            return this;
        }

        /** Centre du cadre d'arrivee. */
        public Builder destinationPos(Vec3d destinationPos) {
            this.destinationPos = destinationPos;
            return this;
        }

        /** Taille du cadre, en blocs ; un par deux par defaut. */
        public Builder size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /** Portail ouvert ou ferme ; ouvert par defaut. */
        public Builder teleportable(boolean teleportable) {
            this.teleportable = teleportable;
            return this;
        }

        /** Portail visible ou seulement teleporteur ; visible par defaut. */
        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        /** Lumiere des blocs transmise d'un cote a l'autre ; non par defaut. */
        public Builder transmitsLight(boolean transmitsLight) {
            this.transmitsLight = transmitsLight;
            return this;
        }

        /** Sortie fugace d'un bloc portail, qui n'existe que sous le regard ; non par defaut. */
        public Builder fleeting(boolean fleeting) {
            this.fleeting = fleeting;
            return this;
        }

        /** Bloc proprietaire : le portail disparait avec lui. */
        public Builder owner(RegistryKey<World> ownerDimension, BlockPos ownerPos) {
            this.owner = GlobalPos.create(ownerDimension, ownerPos);
            return this;
        }

        /**
         * Portail configure, pas encore ajoute au monde.
         *
         * @throws IllegalStateException si un element obligatoire manque ou si la taille n'est pas positive
         */
        public BackroomsPortalEntity build() {
            if (this.position == null) {
                throw new IllegalStateException("Position du portail manquante.");
            }
            if (this.facingSource == null) {
                throw new IllegalStateException("Orientation source du portail manquante.");
            }
            if (this.facingDestination == null) {
                throw new IllegalStateException("Orientation destination du portail manquante.");
            }
            if (this.destinationDim == null) {
                throw new IllegalStateException("Dimension destination du portail manquante.");
            }
            if (this.destinationPos == null) {
                throw new IllegalStateException("Position destination du portail manquante.");
            }
            if (this.width <= 0.0f || this.height <= 0.0f) {
                throw new IllegalStateException("Taille du portail invalide : " + this.width + "x" + this.height + ".");
            }

            BackroomsPortalEntity portal = new BackroomsPortalEntity(ModEntities.BACKROOMS_PORTAL, this.world);
            portal.setPosition(this.position);
            portal.dataTracker.set(TRACKED_FACING_SOURCE, this.facingSource.ordinal());
            portal.dataTracker.set(TRACKED_FACING_DESTINATION, this.facingDestination.ordinal());
            portal.dataTracker.set(TRACKED_DESTINATION_DIM, this.destinationDim.getValue().toString());
            portal.setDestinationPos(this.destinationPos);
            portal.dataTracker.set(TRACKED_WIDTH, this.width);
            portal.dataTracker.set(TRACKED_HEIGHT, this.height);
            portal.dataTracker.set(TRACKED_VISIBLE, this.visible);
            portal.dataTracker.set(TRACKED_TELEPORTABLE, this.teleportable);
            portal.dataTracker.set(TRACKED_TRANSMITS_LIGHT, this.transmitsLight);
            portal.dataTracker.set(TRACKED_FLEETING, this.fleeting);
            portal.owner = this.owner;
            return portal;
        }
    }
}
