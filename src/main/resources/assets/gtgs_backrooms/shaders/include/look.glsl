// Look d'une dimension Backrooms, applique a la couleur finale de chaque fragment (etage 1 du moteur de shaders).
// Avec gtgs_LookId a 0 (vanilla), la couleur ressort telle quelle. Les neons proches (gtgs_NeonBlock, en espace camera,
// remplis par vue) eclairent ce qui les entoure, calcules au centre du texel de chaque fragment d'un bloc ; la face de
// chaque neon du terrain, reconnue a sa texture, suit son etat par le jumeau du calendrier (neon_schedule.glsl) et ce
// que les cris proches lui retirent. L'etalonnage de la dimension (look_grade.glsl) s'applique ensuite. Sous la lumiere
// cuite (gtgs_BakedLight), le vertex du terrain ne porte ni lightmap ni brouillard : le terrain lit l'eclairement de sa
// surface dans la texture de zone, puis le brouillard se pose ici, par-dessus la lumiere.

uniform int gtgs_LookId;
// Pas de la grille des texels par bloc (x, 0 pour la position exacte), grain (y), vacillement (z), temps (w).
uniform vec4 gtgs_LookParams;
// Matrice de vue du dessin, celle que vanilla envoie au vertex : la position arrondie au texel y repasse en espace camera.
uniform mat4 ModelViewMat;
uniform int gtgs_NeonCount;
// Camera de la vue : bloc entier et reste, pour retrouver le bloc d'un fragment sans perdre de precision loin de l'origine.
uniform ivec3 gtgs_CameraBlock;
uniform vec3 gtgs_CameraFract;
// Tick du monde de la vue dans la periode de l'horloge commune (x), graine des neons de sa dimension (y).
uniform ivec2 gtgs_NeonClock;
// Rectangle de la texture du neon dans l'atlas des blocs (u0, v0, u1, v1) ; vide, le jumeau est coupe.
uniform vec4 gtgs_NeonFace;
// Parts des neons qui vacillent (x) et morts (y), et des secteurs en panne (z), celles du profil.
uniform vec3 gtgs_NeonShares;
uniform float gtgs_SurfaceLight;
// 1 si cette vue trace : rebonds de ses surfaces, et ses reflets voient leur dalle en entier.
uniform float gtgs_Traced;
// Une ligne par matiere de la grille : couleur moyenne et 1 si elle emet.
uniform sampler2D gtgs_Materials;
// Atlas des blocs, relu quand le regard entre dans l'epaisseur d'une matiere.
uniform sampler2D gtgs_BlockAtlas;
// 1 si les matieres ont leur epaisseur.
uniform float gtgs_Pile;
// Part du contraste des textures gardee (x), exposition des surfaces (y) et part du direct (z), au palier du joueur : l'Ultra a les siens.
uniform vec3 gtgs_LightLook;
// Force des halos autour des neons (x), et de la brillance des matieres (y).
uniform vec2 gtgs_Air;
// Lumiere de fond du profil.
uniform vec3 gtgs_Ambient;
// Pied de la courbe (x), part du degrade sur la grille des texels (y), grain de pixels dans le noir (z), limite de la lumiere d'une
// surface (w, 0 sans limite).
uniform vec4 gtgs_Tone;
// 1 si la zone couvre cette vue.
uniform float gtgs_BakedLight;
// Debut (x) et fin (y) du brouillard, forme (z, 1 pour un cylindre), epaule des hautes lumieres (w, 1 pour aucune).
uniform vec4 gtgs_FogRange;
uniform vec4 gtgs_FogTint;

layout(std140) uniform gtgs_NeonBlock {
    vec4 gtgs_NeonPos[32];
    vec4 gtgs_NeonColor[32];
    // Sources des perturbations (cris) de la dimension de la vue, relatives a sa camera dans les axes du monde (xyz), rayon (w).
    vec4 gtgs_Disturb[4];
    // Force de chaque perturbation : amplitude du son et plafond de clignotement compris.
    vec4 gtgs_DisturbForce;
    // Direction ou eclairent les dalles, en espace camera.
    vec4 gtgs_NeonAxis;
    // Centre de la face qui eclaire de chaque neon, relatif au bloc de la camera, dans les axes du monde (xyz), et sa part dans ce qui ne
    // compte que les neons les plus proches (w) : elle s'eteint avant qu'il ne quitte la liste.
    vec4 gtgs_NeonFaces[32];
    // Corps qui portent une ombre, relatifs au bloc de la camera, soixante-six groupes chacun : leur enveloppe (centre du bas et rayon,
    // puis haut et nombre de boites), puis leurs boites, les cubes de leur modele (centre et rayon de la sphere qui la resume, puis trois
    // demi-axes, le plus long d'abord).
    vec4 gtgs_Caster[528];
    // Nombre de corps (x), rayons vers chaque dalle (y, 0 sans ombres de corps), portee des dalles (w).
    vec4 gtgs_CasterRays;
    // Cosinus de l'angle d'une dalle (x), cote de son rectangle (y), ce qui change le carre de la lumiere d'une tuile en parts de dalles (z),
    // lumiere d'une dalle (w).
    vec4 gtgs_CasterLight;
};

in vec3 gtgs_ViewPos;
in vec3 gtgs_WorldRel;
in vec2 gtgs_Uv;
in vec3 gtgs_Normal;
flat in float gtgs_Terrain;
in vec3 gtgs_Lightmap;
flat in float gtgs_Lit;

// Ce que le regard a parcouru dans l'epaisseur de la matiere, le long de sa face, en blocs.
vec3 gtgs_Shift = vec3(0.0);
// La part de la lumiere d'une surface que les corps lui laissent : elle se retire apres la limite, qui effacerait leur ombre sous une dalle.
float gtgs_BodyLight = 1.0;
// La part de la lumiere qui revient de la piece que les corps retirent a une surface pres d'eux : leur ombre de contact.
float gtgs_BodyShade = 0.0;

