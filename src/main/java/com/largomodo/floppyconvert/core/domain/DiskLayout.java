package com.largomodo.floppyconvert.core.domain;

import com.largomodo.floppyconvert.core.FloppyType;

import java.util.List;

/**
 * Immutable layout specification for a single floppy disk image.
 * <p>
 * This record represents a planned distribution of ROM parts onto a floppy disk.
 * It captures which parts belong on the disk and what disk format should be used.
 * Used by DiskPacker to express the output of the bin-packing algorithm, and by
 * RomProcessor to execute the actual file copying.
 * </p>
 *
 * @param contents    The list of ROM parts to include on this disk (unmodifiable)
 * @param floppyType  The disk format (720KB, 1.44MB, or 1.6MB)
 */
public record DiskLayout(List<RomPartMetadata> contents, FloppyType floppyType) {
    /**
     * Compact constructor that ensures contents is an unmodifiable copy.
     */
    public DiskLayout {
        contents = List.copyOf(contents);
    }
}
