#version 150

// Standstill's one inverted frame (DESIGN §3.26): every colour flipped, as the clock stops. Amount fades it (it's held for one frame).

uniform sampler2D DiffuseSampler;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    fragColor = vec4(mix(original, vec3(1.0) - original, Amount), 1.0);
}
