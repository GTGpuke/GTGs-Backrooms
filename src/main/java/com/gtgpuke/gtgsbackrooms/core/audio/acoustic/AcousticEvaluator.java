package com.gtgpuke.gtgsbackrooms.core.audio.acoustic;

import org.jetbrains.annotations.Nullable;

import com.gtgpuke.gtgsbackrooms.core.audio.profile.AudioProfile;

/**
 * L'acoustique d'un son par lancer de rayons sur une copie du monde ({@link SolidityWindow}), hors du thread de rendu.
 * Reverb : des rayons repartis uniformement autour de la source, chacun reflechi quelques fois sur les blocs qu'il
 * rencontre ; la longueur moyenne des segments dit la taille de l'espace, la part des rayons sortis sans mur (ou sortis
 * du monde copie) son ouverture. Un rayon qui touche l'ouverture d'un portail ({@link PortalPlanes}) continue de
 * l'autre cote, transpose, une fois au plus. Occlusion : le rayon droit de la source a l'auditeur, et, en qualite
 * complete, des chemins qui contournent par les cotes de la source (le son se faufile par une ouverture), le meilleur
 * retenu avec le cout du detour, et son point de passage garde (la direction d'ou le son semble venir). Les segments
 * des rayons et des chemins s'enregistrent, si on le demande ({@link #record}), pour les voir en jeu. Logique pure : les
 * tableaux de travail sont gardes par l'evaluateur (un par thread de calcul), seul le resultat d'une evaluation, quatre
 * flottants, est cree pour elle.
 */
public final class AcousticEvaluator {

    /** Longueur au-dela de laquelle un rayon est sorti de la salle. */
    public static final double MAX_RAY = 48.0;
    /** Ce qu'un detour par le cote coute au son, en occlusion. */
    static final float DETOUR_COST = 0.1f;
    /** Part de poids d'un rayon qui sort apres un rebond, par rebond : sorti tout de suite, il compte entier. */
    private static final double ESCAPE_DECAY = 0.5;
    /** De combien l'impact est ramene dans la case d'avant pour repartir du bon cote du mur. */
    private static final double BACK_OFF = 1.0e-4;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    /** Points de passage des chemins de contournement, par rapport a la source. */
    private static final double[] DETOURS = {2.0, 0.0, 0.0, -2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, -2.0};

    /** Axe de l'impact quand le rayon n'a rien rencontre. */
    private static final int NOTHING = -1;
    /** Axe de l'impact quand le rayon est entre dans un cadre de portail. */
    private static final int PORTAL = -2;
    /** Axe de l'impact quand le rayon est sorti du monde copie : perdu. */
    private static final int UNKNOWN = -3;

    private final int bounces;
    private final int detours;
    private final double[] directions;
    private double hitDistance;
    private int hitAxis;
    private int hitPortal;
    private @Nullable RayTrace trace;
    private double waypointX;
    private double waypointY;
    private double waypointZ;
    private float direct;

    /**
     * @param rays rayons de reverb par evaluation
     * @param bounces rebonds de chaque rayon, au plus
     * @param paths chemins d'occlusion : le direct, puis jusqu'a quatre detours
     */
    public AcousticEvaluator(int rays, int bounces, int paths) {
        this.bounces = bounces;
        this.detours = Math.min(DETOURS.length / 3, Math.max(0, paths - 1));
        this.directions = new double[rays * 3];
        for (int i = 0; i < rays; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / rays;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = GOLDEN_ANGLE * i;
            this.directions[i * 3] = ring * Math.cos(angle);
            this.directions[i * 3 + 1] = y;
            this.directions[i * 3 + 2] = ring * Math.sin(angle);
        }
    }

    /** L'evaluateur d'une qualite. */
    public static AcousticEvaluator of(AudioQuality quality) {
        return new AcousticEvaluator(quality.rays(), quality.bounces(), quality.paths());
    }

    /** Segments a enregistrer pour les calculs qui suivent, ou {@code null} pour ne rien enregistrer. */
    public void record(@Nullable RayTrace target) {
        this.trace = target;
    }

    /**
     * Le point de passage du meilleur chemin du dernier calcul d'occlusion, relatif a la source : nul si le chemin droit
     * l'emporte. Le son semble venir de la, quand un detour passe mieux que le droit.
     */
    public double waypointX() {
        return this.waypointX;
    }

    /** Voir {@link #waypointX}. */
    public double waypointY() {
        return this.waypointY;
    }

    /** Voir {@link #waypointX}. */
    public double waypointZ() {
        return this.waypointZ;
    }

    /** L'occlusion du chemin droit au dernier calcul d'occlusion, avant les detours. */
    public float directOcclusion() {
        return this.direct;
    }

