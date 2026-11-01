#version 150

// Post masque d'un look sur la copie de l'ecran (Sampler0) : bloom (Sampler1, force w), vignette (x) et aberration
// chromatique (y). Avec gtgs_PostParams.z a 1 (sous un pack de shaders ou un mod qui dessine le terrain, dont les shaders
// ne portent pas le look), le look de l'etage 1 (etalonnage du look gtgs_PostLook.x, grain, vacillement) s'applique
// d'abord. Ni discard ni gl_FragDepth, pour que le test de stencil rejette les autres pixels avant ce shader.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 gtgs_PostParams;
uniform vec4 gtgs_PostLook;
// Style du preset visuel : bande usee (x), part de la hauteur sous chaque bande noire (y).
uniform vec2 gtgs_PostStyle;

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

void main() {
    vec2 uv = texCoord;
    float tape = gtgs_PostStyle.x;
    if (tape > 0.0) {
        // La bande ondule, et chaque ligne glisse un peu d'un tirage a l'autre.
        float line = floor(uv.y * 240.0);
        uv.x += sin(uv.y * 30.0 + gtgs_PostLook.w * 2.0) * 0.0009 * tape + (gtgs_hash(vec2(line, floor(gtgs_PostLook.w * 24.0))) - 0.5) * 0.0018 * tape;
    }
    vec2 d = uv - vec2(0.5);
    vec4 c = texture(Sampler0, uv);
    if (gtgs_PostParams.y > 0.0) {
        vec2 shift = d * gtgs_PostParams.y * 0.03;
        c.r = texture(Sampler0, uv + shift).r;
        c.b = texture(Sampler0, uv - shift).b;
    }
    if (gtgs_PostParams.z > 0.5) {
        float t = gtgs_PostLook.w;
        c.rgb = gtgs_grade(c.rgb, int(gtgs_PostLook.x + 0.5));
        float grain = (gtgs_hash(gl_FragCoord.xy + vec2(fract(t * 7.0) * 97.0, fract(t * 3.0) * 61.0)) - 0.5) * gtgs_PostLook.y;
        float flicker = 1.0 - gtgs_PostLook.z * (0.5 + 0.5 * sin(t * 23.0) * sin(t * 7.3 + 1.7));
        c.rgb = clamp(c.rgb * flicker + grain, 0.0, 1.0);
    }
    if (gtgs_PostParams.w > 0.0) {
        c.rgb += texture(Sampler1, texCoord).rgb * gtgs_PostParams.w;
    }
    float r2 = dot(d, d) * 2.0;
    c.rgb *= 1.0 - gtgs_PostParams.x * smoothstep(0.2, 1.1, r2);
    if (tape > 0.0) {
        c.rgb *= 1.0 - 0.06 * tape * step(0.5, fract(gl_FragCoord.y / 3.0));
        c.rgb += (gtgs_hash(gl_FragCoord.xy + vec2(gtgs_PostLook.w * 91.0, 0.0)) - 0.5) * 0.05 * tape;
    }
    if (abs(texCoord.y - 0.5) > 0.5 - gtgs_PostStyle.y) {
        c.rgb = vec3(0.0);
    }
    fragColor = vec4(clamp(c.rgb, 0.0, 1.0), 1.0);
}
