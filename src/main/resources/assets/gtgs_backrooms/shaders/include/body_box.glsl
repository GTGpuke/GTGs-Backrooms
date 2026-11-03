// Le passage d'un rayon dans une boite orientee : les ombres des corps, faites des cubes de leur modele.

// Le segment de start a start + toTarget passe-t-il dans cette boite, un centre et trois demi-axes perpendiculaires, grossie de grow
// blocs de chaque cote ? Dans le repere de la boite, ou elle va de -1 a 1 sur chaque axe, la part du segment entre les deux faces de
// chaque axe. Grossie, elle contient tout ce qui passe a moins de grow d'elle : un faisceau de rayons voisins s'y trie d'un coup.
bool gtgs_hitsBox(vec3 start, vec3 toTarget, vec3 center, vec3 u, vec3 v, vec3 w, float grow) {
    vec3 offset = start - center;
    float enter = 0.0;
    float leave = 1.0;
    for (int i = 0; i < 3; i++) {
        vec3 axis = i == 0 ? u : (i == 1 ? v : w);
        float length2 = dot(axis, axis);
        float limit = 1.0 + grow * inversesqrt(length2);
        float from = dot(offset, axis) / length2;
        float along = dot(toTarget, axis) / length2;
        if (abs(along) < 1.0e-6) {
            if (abs(from) > limit) {
                return false;
            }
        } else {
            float low = (-limit - from) / along;
            float high = (limit - from) / along;
            enter = max(enter, min(low, high));
            leave = min(leave, max(low, high));
        }
    }
    return enter <= leave;
}
