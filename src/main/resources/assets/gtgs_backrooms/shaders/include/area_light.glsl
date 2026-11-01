// Ce qu'une dalle donne a une surface, le meme calcul que la cuisson (AreaLight). Une dalle n'eclaire que devant elle, depuis le
// point de son rectangle le plus proche de la surface, dans son angle et jusqu'a sa portee, sans cassure.

// Part du carre de la portee a laquelle la lumiere a perdu la moitie de sa force.
const float GTGS_SOFT_SHARE = 0.16;

// Ce qu'il reste de la lumiere d'une dalle a cette distance.
float gtgs_falloff(float distance2, float reach) {
    float share = distance2 / (reach * reach);
    float left = clamp(1.0 - share * share, 0.0, 1.0);
    return left * left / (1.0 + share / GTGS_SOFT_SHARE);
}

// La part d'une dalle que recoit une surface de cette normale : toLight va de la surface au point le plus proche de la dalle, axis
// est la normale de la dalle, cone le cosinus de l'angle ou elle cesse d'eclairer. Rien derriere elle, rien hors de sa portee.
float gtgs_received(vec3 toLight, vec3 axis, vec3 surface, float reach, float cone) {
    float distance2 = dot(toLight, toLight);
    float front = -dot(toLight, axis);
    if (front <= 0.0 || distance2 >= reach * reach) {
        return 0.0;
    }
    float distance = sqrt(distance2);
    float facing = dot(toLight, surface) / distance;
    float emitted = (front / distance - cone) / (1.0 - cone);
    if (facing <= 0.0 || emitted <= 0.0) {
        return 0.0;
    }
    return facing * emitted * gtgs_falloff(distance2, reach);
}
