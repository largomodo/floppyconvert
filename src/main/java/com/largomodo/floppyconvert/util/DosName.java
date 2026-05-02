package com.largomodo.floppyconvert.util;

/**
 * DOS 8.3 filename value type. (ref: DL-002)
 * <p>
 * The 8.3 invariant is enforced at construction via DosNameUtil.sanitize; any caller
 * holding a DosName instance is guaranteed the value satisfies the FAT12 directory-entry
 * constraint. Centralizes validation at the type boundary so all five sites
 * (RomProcessor, RomPartNormalizer, Fat12ImageWriter, validateNoDosCollisions, writeDirEntry)
 * share a single enforcement point.
 */
public record DosName(String value) {

    public DosName {
        value = DosNameUtil.sanitize(value);
    }

    /**
     * Factory method for call-site readability.
     *
     * @param raw Source filename (may contain shell-unsafe or non-ASCII characters)
     * @return DosName with validated 8.3 value
     * @throws IllegalArgumentException if raw cannot be sanitized to a valid DOS name
     */
    public static DosName of(String raw) {
        return new DosName(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
