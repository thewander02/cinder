package dev.thewander02.cinder.pack;

import java.nio.file.Path;
import java.util.Objects;

public record PreparedPack(
        String identity,
        Path source,
        String vertexSource,
        String fragmentSource,
        String contentHash
) {
    public PreparedPack {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(vertexSource, "vertexSource");
        Objects.requireNonNull(fragmentSource, "fragmentSource");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}
