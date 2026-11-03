#version 150

// Post masque d'un look sur la copie de l'ecran (Sampler0) : bloom (Sampler1, force w), vignette (x) et aberration
// chromatique (y). Avec gtgs_PostParams.z a 1 (sous un pack de shaders ou un mod qui dessine le terrain, dont les shaders
// ne portent pas le look), le look de l'etage 1 (etalonnage du look gtgs_PostLook.x, grain, vacillement) s'applique
// d'abord. Ni discard ni gl_FragDepth, pour que le test de stencil rejette les autres pixels avant ce shader.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 gtgs_PostParams;
uniform vec4 gtgs_PostLook;
// Style du preset visuel : cassette (x), part de la hauteur sous chaque bande noire (y), tremble a l'epaule (z), bruit de la bande (w).
uniform vec4 gtgs_PostStyle;

in vec2 texCoord;

out vec4 fragColor;

// Copie a l'identique de look_grade.glsl (les tests comparent les deux) : les shaders du post n'importent pas les
// bibliotheques du mod.
uniform sampler3D gtgs_LookLut;

vec3 gtgs_grade(vec3 c, int look) {
    vec3 size = vec3(textureSize(gtgs_LookLut, 0));
    // Des centres des texels extremes de l'etage du look : l'interpolation ne deborde jamais sur l'etage voisin.
    vec3 p = clamp(c, 0.0, 1.0) * (size.x - 1.0) + 0.5;
    p.z += float(look) * size.x;
    return texture(gtgs_LookLut, p / size).rgb;
}

