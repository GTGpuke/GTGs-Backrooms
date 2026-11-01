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
// Part du contraste des textures gardee (x), exposition des surfaces (y) et part du direct (z), sous la lumiere tracee.
uniform vec3 gtgs_TracedLook;
// Force des halos autour des neons (x), et de la brillance des matieres (y).
uniform vec2 gtgs_Air;
// Lumiere de fond du profil.
uniform vec3 gtgs_Ambient;
// Pied de la courbe (x), part du degrade sur la grille des texels (y), grain de pixels dans le noir (z).
uniform vec3 gtgs_Tone;
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
    // Corps qui portent une ombre, relatifs au bloc de la camera : centre du bas et rayon, puis haut (x).
    vec4 gtgs_Caster[48];
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

// La part de ce qu'une surface recoit vraiment que les corps lui retirent, de 0 a 1. Les rayons visent toute la dalle : l'ombre d'un
// corps est nette a son contact et se dilue en s'en eloignant. Un rayon arrete par un corps ne compte que si les blocs le laissaient
// passer : un corps ne fait pas d'ombre a une dalle qu'on ne voit pas d'ici. seen est ce que la surface recoit, en parts de dalles.
float gtgs_casterShade(vec3 point, vec3 normal, float seen) {
    int bodies = int(gtgs_CasterRays.x);
    int rays = int(gtgs_CasterRays.y);
    if (rays == 0 || bodies == 0 || seen <= 0.0) {
        return 0.0;
    }
    // Les corps assez proches pour que leur ombre compte ici, un bit par cylindre.
    int nearby = 0;
    for (int b = 0; b < bodies; b++) {
        vec3 toBody = gtgs_Caster[b * 2].xyz - point;
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
        // Seuls comptent les corps que le rayon vers le centre de la dalle frole, de la largeur de la dalle pres : tous les autres
        // rayons passent loin, et la plupart des dalles s'arretent ici.
        int crossing = 0;
        for (int b = 0; b < bodies; b++) {
            if ((nearby >> b & 1) != 0) {
                vec4 body = gtgs_Caster[b * 2];
                if (gtgs_crosses(start, toFace, vec4(body.x, body.y - side, body.z, body.w + side), gtgs_Caster[b * 2 + 1].x + side)) {
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
        for (int s = 0; s < rays; s++) {
            float angle = float(s) * 2.3999632;
            float radius = sqrt((float(s) + 0.5) / float(rays)) * side;
            vec3 toTarget = face + vec3(cos(angle) * radius, -0.02, sin(angle) * radius) - start;
            for (int b = 0; b < bodies; b++) {
                if ((crossing >> b & 1) != 0 && gtgs_crosses(start, toTarget, gtgs_Caster[b * 2], gtgs_Caster[b * 2 + 1].x)) {
                    if (gtgs_march(gtgs_CameraBlock, start, toTarget) >= 2.0) {
                        hidden++;
                    }
                    break;
                }
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
    // Plusieurs dalles se partagent une surface : l'ombre d'une seule y serait a peine lisible, la courbe la creuse.
    float shadow = pixels ? pixel.g : lisse.g;
    // Les corps retirent ce qu'ils cachent vraiment, apres la courbe : une ombre de corps ne se creuse pas.
    float kept = 1.0 - gtgs_casterShade(gtgs_texelPoint(), axis, lisse.r * lisse.r * gtgs_CasterLight.z * shadow);
    float open = pixels ? pixel.b : lisse.b;
    // La pente d'un pixel se lit au pixel, meme sous une lumiere lisse : c'est le grain de sa matiere.
    if (gtgs_Traced > 0.5) {
        vec4 traced = gtgs_bounceAt(gtgs_BounceAtlas, tile, smoothSt);
        if (traced.a > 0.5) {
            // Les rebonds font le gros de la lumiere : plus de courbe pour creuser les ombres, plus de grille.
            return gtgs_NeonTint * arriving * arriving * 4.0 * shadow * kept * pixel.a * 2.0 * gtgs_TracedLook.z + traced.rgb + gtgs_Ambient * open;
        }
    }
    shadow *= shadow * kept;
    vec3 direct = gtgs_NeonTint * arriving * arriving * 4.0 * shadow * pixel.a * 2.0;
    vec4 bounced = gtgs_zoneRead(at, face + 6.0);
    return direct + bounced.rgb / max(bounced.a, 0.001) * 4.0 * open;
}

// Distances, en blocs, ou la lumiere passe d'un niveau au suivant : des tuiles a la zone, de la zone a la lightmap. Les tuiles couvrent
// au moins 32 blocs autour de la camera, la zone au moins 48.
const float GTGS_TILE_FADE = 24.0;
const float GTGS_TILE_END = 32.0;
const float GTGS_ZONE_FADE = 40.0;
const float GTGS_ZONE_END = 48.0;
const float GTGS_ZONE_WALLS = 0.14;

// La lightmap vanilla, ramenee a l'exposition de la zone : une piece eclairee au loin l'est autant que de pres. Elle ne connait pas
// l'orientation des surfaces, et les dalles eclairent d'abord les sols.
vec3 gtgs_farLight() {
    return gtgs_Lightmap * (1.25 + 2.75 * max(gtgs_Normal.y, 0.0) - 0.35 * max(-gtgs_Normal.y, 0.0)) * (gtgs_Traced > 0.5 ? 1.0 : 0.85);
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
    color *= seen / max(flat_, vec3(0.02)) * (1.0 + (gtgs_Traced > 0.5 ? GTGS_PILE_SHADE_TRACED : GTGS_PILE_SHADE) * (height - 0.5) * near);
}

// L'eclairement de la surface de ce fragment, lu dans la cellule d'air devant elle, sur la tranche de sa face. Quand elle a
// sa tuile dans le cache de surfaces : sa lumiere directe et son ouverture, plus les rebonds de la zone. Sinon la zone seule,
// et une cellule pas encore cuite laisse la lightmap du vertex.
vec3 gtgs_zoneLight() {
    // Au-dela de ce que la zone couvre a coup sur, la lightmap prend le relais : la texture se repete, elle montrerait autre chose.
    float reach = max(abs(gtgs_WorldRel.x), abs(gtgs_WorldRel.z));
    if (reach >= GTGS_ZONE_END) {
        return gtgs_farLight();
    }
    int side = gtgs_faceOf();
    float face = float(side);
    vec3 axis = GTGS_FACE_NORMAL[side];
    vec3 toZone = axis * 0.5 + vec3(float(gtgs_CameraBlock.x & 127), float(gtgs_CameraBlock.y - gtgs_LightBase), float(gtgs_CameraBlock.z & 127));
    vec3 at = gtgs_texelPoint() + toZone;
    // Au-dessus ou au-dessous de ce que la zone couvre, ses lectures s'arretent a sa derniere rangee : la lumiere d'une autre surface.
    if (at.y < 0.0 || at.y >= 32.0) {
        return gtgs_farLight();
    }
    vec4 zone = gtgs_zoneRead(at, face);
    // Sous la lumiere tracee, les rebonds des tuiles eclairent les murs un peu plus que ceux de la zone : un mur qui attend ses tuiles ne se
    // decoupe pas sur ses voisins.
    vec3 zoned = zone.rgb / max(zone.a, 0.001) * 4.0 * (gtgs_Traced > 0.5 ? 1.0 + GTGS_ZONE_WALLS * (1.0 - abs(axis.y)) : 1.0);
    // Une surface hors de la grille (plinthe rentree dans son bloc) ou pas encore cuite : la lightmap, a l'exposition de la zone.
    vec3 light = mix(gtgs_farLight(), zoned, clamp(zone.a * 8.0, 0.0, 1.0) * (1.0 - smoothstep(GTGS_ZONE_FADE, GTGS_ZONE_END, reach)));
    // Les tuiles couvrent toujours ce qui est proche, et se fondent dans la zone avant le bord de ce qu'elles couvrent : une section qui
    // prend ses tuiles au loin ne se voit pas.
    if (gtgs_SurfaceLight > 0.5 && reach < GTGS_TILE_END) {
        ivec3 cell = ivec3(floor(at));
        if (cell.y >= 0 && cell.y < 32) {
            uint tile = texelFetch(gtgs_SurfaceTiles, ivec3(cell.x & 127, cell.y + int(face) * 32, cell.z & 127), 0).r;
            if (tile != 0u) {
                return mix(gtgs_tileLight(tile, gtgs_CameraFract + gtgs_WorldRel + gtgs_Shift + toZone, at, face, axis), light, smoothstep(GTGS_TILE_FADE, GTGS_TILE_END, reach));
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

// Le niveau de la texture d'un bloc ou elle ne fait plus qu'une couleur : le plus petit, que la lecture ne depasse pas.
const float GTGS_WHOLE_TEXTURE = 16.0;

// La couleur d'une texture, ramenee vers celle de son bloc : sous une lumiere de photo, une texture trop contrastee prend le dessus.
// Ses pixels restent. Un bloc que la grille ne connait pas garde sa texture. La grille ne couvre que la zone et se repete au-dela : hors
// d'elle, la couleur du bloc se lit dans le plus petit niveau de sa texture, sans quoi le contraste changerait par carres de sections.
vec3 gtgs_calmed(vec3 color) {
    ivec3 block = gtgs_CameraBlock + ivec3(floor(gtgs_CameraFract + gtgs_WorldRel - gtgs_Normal * 0.01));
    int row = block.y - gtgs_LightBase;
    float beyond = row < 0 || row >= 32 ? 1.0 : smoothstep(GTGS_ZONE_FADE, GTGS_ZONE_END, max(abs(gtgs_WorldRel.x), abs(gtgs_WorldRel.z)));
    vec3 inside = color;
    if (beyond < 1.0) {
        int material = int(gtgs_material(block));
        inside = material < 2 ? color : mix(texelFetch(gtgs_Materials, ivec2(material, 0), 0).rgb, color, gtgs_TracedLook.x);
    }
    return beyond > 0.0 ? mix(inside, mix(textureLod(gtgs_BlockAtlas, gtgs_Uv, GTGS_WHOLE_TEXTURE).rgb, color, gtgs_TracedLook.x), beyond) : inside;
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
                hidden = gtgs_crosses(start, toAim, gtgs_Caster[b * 2], gtgs_Caster[b * 2 + 1].x);
            }
            if (!hidden) {
                seen++;
            }
        }
        sum += shine * float(seen) / float(rays);
    }
    return gtgs_NeonTint * gtgs_CasterLight.w * sum * gtgs_Air.y * distant;
}

// La lumiere d'une entite ou de la main, sous la meme lumiere que le decor : ce que donne chaque dalle proche, de son cote, s'il n'y a
// pas de bloc entre elles, et ce qui revient autour d'elle. Sans lightmap : elle ne dit rien de ce qui eclaire vraiment.
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
    // Sous la lumiere tracee, presque tout vient des surfaces autour de lui. Une main sous une dalle ne brule pas : la lumiere se tasse.
    vec3 light = gtgs_Ambient * 2.4 + gtgs_NeonTint * gtgs_CasterLight.w * (direct * (gtgs_Traced > 0.5 ? gtgs_TracedLook.z : 1.0) + around * (gtgs_Traced > 0.5 ? 0.6 : 0.15));
    return (1.0 - exp(-light / 1.3)) * 1.3;
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
            if (gtgs_Traced > 0.5) {
                c.rgb = gtgs_calmed(c.rgb);
            }
            c.rgb = (c.rgb * gtgs_darkPixels(gtgs_zoneLight()) + gtgs_gloss()) * (1.0 - gtgs_disturbance(gtgs_WorldRel)) * (gtgs_Traced > 0.5 ? gtgs_TracedLook.y : 1.0);
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
