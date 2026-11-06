package com.ikedi.pos.repository.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ikedi.pos.util.GsonAdapters;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Shared helpers for all JSON repositories.
 *
 * <p><b>Atomic write:</b> we write to a sibling temp file first, then rename.
 * {@code ATOMIC_MOVE} is a single OS syscall so a crash leaves either the
 * old file or the new file, never a partial file. If the file system refuses
 * ATOMIC_MOVE (e.g., across volumes) we fall back to REPLACE_EXISTING.
 *
 * <p><b>Corrupt file:</b> the caller wraps the read with {@link #handleCorrupt}
 * which prints a clear message naming the file and calls {@link System#exit}.
 * We never swallow the error silently and never let a stack trace reach the user.
 */
public final class JsonFileStore {

    /** Single shared Gson with all required type adapters. */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(BigDecimal.class,    new GsonAdapters.BigDecimalAdapter())
            .registerTypeAdapter(LocalDate.class,     new GsonAdapters.LocalDateAdapter())
            .registerTypeAdapter(LocalDateTime.class, new GsonAdapters.LocalDateTimeAdapter())
            .setPrettyPrinting()
            .create();

    private JsonFileStore() {
    }

    /** Atomically replaces {@code path} with {@code content} (UTF-8). */
    public static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Some file systems (e.g., cross-device on Linux) cannot do an atomic rename.
            // REPLACE_EXISTING is not atomic but is still much safer than writing in-place.
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads {@code path} as a UTF-8 string, or returns "{}" if the file does not exist.
     * The caller decides what to do with an empty / missing file.
     */
    public static String read(Path path) throws IOException {
        if (!Files.exists(path)) {
            return "{}";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Call this to wrap any file-read that could fail on a corrupt file.
     * Prints a clear, one-line message naming the file and exits with code 1.
     * We exit rather than continue because a corrupt file means data integrity
     * is unknown; running on is worse than stopping.
     */
    public static void handleCorrupt(Path path, Exception e) {
        System.err.println("ERROR: data file is corrupt and cannot be loaded: " + path);
        System.err.println("       " + e.getMessage());
        System.err.println("       Fix or remove the file, then restart.");
        System.exit(1);
    }
}

