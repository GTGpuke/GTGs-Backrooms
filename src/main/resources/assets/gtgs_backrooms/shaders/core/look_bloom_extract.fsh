#version 150

// Bloom, premiere passe : les pixels les plus lumineux de la copie de l'ecran, a demi-resolution ; le reste noir.

uniform sampler2D Sampler0;
uniform float gtgs_BloomThreshold;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb;
    float brightness = max(c.r, max(c.g, c.b));
    float k = max(brightness - gtgs_BloomThreshold, 0.0) / max(1.0 - gtgs_BloomThreshold, 0.001);
    fragColor = vec4(c * k, 1.0);
}
