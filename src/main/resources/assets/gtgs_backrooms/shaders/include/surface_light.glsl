// Ce qu'une surface recoit, relu la ou un rayon la touche : le terrain et le calcul des rebonds y puisent. Une face a sa tuile dans le
// cache de surfaces, retrouvee par l'indirection ; sinon la texture de zone.

// Texture de zone de la lumiere cuite : 128 blocs de cote qui se repetent, 32 de haut, une tranche par face de cellule (lumiere
// complete), puis six autres sans la lumiere directe.
uniform sampler3D gtgs_LightVolume;
// Cache de surfaces : la lumiere directe et l'ouverture de chaque pixel de texture, et l'indirection vers sa tuile.
uniform sampler2D gtgs_SurfaceAtlas;
uniform usampler3D gtgs_SurfaceTiles;
// Couleur de la lumiere d'une dalle, la plus forte composante a 1.
uniform vec3 gtgs_NeonTint;
// Rebonds : une tuile de 8 pixels par surface, 1 dans sa derniere composante une fois calculee.
uniform sampler2D gtgs_BounceAtlas;
// La matiere des blocs, pixel pour pixel avec l'atlas des blocs : pente (r, g ; 0 sans texture de normales), lissage (b), hauteur (a).
uniform sampler2D gtgs_BlockMaterial;

// Les axes de chaque face (0 a 5 : +X, -X, +Y, -Y, +Z, -Z), et le sens de la texture du modele d'un cube le long des deux derniers.
const vec3 GTGS_FACE_NORMAL[6] = vec3[6](vec3(1.0, 0.0, 0.0), vec3(-1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, -1.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 0.0, -1.0));
const vec3 GTGS_FACE_U[6] = vec3[6](vec3(0.0, 0.0, 1.0), vec3(0.0, 0.0, 1.0), vec3(1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0), vec3(1.0, 0.0, 0.0));
const vec3 GTGS_FACE_V[6] = vec3[6](vec3(0.0, 1.0, 0.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 0.0, 1.0), vec3(0.0, 1.0, 0.0), vec3(0.0, 1.0, 0.0));
const vec2 GTGS_FACE_FLIP[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(1.0, -1.0), vec2(1.0, -1.0), vec2(-1.0, -1.0));

// true si ce pixel de matiere vient d'une texture de normales.
bool gtgs_sloped(vec4 material) {
    return material.r + material.g > 0.0;
}

// La pente d'un pixel d'une texture de normales, dans les axes du monde, sur cette face.
vec3 gtgs_slope(vec4 material, int face) {
    vec2 tilt = (material.rg * 255.0 - 128.0) / 127.0 * GTGS_FACE_FLIP[face];
    return GTGS_FACE_U[face] * tilt.x + GTGS_FACE_V[face] * tilt.y;
}

// Une tranche de la texture de zone. La validite divise la couleur : seules les surfaces voisines comptent.
vec4 gtgs_zoneRead(vec3 at, float layer) {
    vec3 where = at;
    where.y = clamp(where.y, 0.5, 31.5) + layer * 32.0;
    return texture(gtgs_LightVolume, where / vec3(128.0, 384.0, 128.0));
}

// La tuile de la face d'une cellule d'air, position du monde ; 0 si elle n'en a pas.
uint gtgs_tileAt(ivec3 cell, int face) {
    int row = cell.y - gtgs_LightBase;
    if (row < 0 || row >= 32) {
        return 0u;
    }
    return texelFetch(gtgs_SurfaceTiles, ivec3(cell.x & 127, row + face * 32, cell.z & 127), 0).r;
}

// La place d'un point dans sa face, de 0 a 1 sur chaque axe.
vec2 gtgs_faceSt(vec3 at, int face) {
    return fract(face < 2 ? at.zy : (face < 4 ? at.xz : at.xy));
}

// La lumiere directe d'une tuile a cette place, ombres comprises : la vraie part de chaque dalle, celle des rayons. Sans le relief :
// une petite tuile de rebond ne touche que quelques points, et le grain d'une texture y ferait des taches.
vec3 gtgs_directAt(uint tile, vec2 st) {
    ivec2 corner = ivec2(int(tile) % 128, int(tile) / 128) * 32;
    vec4 pixel = texelFetch(gtgs_SurfaceAtlas, corner + ivec2(floor(st * 32.0)), 0);
    return gtgs_NeonTint * pixel.r * pixel.r * 4.0 * pixel.g;
}

// Le rebond d'une tuile a cette place, lisse dans la tuile, et si elle est calculee (a).
vec4 gtgs_bounceAt(sampler2D atlas, uint tile, vec2 st) {
    vec2 place = vec2(int(tile) % 128, int(tile) / 128) * 8.0 + clamp(st * 8.0, 0.5, 7.5);
    vec4 value = textureLod(atlas, place / vec2(1024.0, 2048.0), 0.0);
    return vec4(value.rgb * value.rgb * 4.0, value.a);
}
