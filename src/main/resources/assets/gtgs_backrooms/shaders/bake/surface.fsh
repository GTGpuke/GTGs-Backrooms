#version 150

// Le calcul d'une tuile du cache de surfaces, pour chaque pixel de texture d'une face de bloc. Quatre valeurs separees, pour
// que le terrain lisse ce qui doit etre lisse et garde sur la grille ce qui s'y lit : la racine de la lumiere qui arrive sans
// rien sur son chemin, lisse (r) ; la part de cette lumiere que le pixel voit vraiment, ses ombres portees (g) ; la part du
// ciel de la surface que rien ne cache (b) ; ce que le relief du bloc ajoute ou retire, autour de 1 (a, divise par 2).
// Rien de tire au hasard : une tuile recalculee sans que rien ait change donne les memes valeurs, et le bord d'une ombre
// passe d'une tuile a la suivante sans couture.

#pragma gtgs_includes

uniform sampler2D gtgs_BlockAtlas;
// Coin de la tuile dans le cache, en pixels.
uniform ivec2 gtgs_TileOrigin;
// Cellule d'air devant la surface, position du monde, et face (0 a 5 : +X, -X, +Y, -Y, +Z, -Z).
uniform ivec3 gtgs_Cell;
uniform int gtgs_Face;
// Rectangle de la texture du bloc dans l'atlas (u0, v0, u1, v1).
uniform vec4 gtgs_Sprite;
uniform int gtgs_LightCount;
// Neons de cette surface : position du bloc (xyz) et face qui eclaire (w).
uniform ivec4 gtgs_Lights[24];
// Lumiere d'une dalle (x), cote du rectangle qui eclaire (y), ecart des rayons autour de son centre (z), portee (w), en blocs. Le
// palier Ultra vise toute la dalle : ses ombres ont leur vraie penombre.
uniform vec4 gtgs_Emission;
// Cosinus de l'angle ou une dalle cesse d'eclairer, compte depuis sa normale.
uniform float gtgs_Cone;
// Force du relief.
uniform float gtgs_Relief;
// Rayons par dalle (x) et rayons de l'ouverture (y), selon le palier.
uniform ivec2 gtgs_Rays;

out vec4 fragColor;

const int TILE = 32;
const float AO_LENGTH = 2.0;
const float MAX_LIGHT = 4.0;
const float OFFSET = 0.02;
// Part de la lumiere d'une surface sous laquelle une dalle n'a plus droit qu'a deux rayons : elle ne dessine pas d'ombre lisible.
const float WEAK_LIGHT = 0.02;

// Coordonnees de texture d'un point d'une face, avec l'orientation du modele d'un cube, dans le rectangle du bloc.
vec2 inSprite(vec2 st) {
    vec2 flip = GTGS_FACE_FLIP[gtgs_Face];
    return mix(gtgs_Sprite.xy, gtgs_Sprite.zw, fract(vec2(flip.x < 0.0 ? 1.0 - st.x : st.x, flip.y < 0.0 ? 1.0 - st.y : st.y)));
}

float height(vec2 st) {
    return dot(textureLod(gtgs_BlockAtlas, inSprite(st), 0.0).rgb, vec3(0.299, 0.587, 0.114));
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy) - gtgs_TileOrigin;
    vec2 st = (vec2(pixel) + 0.5) / float(TILE);
    vec3 normal = GTGS_FACE_NORMAL[gtgs_Face];
    vec3 u = GTGS_FACE_U[gtgs_Face];
    vec3 v = GTGS_FACE_V[gtgs_Face];
    vec3 point = vec3(0.5) - normal * 0.5 + u * (st.x - 0.5) + v * (st.y - 0.5);
    vec3 start = point + normal * OFFSET;

    // Le relief penche la normale de chaque pixel : celui de la texture de normales du bloc, ou sans elle celui qu'on devine, un pixel
    // clair etant plus haut que ses voisins sombres.
    vec3 lit = normal;
    bool relief = gtgs_Relief > 0.0 && gtgs_Sprite.z > gtgs_Sprite.x;
    if (relief) {
        vec4 material = textureLod(gtgs_BlockMaterial, inSprite(st), 0.0);
        vec3 slope;
        if (gtgs_sloped(material)) {
            slope = gtgs_slope(material, gtgs_Face);
        } else {
            float texel = 1.0 / float(TILE);
            slope = -(height(st + vec2(texel, 0.0)) - height(st - vec2(texel, 0.0))) * u - (height(st + vec2(0.0, texel)) - height(st - vec2(0.0, texel))) * v;
        }
        lit = normalize(normal + gtgs_Relief * slope);
    }

    float arriving = 0.0;
    float seenLight = 0.0;
    float raised = 0.0;
    float size = gtgs_Emission.y * 0.5;
    for (int i = 0; i < gtgs_LightCount; i++) {
        ivec4 neon = gtgs_Lights[i];
        vec3 axis = GTGS_FACE_NORMAL[neon.w];
        vec3 center = vec3(neon.xyz - gtgs_Cell) + vec3(0.5) + axis * 0.5;
        vec3 ax = axis.z != 0.0 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 0.0, 1.0);
        vec3 ay = cross(axis, ax);
        vec3 extent = (abs(ax) + abs(ay)) * size;
        vec3 toLight = clamp(point, center - extent, center + extent) - point;
        float form = gtgs_received(toLight, axis, normal, gtgs_Emission.w, gtgs_Cone);
        if (form < 1.0e-4) {
            continue;
        }
        // Une dalle se voit par quelques rayons, vers les memes points d'un pixel a l'autre : le bord d'une ombre reste net, en
        // marches de la taille d'un pixel de texture, comme une ombre de Minecraft et non comme du bruit.
        int rays = form < WEAK_LIGHT ? min(gtgs_Rays.x, 2) : gtgs_Rays.x;
        float seen = 0.0;
        for (int s = 0; s < rays; s++) {
            float angle = float(s) * 2.3999632;
            float radius = sqrt((float(s) + 0.5) / float(rays)) * gtgs_Emission.z;
            vec3 target = center + axis * OFFSET + ax * (cos(angle) * radius) + ay * (sin(angle) * radius);
            if (gtgs_march(gtgs_Cell, start, target - start) >= 2.0) {
                seen += 1.0;
            }
        }
        arriving += form;
        seenLight += form * seen / float(rays);
        raised += relief ? gtgs_received(toLight, axis, lit, gtgs_Emission.w, gtgs_Cone) : form;
    }

    // Les blocs tout proches cachent le ciel de la surface : c'est ce qui assombrit les coins.
    float open = 0.0;
    for (int r = 0; r < gtgs_Rays.y; r++) {
        float angle = float(r) * 2.3999632;
        float share = (float(r) + 0.5) / float(gtgs_Rays.y);
        float radius = sqrt(share);
        vec3 direction = normal * sqrt(1.0 - share) + u * (cos(angle) * radius) + v * (sin(angle) * radius);
        float t = gtgs_march(gtgs_Cell, start, direction * AO_LENGTH);
        open += t >= 2.0 ? 1.0 : t;
    }

    float lightness = sqrt(clamp(arriving * gtgs_Emission.x / MAX_LIGHT, 0.0, 1.0));
    float shadow = arriving > 0.0 ? seenLight / arriving : 1.0;
    float reliefShare = arriving > 0.0 ? clamp(raised / arriving * 0.5, 0.0, 1.0) : 0.5;
    fragColor = vec4(lightness, shadow, open / float(gtgs_Rays.y), reliefShare);
}