    /**
     * L'espace autour d'une source : taille et ouverture, par les rayons, sans portail.
     *
     * @param window copie du monde de la source
     * @param sx source
     * @param sy source
     * @param sz source
     * @return taille et ouverture, occlusion nulle
     */
    public AcousticResult reverb(SolidityWindow window, double sx, double sy, double sz) {
        return reverb(window, sx, sy, sz, null, null);
    }

    /**
     * L'espace autour d'une source : taille et ouverture, par les rayons ; un rayon qui entre dans un cadre de portail
     * continue dans la copie du monde d'arrivee, une fois au plus.
     *
     * @param window copie du monde de la source
     * @param sx source
     * @param sy source
     * @param sz source
     * @param portals cadres ouverts du monde de la source, ou {@code null}
     * @param arrivals copie du monde d'arrivee de chaque portail, dans l'ordre des portails ({@code null} sans portail)
     * @return taille et ouverture, occlusion nulle
     */
    public AcousticResult reverb(SolidityWindow window, double sx, double sy, double sz, @Nullable PortalPlanes portals,
            @Nullable SolidityWindow[] arrivals) {
        int rays = this.directions.length / 3;
        double totalLength = 0.0;
        int segments = 0;
        double escaped = 0.0;
        int lost = 0;
        for (int i = 0; i < rays; i++) {
            double ox = sx;
            double oy = sy;
            double oz = sz;
            double dx = this.directions[i * 3];
            double dy = this.directions[i * 3 + 1];
            double dz = this.directions[i * 3 + 2];
            double weight = 1.0;
            SolidityWindow current = window;
            PortalPlanes frames = portals;
            for (int bounce = 0; bounce <= this.bounces;) {
                march(current, ox, oy, oz, dx, dy, dz, frames);
                trace(ox, oy, oz, dx, dy, dz);
                if (this.hitAxis == PORTAL) {
                    // Le rayon passe le cadre : il continue de l'autre cote, transpose, sans compter de rebond ni repasser un cadre.
                    totalLength += this.hitDistance;
                    segments++;
                    frames.transform(this.hitPortal, ox + dx * this.hitDistance, oy + dy * this.hitDistance, oz + dz * this.hitDistance, dx, dy, dz);
                    dx = frames.directionX();
                    dy = frames.directionY();
                    dz = frames.directionZ();
                    ox = frames.pointX() + dx * BACK_OFF;
                    oy = frames.pointY() + dy * BACK_OFF;
                    oz = frames.pointZ() + dz * BACK_OFF;
                    current = arrivals[this.hitPortal];
                    frames = null;
                    continue;
                }
                if (this.hitAxis == UNKNOWN) {
                    lost++;
                    break;
                }
                if (this.hitAxis == NOTHING) {
                    escaped += weight;
                    break;
                }
                totalLength += this.hitDistance;
                segments++;
                ox += dx * this.hitDistance;
                oy += dy * this.hitDistance;
                oz += dz * this.hitDistance;
                // Rebond : l'impact ramene d'un cheveu dans la case d'avant, la composante de l'axe franchi inversee.
                if (this.hitAxis == 0) {
                    ox -= Math.signum(dx) * BACK_OFF;
                    dx = -dx;
                } else if (this.hitAxis == 1) {
                    oy -= Math.signum(dy) * BACK_OFF;
                    dy = -dy;
                } else {
                    oz -= Math.signum(dz) * BACK_OFF;
                    dz = -dz;
                }
                weight *= ESCAPE_DECAY;
                bounce++;
            }
        }
        float roomSize = (float) (segments == 0 ? MAX_RAY : totalLength / segments);
        int known = rays - lost;
        return new AcousticResult(roomSize, known == 0 ? 0.0f : (float) (escaped / known), (float) lost / rays, 0.0f);
    }