// Point d'un bloc ou se calcule sa lumiere, depuis le bloc de la camera : le centre du texel du fragment sur sa face
// (gtgs_LookParams.x pas par bloc, l'axe de la normale garde la position), pour que la lumiere suive les pixels des textures.
vec3 gtgs_texelPoint() {
    float n = gtgs_LookParams.x;
    vec3 p = gtgs_CameraFract + gtgs_WorldRel + gtgs_Shift;
    if (n <= 0.0) {
        return p;
    }
    vec3 a = abs(gtgs_Normal);
    vec3 keep = a.x >= a.y && a.x >= a.z ? vec3(1.0, 0.0, 0.0) : (a.y >= a.z ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0));
    return mix((floor(p * n) + 0.5) / n, p, keep);
}

// Position du fragment ou se calcule la lumiere des neons, en espace camera.
vec3 gtgs_lightPosition() {
    if (gtgs_LookParams.x <= 0.0 || gtgs_Terrain < 0.5) {
        return gtgs_ViewPos;
    }
    return (ModelViewMat * vec4(gtgs_texelPoint() - gtgs_CameraFract, 1.0)).xyz;
}

float gtgs_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Ce que les cris retirent a la lumiere en un point, relatif a la camera.
float gtgs_disturbance(vec3 point) {
    float drop = 0.0;
    for (int i = 0; i < 4; i++) {
        float radius = gtgs_Disturb[i].w;
        if (radius > 0.0) {
            drop = max(drop, gtgs_DisturbForce[i] * clamp(1.0 - length(point - gtgs_Disturb[i].xyz) / radius, 0.0, 1.0));
        }
    }
    return drop;
}

bool gtgs_onNeonFace() {
    return gtgs_Terrain >= 0.5 && gtgs_NeonFace.z > gtgs_NeonFace.x && gtgs_Uv.x >= gtgs_NeonFace.x && gtgs_Uv.y >= gtgs_NeonFace.y
            && gtgs_Uv.x <= gtgs_NeonFace.z && gtgs_Uv.y <= gtgs_NeonFace.w;
}

