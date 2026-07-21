package dev.thewander02.cinder.config;

public record CinderConfig(boolean enabled, String shaderPack) {
    public CinderConfig {
        shaderPack = shaderPack == null ? "" : shaderPack.trim();
    }

    public static CinderConfig disabled() {
        return new CinderConfig(false, "");
    }
}
