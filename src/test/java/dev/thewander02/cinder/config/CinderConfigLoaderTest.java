package dev.thewander02.cinder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CinderConfigLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDisabledDefaultWhenMissing() throws IOException {
        CinderConfigLoader loader = new CinderConfigLoader(temporaryDirectory.resolve("config"));

        CinderConfig config = loader.load();

        assertFalse(config.enabled());
        assertEquals("", config.shaderPack());
        assertTrue(Files.isRegularFile(loader.configFile()));
    }

    @Test
    void readsEnabledPackAndTrimsItsName() throws IOException {
        Path configDirectory = temporaryDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Files.writeString(configDirectory.resolve("cinder.json"), """
                {"enabled": true, "shaderPack": "  fixture.zip  "}
                """, StandardCharsets.UTF_8);

        CinderConfig config = new CinderConfigLoader(configDirectory).load();

        assertTrue(config.enabled());
        assertEquals("fixture.zip", config.shaderPack());
    }

    @Test
    void rejectsBlankPackWhenEnabled() throws IOException {
        Path configDirectory = temporaryDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Files.writeString(configDirectory.resolve("cinder.json"),
                "{\"enabled\":true,\"shaderPack\":\"\"}", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> new CinderConfigLoader(configDirectory).load());
    }

    @Test
    void rejectsMalformedJsonAndWrongFieldTypes() throws IOException {
        Path configDirectory = temporaryDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Path configFile = configDirectory.resolve("cinder.json");
        Files.writeString(configFile, "{", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new CinderConfigLoader(configDirectory).load());

        Files.writeString(configFile, "{\"enabled\":\"yes\"}", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> new CinderConfigLoader(configDirectory).load());
    }
}
