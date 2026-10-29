// Look d'une dimension Backrooms, applique a la couleur finale de chaque fragment (etage 1 du moteur de shaders).
// Avec gtgs_LookId a 0 (vanilla), la couleur ressort telle quelle. Les neons proches (gtgs_NeonBlock, en espace camera,
// remplis par vue) eclairent ce qui les entoure ; la face de chaque neon du terrain, reconnue a sa texture, suit son
// etat par le jumeau du calendrier (neon_schedule.glsl) et ce que les cris proches lui retirent.

uniform int gtgs_LookId;
uniform vec4 gtgs_LookTint;
uniform vec4 gtgs_LookParams;
uniform int gtgs_NeonCount;
// Camera de la vue : bloc entier et reste, pour retrouver le bloc d'un fragment sans perdre de precision loin de l'origine.
uniform ivec3 gtgs_CameraBlock;
uniform vec3 gtgs_CameraFract;
// Tick du monde de la vue dans la periode de l'horloge commune (x), graine des neons de sa dimension (y).
uniform ivec2 gtgs_NeonClock;
// Rectangle de la texture du neon dans l'atlas des blocs (u0, v0, u1, v1) ; vide, le jumeau est coupe.
uniform vec4 gtgs_NeonFace;
// Parts des neons qui vacillent (x) et morts (y), celles du profil.
uniform vec2 gtgs_NeonShares;

layout(std140) uniform gtgs_NeonBlock {
    vec4 gtgs_NeonPos[32];
    vec4 gtgs_NeonColor[32];
    // Sources des perturbations (cris) de la dimension de la vue, relatives a sa camera dans les axes du monde (xyz), rayon (w).
    vec4 gtgs_Disturb[4];
    // Force de chaque perturbation : amplitude du son et plafond de clignotement compris.
    vec4 gtgs_DisturbForce;
};

in vec3 gtgs_ViewPos;
in vec3 gtgs_WorldRel;
in vec2 gtgs_Uv;
in vec3 gtgs_Normal;
flat in float gtgs_Terrain;

float gtgs_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// 1 pour tout fragment qui n'est pas la face d'un neon du terrain ; sinon son etat, moins ce que les cris lui retirent.
float gtgs_neonFace() {
    if (gtgs_Terrain < 0.5 || gtgs_NeonFace.z <= gtgs_NeonFace.x || gtgs_Uv.x < gtgs_NeonFace.x || gtgs_Uv.y < gtgs_NeonFace.y
            || gtgs_Uv.x > gtgs_NeonFace.z || gtgs_Uv.y > gtgs_NeonFace.w) {
        return 1.0;
    }
    // Le bloc du neon : un centieme de bloc derriere la face, cote interieur.
    ivec3 cell = ivec3(floor(gtgs_CameraFract + gtgs_WorldRel - gtgs_Normal * 0.01));
    float state = gtgs_neonIntensity(gtgs_CameraBlock + cell, gtgs_NeonClock.y, gtgs_NeonClock.x, gtgs_NeonShares.x, gtgs_NeonShares.y);
    vec3 center = vec3(cell) + 0.5 - gtgs_CameraFract;
    float drop = 0.0;
    for (int i = 0; i < 4; i++) {
        float radius = gtgs_Disturb[i].w;
        if (radius > 0.0) {
            drop = max(drop, gtgs_DisturbForce[i] * clamp(1.0 - length(center - gtgs_Disturb[i].xyz) / radius, 0.0, 1.0));
        }
    }
    return state * (1.0 - drop);
}

vec4 gtgs_applyLook(vec4 c) {
    if (gtgs_LookId == 0) {
        return c;
    }
    float t = gtgs_LookParams.w;
    float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));
    c.rgb = mix(c.rgb, vec3(l), gtgs_LookParams.x);
    c.rgb = mix(c.rgb, c.rgb * gtgs_LookTint.rgb, gtgs_LookTint.a);
    bool twin = gtgs_NeonFace.z > gtgs_NeonFace.x;
    vec3 light = vec3(0.0);
    for (int i = 0; i < gtgs_NeonCount; i++) {
        vec3 d = gtgs_ViewPos - gtgs_NeonPos[i].xyz;
        float dist = length(d);
        float k = clamp(1.0 - dist / gtgs_NeonPos[i].w, 0.0, 1.0);
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
    float grain = (gtgs_hash(gl_FragCoord.xy + vec2(fract(t * 7.0) * 97.0, fract(t * 3.0) * 61.0)) - 0.5) * gtgs_LookParams.y;
    float flicker = 1.0 - gtgs_LookParams.z * (0.5 + 0.5 * sin(t * 23.0) * sin(t * 7.3 + 1.7));
    c.rgb = clamp(c.rgb * flicker + grain, 0.0, 1.0);
    return c;
}
