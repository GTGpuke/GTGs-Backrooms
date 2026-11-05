// Les ombres des corps : l'ombre de contact, lue par le terrain a chaque fragment, et ce que les corps retirent de la lumiere des dalles,
// calcule par texel des tuiles proches d'un corps dans la passe des ombres (body_shadow.fsh), puis lu par le terrain dans son cache
// (look.glsl, gtgs_bodyRemoved). Les corps viennent du bloc des neons (neon_block.glsl), les boites de body_box.glsl, la grille des
// blocs de block_march.glsl, les cadres d'area_light.glsl.

// Le segment de start a start + toTarget traverse-t-il ce corps, un cylindre debout : centre du bas (xyz), rayon (w), haut (top) ?
bool gtgs_crosses(vec3 start, vec3 toTarget, vec4 body, float top) {
    vec2 across = start.xz - body.xz;
    float a = dot(toTarget.xz, toTarget.xz);
    float b = dot(across, toTarget.xz);
    float c = dot(across, across) - body.w * body.w;
    float enter = 0.0;
    float leave = 1.0;
    if (a < 1.0e-6) {
        if (c > 0.0) {
            return false;
        }
    } else {
        float inside = b * b - a * c;
        if (inside < 0.0) {
            return false;
        }
        float root = sqrt(inside);
        enter = (-b - root) / a;
        leave = (-b + root) / a;
    }
    float low = (body.y - start.y) / toTarget.y;
    float high = (top - start.y) / toTarget.y;
    return max(max(enter, min(low, high)), 0.0) <= min(min(leave, max(low, high)), 1.0);
}

// Groupes de quatre valeurs d'un corps : son enveloppe, puis seize boites de quatre groupes ; corps, au plus.
const int GTGS_BODY_ENTRIES = 66;
const int GTGS_MAX_BODIES = 8;

// Le segment de start a start + toTarget passe-t-il dans ce corps ? Son enveloppe ecarte vite les rayons qui passent loin.
bool gtgs_bodyHides(vec3 start, vec3 toTarget, int body) {
    int at = body * GTGS_BODY_ENTRIES;
    vec4 top = gtgs_Caster[at + 1];
    if (!gtgs_crosses(start, toTarget, gtgs_Caster[at], top.x)) {
        return false;
    }
    int boxes = int(top.y);
    for (int k = 0; k < boxes; k++) {
        int box = at + 2 + k * 4;
        if (gtgs_hitsBox(start, toTarget, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz, gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz, 0.0)) {
            return true;
        }
    }
    return false;
}

// Distance, en blocs, au-dela de laquelle un corps ne retire plus rien de la lumiere qui revient de la piece, et ce qu'il en retire au plus.
const float GTGS_BODY_REACH = 3.0;
const float GTGS_BODY_OCCLUSION = 0.7;

// La part de la lumiere qui revient de la piece que les corps cachent a ce point : chaque boite, trois spheres de la taille de sa
// section le long de son axe le plus long, cache ce qu'une sphere cache d'un ciel uniforme, le carre de son rayon apparent par le
// cosinus de sa direction ; une boite longue compte autant que les spheres qui la rempliraient.
float gtgs_bodyOcclusion(vec3 point, vec3 normal) {
    int bodies = int(gtgs_CasterRays.x);
    int all = bodies + int(gtgs_CasterRays.z);
    float hidden = 0.0;
    for (int b = 0; b < all; b++) {
        int at = b * GTGS_BODY_ENTRIES;
        vec4 bounds = gtgs_Caster[at];
        vec4 top = gtgs_Caster[at + 1];
        vec2 across = point.xz - bounds.xz;
        float reach = bounds.w + GTGS_BODY_REACH;
        if (dot(across, across) >= reach * reach || point.y < bounds.y - GTGS_BODY_REACH || point.y > top.x + GTGS_BODY_REACH) {
            continue;
        }
        int boxes = int(top.y);
        // Un corps de l'autre cote d'un cadre, reporte derriere lui, ne cache a une surface de ce cote que ce qu'elle voit par l'ouverture.
        int frame = b < bodies ? -1 : int(top.z);
        for (int k = 0; k < boxes; k++) {
            vec4 center = gtgs_Caster[at + 2 + k * 4];
            if (frame >= 0 && gtgs_frameCrossing(point, center.xyz, gtgs_Frames[frame * 3].xyz, gtgs_Frames[frame * 3 + 1].xyz, gtgs_Frames[frame * 3 + 2].xyz,
                    GTGS_FACE_NORMAL[int(gtgs_Frames[frame * 3].w)]) < 0.0) {
                continue;
            }
            vec3 longest = gtgs_Caster[at + 3 + k * 4].xyz;
            float half_ = length(longest);
            float radius = center.w;
            float weight = max(1.0, half_ / radius) / 3.0;
            vec3 step_ = longest * (max(half_ - radius, 0.0) / half_);
            for (int s = -1; s <= 1; s++) {
                vec3 toSphere = center.xyz + step_ * float(s) - point;
                float distance2 = max(dot(toSphere, toSphere), radius * radius);
                float facing = max(dot(normal, toSphere), 0.0) * inversesqrt(distance2);
                hidden += weight * facing * radius * radius / distance2;
            }
        }
    }
    return min(hidden, GTGS_BODY_OCCLUSION);
}

