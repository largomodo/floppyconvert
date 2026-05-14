package com.largomodo.floppyconvert.core;

import java.io.IOException;
import com.largomodo.floppyconvert.format.CopierFormat;

/**
 * Floppy disk format types with geometry specifications and capacity limits.
 * <p>
 * Capacity limits are conservative to account for FAT12 filesystem overhead
 * (boot sector, FAT tables, root directory). Using raw disk capacity would
 * cause write failures when filesystem structures exceed available space.
 * <p>
 * Geometry fields (tracks, heads, sectorsPerTrack, rootEntryCount, mediaDescriptor,
 * sectorsPerFat, sectorsPerCluster) define the physical disk format and are used for dynamic image generation.
 * These values must match Fat12ImageWriter's BPB offset calculations. See Fat12FormatFactory
 * for BPB structure layout.
 * <p>
 * Declaration order (FLOPPY_720K, FLOPPY_144M, FLOPPY_160M) must match ascending capacity order;
 * bestFit(long, FloppyType) uses ordinal comparison for floor clamping.
 */
public enum FloppyType {
    // 720KB raw (80 tracks × 9 sectors × 2 sides × 512 bytes = 737,280)
    // Conservative limit accounts for ~12KB FAT overhead
    FLOPPY_720K(725_000, (short) 80, (short) 2, (short) 9, 112, (byte) 0xF9, (short) 3, (byte) 2),

    // 1.44MB raw (80 tracks × 18 sectors × 2 sides × 512 bytes = 1,474,560)
    // Conservative limit accounts for ~24KB FAT overhead
    FLOPPY_144M(1_450_000, (short) 80, (short) 2, (short) 18, 224, (byte) 0xF0, (short) 9, (byte) 1),

    // 1.6MB raw (80 tracks × 20 sectors × 2 sides × 512 bytes = 1,638,400)
    // Conservative limit accounts for ~38KB FAT overhead
    FLOPPY_160M(1_600_000, (short) 80, (short) 2, (short) 20, 224, (byte) 0xF0, (short) 10, (byte) 1);

    private final long usableBytes;
    private final short tracks;
    private final short heads;
    private final short sectorsPerTrack;
    private final int rootEntryCount;
    private final byte mediaDescriptor;
    // Pre-calculated FAT12 sectors per FAT using: ceil((totalClusters * 1.5) / 512)
    // where totalClusters = (totalSectors - reservedSectors - rootDirSectors) / sectorsPerCluster
    // 720KB:  rootDirSectors=7  → (1440-1-7)/2=716   → ceil(716*1.5/512)=ceil(2.10)=3
    // 1.44MB: rootDirSectors=14 → (2880-1-14)/1=2865 → ceil(2865*1.5/512)=ceil(8.39)=9
    // 1.6MB:  rootDirSectors=14 → (3200-1-14)/1=3185 → ceil(3185*1.5/512)=ceil(9.33)=10
    // Note: rootDirSectors = (rootEntryCount * 32) / 512
    private final short sectorsPerFat;
    private final byte sectorsPerCluster;

    FloppyType(long usableBytes, short tracks, short heads, short sectorsPerTrack,
               int rootEntryCount, byte mediaDescriptor, short sectorsPerFat, byte sectorsPerCluster) {
        this.usableBytes = usableBytes;
        this.tracks = tracks;
        this.heads = heads;
        this.sectorsPerTrack = sectorsPerTrack;
        this.rootEntryCount = rootEntryCount;
        this.mediaDescriptor = mediaDescriptor;
        this.sectorsPerFat = sectorsPerFat;
        this.sectorsPerCluster = sectorsPerCluster;
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

    /**
     * Selects smallest floppy format that can hold specified payload size, subject to a minimum.
     * <p>
     * Returns the larger of bestFit(sizeInBytes) and minimum, using enum ordinal ordering
     * (FLOPPY_720K < FLOPPY_144M < FLOPPY_160M).
     * WARNING: inserting a new FloppyType between existing constants breaks the ordinal comparison
     * silently. Declaration order must equal size order.
     *
     * @param sizeInBytes total payload size in bytes
     * @param minimum     floor type; result will never be smaller than this
     * @return smallest format that fits payload and meets minimum
     * @throws IllegalArgumentException if sizeInBytes is negative
     * @throws IOException              if payload exceeds 1.6MB capacity
     */
    public static FloppyType bestFit(long sizeInBytes, FloppyType minimum) throws IOException {
        FloppyType fit = bestFit(sizeInBytes);
        return fit.ordinal() >= minimum.ordinal() ? fit : minimum;
    }

    /**
     * Returns the minimum floppy type required by the given copier format.
     * <p>
     * GD3 firmware rejects 720K BPB (SPT=9 has no handler), so GD3 requires at least 1.44M.
     * All other formats accept 720K.
     * Lives on FloppyType (not CopierFormat) to preserve the acyclic package constraint:
     * format package has zero imports from core.
     *
     * @param format the copier format
     * @return minimum FloppyType for the format
     */
    public static FloppyType minimumForFormat(CopierFormat format) {
        return format == CopierFormat.GD3 ? FLOPPY_144M : FLOPPY_720K;
    }

    /**
     * Calculates total disk capacity in bytes.
     *
     * @return total capacity (tracks × heads × sectorsPerTrack × 512 bytes)
     */
    public long getTotalCapacity() {
        return (long) tracks * heads * sectorsPerTrack * 512;
    }

    public long getUsableBytes() {
        return usableBytes;
    }

    public short getTracks() {
        return tracks;
    }

    public short getHeads() {
        return heads;
    }

    public short getSectorsPerTrack() {
        return sectorsPerTrack;
    }

    public int getRootEntryCount() {
        return rootEntryCount;
    }

    public byte getMediaDescriptor() {
        return mediaDescriptor;
    }

    public short getSectorsPerFat() {
        return sectorsPerFat;
    }

    public byte getSectorsPerCluster() {
        return sectorsPerCluster;
    }
}
