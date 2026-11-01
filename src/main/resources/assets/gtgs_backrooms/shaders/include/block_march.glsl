// La grille des blocs de la zone : 128 blocs de cote qui se repetent, 32 de haut, l'indice de la matiere de chaque bloc. Le calcul des
// tuiles et le terrain y marchent bloc par bloc.

uniform usampler3D gtgs_Blocks;
// Y du premier bloc de la zone.
uniform int gtgs_LightBase;

// La matiere d'un bloc du monde ; 1 hors de la grille en hauteur, opaque.
uint gtgs_material(ivec3 world) {
    int row = world.y - gtgs_LightBase;
    if (row < 0 || row >= 32) {
        return 1u;
    }
    return texelFetch(gtgs_Blocks, ivec3(world.x & 127, row, world.z & 127), 0).r;
}

// Marche dans la grille depuis un point relatif au bloc base : rend la longueur parcourue jusqu'au premier bloc, en longueurs de la
// direction, ou 2 si rien ne l'arrete avant 1. Pose le bloc touche, relatif a base, et la face de la surface touchee : la meme
// numerotation que les tuiles, la face regarde d'ou vient le rayon.
float gtgs_trace(ivec3 base, vec3 origin, vec3 direction, out ivec3 hit, out int face) {
    ivec3 cell = ivec3(floor(origin));
    ivec3 step = ivec3(sign(direction));
    vec3 delta = vec3(1.0e6);
    vec3 next = vec3(1.0e6);
    hit = cell;
    face = 0;
    for (int axis = 0; axis < 3; axis++) {
        if (direction[axis] != 0.0) {
            delta[axis] = abs(1.0 / direction[axis]);
            next[axis] = (direction[axis] > 0.0 ? float(cell[axis]) + 1.0 - origin[axis] : origin[axis] - float(cell[axis])) * delta[axis];
        }
    }
    for (int i = 0; i < 48; i++) {
        int axis = next.x <= next.y && next.x <= next.z ? 0 : (next.y <= next.z ? 1 : 2);
        float t = next[axis];
        if (t > 1.0) {
            return 2.0;
        }
        next[axis] += delta[axis];
        cell[axis] += step[axis];
        if (gtgs_material(base + cell) != 0u) {
            hit = cell;
            face = axis * 2 + (step[axis] > 0 ? 1 : 0);
            return t;
        }
    }
    return 2.0;
}

// La meme marche, quand seul compte ce qui l'arrete.
float gtgs_march(ivec3 base, vec3 origin, vec3 direction) {
    ivec3 hit;
    int face;
    return gtgs_trace(base, origin, direction, hit, face);
}
