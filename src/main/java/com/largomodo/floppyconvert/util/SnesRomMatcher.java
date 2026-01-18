package com.largomodo.floppyconvert.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SNES ROM format detection for multi-format support.
 * <p>
 * Recognizes standard SNES extensions (.sfc, .fig, .smw, .swc, .ufo)
 * and Game Doctor naming conventions (e.g., sf32chra.078, SF12TEST).
 * <p>
 * Stateless utility performing filesystem checks. Safe for concurrent use when each
 * caller provides independent Path instances.
 */
public class SnesRomMatcher {

    private static final Set<String> EXTENSIONS = Set.of(
            ".sfc", ".fig", ".smw", ".swc", ".ufo"
    );

    private static final Pattern GAME_DOCTOR_PATTERN =
            Pattern.compile("(?i)^sf\\d{1,2}[a-z0-9]{3,4}(\\.(048|058|078))?$");

    private SnesRomMatcher() {
        // Static utility class - prevent instantiation
    }

    /**
     * Check if path is a recognized SNES ROM file.
     * <p>
     * Hybrid approach: O(n) iteration over 6 common extensions (fast path), regex for Game Doctor
     * variable naming patterns. Returns false for directories (prevents false positives:
     * directories can match Game Doctor regex like "sf01test").
     *
     * @param path File path to check (can be null)
     * @return true if path is a regular file matching ROM patterns, false otherwise
     */
    public static boolean isRom(Path path) {
        if (path == null) {
            return false;  // Safe filter predicate semantics (prevents NPE in stream filters)
        }

        if (!Files.isRegularFile(path)) {
            // Directories can match Game Doctor regex (e.g., 'sf01test').
            // Without this check, IOException when attempting to read directory as file.
            return false;
        }

        String filename = path.getFileName().toString().toLowerCase();

        for (String ext : EXTENSIONS) {
            if (filename.endsWith(ext)) {
                return true;
            }
        }

        // Game Doctor files may not have extensions (e.g., SF12TEST)
        // or may have split part extensions (.048, .058, .078)
        return GAME_DOCTOR_PATTERN.matcher(filename).matches();
    }
}