    /**
     * Occlusion d'une source a un auditeur : le rayon droit, puis, si des blocs le bouchent, les detours par les cotes de
     * la source (un point de passage dans un bloc solide ne compte pas), le meilleur chemin retenu.
     *
     * @param window copie du monde du trajet
     * @param sx source
     * @param sy source
     * @param sz source
     * @param lx auditeur
     * @param ly auditeur
     * @param lz auditeur
     * @param rule poids des blocs et plafond du profil
     */
    public float occlusion(SolidityWindow window, double sx, double sy, double sz, double lx, double ly, double lz, AudioProfile.Occlusion rule) {
        float best = OcclusionTrace.occlusion(sx, sy, sz, lx, ly, lz, window, rule);
        this.direct = best;
        this.waypointX = 0.0;
        this.waypointY = 0.0;
        this.waypointZ = 0.0;
        if (this.trace != null) {
            this.trace.add(sx, sy, sz, lx, ly, lz, RayTrace.OCCLUSION, best);
        }
        for (int i = 0; i < this.detours && best > DETOUR_COST; i++) {
            double px = sx + DETOURS[i * 3];
            double py = sy + DETOURS[i * 3 + 1];
            double pz = sz + DETOURS[i * 3 + 2];
            if (window.at(floor(px), floor(py), floor(pz)) != OcclusionTrace.AIR) {
                continue;
            }
            float via = Math.min(rule.max(), OcclusionTrace.combine(OcclusionTrace.occlusion(sx, sy, sz, px, py, pz, window, rule),
                    OcclusionTrace.occlusion(px, py, pz, lx, ly, lz, window, rule), rule) + DETOUR_COST);
            if (this.trace != null) {
                this.trace.add(sx, sy, sz, px, py, pz, RayTrace.OCCLUSION, via);
                this.trace.add(px, py, pz, lx, ly, lz, RayTrace.OCCLUSION, via);
            }
            if (via < best) {
                best = via;
                this.waypointX = DETOURS[i * 3];
                this.waypointY = DETOURS[i * 3 + 1];
                this.waypointZ = DETOURS[i * 3 + 2];
            }
        }
        return best;
    }

    /** Enregistre le segment que le dernier {@link #march} a parcouru, si on le demande. */
    private void trace(double ox, double oy, double oz, double dx, double dy, double dz) {
        if (this.trace == null) {
            return;
        }
        int kind;
        if (this.hitAxis == PORTAL) {
            kind = RayTrace.PORTAL;
        } else if (this.hitAxis == UNKNOWN) {
            kind = RayTrace.LOST;
        } else if (this.hitAxis == NOTHING) {
            kind = RayTrace.ESCAPE;
        } else {
            kind = RayTrace.REFLECT;
        }
        double length = this.hitDistance;
        this.trace.add(ox, oy, oz, ox + dx * length, oy + dy * length, oz + dz * length, kind, 0.0f);
    }

    /**
     * Avance un rayon jusqu'au premier bloc solide, a l'entree d'un cadre de portail, a la sortie du monde copie ou a
     * {@link #MAX_RAY} : la distance et l'axe de la face franchie ({@link #NOTHING} sans mur, {@link #PORTAL} et l'index
     * du portail pour un cadre, {@link #UNKNOWN} pour un rayon perdu hors du monde copie) restent dans l'evaluateur. La case de depart n'est pas testee (la source est dans son
     * bloc), comme pour l'occlusion.
     */
    private void march(SolidityWindow window, double ox, double oy, double oz, double dx, double dy, double dz, @Nullable PortalPlanes portals) {
        int portal = portals == null ? -1 : portals.intersect(ox, oy, oz, dx, dy, dz, MAX_RAY);
        double portalDistance = portal < 0 ? Double.POSITIVE_INFINITY : portals.hitDistance();
        int x = floor(ox);
        int y = floor(oy);
        int z = floor(oz);
        int stepX = step(dx);
        int stepY = step(dy);
        int stepZ = step(dz);
        double tMaxX = boundary(ox, x, dx, stepX);
        double tMaxY = boundary(oy, y, dy, stepY);
        double tMaxZ = boundary(oz, z, dz, stepZ);
        double tDeltaX = delta(dx);
        double tDeltaY = delta(dy);
        double tDeltaZ = delta(dz);
        this.hitDistance = MAX_RAY;
        this.hitAxis = NOTHING;
        while (true) {
            int axis;
            double t;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                t = tMaxX;
                x += stepX;
                tMaxX += tDeltaX;
                axis = 0;
            } else if (tMaxY < tMaxZ) {
                t = tMaxY;
                y += stepY;
                tMaxY += tDeltaY;
                axis = 1;
            } else {
                t = tMaxZ;
                z += stepZ;
                tMaxZ += tDeltaZ;
                axis = 2;
            }
            if (t > portalDistance) {
                this.hitDistance = portalDistance;
                this.hitAxis = PORTAL;
                this.hitPortal = portal;
                return;
            }
            if (t > MAX_RAY) {
                return;
            }
            if (!window.known(x, y, z)) {
                // Sorti du monde copie : le rayon est perdu, le resultat le dit.
                this.hitDistance = t;
                this.hitAxis = UNKNOWN;
                return;
            }
            if (window.at(x, y, z) != OcclusionTrace.AIR) {
                this.hitDistance = t;
                this.hitAxis = axis;
                return;
            }
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int step(double direction) {
        return direction > 0.0 ? 1 : direction < 0.0 ? -1 : 0;
    }

    private static double boundary(double start, int cell, double direction, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double next = step > 0 ? cell + 1.0 : cell;
        return (next - start) / direction;
    }

    private static double delta(double direction) {
        return direction == 0.0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / direction);
    }
}
