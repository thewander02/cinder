#version 330

in vec2 cinderUv;
out vec4 fragColor;

void main() {
    vec2 uv = clamp(cinderUv, 0.0, 1.0);
    fragColor = vec4(uv.x, 0.18 + uv.y * 0.62, 1.0 - uv.x * 0.7, 1.0);
}
