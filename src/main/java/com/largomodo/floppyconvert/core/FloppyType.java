package com.largomodo.floppyconvert.core;

import java.io.IOException;

/**
 * Floppy disk format types with capacity limits and template selection.
 * <p>
 * Capacity limits are conservative to account for FAT12 filesystem overhead
 * (boot sector, FAT tables, root directory). Using raw disk capacity would
 * cause write failures when filesystem structures exceed available space.
 */
public enum FloppyType {
    // 720KB raw (80 tracks × 9 sectors × 2 sides × 512 bytes = 737,280)
    // Conservative limit accounts for ~12KB FAT overhead
    FLOPPY_720K(725_000, "/floppyconvert/720k.img"),

    // 1.44MB raw (80 tracks × 18 sectors × 2 sides × 512 bytes = 1,474,560)
    // Conservative limit accounts for ~24KB FAT overhead
    FLOPPY_144M(1_450_000, "/floppyconvert/1m44.img"),

    // 1.6MB raw (80 tracks × 20 sectors × 2 sides × 512 bytes = 1,638,400)
    // Conservative limit accounts for ~38KB FAT overhead
    FLOPPY_160M(1_600_000, "/floppyconvert/1m6.img");

    private final long usableBytes;
    private final String resourcePath;

    FloppyType(long usableBytes, String resourcePath) {
        this.usableBytes = usableBytes;
        this.resourcePath = resourcePath;
    }

    /**
     * Selects smallest floppy format that can hold specified payload size.
     * <p>
     * Greedy best-fit: returns first format with sufficient capacity.
     * Throws if payload exceeds maximum supported capacity (1.6MB).
     *
     * @param sizeInBytes total payload size in bytes
     * @return smallest format that fits payload
     * @throws IllegalArgumentException if sizeInBytes is negative
     * @throws IOException              if payload exceeds 1.6MB capacity
     */
    public static FloppyType bestFit(long sizeInBytes) throws IOException {
        if (sizeInBytes < 0) {
            throw new IllegalArgumentException("Payload size cannot be negative: " + sizeInBytes);
        }
        if (sizeInBytes <= FLOPPY_720K.usableBytes) {
            return FLOPPY_720K;
        } else if (sizeInBytes <= FLOPPY_144M.usableBytes) {
            return FLOPPY_144M;
        } else if (sizeInBytes <= FLOPPY_160M.usableBytes) {
            return FLOPPY_160M;
        } else {
            throw new IOException("Total payload size (" + sizeInBytes +
                    " bytes) exceeds maximum floppy capacity (1.6MB). " +
                    "Consider using larger ROM split size.");
        }
    }

    public long getUsableBytes() {
        return usableBytes;
    }

    public String getResourcePath() {
        return resourcePath;
    }
}
