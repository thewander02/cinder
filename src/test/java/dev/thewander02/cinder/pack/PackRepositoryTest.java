package dev.thewander02.cinder.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackRepositoryTest {
    private static final byte[] VERTEX = "#version 330\nvoid main(){}\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FRAGMENT = "#version 330\nout vec4 c; void main(){c=vec4(1);}\n"
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path gameDirectory;

    @Test
    void discoversOnlyImmediateDirectoriesAndZipFiles() throws IOException {
        Path shaderpacks = gameDirectory.resolve("shaderpacks");
        Files.createDirectories(shaderpacks.resolve("directory-pack"));
        Files.write(shaderpacks.resolve("packed.zip"), new byte[0]);
        Files.writeString(shaderpacks.resolve("notes.txt"), "ignored");

        assertEquals(List.of("directory-pack", "packed.zip"), new PackRepository(gameDirectory).discover());
    }

    @Test
    void loadsDirectoryAndZipWithSameDeterministicHash() throws IOException {
        createDirectoryPack("directory-pack", VERTEX, FRAGMENT);
        createZip("packed.zip", Map.of(
                PackRepository.VERTEX_PATH, VERTEX,
                PackRepository.FRAGMENT_PATH, FRAGMENT
        ));

        PackRepository repository = new PackRepository(gameDirectory);
        PreparedPack directory = repository.load("directory-pack");
        PreparedPack zip = repository.load("packed.zip");

        assertEquals(directory.contentHash(), zip.contentHash());
        assertEquals(new String(VERTEX, StandardCharsets.UTF_8), directory.vertexSource());
        assertEquals(new String(FRAGMENT, StandardCharsets.UTF_8), zip.fragmentSource());

        createDirectoryPack("changed-pack", VERTEX, "changed".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(directory.contentHash(), repository.load("changed-pack").contentHash());
    }

    @Test
    void rejectsSelectedPathTraversalAndMissingStages() throws IOException {
        PackRepository repository = new PackRepository(gameDirectory);
        assertThrows(PackLoadException.class, () -> repository.load("../outside"));
        assertThrows(PackLoadException.class, () -> repository.load("nested/pack"));

        Path incomplete = gameDirectory.resolve("shaderpacks/incomplete/shaders");
        Files.createDirectories(incomplete);
        Files.write(incomplete.resolve("final.vsh"), VERTEX);
        assertThrows(PackLoadException.class, () -> repository.load("incomplete"));
    }

    @Test
    void rejectsUnsafeAndDuplicateZipEntries() throws IOException {
        createZip("unsafe.zip", Map.of(
                "../escape", new byte[]{1},
                PackRepository.VERTEX_PATH, VERTEX,
                PackRepository.FRAGMENT_PATH, FRAGMENT
        ));
        PackRepository repository = new PackRepository(gameDirectory);
        assertThrows(PackLoadException.class, () -> repository.load("unsafe.zip"));

        createZip("windows-absolute.zip", Map.of(
                "C:/escape", new byte[]{1},
                PackRepository.VERTEX_PATH, VERTEX,
                PackRepository.FRAGMENT_PATH, FRAGMENT
        ));
        assertThrows(PackLoadException.class, () -> repository.load("windows-absolute.zip"));

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PackRepository.VERTEX_PATH, VERTEX);
        entries.put("shaders/other.vsh", VERTEX);
        entries.put(PackRepository.FRAGMENT_PATH, FRAGMENT);
        Path duplicate = createZip("duplicate.zip", entries);
        replaceAscii(duplicate, "shaders/other.vsh", PackRepository.VERTEX_PATH);

        PackLoadException exception = assertThrows(PackLoadException.class,
                () -> repository.load("duplicate.zip"));
        assertTrue(exception.getMessage().contains("Duplicate ZIP entry"));
    }

    @Test
    void rejectsOversizedAndMalformedUtf8Sources() throws IOException {
        byte[] oversized = new byte[PackRepository.MAX_SHADER_BYTES + 1];
        createDirectoryPack("oversized", oversized, FRAGMENT);
        PackRepository repository = new PackRepository(gameDirectory);
        assertThrows(PackLoadException.class, () -> repository.load("oversized"));

        createDirectoryPack("bad-utf8", new byte[]{(byte) 0xC3, (byte) 0x28}, FRAGMENT);
        assertThrows(PackLoadException.class, () -> repository.load("bad-utf8"));
    }

    @Test
    void rejectsSymlinkThatEscapesPackRoot() throws IOException {
        Path packShaders = gameDirectory.resolve("shaderpacks/symlink-pack/shaders");
        Files.createDirectories(packShaders);
        Path external = gameDirectory.resolve("external.vsh");
        Files.write(external, VERTEX);
        Files.write(packShaders.resolve("final.fsh"), FRAGMENT);

        try {
            Files.createSymbolicLink(packShaders.resolve("final.vsh"), external);
        } catch (UnsupportedOperationException | FileSystemException exception) {
            Assumptions.abort("Symbolic links are unavailable in this test environment");
        }

        assertThrows(PackLoadException.class, () -> new PackRepository(gameDirectory).load("symlink-pack"));
    }

    private void createDirectoryPack(String name, byte[] vertex, byte[] fragment) throws IOException {
        Path shaders = gameDirectory.resolve("shaderpacks").resolve(name).resolve("shaders");
        Files.createDirectories(shaders);
        Files.write(shaders.resolve("final.vsh"), vertex);
        Files.write(shaders.resolve("final.fsh"), fragment);
    }

    private Path createZip(String name, Map<String, byte[]> entries) throws IOException {
        Path zipPath = gameDirectory.resolve("shaderpacks").resolve(name);
        Files.createDirectories(zipPath.getParent());
        try (OutputStream output = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return zipPath;
    }

    private static void replaceAscii(Path file, String original, String replacement) throws IOException {
        byte[] originalBytes = original.getBytes(StandardCharsets.US_ASCII);
        byte[] replacementBytes = replacement.getBytes(StandardCharsets.US_ASCII);
        assertEquals(originalBytes.length, replacementBytes.length);

        byte[] bytes = Files.readAllBytes(file);
        int replacements = 0;
        for (int index = 0; index <= bytes.length - originalBytes.length; index++) {
            if (Arrays.equals(
                    Arrays.copyOfRange(bytes, index, index + originalBytes.length), originalBytes)) {
                System.arraycopy(replacementBytes, 0, bytes, index, replacementBytes.length);
                replacements++;
            }
        }
        assertTrue(replacements >= 2, "Expected local and central ZIP names to be replaced");
        Files.write(file, bytes);
    }
}
