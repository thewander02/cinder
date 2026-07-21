package dev.thewander02.cinder.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackRepository {
    public static final int MAX_SHADER_BYTES = 1024 * 1024;
    public static final String VERTEX_PATH = "shaders/final.vsh";
    public static final String FRAGMENT_PATH = "shaders/final.fsh";

    private final Path shaderpacksDirectory;

    public PackRepository(Path gameDirectory) {
        this.shaderpacksDirectory = gameDirectory.resolve("shaderpacks").toAbsolutePath().normalize();
    }

    public List<String> discover() throws IOException {
        Files.createDirectories(shaderpacksDirectory);
        try (var children = Files.list(shaderpacksDirectory)) {
            return children
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            || (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")))
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public PreparedPack load(String selectedName) throws IOException {
        validateSelectedName(selectedName);
        Files.createDirectories(shaderpacksDirectory);

        Path root = shaderpacksDirectory.toRealPath();
        Path candidate = root.resolve(selectedName).normalize();
        if (!candidate.startsWith(root)) {
            throw new PackLoadException("Shaderpack path escapes shaderpacks/: " + selectedName);
        }
        if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new PackLoadException("Shaderpack does not exist: " + selectedName);
        }

        Path realCandidate = candidate.toRealPath();
        if (!realCandidate.startsWith(root)) {
            throw new PackLoadException("Shaderpack symlink escapes shaderpacks/: " + selectedName);
        }

        SourcePair pair;
        if (Files.isDirectory(realCandidate)) {
            pair = readDirectory(realCandidate);
        } else if (Files.isRegularFile(realCandidate)
                && selectedName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            pair = readZip(realCandidate);
        } else {
            throw new PackLoadException("Shaderpack must be a directory or .zip file: " + selectedName);
        }

        return new PreparedPack(
                selectedName,
                realCandidate,
                decodeUtf8(pair.vertex(), VERTEX_PATH),
                decodeUtf8(pair.fragment(), FRAGMENT_PATH),
                contentHash(pair.vertex(), pair.fragment())
        );
    }

    public Path shaderpacksDirectory() {
        return shaderpacksDirectory;
    }

    private static SourcePair readDirectory(Path packRoot) throws IOException {
        byte[] vertex = readContainedFile(packRoot, VERTEX_PATH);
        byte[] fragment = readContainedFile(packRoot, FRAGMENT_PATH);
        return new SourcePair(vertex, fragment);
    }

    private static byte[] readContainedFile(Path packRoot, String relativeName) throws IOException {
        Path requested = packRoot.resolve(relativeName).normalize();
        if (!requested.startsWith(packRoot) || !Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new PackLoadException("Missing required shader: " + relativeName);
        }

        Path realFile = requested.toRealPath();
        if (!realFile.startsWith(packRoot)) {
            throw new PackLoadException("Shader symlink escapes pack root: " + relativeName);
        }
        if (!Files.isRegularFile(realFile)) {
            throw new PackLoadException("Required shader is not a regular file: " + relativeName);
        }

        try (InputStream input = Files.newInputStream(realFile)) {
            return readLimited(input, relativeName);
        }
    }

    private static SourcePair readZip(Path zipPath) throws IOException {
        byte[] vertex = null;
        byte[] fragment = null;
        Set<String> seenNames = new HashSet<>();

        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String normalizedName = validateZipEntryName(entry.getName(), entry.isDirectory());
                if (!seenNames.add(normalizedName)) {
                    throw new PackLoadException("Duplicate ZIP entry: " + normalizedName);
                }
                if (entry.isDirectory()) {
                    continue;
                }

                if (VERTEX_PATH.equals(normalizedName)) {
                    vertex = readZipEntry(zip, entry, VERTEX_PATH);
                } else if (FRAGMENT_PATH.equals(normalizedName)) {
                    fragment = readZipEntry(zip, entry, FRAGMENT_PATH);
                }
            }
        }

        if (vertex == null) {
            throw new PackLoadException("Missing required shader in ZIP: " + VERTEX_PATH);
        }
        if (fragment == null) {
            throw new PackLoadException("Missing required shader in ZIP: " + FRAGMENT_PATH);
        }
        return new SourcePair(vertex, fragment);
    }

    private static byte[] readZipEntry(ZipFile zip, ZipEntry entry, String displayName) throws IOException {
        if (entry.getSize() > MAX_SHADER_BYTES) {
            throw new PackLoadException("Shader exceeds 1 MiB limit: " + displayName);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            return readLimited(input, displayName);
        }
    }

    private static byte[] readLimited(InputStream input, String displayName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int remaining = MAX_SHADER_BYTES + 1;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
        if (output.size() > MAX_SHADER_BYTES || input.read() != -1) {
            throw new PackLoadException("Shader exceeds 1 MiB limit: " + displayName);
        }
        return output.toByteArray();
    }

    private static String decodeUtf8(byte[] bytes, String displayName) throws PackLoadException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new PackLoadException("Shader is not valid UTF-8: " + displayName, exception);
        }
    }

    private static String validateZipEntryName(String rawName, boolean directory) throws PackLoadException {
        if (rawName.isEmpty() || rawName.startsWith("/") || rawName.contains("\\")
                || rawName.matches("^[A-Za-z]:/.*")) {
            throw new PackLoadException("Unsafe ZIP entry: " + rawName);
        }

        String name = directory && rawName.endsWith("/")
                ? rawName.substring(0, rawName.length() - 1)
                : rawName;
        if (name.isEmpty()) {
            throw new PackLoadException("Unsafe ZIP entry: " + rawName);
        }

        String[] parts = name.split("/", -1);
        List<String> normalized = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new PackLoadException("Unsafe ZIP entry: " + rawName);
            }
            normalized.add(part);
        }
        return String.join("/", normalized);
    }

    private static void validateSelectedName(String selectedName) throws PackLoadException {
        if (selectedName == null || selectedName.isBlank()) {
            throw new PackLoadException("Shaderpack name is blank");
        }
        if (selectedName.equals(".") || selectedName.equals("..")
                || selectedName.contains("/") || selectedName.contains("\\")) {
            throw new PackLoadException("Shaderpack must be one immediate child of shaderpacks/: " + selectedName);
        }
        Path path;
        try {
            path = Path.of(selectedName);
        } catch (RuntimeException exception) {
            throw new PackLoadException("Invalid shaderpack name: " + selectedName, exception);
        }
        if (path.isAbsolute() || path.getNameCount() != 1) {
            throw new PackLoadException("Shaderpack must be one immediate child of shaderpacks/: " + selectedName);
        }
    }

    private static String contentHash(byte[] vertex, byte[] fragment) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, VERTEX_PATH, vertex);
            updateDigest(digest, FRAGMENT_PATH, fragment);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String stageName, byte[] source) {
        digest.update(stageName.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(source.length).array());
        digest.update(source);
    }

    private record SourcePair(byte[] vertex, byte[] fragment) {
    }
}
