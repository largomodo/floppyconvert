package com.largomodo.floppyconvert.core.domain;

import com.largomodo.floppyconvert.util.DosName;
import java.nio.file.Path;

/**
 * Immutable metadata for a ROM part file after splitting and sanitization.
 * <p>
 * This record captures the essential properties of a ROM part that are needed
 * for disk layout planning: the file path, size, and DOS-compatible filename.
 * Used by DiskPacker to determine optimal distribution across floppy images.
 * </p>
 *
 * @param originalPath The filesystem path to the ROM part file
 * @param sizeInBytes  The file size in bytes (must be > 0)
 * @param dosName      The DOS 8.3 compatible filename for this part
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

    /**
     * Production-boundary factory that validates the DOS name via DosName.of. (ref: DL-002, DL-008)
     * <p>
     * Called by RomPartNormalizer.normalize; the canonical constructor remains available for
     * test fixtures that construct metadata directly without re-validation.
     *
     * @param originalPath Filesystem path to the ROM part file
     * @param sizeInBytes  File size in bytes (must be positive)
     * @param dosName      Raw DOS name string; validated via DosName.of and stored as the sanitized value
     * @return RomPartMetadata with a validated DOS name
     */
    public static RomPartMetadata of(Path originalPath, long sizeInBytes, String dosName) {
        String validated = DosName.of(dosName).value();
        return new RomPartMetadata(originalPath, sizeInBytes, validated);
    }

    /**
     * Accessor returning the DOS name wrapped in a typed DosName value.
     */
    public DosName dosNameTyped() {
        return new DosName(dosName);
    }
}
