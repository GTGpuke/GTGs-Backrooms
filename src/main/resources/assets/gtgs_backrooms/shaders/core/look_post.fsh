#version 150

// Post masque d'un look sur la copie de l'ecran (Sampler0) : bloom (Sampler1, force w), vignette (x) et aberration
// chromatique (y). Avec gtgs_PostParams.z a 1 (sous un pack de shaders ou un mod qui dessine le terrain, dont les shaders
// ne portent pas le look), le look
// de l'etage 1 (teinte, desaturation, grain, vacillement) s'applique d'abord. Ni discard ni gl_FragDepth, pour que le
// test de stencil rejette les autres pixels avant ce shader.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 gtgs_PostParams;
uniform vec4 gtgs_PostTint;
uniform vec4 gtgs_PostLook;

in vec2 texCoord;

out vec4 fragColor;

float gtgs_hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 d = texCoord - vec2(0.5);
    vec4 c = texture(Sampler0, texCoord);
    if (gtgs_PostParams.y > 0.0) {
        vec2 shift = d * gtgs_PostParams.y * 0.03;
        c.r = texture(Sampler0, texCoord + shift).r;
        c.b = texture(Sampler0, texCoord - shift).b;
    }
    if (gtgs_PostParams.z > 0.5) {
        float t = gtgs_PostLook.w;
        float l = dot(c.rgb, vec3(0.299, 0.587, 0.114));
        c.rgb = mix(c.rgb, vec3(l), gtgs_PostLook.x);
        c.rgb = mix(c.rgb, c.rgb * gtgs_PostTint.rgb, gtgs_PostTint.a);
        float grain = (gtgs_hash(gl_FragCoord.xy + vec2(fract(t * 7.0) * 97.0, fract(t * 3.0) * 61.0)) - 0.5) * gtgs_PostLook.y;
        float flicker = 1.0 - gtgs_PostLook.z * (0.5 + 0.5 * sin(t * 23.0) * sin(t * 7.3 + 1.7));
        c.rgb = clamp(c.rgb * flicker + grain, 0.0, 1.0);
    }
    if (gtgs_PostParams.w > 0.0) {
        c.rgb += texture(Sampler1, texCoord).rgb * gtgs_PostParams.w;
    }
    float r2 = dot(d, d) * 2.0;
    c.rgb *= 1.0 - gtgs_PostParams.x * smoothstep(0.2, 1.1, r2);
    fragColor = vec4(clamp(c.rgb, 0.0, 1.0), 1.0);
}
