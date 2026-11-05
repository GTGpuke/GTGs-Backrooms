#version 150

// La passe des ombres des corps : pour chaque tuile du cache de surfaces proche d'un corps et vue par la camera, ce que les corps
// retirent a chacun de ses texels de la lumiere des dalles (gtgs_casterRemoved, body_shadow.glsl), ecrit dans son emplacement du
// cache des ombres, un texel de bordure autour (la lumiere lisse du terrain le mele au bord de la tuile). Le terrain lit ce cache au
// lieu de refaire le calcul a chaque fragment : une tuile se calcule une fois par image, quel que soit le nombre de pixels qui la
// montrent. Le texel se calcule au meme point que le terrain lirait sur la grille des texels : le centre du texel sur la face.

#pragma gtgs_includes

// La tuile : emplacement + 1 et face (x : emplacement dans les seize bits bas, face au-dessus), cellule d'air devant la surface,
// relative au bloc de la camera (yzw).
flat in ivec4 gtgs_Tile;

out float fragColor;

const int SLOT = 34;
const int SLOTS_ACROSS = 64;
const int TILE = 32;

void main() {
    int place = (gtgs_Tile.x & 0xFFFF) - 1;
    int face = gtgs_Tile.x >> 16;
    ivec2 origin = ivec2(place % SLOTS_ACROSS, place / SLOTS_ACROSS) * SLOT + 1;
    ivec2 pixel = ivec2(gl_FragCoord.xy) - origin;
    vec2 st = (vec2(pixel) + 0.5) / float(TILE);
    vec3 normal = GTGS_FACE_NORMAL[face];
    vec3 point = vec3(gtgs_Tile.yzw) + vec3(0.5) - normal * 0.5 + GTGS_FACE_U[face] * (st.x - 0.5) + GTGS_FACE_V[face] * (st.y - 0.5);
    fragColor = gtgs_casterRemoved(point, normal);
}
