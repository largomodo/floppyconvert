package com.largomodo.floppyconvert.util;

/**
 * DOS 8.3 filename sanitization for FAT12 filesystem compatibility.
 * <p>
 * Enforces strict FAT12 naming constraints: 8 character name + 3 character extension,
 * uppercase alphanumeric only. Preserves numeric extensions (.1, .2, .3) exactly to
 * maintain ROM part ordering for reassembly.
 * <p>
 * Pure function with no state or dependencies. Safe for concurrent use.
 */
public class DosNameUtil {

    private DosNameUtil() {
        // Static utility class - prevent instantiation
    }

    /**
     * Convert filename to DOS 8.3 format.
     * <p>
     * Strategy: Split on last dot (preserves "game.v1.0.sfc" → extension "sfc"),
     * strip non-alphanumeric from both parts, truncate to limits, uppercase.
     *
     * @param filename Source filename (can contain path separators, spaces, special chars)
     * @return DOS-compliant 8.3 filename in uppercase
     * @throws IllegalArgumentException if filename is null or blank
     */
    public static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or blank");
        }

        // Split on LAST dot to handle multiple dots: "game.v1.0.sfc" → name="game.v1.0", ext="sfc"
        // FAT12 has no concept of multiple extensions; rightmost dot is canonical separator
        int lastDot = filename.lastIndexOf('.');
        String name;
        String extension;

        if (lastDot > 0) {
            name = filename.substring(0, lastDot);
            extension = filename.substring(lastDot + 1);
        } else {
            name = filename;
            extension = "";
        }

        // Strip non-alphanumeric from name: "Super Mario" → "SuperMario"
        // Preserves digits for version numbers: "game_v2" → "GAMEV2"
        String cleanName = name.replaceAll("[^A-Za-z0-9]", "");

        // Truncate to 8 chars (FAT12 limit)
        if (cleanName.length() > 8) {
            cleanName = cleanName.substring(0, 8);
        }

        // Validate name portion is not empty (FAT12 requires at least 1 char in name)
        // Prevents cryptic mcopy "Cannot initialize" errors later in pipeline
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Filename cannot be sanitized to valid DOS 8.3 format (name portion would be empty): " + filename
            );
        }

        // Extension: strip non-alphanumeric, truncate to 3 chars
        // Numeric extensions (.1, .2, .99) pass through unchanged (already alphanumeric)
        String cleanExtension = extension.replaceAll("[^A-Za-z0-9]", "");
        if (cleanExtension.length() > 3) {
            cleanExtension = cleanExtension.substring(0, 3);
        }

        return (cleanName + (cleanExtension.isEmpty() ? "" : "." + cleanExtension)).toUpperCase();
    }
}
