package com.largomodo.floppyconvert.core.domain;

import com.largomodo.floppyconvert.core.FloppyType;
import java.util.List;

/**
 * Strategy interface for packing ROM parts into floppy disk layouts.
 * <p>
 * Implementations determine how to distribute parts across multiple disks
 * while respecting capacity constraints and optimizing disk usage.
 * The minimum parameter varies per invocation (driven by CopierFormat), so it
 * is a method parameter rather than a constructor field.
 * <p>
 * RomProcessor is the sole caller of pack(); no other callers exist.
 * Signature changes require updates only in GreedyDiskPacker, RomProcessor, and their tests.
 */
public interface DiskPacker {
    /**
     * Packs ROM parts into disk layouts using implementation-specific strategy.
     * <p>
     * Each resulting DiskLayout contains parts that fit within the selected
     * floppy format's capacity. The disk format is chosen dynamically per disk
     * based on actual usage via FloppyType.bestFit(size, minimum).
     *
     * @param parts   list of ROM parts to pack, must not be null
     * @param minimum minimum floppy type for each disk; result type will never be smaller
     * @return list of disk layouts, empty if parts is empty
     * @throws IllegalArgumentException if any single part exceeds maximum capacity
     */
    List<DiskLayout> pack(List<RomPartMetadata> parts, FloppyType minimum);
}
