#version 150

// A whole-screen grayscale (the screen effect hook, DESIGN §3.23): Time Manipulation's Standstill will use it. Amount fades it in
// and out.

uniform sampler2D DiffuseSampler;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 original = texture(DiffuseSampler, texCoord).rgb;
    float l = dot(original, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(mix(original, vec3(l), Amount), 1.0);
}
