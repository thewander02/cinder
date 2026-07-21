package dev.thewander02.cinder.pack;

import java.io.IOException;

public final class PackLoadException extends IOException {
    public PackLoadException(String message) {
        super(message);
    }

    public PackLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