// true si un cadre de portail coupe le segment (relatif au bloc de la camera) : la lumiere qui le suit part de l'autre cote.
bool gtgs_walled(vec3 from, vec3 to) {
    int count = int(gtgs_OpeningCount.y);
    for (int i = 0; i < count; i++) {
        if (gtgs_frameCrossing(from, to, gtgs_Frames[i * 3].xyz, gtgs_Frames[i * 3 + 1].xyz, gtgs_Frames[i * 3 + 2].xyz,
                GTGS_FACE_NORMAL[int(gtgs_Frames[i * 3].w)]) >= 0.0) {
            return true;
        }
    }
    return false;
}

// Le centre d'une dalle, vu d'ici : 1 si les blocs le laissent voir ; 0 si le bloc qui le cache cache toute la dalle : le rayon entre
// par le milieu de sa face, et les rayons vers les autres points de la dalle, ecartes du centre de la demi-diagonale de la dalle au plus
// a l'arrivee et d'autant moins a ce bloc, entrent par la meme face ; -1 s'il le cache de justesse, par un bord, ou chaque rayon se
// verifie. Une dalle de la piece d'a cote, derriere un mur, est le cas de la plupart des dalles a portee : une marche, rien d'autre.
const float GTGS_SIGHT_MARGIN = 0.02;
const float GTGS_SQRT2 = 1.41421356;

int gtgs_dalleSight(vec3 start, vec3 toFace, float side) {
    ivec3 hit;
    int face;
    float t = gtgs_trace(gtgs_CameraBlock, start, toFace, hit, face);
    if (t >= 2.0) {
        return 1;
    }
    vec3 entry = abs(start + toFace * t - vec3(hit) - 0.5);
    int axis = face / 2;
    vec2 across = axis == 0 ? entry.yz : (axis == 1 ? entry.xz : entry.xy);
    float covered = 0.5 - t * side * GTGS_SQRT2 - GTGS_SIGHT_MARGIN;
    return across.x <= covered && across.y <= covered ? 0 : -1;
}

