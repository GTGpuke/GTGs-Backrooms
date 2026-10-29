#version 150

// Bloom, flou separable : neuf echantillons le long d'un axe (gtgs_BlurStep : un texel vers la droite ou vers le haut),
// a quart de resolution, une passe par axe.

uniform sampler2D Sampler0;
uniform vec2 gtgs_BlurStep;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(Sampler0, texCoord).rgb * 0.227;
    c += (texture(Sampler0, texCoord + gtgs_BlurStep).rgb + texture(Sampler0, texCoord - gtgs_BlurStep).rgb) * 0.194;
    c += (texture(Sampler0, texCoord + gtgs_BlurStep * 2.0).rgb + texture(Sampler0, texCoord - gtgs_BlurStep * 2.0).rgb) * 0.121;
    c += (texture(Sampler0, texCoord + gtgs_BlurStep * 3.0).rgb + texture(Sampler0, texCoord - gtgs_BlurStep * 3.0).rgb) * 0.054;
    c += (texture(Sampler0, texCoord + gtgs_BlurStep * 4.0).rgb + texture(Sampler0, texCoord - gtgs_BlurStep * 4.0).rgb) * 0.016;
    fragColor = vec4(c, 1.0);
}
