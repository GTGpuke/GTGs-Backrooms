// Etalonnage d'un look : sa LUT (LookLut), un etage de la texture 3D gtgs_LookLut par identifiant de look, appliquee a
// une couleur de 0 a 1. Le shader du post en garde une copie a l'identique, que les tests comparent.

uniform sampler3D gtgs_LookLut;

vec3 gtgs_grade(vec3 c, int look) {
    vec3 size = vec3(textureSize(gtgs_LookLut, 0));
    // Des centres des texels extremes de l'etage du look : l'interpolation ne deborde jamais sur l'etage voisin.
    vec3 p = clamp(c, 0.0, 1.0) * (size.x - 1.0) + 0.5;
    p.z += float(look) * size.x;
    return texture(gtgs_LookLut, p / size).rgb;
}