float gtgs_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Un tirage de la bande, de 0 a 1, pour deux entiers positifs (une colonne ou une ligne, et un numero d'image) : sur des entiers non
// signes, sans sinus, aucun motif ne s'y dessine, meme pour de grands numeros.
float gtgs_draw(float a, float b) {
    uint h = uint(a) * 0x9E3779B1u + uint(b) * 0x85EBCA77u;
    h ^= h >> 15;
    h *= 0xC2B2AE3Du;
    h ^= h >> 13;
    h *= 0x27D4EB2Fu;
    h ^= h >> 16;
    return float(h >> 8) / 16777216.0;
}

const vec3 GTGS_LUMA = vec3(0.299, 0.587, 0.114);
// Lignes d'une trame de cassette, et images par seconde de son bruit.
const float GTGS_TAPE_LINES = 240.0;
const float GTGS_TAPE_RATE = 30.0;
// Ce que les lignes d'une trame gardent de leur place, contre ce qu'elles prennent a la ligne exacte : une image de 240 lignes, adoucie.
const float GTGS_FIELD = 0.5;
// Demi-hauteur de la bande de suivi, et sa vitesse de remontee, en hauteurs d'image par seconde.
const float GTGS_TRACK_HEIGHT = 0.025;
const float GTGS_TRACK_SPEED = 0.05;
// Part de la hauteur d'une ligne que prennent les traits blancs (pertes, tirets).
const float GTGS_WHITE_LINE = 0.35;
// Hauteur du bas de l'image que dechire le changement de tete.
const float GTGS_HEAD_HEIGHT = 0.018;
// Le cadre d'une cassette : 4:3, au milieu de l'ecran.
const float GTGS_FRAME_RATIO = 4.0 / 3.0;
// L'objectif du camescope : ce qui resserre les bords de l'image, ou les droites se courbent, le milieu restant presque droit (termes
// en r^2 et en r^4), et la frange de couleur qu'il y laisse.
const float GTGS_LENS = 0.01;
const float GTGS_LENS_EDGE = 0.02;
const float GTGS_FRINGE = 0.0025;
// Le tremble a l'epaule, en demi-hauteurs du cadre ; l'image garde autant de marge.
const float GTGS_HAND = 0.0008;
// Accentuation du magnetoscope : le contour clair et sombre de chaque bord.
const float GTGS_SHARPEN = 0.8;
// Poids des voisins d'un point sur sa ligne, pour la luminosite molle (pres, exp(-k^2/2)) et la base de l'accentuation (large, exp(-k^2/12)),
// et leurs sommes sur les neuf voisins.
const float GTGS_NEAR[5] = float[5](1.0, 0.6065, 0.1353, 0.0111, 0.0003);
const float GTGS_WIDE[5] = float[5](1.0, 0.92, 0.7165, 0.4724, 0.2636);
const float GTGS_NEAR_SUM = 2.5064;
const float GTGS_WIDE_SUM = 5.745;

// Un signal video : la luminosite (Y) et ses deux composantes de couleur (I, Q), et le retour.
vec3 gtgs_yiq(vec3 c) {
    return vec3(dot(c, GTGS_LUMA), dot(c, vec3(0.596, -0.274, -0.322)), dot(c, vec3(0.211, -0.523, 0.312)));
}

vec3 gtgs_rgb(vec3 s) {
    return vec3(s.x + 0.956 * s.y + 0.621 * s.z, s.x - 0.272 * s.y - 0.647 * s.z, s.x - 1.106 * s.y + 1.703 * s.z);
}

// La luminosite d'un point lue sur la bande : molle, la bande ne tenant pas les details fins, puis accentuee par le magnetoscope, qui
// cerne chaque bord d'un lisere clair d'un cote et sombre de l'autre. step : pas entre deux voisins sur la ligne.
float gtgs_tapeLuma(vec2 uv, float step) {
    float near = 0.0;
    float wide = 0.0;
    for (int i = -4; i <= 4; i++) {
        float l = dot(texture(Sampler0, uv + vec2(float(i) * step, 0.0)).rgb, GTGS_LUMA);
        near += l * GTGS_NEAR[abs(i)];
        wide += l * GTGS_WIDE[abs(i)];
    }
    near /= GTGS_NEAR_SUM;
    wide /= GTGS_WIDE_SUM;
    return near + GTGS_SHARPEN * (near - wide);
}

// La couleur d'un point : enregistree bien plus bas que la luminosite, elle bave sur une large part de la ligne et arrive en retard
// (decalee vers la droite).
vec2 gtgs_tapeChroma(vec2 uv, float step) {
    vec2 chroma = vec2(0.0);
    for (int i = 0; i < 8; i++) {
        chroma += gtgs_yiq(texture(Sampler0, uv + vec2((float(i) - 4.5) * step, 0.0)).rgb).yz;
    }
    return chroma / 8.0;
}

// Ce que les fortes lumieres laissent a leur droite : la bande sature, et la trainee met un moment a retomber.
float gtgs_smear(vec2 uv, float step) {
    float trail = 0.0;
    for (int i = 1; i <= 5; i++) {
        float l = dot(texture(Sampler0, uv - vec2(float(i) * step, 0.0)).rgb, GTGS_LUMA);
        trail += max(l - 0.8, 0.0) * (1.0 - float(i) / 6.0);
    }
    return trail;
}

// La bande de suivi : quelques lignes qui remontent lentement l'image, 1 en son milieu, et qui ne se montrent que rarement, quelques
// secondes de temps en temps.
float gtgs_track(float y, float t) {
    float shown = smoothstep(0.9, 0.97, 0.5 + 0.5 * sin(t * 0.13) * sin(t * 0.29));
    return (1.0 - smoothstep(0.0, GTGS_TRACK_HEIGHT, abs(y - fract(1.0 - t * GTGS_TRACK_SPEED)))) * shown;
}

void main() {
    vec2 uv = texCoord;
    float tape = gtgs_PostStyle.x;
    float t = gtgs_PostLook.w;
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 d = uv - vec2(0.5);
    vec4 c = texture(Sampler0, uv);
    float shown = 1.0;
    if (tape > 0.0) {
        float noise = gtgs_PostStyle.w;
        float frame = floor(t * GTGS_TAPE_RATE);
        float line = floor(texCoord.y * GTGS_TAPE_LINES);
        // Le cadre 4:3 au milieu de l'ecran, de -1 a 1 sur chaque axe ; au-dela, les bandes noires. Ses lignes sont celles d'une trame, qui
        // bougent d'un cinquieme de ligne d'une image a la suivante : l'entrelacement, sans faire sautiller les bords.
        float width = min(1.0, size.y * GTGS_FRAME_RATIO / size.x);
        float y = mix(texCoord.y, (line + 0.4 + 0.2 * mod(frame, 2.0)) / GTGS_TAPE_LINES, GTGS_FIELD);
        vec2 framed = vec2((texCoord.x - 0.5) / (width * 0.5), (y - 0.5) * 2.0);
        d = framed * 0.5;
        float across = framed.x;
        // Le temps d'une ligne n'est pas tout a fait tenu : chaque ligne glisse a peine d'un tirage a l'autre, celles de la bande de suivi
        // davantage, le haut de l'image penche un peu et le bas se dechire au changement de tete. Le cadre, lui, reste droit.
        float head = 1.0 - smoothstep(0.0, GTGS_HEAD_HEIGHT, texCoord.y);
        float slip = (gtgs_draw(line, frame) - 0.5) * 0.0005 + sin(texCoord.y * 40.0 + t * 1.3) * 0.0002
                + smoothstep(0.95, 1.0, texCoord.y) * 0.002 * sin(t * 0.7)
                + head * (0.027 + (gtgs_draw(line + 300.0, frame) - 0.5) * 0.02)
                + (gtgs_draw(line + 600.0, frame) - 0.5) * 0.025 * gtgs_track(texCoord.y, t) * noise;
        framed.x -= slip;
        // L'objectif : les droites se courbent vers les bords de l'image, le cadre reste droit. Le milieu s'agrandit d'autant, pour que
        // l'image remplisse le cadre jusqu'aux coins, avec la marge du tremble a l'epaule.
        vec2 p = vec2(framed.x * GTGS_FRAME_RATIO, framed.y);
        float r2 = dot(p, p);
        float corner = GTGS_FRAME_RATIO * GTGS_FRAME_RATIO + 1.0;
        float bend = (1.0 + GTGS_LENS * r2 + GTGS_LENS_EDGE * r2 * r2) / ((1.0 + GTGS_LENS * corner + GTGS_LENS_EDGE * corner * corner) * (1.0 + GTGS_HAND));
        vec2 hand = gtgs_PostStyle.z * GTGS_HAND * vec2(sin(t * 0.83) * 0.6 + sin(t * 2.1 + 1.3) * 0.3 + sin(t * 5.3) * 0.1,
                sin(t * 0.71 + 2.0) * 0.6 + sin(t * 1.9) * 0.3 + sin(t * 4.7 + 0.5) * 0.1);
        vec2 seen = framed * bend + hand;
        uv = vec2(0.5 + seen.x * width * 0.5, 0.5 + seen.y * 0.5);
        // Le signal : luminosite molle et accentuee, trainee des lumieres, couleur qui bave et tremble un peu d'une ligne a l'autre.
        float pixel = size.y / 1080.0 / size.x;
        float luma = gtgs_tapeLuma(uv, 1.5 * pixel) + gtgs_smear(uv, 4.5 * pixel) * 0.6;
        vec2 chroma = gtgs_tapeChroma(uv, 6.0 * pixel);
        float hue = 0.04 * sin(t * 0.5) + (gtgs_draw(line + 900.0, frame) - 0.5) * 0.045 * noise;
        chroma = mat2(cos(hue), sin(hue), -sin(hue), cos(hue)) * chroma;
        // Le bruit de la bande : des traits le long des lignes, discrets dans le clair, forts dans le sombre, ou il prend aussi de la couleur.
        float scale = size.y / 1080.0;
        float dark = 1.0 - smoothstep(0.05, 0.45, luma);
        float row = line * 4096.0 + mod(frame, 4096.0);
        float grain = gtgs_draw(floor(gl_FragCoord.x / (3.0 * scale)), row) - 0.5;
        float blot = floor(gl_FragCoord.x / (14.0 * scale));
        vec2 tint = vec2(gtgs_draw(blot + 1000.0, row), gtgs_draw(blot + 2000.0, row)) - 0.5;
        luma += grain * (0.03 * mix(0.6, 1.0, smoothstep(0.0, 0.25, dark)) + 0.05 * dark) * noise;
        chroma += tint * 0.1 * dark * noise;
        c.rgb = gtgs_rgb(vec3(luma, chroma));
        // La frange de l'objectif : vers les bords, le rouge et le bleu ne tombent pas au meme endroit que le vert.
        vec2 fringe = (uv - 0.5) * GTGS_FRINGE * r2;
        vec3 straight = texture(Sampler0, uv).rgb;
        c.r += texture(Sampler0, uv + fringe).r - straight.r;
        c.b += texture(Sampler0, uv - fringe).b - straight.b;
        // De rares pertes blanches ; dans la bande de suivi et le bas dechire, des tirets blancs le long des lignes. Les traits blancs ne
        // prennent qu'un tiers de la hauteur d'une ligne : fins.
        float thin = step(fract(texCoord.y * GTGS_TAPE_LINES), GTGS_WHITE_LINE);
        float drop = step(0.9985, gtgs_draw(line + 1200.0, frame)) * step(0.6, gtgs_draw(floor(gl_FragCoord.x / (40.0 * scale)) + 3000.0, row));
        float dash = step(0.82, gtgs_draw(floor(gl_FragCoord.x / (6.0 * scale)) + 4000.0, row)) * thin;
        float track = gtgs_track(texCoord.y, t) * (dash * 0.5 - 0.07);
        float torn = head * (dash * 0.35 - 0.035);
        c.rgb += (drop * thin * 0.45 + track + torn) * noise;
        // Hors du cadre, le noir, pose apres le bloom et la vignette : des bords droits.
        shown = step(abs(across), 1.0);
    }
    if (gtgs_PostParams.y > 0.0 && tape <= 0.0) {
        vec2 shift = d * gtgs_PostParams.y * 0.03;
        c.r = texture(Sampler0, uv + shift).r;
        c.b = texture(Sampler0, uv - shift).b;
    }
    if (gtgs_PostParams.z > 0.5) {
        c.rgb = gtgs_grade(c.rgb, int(gtgs_PostLook.x + 0.5));
        float grain = (gtgs_hash(gl_FragCoord.xy + vec2(fract(t * 7.0) * 97.0, fract(t * 3.0) * 61.0)) - 0.5) * gtgs_PostLook.y;
        float flicker = 1.0 - gtgs_PostLook.z * (0.5 + 0.5 * sin(t * 23.0) * sin(t * 7.3 + 1.7));
        c.rgb = clamp(c.rgb * flicker + grain, 0.0, 1.0);
    }
    if (gtgs_PostParams.w > 0.0) {
        c.rgb += texture(Sampler1, uv).rgb * gtgs_PostParams.w;
    }
    float r2 = dot(d, d) * 2.0;
    c.rgb *= 1.0 - gtgs_PostParams.x * smoothstep(0.2, 1.1, r2);
    if (abs(texCoord.y - 0.5) > 0.5 - gtgs_PostStyle.y) {
        shown = 0.0;
    }
    c.rgb *= shown;
    fragColor = vec4(clamp(c.rgb, 0.0, 1.0), 1.0);
}
