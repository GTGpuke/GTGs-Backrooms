// Les neons de la vue et tout ce qui en depend : le bloc d'uniformes rempli par vue (NeonLights), les corps qui portent une ombre, les
// passages et cadres des portails. Lu par les shaders du terrain (look.glsl) et par la passe des ombres des corps (body_shadow.fsh).

uniform int gtgs_NeonCount;
// Bloc entier de la camera de la vue : les positions du bloc lui sont relatives, sans perdre de precision loin de l'origine.
uniform ivec3 gtgs_CameraBlock;

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
    // puis haut, nombre de boites et rang du cadre d'un corps de l'autre cote), puis leurs boites, les cubes de leur modele (centre et
    // rayon de la sphere qui la resume, puis trois demi-axes, le plus long d'abord). Ceux de ce cote d'abord, puis ceux de l'autre cote
    // des cadres, reportes derriere eux.
    vec4 gtgs_Caster[528];
    // Nombre de corps de ce cote (x), rayons vers chaque dalle (y, 0 sans ombres de corps), corps de l'autre cote des cadres (z), portee
    // des dalles (w).
    vec4 gtgs_CasterRays;
    // Cosinus de l'angle d'une dalle (x), cote de son rectangle (y), ce qui change le carre de la lumiere d'une tuile en parts de dalles (z),
    // lumiere d'une dalle (w).
    vec4 gtgs_CasterLight;
    // Passages des portails : centre relatif au bloc de la camera, dans les axes du monde (xyz), et face par laquelle on y entre (w) ; puis
    // ce qui en sort, mesure dans la lumiere cuite de l'autre cote, dans ses unites.
    vec4 gtgs_Openings[8];
    // Nombre d'ouvertures (x), de cadres (y) et de neons vus par eux (z), et de neons du halo d'avant le plan d'arrivee (w).
    vec4 gtgs_OpeningCount;
    // Cadres des portails, qui arretent les rayons de leur monde : centre relatif au bloc de la camera et face, demi-largeur, demi-hauteur.
    vec4 gtgs_Frames[12];
    // Neons de l'autre cote vus par un cadre, reportes de ce cote : centre relatif au bloc de la camera et face, puis couleur et rang du cadre.
    vec4 gtgs_Seen[32];
    // Plan du cadre d'arrivee de la vue, relatif au bloc de sa camera : normale du cote vu (xyz) et decalage (w), dot(normale, point) + w >= 0
    // du cote vu ; nul pour le monde du joueur.
    vec4 gtgs_ViewPlane;
    // Dans une vue, le halo de l'air d'avant son plan d'arrivee : les neons du halo de la vue qui la contient, passes par le portail, relatifs
    // au bloc de sa camera : face qui eclaire (xyz) et sa direction (w), puis couleur, etat et part comptes (rgb).
    vec4 gtgs_Halo[20];
};
