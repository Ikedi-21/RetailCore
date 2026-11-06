package com.ikedi.pos.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Initializes the data directory with default seed resources on first run.
 *
 * <p>Requirement (Step 3):
 * 15+ products covering STANDARD, WEIGHTED and PERISHABLE, with suppliers and categories.
 * Stored as classpath resources and copied to the target data/ directory on first run only.
 * If the target file already exists, it is never overwritten so existing user edits survive.
 */
public class DataInitializer {

    private static final List<String> SEED_FILES = List.of(
            "suppliers.json",
            "categories.json",
            "products.json",
            "discounts.json"
    );

    private final Path dataDir;

    public DataInitializer(Path dataDir) {
        this.dataDir = dataDir;
    }

    /** Returns true if the data directory has not been initialized yet (products.json missing). */
    public boolean isFirstRun() {
        return !Files.exists(dataDir.resolve("products.json"));
    }

    /**
     * Copies each seed resource into dataDir only if the destination file does not exist.
     * Returns true if any seed file was copied.
     */
    public boolean initializeIfAbsent() throws IOException {
        Files.createDirectories(dataDir);
        boolean copiedAny = false;
        for (String file : SEED_FILES) {
            Path target = dataDir.resolve(file);
            if (!Files.exists(target)) {
                try (InputStream in = getClass().getResourceAsStream("/seed/" + file)) {
                    if (in != null) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        copiedAny = true;
                    }
                }
            }
        }
        return copiedAny;
    }

    public Path getDataDir() {
        return dataDir;
    }
}
