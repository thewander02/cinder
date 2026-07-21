package dev.thewander02.cinder.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CinderConfigLoader {
    private static final String DEFAULT_CONFIG = """
            {
              "enabled": false,
              "shaderPack": ""
            }
            """;

    private final Path configFile;

    public CinderConfigLoader(Path configDirectory) {
        this.configFile = configDirectory.resolve("cinder.json");
    }

    public CinderConfig load() throws IOException {
        ensureDefaultExists();

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                throw new IOException("Cinder configuration must be a JSON object: " + configFile);
            }

            JsonObject object = root.getAsJsonObject();
            boolean enabled = readBoolean(object, "enabled", false);
            String shaderPack = readString(object, "shaderPack", "");
            if (enabled && shaderPack.isBlank()) {
                throw new IOException("Cinder is enabled but shaderPack is blank in " + configFile);
            }

            return new CinderConfig(enabled, shaderPack);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IOException("Invalid Cinder configuration: " + configFile, exception);
        }
    }

    public Path configFile() {
        return configFile;
    }

    private void ensureDefaultExists() throws IOException {
        Files.createDirectories(configFile.getParent());
        if (!Files.exists(configFile)) {
            Files.writeString(configFile, DEFAULT_CONFIG, StandardCharsets.UTF_8);
        }
    }

    private static boolean readBoolean(JsonObject object, String name, boolean defaultValue) throws IOException {
        JsonElement value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("Configuration field '" + name + "' must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String readString(JsonObject object, String name, String defaultValue) throws IOException {
        JsonElement value = object.get(name);
        if (value == null) {
            return defaultValue;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Configuration field '" + name + "' must be a string");
        }
        return value.getAsString();
    }
}
