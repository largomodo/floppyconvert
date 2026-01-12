package com.largomodo.floppyconvert.core.domain;

import java.nio.file.Path;

/**
 * Immutable metadata for a ROM part file after splitting and sanitization.
 * <p>
 * This record captures the essential properties of a ROM part that are needed
 * for disk layout planning: the file path, size, and DOS-compatible filename.
 * Used by DiskPacker to determine optimal distribution across floppy images.
 * </p>
 *
 * @param originalPath  The filesystem path to the ROM part file
 * @param sizeInBytes   The file size in bytes (must be > 0)
 * @param dosName       The DOS 8.3 compatible filename for this part
 */
public record RomPartMetadata(Path originalPath, long sizeInBytes, String dosName) {
    /**
     * Compact constructor that validates the sizeInBytes constraint.
     *
     * @throws IllegalArgumentException if sizeInBytes is not positive
     */
    public RomPartMetadata {
        if (originalPath == null) {
            throw new IllegalArgumentException("originalPath must not be null");
        }
        if (dosName == null || dosName.isBlank()) {
            throw new IllegalArgumentException("dosName must not be null or blank");
        }
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException(
                "sizeInBytes must be positive, got: " + sizeInBytes
            );
        }
    }
}