// Ce que les corps retirent a une surface de la lumiere de ses dalles, en parts de dalles (la meme unite que ce qu'elle recoit). Les
// rayons visent toute la dalle : l'ombre d'un corps est nette a son contact et se dilue en s'en eloignant. Un rayon arrete par un corps
// ne compte que si les blocs le laissaient passer : un corps ne fait pas d'ombre a une dalle qu'on ne voit pas d'ici. Calcule par la
// passe des ombres des corps, une fois par texel des tuiles proches d'un corps (BodyShadows) ; le terrain lit le resultat dans son cache.
float gtgs_casterRemoved(vec3 point, vec3 normal) {
    int bodies = int(gtgs_CasterRays.x);
    int all = bodies + int(gtgs_CasterRays.z);
    int rays = int(gtgs_CasterRays.y);
    if (rays == 0 || all == 0) {
        return 0.0;
    }
    // Les corps assez proches pour que leur ombre compte ici, un bit par corps.
    int nearby = 0;
    for (int b = 0; b < all; b++) {
        vec3 toBody = gtgs_Caster[b * GTGS_BODY_ENTRIES].xyz - point;
        if (dot(toBody, toBody) < 100.0) {
            nearby |= 1 << b;
        }
    }
    if (nearby == 0) {
        return 0.0;
    }
    vec3 start = point + normal * 0.02;
    float side = gtgs_CasterLight.y * 0.5;
    vec3 extent = vec3(side, 0.0, side);
    float reach = gtgs_CasterRays.w + side * 1.5;
    float removed = 0.0;
    for (int i = 0; i < gtgs_NeonCount; i++) {
        vec3 face = gtgs_NeonFaces[i].xyz;
        vec3 toFace = face - start;
        if (toFace.y <= 0.0 || dot(toFace, toFace) >= reach * reach) {
            continue;
        }
        // Ce que la dalle donnerait ici sans rien devant : rien, rien a lui retirer.
        float form = gtgs_NeonColor[i].a * gtgs_received(clamp(point, face - extent, face + extent) - point, vec3(0.0, -1.0, 0.0), normal, gtgs_CasterRays.w, gtgs_CasterLight.x);
        if (form < 1.0e-3) {
            continue;
        }
        // Les corps que le rayon vers le centre de la dalle frole, de la largeur de la dalle pres : tout autre rayon vers la dalle passe a
        // moins de cette largeur de lui. La plupart des dalles s'arretent ici.
        int crossing = 0;
        for (int b = 0; b < bodies; b++) {
            int at = b * GTGS_BODY_ENTRIES;
            vec4 body = gtgs_Caster[at];
            if ((nearby >> b & 1) != 0 && gtgs_crosses(start, toFace, vec4(body.x, body.y - side, body.z, body.w + side), gtgs_Caster[at + 1].x + side)) {
                crossing |= 1 << b;
            }
        }
        if (crossing == 0) {
            continue;
        }
        // Les blocs laissent-ils voir la dalle ? Une seule marche dans la grille, vers son centre. S'ils la cachent toute (une dalle de la
        // piece d'a cote, derriere un mur : la plupart), un corps n'a rien a lui retirer, et ni ses boites ni les rayons ne se testent ;
        // s'ils n'en cachent que le centre, par un bord, chaque rayon arrete par un corps refait sa marche.
        int sight = gtgs_dalleSight(start, toFace + vec3(0.0, -0.02, 0.0), side);
        if (sight == 0) {
            continue;
        }
        // Seules comptent les boites que ce rayon frole, de la largeur de la dalle pres : chaque rayon ne teste que celles-la.
        int touched[GTGS_MAX_BODIES];
        int hitting = 0;
        for (int b = 0; b < bodies; b++) {
            touched[b] = 0;
            if ((crossing >> b & 1) != 0) {
                int at = b * GTGS_BODY_ENTRIES;
                for (int k = 0; k < int(gtgs_Caster[at + 1].y); k++) {
                    int box = at + 2 + k * 4;
                    if (gtgs_hitsBox(start, toFace, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz, gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz,
                            side + 0.02)) {
                        touched[b] |= 1 << k;
                    }
                }
                if (touched[b] != 0) {
                    hitting |= 1 << b;
                }
            }
        }
        if (hitting == 0) {
            continue;
        }
        int hidden = 0;
        for (int s = 0; s < rays; s++) {
            float angle = float(s) * 2.3999632;
            float radius = sqrt((float(s) + 0.5) / float(rays)) * side;
            vec3 toTarget = face + vec3(cos(angle) * radius, -0.02, sin(angle) * radius) - start;
            bool stopped = false;
            for (int b = 0; b < bodies && !stopped; b++) {
                int boxes = (hitting >> b & 1) != 0 ? touched[b] : 0;
                int at = b * GTGS_BODY_ENTRIES + 2;
                for (int k = 0; boxes >> k != 0 && !stopped; k++) {
                    int box = at + k * 4;
                    stopped = (boxes >> k & 1) != 0 && gtgs_hitsBox(start, toTarget, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz,
                            gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz, 0.0);
                }
            }
            // Un rayon qu'un cadre arrete n'apportait rien : le corps ne lui retire rien (neon derriere un cadre qui bouche le couloir).
            if (stopped && !gtgs_walled(start, start + toTarget) && (sight == 1 || gtgs_march(gtgs_CameraBlock, start, toTarget) >= 2.0)) {
                hidden++;
            }
        }
        removed += form * float(hidden) / float(rays);
    }
    // Les neons de l'autre cote d'un portail, vus par son cadre : un corps de ce cote coupe leur faisceau entre la surface et le cadre, un
    // corps de l'autre cote entre le cadre et le neon, tous deux reportes derriere lui.
    int seenCount = int(gtgs_OpeningCount.z);
    for (int i = 0; i < seenCount; i++) {
        vec3 center = gtgs_Seen[i * 2].xyz;
        vec3 axis = GTGS_FACE_NORMAL[int(gtgs_Seen[i * 2].w)];
        vec3 color = gtgs_Seen[i * 2 + 1].rgb;
        int frame = int(gtgs_Seen[i * 2 + 1].w);
        vec3 ax = axis.z != 0.0 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
        vec3 ay = cross(axis, ax);
        // Ce que la dalle donne ici, a son point le plus proche, comme la tuile le compte : vise a son centre, la part retiree etait
        // trop faible devant ce que la tuile recoit, et la penombre d'un corps s'eclaircissait derriere le plan.
        vec3 span = (abs(ax) + abs(ay)) * side;
        float form = max(color.r, max(color.g, color.b)) / max(gtgs_CasterLight.w, 1.0e-4)
                * gtgs_received(clamp(point, center - span, center + span) - point, axis, normal, gtgs_CasterRays.w, gtgs_CasterLight.x);
        if (form < 1.0e-3) {
            continue;
        }
        // Comme pour un neon : seuls comptent les corps que le rayon vers son centre frole, de la largeur de la dalle pres.
        bool grazed = false;
        for (int b = 0; b < all && !grazed; b++) {
            vec4 body = gtgs_Caster[b * GTGS_BODY_ENTRIES];
            vec4 top = gtgs_Caster[b * GTGS_BODY_ENTRIES + 1];
            grazed = (nearby >> b & 1) != 0 && (b < bodies || int(top.z) == frame)
                    && gtgs_crosses(start, center - start, vec4(body.x, body.y - side, body.z, body.w + side), top.x + side);
        }
        if (!grazed) {
            continue;
        }
        int hidden = 0;
        for (int s = 0; s < rays; s++) {
            float angle = float(s) * 2.3999632;
            float radius = sqrt((float(s) + 0.5) / float(rays)) * side;
            vec3 target = center + ax * (cos(angle) * radius) + ay * (sin(angle) * radius);
            float t = gtgs_frameCrossing(start, target, gtgs_Frames[frame * 3].xyz, gtgs_Frames[frame * 3 + 1].xyz, gtgs_Frames[frame * 3 + 2].xyz,
                    GTGS_FACE_NORMAL[int(gtgs_Frames[frame * 3].w)]);
            if (t < 0.0) {
                continue;
            }
            vec3 toHit = (target - start) * t;
            bool stopped = false;
            // De ce cote, le rayon de la surface au cadre ; puis, de l'autre, celui du cadre au neon, pour les seuls corps de ce cadre.
            for (int b = 0; b < bodies && !stopped; b++) {
                int at = b * GTGS_BODY_ENTRIES;
                if ((nearby >> b & 1) != 0 && gtgs_crosses(start, toHit, gtgs_Caster[at], gtgs_Caster[at + 1].x)) {
                    for (int k = 0; k < int(gtgs_Caster[at + 1].y) && !stopped; k++) {
                        int box = at + 2 + k * 4;
                        stopped = gtgs_hitsBox(start, toHit, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz, gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz, 0.0);
                    }
                }
            }
            vec3 hit = start + toHit;
            vec3 beyond = target - hit;
            for (int b = bodies; b < all && !stopped; b++) {
                int at = b * GTGS_BODY_ENTRIES;
                if ((nearby >> b & 1) != 0 && int(gtgs_Caster[at + 1].z) == frame && gtgs_crosses(hit, beyond, gtgs_Caster[at], gtgs_Caster[at + 1].x)) {
                    for (int k = 0; k < int(gtgs_Caster[at + 1].y) && !stopped; k++) {
                        int box = at + 2 + k * 4;
                        stopped = gtgs_hitsBox(hit, beyond, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz, gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz, 0.0);
                    }
                }
            }
            if (stopped && gtgs_march(gtgs_CameraBlock, start, toHit) >= 2.0) {
                hidden++;
            }
        }
        removed += form * float(hidden) / float(rays);
    }
    return removed;
}