// 1 pour tout fragment qui n'est pas la face d'un neon du terrain ; sinon son etat, moins ce que les cris lui retirent.
float gtgs_neonFace() {
    if (!gtgs_onNeonFace()) {
        return 1.0;
    }
    // Le bloc du neon : un centieme de bloc derriere la face, cote interieur.
    ivec3 cell = ivec3(floor(gtgs_CameraFract + gtgs_WorldRel - gtgs_Normal * 0.01));
    float state = gtgs_neonIntensity(gtgs_CameraBlock + cell, gtgs_NeonClock.y, gtgs_NeonClock.x, gtgs_NeonShares.x, gtgs_NeonShares.y, gtgs_NeonShares.z);
    return state * (1.0 - gtgs_disturbance(vec3(cell) + 0.5 - gtgs_CameraFract));
}

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
    float hidden = 0.0;
    for (int b = 0; b < bodies; b++) {
        int at = b * GTGS_BODY_ENTRIES;
        vec4 bounds = gtgs_Caster[at];
        vec4 top = gtgs_Caster[at + 1];
        vec2 across = point.xz - bounds.xz;
        float reach = bounds.w + GTGS_BODY_REACH;
        if (dot(across, across) >= reach * reach || point.y < bounds.y - GTGS_BODY_REACH || point.y > top.x + GTGS_BODY_REACH) {
            continue;
        }
        int boxes = int(top.y);
        for (int k = 0; k < boxes; k++) {
            vec4 center = gtgs_Caster[at + 2 + k * 4];
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

// La part de ce qu'une surface recoit vraiment que les corps lui retirent, de 0 a 1. Les rayons visent toute la dalle : l'ombre d'un
// corps est nette a son contact et se dilue en s'en eloignant. Un rayon arrete par un corps ne compte que si les blocs le laissaient
// passer : un corps ne fait pas d'ombre a une dalle qu'on ne voit pas d'ici. seen est ce que la surface recoit, en parts de dalles.
float gtgs_casterShade(vec3 point, vec3 normal, float seen) {
    int bodies = int(gtgs_CasterRays.x);
    int rays = int(gtgs_CasterRays.y);
    if (rays == 0 || bodies == 0 || seen <= 0.0) {
        return 0.0;
    }
    // Les corps assez proches pour que leur ombre compte ici, un bit par corps.
    int nearby = 0;
    for (int b = 0; b < bodies; b++) {
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
        // Seules comptent les boites que le rayon vers le centre de la dalle frole, de la largeur de la dalle pres : tout autre rayon vers
        // la dalle passe a moins de cette largeur de lui. La plupart des dalles s'arretent ici, et chaque rayon ne teste que ces boites.
        int crossing = 0;
        int touched[GTGS_MAX_BODIES];
        for (int b = 0; b < bodies; b++) {
            touched[b] = 0;
            int at = b * GTGS_BODY_ENTRIES;
            vec4 body = gtgs_Caster[at];
            vec4 top = gtgs_Caster[at + 1];
            if ((nearby >> b & 1) != 0 && gtgs_crosses(start, toFace, vec4(body.x, body.y - side, body.z, body.w + side), top.x + side)) {
                for (int k = 0; k < int(top.y); k++) {
                    int box = at + 2 + k * 4;
                    if (gtgs_hitsBox(start, toFace, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz, gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz,
                            side + 0.02)) {
                        touched[b] |= 1 << k;
                    }
                }
                if (touched[b] != 0) {
                    crossing |= 1 << b;
                }
            }
        }
        if (crossing == 0) {
            continue;
        }
        float form = gtgs_NeonColor[i].a * gtgs_received(clamp(point, face - extent, face + extent) - point, vec3(0.0, -1.0, 0.0), normal, gtgs_CasterRays.w, gtgs_CasterLight.x);
        if (form < 1.0e-3) {
            continue;
        }
        int hidden = 0;
        // Les blocs laissent-ils voir le centre de la dalle ? Une seule marche dans la grille, au premier rayon arrete par un corps ; s'ils
        // le cachent, chaque rayon arrete refait la sienne.
        int open = -1;
        for (int s = 0; s < rays; s++) {
            float angle = float(s) * 2.3999632;
            float radius = sqrt((float(s) + 0.5) / float(rays)) * side;
            vec3 toTarget = face + vec3(cos(angle) * radius, -0.02, sin(angle) * radius) - start;
            bool stopped = false;
            for (int b = 0; b < bodies && !stopped; b++) {
                int boxes = (crossing >> b & 1) != 0 ? touched[b] : 0;
                int at = b * GTGS_BODY_ENTRIES + 2;
                for (int k = 0; boxes >> k != 0 && !stopped; k++) {
                    int box = at + k * 4;
                    stopped = (boxes >> k & 1) != 0 && gtgs_hitsBox(start, toTarget, gtgs_Caster[box].xyz, gtgs_Caster[box + 1].xyz,
                            gtgs_Caster[box + 2].xyz, gtgs_Caster[box + 3].xyz, 0.0);
                }
            }
            if (stopped && open < 0) {
                open = gtgs_march(gtgs_CameraBlock, start, toFace + vec3(0.0, -0.02, 0.0)) >= 2.0 ? 1 : 0;
            }
            if (stopped && (open == 1 || gtgs_march(gtgs_CameraBlock, start, toTarget) >= 2.0)) {
                hidden++;
            }
        }
        removed += form * float(hidden) / float(rays);
    }
    // La lumiere qui revient des murs passe autour d'un corps : sous ses pieds, l'ombre ne fait pas un trou noir.
    return clamp(removed / seen, 0.0, 0.8);
}

// L'effet sombre pixelise : dans le noir, chaque pixel de texture tire un petit ecart de lumiere, fixe, qui s'efface en pleine
// lumiere et au loin, la ou les texels tombent sous le pixel de l'ecran.
vec3 gtgs_darkPixels(vec3 light) {
    float n = gtgs_LookParams.x;
    if (n <= 0.0 || gtgs_Tone.z <= 0.0) {
        return light;
    }
    vec3 texel = floor((gtgs_texelPoint() + vec3(gtgs_CameraBlock & 1023)) * n);
    float pick = gtgs_hash(texel.xy + texel.z * vec2(0.37, 0.71)) - 0.5;
    float dark = 1.0 - smoothstep(0.0, 0.45, dot(light, vec3(0.333)));
    float near = 1.0 - smoothstep(8.0, 20.0, length(gtgs_WorldRel));
    return light * (1.0 + pick * gtgs_Tone.z * dark * near);
}

// La lumiere d'une surface avant ce que les corps lui retirent, et la part qu'ils lui laissent, gardee a part : ce que leurs ombres
// portees laissent de la lumiere directe, ce que leur ombre de contact laisse de celle qui revient de la piece.
vec3 gtgs_underBodies(vec3 direct, vec3 around, float kept) {
    vec3 light = direct + around;
    gtgs_BodyLight = dot(direct * kept + around * (1.0 - gtgs_BodyShade), vec3(1.0)) / max(dot(light, vec3(1.0)), 1.0e-3);
    return light;
}

// La tuile d'une surface : la lumiere qui arrive reste lisse d'un pixel d'ecran a l'autre, sauf la part du degrade que le profil
// veut sur la grille ; l'ombre portee, le relief et les coins se lisent par pixel de texture, comme la texture elle-meme. Plus
// tard, sans pixels d'ombre, tout se lisse.
vec3 gtgs_tileLight(uint tile, vec3 exact, vec3 at, float face, vec3 axis) {
    vec2 st = fract(face < 1.5 ? at.zy : (face < 3.5 ? at.xz : at.xy));
    vec2 smoothSt = fract(face < 1.5 ? exact.zy : (face < 3.5 ? exact.xz : exact.xy));
    ivec2 corner = ivec2(int(tile) % 128, int(tile) / 128) * 32;
    vec4 pixel = texelFetch(gtgs_SurfaceAtlas, corner + ivec2(floor(st * 32.0)), 0);
    // La tuile ne connait pas ses voisines : la lecture s'arrete au dernier pixel, ce qui ne se voit pas sur une lumiere lisse.
    vec2 place = clamp(smoothSt * 32.0 - 0.5, 0.0, 30.999);
    ivec2 low = ivec2(floor(place));
    vec2 mixed = place - vec2(low);
    vec4 lisse = mix(mix(texelFetch(gtgs_SurfaceAtlas, corner + low, 0), texelFetch(gtgs_SurfaceAtlas, corner + low + ivec2(1, 0), 0), mixed.x),
            mix(texelFetch(gtgs_SurfaceAtlas, corner + low + ivec2(0, 1), 0), texelFetch(gtgs_SurfaceAtlas, corner + low + ivec2(1, 1), 0), mixed.x), mixed.y);
    bool pixels = gtgs_LookParams.x > 0.0;
    float arriving = mix(lisse.r, pixel.r, pixels ? gtgs_Tone.y : 0.0);
    float shadow = pixels ? pixel.g : lisse.g;
    // Les corps retirent ce qu'ils cachent vraiment.
    float kept = 1.0 - gtgs_casterShade(gtgs_texelPoint(), axis, lisse.r * lisse.r * gtgs_CasterLight.z * shadow);
    float open = pixels ? pixel.b : lisse.b;
    // La pente d'un pixel se lit au pixel, meme sous une lumiere lisse : c'est le grain de sa matiere.
    if (gtgs_Traced > 0.5) {
        vec4 traced = gtgs_bounceAt(gtgs_BounceAtlas, tile, smoothSt);
        if (traced.a > 0.5) {
            // Les rebonds font le gros de la lumiere, sans grille.
            return gtgs_underBodies(gtgs_NeonTint * arriving * arriving * 4.0 * shadow * pixel.a * 2.0 * gtgs_LightLook.z, traced.rgb + gtgs_Ambient * open, kept);
        }
    }
    // Sans rebonds traces, ceux de la zone, qui assombrit deja ses coins : la lumiere de l'Ultra, a ses rebonds pres.
    vec3 direct = gtgs_NeonTint * arriving * arriving * 4.0 * shadow * pixel.a * 2.0 * gtgs_LightLook.z;
    vec4 bounced = gtgs_zoneRead(at, face + 6.0);
    return gtgs_underBodies(direct, bounced.rgb / max(bounced.a, 0.001) * 4.0, kept);
}

// Distances, en blocs, ou la lumiere passe d'un niveau au suivant : des tuiles a la zone, de la zone au loin, du loin a la lightmap. Les
// tuiles couvrent au moins 32 blocs autour de la camera, la zone au moins 48, le loin au moins 128.
const float GTGS_TILE_FADE = 24.0;
const float GTGS_TILE_END = 32.0;
const float GTGS_ZONE_FADE = 40.0;
const float GTGS_ZONE_END = 48.0;
const float GTGS_FAR_FADE = 96.0;
const float GTGS_FAR_END = 128.0;

// Le loin : la lumiere cuite comme celle de la zone, une cellule pour deux blocs a l'horizontale, 144 de cote qui se repetent tous les
// 288 blocs ; une tranche de 32 rangees par face pour l'eclairement complet, puis autant pour sa part indirecte.
uniform sampler3D gtgs_FarVolume;
const int GTGS_FAR_SPAN = 288;
// Un multiple de la periode du loin qui rend positive toute coordonnee du monde : le reste se prend alors sur des entiers positifs.
const int GTGS_FAR_SHIFT = 57600000;

vec4 gtgs_farRead(vec3 at, float layer) {
    vec3 where = vec3(at.x * 0.5, clamp(at.y, 0.5, 31.5) + layer * 32.0, at.z * 0.5);
    return texture(gtgs_FarVolume, where / vec3(144.0, 384.0, 144.0));
}

// La piece moyenne de la dimension, mesuree sur ce que la zone et le loin ont cuit : par genre de surface (murs, sols, plafonds),
// l'eclairement complet de ses surfaces eclairees puis sa part indirecte.
uniform vec3 gtgs_FarRoom[6];

// Avant toute mesure dans la dimension (une arrivee, une vue de portail tout juste ouverte : la copie et la cuisson prennent quelques
// secondes), la lightmap, ombree selon l'orientation de la surface et ramenee a peu pres a la lumiere des surfaces, comme avant le loin.
const float GTGS_LIGHTMAP_GUESS = 1.15;

// La lumiere au-dela du loin, ou a defaut de tout le reste : celle de la piece moyenne, qui s'eteint avec la lightmap la ou elle tombe
// sous ce qu'elle vaut dans une piece eclairee. Elle ne sait pas quel mur fait face aux dalles ; le loin, si.
vec3 gtgs_farLight() {
    int kind = gtgs_Normal.y > 0.5 ? 1 : (gtgs_Normal.y < -0.5 ? 2 : 0);
    if (dot(gtgs_FarRoom[kind * 2], vec3(1.0)) <= 0.0) {
        return gtgs_Lightmap * (1.25 + 2.75 * max(gtgs_Normal.y, 0.0) - 0.35 * max(-gtgs_Normal.y, 0.0)) * GTGS_LIGHTMAP_GUESS;
    }
    vec3 around = gtgs_FarRoom[kind * 2 + 1];
    vec3 room = (gtgs_FarRoom[kind * 2] - around) * gtgs_LightLook.z + around;
    float lit = smoothstep(0.15, 0.4, dot(gtgs_Lightmap, vec3(0.299, 0.587, 0.114)));
    return gtgs_Ambient + max(room - gtgs_Ambient, vec3(0.0)) * lit;
}

// Au-dela de la zone : le loin, lu comme la zone, puis la piece moyenne seule plus loin encore ou la ou le loin n'est pas encore cuit.
// rel : le point devant la surface, relatif au bloc de la camera.
vec3 gtgs_beyondLight(vec3 rel, float face, float reach) {
    vec3 average = gtgs_farLight();
    if (reach >= GTGS_FAR_END) {
        return average;
    }
    vec3 at = rel + vec3(float((gtgs_CameraBlock.x + GTGS_FAR_SHIFT) % GTGS_FAR_SPAN), float(gtgs_CameraBlock.y - gtgs_LightBase),
            float((gtgs_CameraBlock.z + GTGS_FAR_SHIFT) % GTGS_FAR_SPAN));
    vec4 far = gtgs_farRead(at, face);
    vec4 around = gtgs_farRead(at, face + 6.0);
    vec3 bounces = around.rgb / max(around.a, 0.001) * 4.0;
    // La lumiere directe a la meme part que dans la zone.
    vec3 lit = (far.rgb / max(far.a, 0.001) * 4.0 - bounces) * gtgs_LightLook.z + bounces;
    return mix(average, lit, clamp(far.a * 8.0, 0.0, 1.0) * (1.0 - smoothstep(GTGS_FAR_FADE, GTGS_FAR_END, reach)));
}

// La face d'un fragment du terrain, d'apres sa normale.
int gtgs_faceOf() {
    vec3 a = abs(gtgs_Normal);
    if (a.x >= a.y && a.x >= a.z) {
        return gtgs_Normal.x > 0.0 ? 0 : 1;
    }
    if (a.y >= a.z) {
        return gtgs_Normal.y > 0.0 ? 2 : 3;
    }
    return gtgs_Normal.z > 0.0 ? 4 : 5;
}

// L'epaisseur d'une matiere, en blocs, et les pas du regard qui y entre : un pixel de texture en fait presque un, pour que chaque brin
// reste un pixel.
const float GTGS_PILE_DEPTH = 0.08;
const int GTGS_PILE_STEPS = 10;
// Ce que le creux entre deux brins perd de lumiere, et ce que leur pointe gagne : c'est ce qui se voit sous une dalle, qui eclaire d'en
// haut. Sous la lumiere tracee, la texture calmee ne porte plus ce contraste.
const float GTGS_PILE_SHADE = 0.15;
const float GTGS_PILE_SHADE_TRACED = 0.38;

// La place d'un point de la face dans la texture de son bloc, dans le rectangle de celle-ci.
vec2 gtgs_inTexture(vec2 at, vec2 flip, vec2 origin, vec2 span) {
    vec2 st = fract(at);
    return origin + min(vec2(flip.x < 0.0 ? 1.0 - st.x : st.x, flip.y < 0.0 ? 1.0 - st.y : st.y), vec2(0.99999)) * span;
}

// Une matiere qui a une hauteur a une epaisseur : le regard entre par le haut et s'arrete au premier pixel qui depasse. Le fragment prend
// la couleur de ce pixel, et sa lumiere a sa place (gtgs_Shift) : les brins se cachent les uns les autres et bougent avec la camera.
void gtgs_enterPile(inout vec3 color) {
    float near = 1.0 - smoothstep(10.0, 16.0, length(gtgs_WorldRel));
    if (gtgs_Pile < 0.5 || near <= 0.0) {
        return;
    }
    vec4 material = textureLod(gtgs_BlockMaterial, gtgs_Uv, 0.0);
    if (material.a >= 1.0 || !gtgs_sloped(material)) {
        return;
    }
    int face = gtgs_faceOf();
    vec3 u = GTGS_FACE_U[face];
    vec3 v = GTGS_FACE_V[face];
    vec3 view = normalize(gtgs_WorldRel);
    float steep = max(-dot(view, GTGS_FACE_NORMAL[face]), 0.3);
    vec2 pace = vec2(dot(view, u), dot(view, v)) / steep * GTGS_PILE_DEPTH * near / float(GTGS_PILE_STEPS);
    vec2 flip = GTGS_FACE_FLIP[face];
    vec2 size = vec2(textureSize(gtgs_BlockMaterial, 0));
    vec2 span = 32.0 / size;
    vec2 start = gtgs_faceSt(gtgs_CameraFract + gtgs_WorldRel, face);
    // Le coin de la texture dans l'atlas, en pixels entiers : au bord d'un bloc, la position dans le monde et les coordonnees de texture ne
    // tombent pas toujours du meme cote, et une difference ici lirait la texture voisine.
    vec2 texel = gtgs_Uv * size;
    vec2 inside = vec2(flip.x < 0.0 ? 1.0 - start.x : start.x, flip.y < 0.0 ? 1.0 - start.y : start.y) * 32.0;
    vec2 origin = (floor(texel) - floor(inside + 0.5 - fract(texel))) / size;
    vec2 at = start;
    float height = material.a;
    // Un pixel plat n'est ni une pointe ni un creux : une dalle basse sous son rail ne s'eclaircit pas de pres.
    vec2 tilt = material.rg * 255.0 - 128.0;
    float shade = dot(tilt, tilt) > 0.25 ? (gtgs_Traced > 0.5 ? GTGS_PILE_SHADE_TRACED : GTGS_PILE_SHADE) : 0.0;
    for (int i = 1; i <= GTGS_PILE_STEPS && height < 1.0 - float(i) / float(GTGS_PILE_STEPS); i++) {
        at += pace;
        height = textureLod(gtgs_BlockMaterial, gtgs_inTexture(at, flip, origin, span), 0.0).a;
    }
    vec2 moved = at - start;
    vec3 shift = u * moved.x + v * moved.y;
    // La lumiere se lit ou le regard s'arrete, tant que la surface y continue : sous le mur qui la borde, elle n'a pas de tuile.
    if (gtgs_material(gtgs_CameraBlock + ivec3(floor(gtgs_CameraFract + gtgs_WorldRel + shift + GTGS_FACE_NORMAL[face] * 0.5))) == 0u) {
        gtgs_Shift = shift;
    }
    vec2 dx = dFdx(gtgs_Uv);
    vec2 dy = dFdy(gtgs_Uv);
    vec3 seen = textureGrad(gtgs_BlockAtlas, gtgs_inTexture(at, flip, origin, span), dx, dy).rgb;
    vec3 flat_ = textureGrad(gtgs_BlockAtlas, gtgs_Uv, dx, dy).rgb;
    color *= seen / max(flat_, vec3(0.02)) * (1.0 + shade * (height - 0.5) * near);
}

// L'eclairement de la surface de ce fragment, lu dans la cellule d'air devant elle, sur la tranche de sa face. Quand elle a
// sa tuile dans le cache de surfaces : sa lumiere directe et son ouverture, plus les rebonds de la zone. Sinon la zone seule,
// et une cellule pas encore cuite laisse la lightmap du vertex.
vec3 gtgs_zoneLight() {
    float reach = max(abs(gtgs_WorldRel.x), abs(gtgs_WorldRel.z));
    int side = gtgs_faceOf();
    float face = float(side);
    vec3 axis = GTGS_FACE_NORMAL[side];
    vec3 point = gtgs_texelPoint();
    // Pres d'un corps, la lumiere qui revient de la piece passe moins : a tous les paliers, sans les tuiles comme avec.
    gtgs_BodyShade = gtgs_bodyOcclusion(point, axis);
    gtgs_BodyLight = 1.0 - gtgs_BodyShade;
    vec3 rel = point + axis * 0.5;
    vec3 toZone = axis * 0.5 + vec3(float(gtgs_CameraBlock.x & 127), float(gtgs_CameraBlock.y - gtgs_LightBase), float(gtgs_CameraBlock.z & 127));
    vec3 at = point + toZone;
    // Au-dessus ou au-dessous de ce que la zone et le loin couvrent, leurs lectures s'arretent a leur derniere rangee : la lumiere d'une autre
    // surface.
    if (at.y < 0.0 || at.y >= 32.0) {
        return gtgs_farLight();
    }
    // Au-dela de ce que la zone couvre a coup sur, le loin prend le relais : la texture de zone se repete, elle montrerait autre chose.
    if (reach >= GTGS_ZONE_END) {
        return gtgs_beyondLight(rel, face, reach);
    }
    vec4 zone = gtgs_zoneRead(at, face);
    vec3 zoned = zone.rgb / max(zone.a, 0.001) * 4.0;
    // La lumiere directe de la zone a la meme part que celle des tuiles : sinon, un anneau se voit autour de la camera.
    vec4 around = gtgs_zoneRead(at, face + 6.0);
    vec3 bounces = around.rgb / max(around.a, 0.001) * 4.0;
    zoned = (zoned - bounces) * gtgs_LightLook.z + bounces;
    // Une surface hors de la grille (plinthe rentree dans son bloc) ou pas encore cuite : le loin. Pres du bord de la zone, les deux se fondent.
    float kept = clamp(zone.a * 8.0, 0.0, 1.0) * (1.0 - smoothstep(GTGS_ZONE_FADE, GTGS_ZONE_END, reach));
    vec3 light = kept >= 1.0 ? zoned : mix(gtgs_beyondLight(rel, face, reach), zoned, kept);
    // Les tuiles couvrent toujours ce qui est proche, et se fondent dans la zone avant le bord de ce qu'elles couvrent : une section qui
    // prend ses tuiles au loin ne se voit pas.
    if (gtgs_SurfaceLight > 0.5 && reach < GTGS_TILE_END) {
        ivec3 cell = ivec3(floor(at));
        if (cell.y >= 0 && cell.y < 32) {
            uint tile = texelFetch(gtgs_SurfaceTiles, ivec3(cell.x & 127, cell.y + int(face) * 32, cell.z & 127), 0).r;
            if (tile != 0u) {
                vec3 lit = gtgs_tileLight(tile, gtgs_CameraFract + gtgs_WorldRel + gtgs_Shift + toZone, at, face, axis);
                float fade = smoothstep(GTGS_TILE_FADE, GTGS_TILE_END, reach);
                gtgs_BodyLight = mix(gtgs_BodyLight, 1.0 - gtgs_BodyShade, fade);
                return mix(lit, light, fade);
            }
        }
    }
    return light;
}

// Le halo des neons : la lumiere que l'air renvoie vers la camera le long du rayon de ce fragment, l'integrale exacte d'une
// source ponctuelle sur le segment, pour les seuls rayons qui passent pres d'une dalle : un voile sur toute l'image ferait
// un brouillard.
vec3 gtgs_airGlow() {
    float along = length(gtgs_ViewPos);
    vec3 ray = gtgs_ViewPos / max(along, 0.001);
    vec3 glow = vec3(0.0);
    int count = min(gtgs_NeonCount, 10);
    for (int i = 0; i < count; i++) {
        vec3 neon = gtgs_NeonPos[i].xyz + gtgs_NeonAxis.xyz * 0.5;
        float closest = dot(neon, ray);
        float side = sqrt(max(dot(neon, neon) - closest * closest, 0.09));
        // Une dalle n'eclaire l'air que sous son plan.
        float near = clamp(1.0 - side / 3.0, 0.0, 1.0) * clamp(0.5 + dot(ray * closest - neon, gtgs_NeonAxis.xyz) * 2.0, 0.0, 1.0);
        glow += gtgs_NeonColor[i].rgb * gtgs_NeonColor[i].a * gtgs_NeonFaces[i].w * (atan((along - closest) / side) + atan(closest / side)) / side * near * near;
    }
    return glow * gtgs_Air.x;
}

// La part de sa limite ou la lumiere d'une surface commence a se tasser.
const float GTGS_LIGHT_KNEE = 0.75;

// La lumiere d'une surface se tasse vers sa limite, composante par composante : sous une dalle, toute lumiere finit blanche et la surface
// a la couleur de sa texture, alors que ce qui n'a que des rebonds garde leur couleur. La limite vaut pour l'image, exposition comprise.
vec3 gtgs_limited(vec3 light) {
    float limit = gtgs_Tone.w / gtgs_LightLook.y;
    if (limit <= 0.0) {
        return light;
    }
    float knee = limit * GTGS_LIGHT_KNEE;
    return min(light, vec3(knee)) + (limit - knee) * (1.0 - exp(-max(light - knee, vec3(0.0)) / (limit - knee)));
}

// Le niveau de la texture d'un bloc ou elle ne fait plus qu'une couleur : le plus petit, que la lecture ne depasse pas.
const float GTGS_WHOLE_TEXTURE = 16.0;
// La hauteur moyenne d'une matiere presque plate.
const float GTGS_NEARLY_FLAT = 0.7;

// La couleur d'une texture, ramenee vers celle de son bloc : sous une lumiere de photo, une texture trop contrastee prend le dessus.
// Ses pixels restent. Un bloc que la grille ne connait pas garde sa texture. La grille ne couvre que la zone et se repete au-dela : hors
// d'elle, la couleur du bloc se lit dans le plus petit niveau de sa texture, sans quoi le contraste changerait par carres de sections.
// Sans lumiere tracee, la table des matieres n'existe pas : la couleur du bloc se lit partout dans sa texture.
vec3 gtgs_calmed(vec3 color) {
    // Un pixel tout en haut d'une matiere presque plate (le rail d'une dalle) est une piece a part, pas du grain : il garde sa couleur. Les
    // brins d'une moquette, eux, en sont le grain : sa hauteur moyenne est bien plus basse.
    float whole = textureLod(gtgs_BlockMaterial, gtgs_Uv, GTGS_WHOLE_TEXTURE).a;
    if (whole > GTGS_NEARLY_FLAT && whole < 1.0 && textureLod(gtgs_BlockMaterial, gtgs_Uv, 0.0).a >= 1.0) {
        return color;
    }
    ivec3 block = gtgs_CameraBlock + ivec3(floor(gtgs_CameraFract + gtgs_WorldRel - gtgs_Normal * 0.01));
    int row = block.y - gtgs_LightBase;
    float beyond = gtgs_Traced < 0.5 || row < 0 || row >= 32 ? 1.0 : smoothstep(GTGS_ZONE_FADE, GTGS_ZONE_END, max(abs(gtgs_WorldRel.x), abs(gtgs_WorldRel.z)));
    vec3 inside = color;
    if (beyond < 1.0) {
        int material = int(gtgs_material(block));
        inside = material < 2 ? color : mix(texelFetch(gtgs_Materials, ivec2(material, 0), 0).rgb, color, gtgs_LightLook.x);
    }
    return beyond > 0.0 ? mix(inside, mix(textureLod(gtgs_BlockAtlas, gtgs_Uv, GTGS_WHOLE_TEXTURE).rgb, color, gtgs_LightLook.x), beyond) : inside;
}

// Ce que renvoie de face une matiere qui n'est pas du metal.
const float GTGS_REFLECTANCE = 0.05;

// Ce qu'une surface renvoie des dalles, au pixel de sa texture : seule la texture de matiere de son bloc le decide, et un pixel mat ne
// renvoie rien, quelle que soit sa face. Le lobe suit le lissage, la normale suit la pente du pixel, plus fort en rasant. Chaque dalle se
// voit au point de son rectangle le plus proche du miroir, par un rayon dans la grille, quatre sur sa surface sous la lumiere tracee : un
// mur ou un corps coupe le reflet.
vec3 gtgs_gloss() {
    if (gtgs_Air.y <= 0.0 || gtgs_SurfaceLight < 0.5) {
        return vec3(0.0);
    }
    vec4 material = texture(gtgs_BlockMaterial, gtgs_Uv);
    float distant = 1.0 - smoothstep(16.0, 32.0, length(gtgs_WorldRel));
    if (material.b < 0.02 || distant <= 0.0) {
        return vec3(0.0);
    }
    int face = gtgs_faceOf();
    vec3 point = gtgs_texelPoint();
    vec3 normal = gtgs_sloped(material) ? normalize(GTGS_FACE_NORMAL[face] + gtgs_slope(material, face)) : GTGS_FACE_NORMAL[face];
    vec3 view = normalize(point - gtgs_CameraFract);
    vec3 mirror = reflect(view, normal);
    float rough = (1.0 - material.b) * (1.0 - material.b);
    float rough2 = rough * rough;
    vec3 start = point + GTGS_FACE_NORMAL[face] * 0.02;
    float side = gtgs_CasterLight.y * 0.5;
    int rays = gtgs_Traced > 0.5 ? 4 : 1;
    int bodies = int(gtgs_CasterRays.x);
    int count = min(gtgs_NeonCount, 10);
    float sum = 0.0;
    for (int i = 0; i < count; i++) {
        vec3 dalle = gtgs_NeonFaces[i].xyz;
        float climb = dalle.y - point.y;
        if (climb <= 0.0) {
            continue;
        }
        vec2 aimed = mirror.y > 0.0 ? point.xz + mirror.xz * climb / mirror.y : point.xz;
        vec3 target = vec3(clamp(aimed.x, dalle.x - side, dalle.x + side), dalle.y, clamp(aimed.y, dalle.z - side, dalle.z + side));
        vec3 toLight = target - point;
        float share = gtgs_NeonColor[i].a * gtgs_NeonFaces[i].w * gtgs_received(toLight, vec3(0.0, -1.0, 0.0), normal, gtgs_CasterRays.w, gtgs_CasterLight.x);
        if (share < 1.0e-3) {
            continue;
        }
        vec3 half_ = normalize(normalize(toLight) - view);
        float across = max(dot(normal, half_), 0.0);
        float spread = across * across * (rough2 - 1.0) + 1.0;
        float leaving = clamp(-dot(view, half_), 0.0, 1.0);
        float fresnel = GTGS_REFLECTANCE + (1.0 - GTGS_REFLECTANCE) * pow(1.0 - leaving, 5.0);
        float shine = share * rough2 / (3.14159265 * spread * spread) * fresnel / (4.0 * max(leaving * leaving, 0.1));
        if (shine < 1.0e-3) {
            continue;
        }
        int seen = 0;
        for (int r = 0; r < rays; r++) {
            float angle = float(r) * 2.3999632;
            float radius = rays > 1 ? sqrt((float(r) + 0.5) / float(rays)) * side : 0.0;
            vec2 aim = clamp(target.xz + vec2(cos(angle), sin(angle)) * radius, dalle.xz - side, dalle.xz + side);
            vec3 toAim = vec3(aim.x, dalle.y - 0.02, aim.y) - start;
            bool hidden = gtgs_march(gtgs_CameraBlock, start, toAim) < 2.0;
            for (int b = 0; b < bodies && !hidden; b++) {
                hidden = gtgs_bodyHides(start, toAim, b);
            }
            if (!hidden) {
                seen++;
            }
        }
        sum += shine * float(seen) / float(rays);
    }
    return gtgs_NeonTint * gtgs_CasterLight.w * sum * gtgs_Air.y * distant;
}

// La longueur du rayon qu'un corps envoie, et la couleur de ce qu'il voit sous un palier sans table des matieres.
const float GTGS_ROOM_RAY = 24.0;
const vec3 GTGS_ROOM_SEEN = vec3(0.6);
// Sans grille des blocs pour voir la piece, la part des dalles proches qui revient d'elle.
const float GTGS_ROOM_AROUND = 0.15;

// La lumiere d'un point d'une surface, relatif au bloc de la camera : celle de sa tuile sous la lumiere tracee, ou celle de la zone.
vec3 gtgs_lightSeen(vec3 at, int side) {
    vec3 axis = GTGS_FACE_NORMAL[side];
    if (gtgs_Traced > 0.5) {
        uint tile = gtgs_tileAt(gtgs_CameraBlock + ivec3(floor(at + axis * 0.5)), side);
        if (tile != 0u) {
            vec2 st = gtgs_faceSt(at, side);
            vec4 bounced = gtgs_bounceAt(gtgs_BounceAtlas, tile, st);
            if (bounced.a > 0.5) {
                return gtgs_directAt(tile, st) * gtgs_LightLook.z + bounced.rgb;
            }
        }
    }
    vec4 zone = gtgs_zoneRead(at + axis * 0.5 + vec3(float(gtgs_CameraBlock.x & 127), float(gtgs_CameraBlock.y - gtgs_LightBase), float(gtgs_CameraBlock.z & 127)), float(side));
    return zone.rgb / max(zone.a, 0.001) * 4.0;
}

// Ce qui revient a un corps de la piece, de ce cote : la surface qu'il voit, sa lumiere, dans la couleur de son bloc. Un corps pres d'un mur
// jaune prend son jaune, et dans le noir il ne recoit rien.
vec3 gtgs_roomLight(vec3 start, vec3 normal) {
    vec3 ray = normal * GTGS_ROOM_RAY;
    ivec3 hit;
    int side;
    float t = gtgs_trace(gtgs_CameraBlock, start, ray, hit, side);
    if (t >= 2.0) {
        return vec3(0.0);
    }
    vec3 color = gtgs_Traced > 0.5 ? texelFetch(gtgs_Materials, ivec2(int(gtgs_material(gtgs_CameraBlock + hit)), 0), 0).rgb : GTGS_ROOM_SEEN;
    return color * gtgs_lightSeen(start + ray * t, side);
}

// La lumiere d'une entite ou de la main, sous la meme lumiere que le decor : ce que donne chaque dalle proche, de son cote, s'il n'y a
// pas de bloc entre elles, et ce que la piece lui renvoie. Sans lightmap : elle ne dit rien de ce qui eclaire vraiment.
vec3 gtgs_entityLight() {
    vec3 normal = normalize(gtgs_Normal);
    vec3 point = gtgs_CameraFract + gtgs_WorldRel;
    // Le point du corps qui regarde les dalles, sur une grille de seize par bloc : l'ombre d'un bloc le traverse en marches, comme le
    // terrain. La main, elle, tient toute dans un bloc : elle regarde depuis la camera, et l'ombre d'un mur ne la decoupe pas.
    vec3 body = dot(gtgs_WorldRel, gtgs_WorldRel) < 2.0 ? gtgs_CameraFract : (floor(point * 16.0) + 0.5) / 16.0 + normal * 0.1;
    float side = gtgs_CasterLight.y * 0.5;
    vec3 extent = vec3(side, 0.0, side);
    float direct = 0.0;
    float around = 0.0;
    int count = min(gtgs_NeonCount, 10);
    for (int i = 0; i < count; i++) {
        vec3 face = gtgs_NeonFaces[i].xyz;
        vec3 toLight = clamp(point, face - extent, face + extent) - point;
        vec3 direction = normalize(toLight);
        float share = gtgs_NeonColor[i].a * gtgs_NeonFaces[i].w * gtgs_received(toLight, vec3(0.0, -1.0, 0.0), direction, gtgs_CasterRays.w, gtgs_CasterLight.x);
        if (share > 0.0) {
            around += share;
            if (gtgs_CasterRays.y < 0.5 || gtgs_march(gtgs_CameraBlock, body, face + vec3(0.0, -0.02, 0.0) - body) >= 2.0) {
                direct += share * (dot(normal, direction) * 0.5 + 0.5);
            }
        }
    }
    // Sans le cache de surfaces, la grille des blocs n'existe pas : ce que la piece renvoie s'estime d'apres les dalles proches.
    vec3 room = gtgs_SurfaceLight > 0.5 ? gtgs_roomLight(body, normal) : gtgs_NeonTint * gtgs_CasterLight.w * around * GTGS_ROOM_AROUND;
    // Une main sous une dalle ne brule pas : la lumiere se tasse comme celle d'une surface, a la meme exposition.
    vec3 light = gtgs_Ambient * 2.4 + gtgs_NeonTint * gtgs_CasterLight.w * direct * gtgs_LightLook.z + room;
    return gtgs_limited(light) * gtgs_LightLook.y;
}

// Le brouillard de la vue, pose par-dessus la lumiere : les distances et la couleur de vanilla, calculees au fragment.
vec3 gtgs_fog(vec3 color) {
    vec3 r = gtgs_WorldRel;
    float distance = gtgs_FogRange.z > 0.5 ? max(length(r.xz), abs(r.y)) : length(r);
    return mix(color, gtgs_FogTint.rgb, smoothstep(gtgs_FogRange.x, gtgs_FogRange.y, distance) * gtgs_FogTint.a);
}

// La courbe de l'image : son pied enfonce ce qui est presque noir vers le vrai noir, sans toucher au reste ; ses hautes lumieres
// se tassent vers 1 au-dessus de l'epaule, au lieu de saturer d'un coup.
vec3 gtgs_tone(vec3 color) {
    vec3 c = color * color / (color + vec3(gtgs_Tone.x) + 1.0e-5);
    float shoulder = gtgs_FogRange.w;
    if (shoulder >= 1.0) {
        return c;
    }
    vec3 over = max(c - shoulder, 0.0) / (1.0 - shoulder);
    return min(c, vec3(shoulder)) + (1.0 - shoulder) * (1.0 - exp(-over));
}

vec4 gtgs_applyLook(vec4 c) {
    if (gtgs_LookId == 0) {
        return c;
    }
    float t = gtgs_LookParams.w;
    if (gtgs_BakedLight > 0.5 && gtgs_Terrain > 0.5) {
        // La face d'un neon brille d'elle-meme ; toute autre surface recoit sa lumiere cuite, moins ce que les cris retirent.
        if (gtgs_onNeonFace()) {
            c.rgb *= max(gtgs_neonFace(), 0.15);
        } else {
            gtgs_enterPile(c.rgb);
            c.rgb = gtgs_calmed(c.rgb);
            vec3 lit = gtgs_limited(gtgs_darkPixels(gtgs_zoneLight()));
            c.rgb = (c.rgb * lit * gtgs_BodyLight + gtgs_gloss()) * (1.0 - gtgs_disturbance(gtgs_WorldRel)) * gtgs_LightLook.y;
        }
        c.rgb = gtgs_fog(c.rgb) + gtgs_airGlow();
    } else if (gtgs_BakedLight > 0.5 && gtgs_Lit > 0.5) {
        c.rgb *= gtgs_entityLight() * (1.0 - gtgs_disturbance(gtgs_WorldRel));
    } else {
        bool twin = gtgs_NeonFace.z > gtgs_NeonFace.x;
        vec3 position = gtgs_lightPosition();
        vec3 light = vec3(0.0);
        for (int i = 0; i < gtgs_NeonCount; i++) {
            vec3 d = position - gtgs_NeonPos[i].xyz;
            float dist = length(d);
            // Rien au-dessus du plan de la dalle.
            float k = clamp(1.0 - dist / gtgs_NeonPos[i].w, 0.0, 1.0) * step(0.5, dot(d, gtgs_NeonAxis.xyz));
            float state = gtgs_NeonColor[i].a;
            light += gtgs_NeonColor[i].rgb * (state * k * k);
            // Sans le jumeau du calendrier, la face d'un neon proche et son voisinage immediat suivent son etat envoye.
            if (!twin) {
                c.rgb *= mix(1.0, max(state, 0.15), step(dist, 0.9));
            }
        }
        // Les tubes voisins s'additionnent ; la somme est plafonnee pour qu'un couloir dense ne vire pas au blanc.
        c.rgb *= 1.0 + min(light, vec3(0.8));
        if (twin) {
            c.rgb *= max(gtgs_neonFace(), 0.15);
        }
    }
    c.rgb = gtgs_grade(gtgs_tone(c.rgb), gtgs_LookId);
    // Le grain ne remonte pas le noir : il s'efface avec la lumiere.
    float grain = (gtgs_hash(gl_FragCoord.xy + vec2(fract(t * 7.0) * 97.0, fract(t * 3.0) * 61.0)) - 0.5) * gtgs_LookParams.y
            * smoothstep(0.0, 0.12, dot(c.rgb, vec3(0.299, 0.587, 0.114)));
    float flicker = 1.0 - gtgs_LookParams.z * (0.5 + 0.5 * sin(t * 23.0) * sin(t * 7.3 + 1.7));
    c.rgb = clamp(c.rgb * flicker + grain, 0.0, 1.0);
    return c;
}
