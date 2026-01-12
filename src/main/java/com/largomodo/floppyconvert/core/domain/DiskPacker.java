package com.largomodo.floppyconvert.core.domain;

import java.util.List;

/**
 * Strategy interface for packing ROM parts into floppy disk layouts.
 * <p>
 * Implementations determine how to distribute parts across multiple disks
 * while respecting capacity constraints and optimizing disk usage.
 */
public interface DiskPacker {
    /**
     * Packs ROM parts into disk layouts using implementation-specific strategy.
     * <p>
     * Each resulting DiskLayout contains parts that fit within the selected
     * floppy format's capacity. The disk format is chosen dynamically per disk
     * based on actual usage via FloppyType.bestFit().
     *
     * @param parts list of ROM parts to pack, must not be null
     * @return list of disk layouts, empty if parts is empty
     * @throws IllegalArgumentException if any single part exceeds maximum capacity
     */
    List<DiskLayout> pack(List<RomPartMetadata> parts);
}
