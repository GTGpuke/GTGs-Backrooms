#version 150

// Le rebond d'une tuile, en rayons : chaque pixel lance les memes rayons dans son demi-espace, repartis selon le cosinus de sa
// normale ; un rayon qui touche une surface y reprend sa lumiere, dans la couleur de son bloc. Une marche dans la grille des blocs
// voit tout ce qui est sur le chemin : les coins s'assombrissent d'eux-memes, et un mur eclaire teinte ce qui lui fait face. Le second
// tour compte aussi les rebonds suivants, par une part fixe de ce qu'il ajoute : une estimation par pixel y mettrait des taches. Rien
// de tire au hasard : au repos, deux calculs donnent la meme tuile.

#pragma gtgs_includes

// Une ligne par matiere : couleur moyenne et 1 si elle emet.
uniform sampler2D gtgs_Materials;
// Le premier tour d'un calcul : ce qu'une surface renvoie au second tour a deja rebondi une fois.
uniform sampler2D gtgs_BouncePrevious;
// 1 au second tour ; 0 au premier, qui ne relit que la lumiere directe : le resultat ne depend pas de ce qui etait la avant.
uniform float gtgs_PreviousShare;
// Coin de la tuile dans le cache des rebonds, en pixels.
uniform ivec2 gtgs_TileOrigin;
// Cellule d'air devant la surface, position du monde, et face (0 a 5 : +X, -X, +Y, -Y, +Z, -Z).
uniform ivec3 gtgs_Cell;
uniform int gtgs_Face;
uniform int gtgs_Rays;
// Part de la lumiere que renvoient les surfaces.
uniform float gtgs_Bounce;
// Ce que les surfaces ajoutent a la couleur de leur bloc dans ce qu'elles renvoient.
uniform vec3 gtgs_BounceColor;
// 1 pour une surface neuve : elle garde les rebonds que la texture de zone lui donnait, et sa tuile arrive sans que sa lumiere change.
// Le balayage suivant les trace.
uniform float gtgs_Fresh;

out vec4 fragColor;

const int BOUNCE_TILE = 8;
const float RAY_LENGTH = 24.0;
// Ce que valent tous les rebonds a partir du second, en parts du second : chacun garde environ la moitie du precedent.
const float FOLLOWING = 2.0;
const float MAX_BOUNCE = 4.0;
const float OFFSET = 0.02;

// Le premier tour a cette place de sa tuile, au pixel : une lecture filtree dependrait de la place de la tuile dans le cache, et un
// recalcul complet ne redonnerait pas le meme arrondi.
vec3 previousAt(uint tile, vec2 st) {
    ivec2 corner = ivec2(int(tile) % 128, int(tile) / 128) * BOUNCE_TILE;
    vec3 value = texelFetch(gtgs_BouncePrevious, corner + ivec2(floor(st * float(BOUNCE_TILE))), 0).rgb;
    return value * value * MAX_BOUNCE;
}

// Ce que renvoie la surface touchee, avant la couleur de son bloc.
vec3 lightAt(ivec3 cell, int face, vec3 at) {
    uint tile = gtgs_tileAt(cell, face);
    if (tile != 0u) {
        vec2 st = gtgs_faceSt(at, face);
        return gtgs_directAt(tile, st) + previousAt(tile, st) * gtgs_PreviousShare;
    }
    vec3 where = vec3(float(cell.x & 127), float(cell.y - gtgs_LightBase), float(cell.z & 127)) + vec3(0.5);
    vec4 zone = gtgs_zoneRead(where, float(face));
    return zone.rgb / max(zone.a, 0.001) * 4.0;
}

void main() {
    vec2 st = (vec2(ivec2(gl_FragCoord.xy) - gtgs_TileOrigin) + 0.5) / float(BOUNCE_TILE);
    vec3 normal = GTGS_FACE_NORMAL[gtgs_Face];
    vec3 u = GTGS_FACE_U[gtgs_Face];
    vec3 v = GTGS_FACE_V[gtgs_Face];
    if (gtgs_Fresh > 0.5) {
        vec3 where = vec3(float(gtgs_Cell.x & 127), float(gtgs_Cell.y - gtgs_LightBase), float(gtgs_Cell.z & 127)) + vec3(0.5) + u * (st.x - 0.5) + v * (st.y - 0.5);
        vec4 around = gtgs_zoneRead(where, float(gtgs_Face) + 6.0);
        fragColor = vec4(sqrt(clamp(around.rgb / max(around.a, 0.001) * 4.0 / MAX_BOUNCE, 0.0, 1.0)), 1.0);
        return;
    }
    vec3 start = vec3(0.5) - normal * (0.5 - OFFSET) + u * (st.x - 0.5) + v * (st.y - 0.5);
    vec3 sum = vec3(0.0);
    for (int r = 0; r < gtgs_Rays; r++) {
        float angle = float(r) * 2.3999632;
        float share = (float(r) + 0.5) / float(gtgs_Rays);
        float radius = sqrt(share);
        vec3 direction = (normal * sqrt(1.0 - share) + u * (cos(angle) * radius) + v * (sin(angle) * radius)) * RAY_LENGTH;
        ivec3 hit;
        int face;
        float t = gtgs_trace(gtgs_Cell, start, direction, hit, face);
        if (t < 2.0) {
            vec4 material = texelFetch(gtgs_Materials, ivec2(int(gtgs_material(gtgs_Cell + hit)), 0), 0);
            // La lumiere d'une dalle a deja ete comptee par le cache de surfaces.
            if (material.a < 0.5) {
                sum += material.rgb * gtgs_BounceColor * lightAt(gtgs_Cell + hit + ivec3(GTGS_FACE_NORMAL[face]), face, vec3(gtgs_Cell) + start + direction * t);
            }
        }
    }
    vec3 bounced = sum / float(gtgs_Rays) * gtgs_Bounce;
    if (gtgs_PreviousShare > 0.5) {
        vec3 root = texelFetch(gtgs_BouncePrevious, ivec2(gl_FragCoord.xy), 0).rgb;
        vec3 first = root * root * MAX_BOUNCE;
        bounced = first + max(bounced - first, vec3(0.0)) * FOLLOWING;
    }
    fragColor = vec4(sqrt(clamp(bounced / MAX_BOUNCE, 0.0, 1.0)), 1.0);
}
