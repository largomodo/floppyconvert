package com.largomodo.floppyconvert.snes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable representation of a SNES ROM image and its metadata.
 * <p>
 * This record holds the raw ROM data (stripped of any copier headers) and the
 * metadata parsed from the internal SNES header.
 * <p>
 * Invariant: {@code rawData} is always linear (deinterleaved, copier header stripped).
 * {@code headerBase}, {@code interleavedSource} and {@code gd3Headered} record what the source file looked like.
 * "Extended" is a property of where the header was found, not a third map type: it also covers extended LoROM.
 * (ref: DL-003)
 */
public record SnesRom(
        byte[] rawData,
        RomType type,
        int sramSize,
        String title,
        boolean hasDsp,
        int region,
        int maker,
        int version,
        int checksum,
        int complement,
        int headerBase,
        boolean interleavedSource,
        boolean gd3Headered
) {
    /**
     * Header base of ROMs that carry their internal header 4 MB in (ucon64 SNES_EROM).
     */
    public static final int EXTENDED_BASE = 0x400000;

    private static final int LOROM_HEADER_START = 0x7FB0;
    private static final int HIROM_HEADER_START = 0xFFB0;

    /**
     * Ordinary ROM: header at the base-0 position, linear, no copier header of its own.
     */
    public SnesRom(byte[] rawData, RomType type, int sramSize, String title, boolean hasDsp, int region,
                   int maker, int version, int checksum, int complement) {
        this(rawData, type, sramSize, title, hasDsp, region, maker, version, checksum, complement, 0, false, false);
    }

    public SnesRom {
        Objects.requireNonNull(rawData, "rawData must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        // (checksum + complement) & 0xFFFF == 0xFFFF holds for all real SNES ROMs;
        // SnesRomReader already asserts this during scoring and all E2E fixture ROMs satisfy it.
        // Enforcing it here makes validity a property of the type, matching the null-check pattern
        // for reference fields. ROMs it rejects surface as CHECKSUM failures and never reach a format
        // path.
        if (((checksum + complement) & 0xFFFF) != 0xFFFF) {
            // The typed exception lets the batch classifier report CHECKSUM without parsing messages.
            throw new InvalidChecksumException(checksum, complement);
        }
        if (headerBase != 0 && headerBase != EXTENDED_BASE) {
            throw new IllegalArgumentException(
                    String.format("Invalid header base 0x%X (must be 0 or 0x%X)", headerBase, EXTENDED_BASE));
        }
    }

    /**
     * @return true if the ROM uses HiROM mapping, extended or not.
     */
    public boolean isHiRom() {
        return type == RomType.HiROM;
    }

    /**
     * @return true if the internal header sits 4 MB into the image (extended HiROM or extended LoROM).
     */
    public boolean isExtended() {
        return headerBase == EXTENDED_BASE;
    }

    /**
     * @return offset of the internal header in {@code rawData}: headerBase + 0x7FB0 (LoROM) or + 0xFFB0 (HiROM)
     */
    public int headerOffset() {
        return headerBase + (isHiRom() ? HIROM_HEADER_START : LOROM_HEADER_START);
    }

    /**
     * @param offset byte offset relative to {@link #headerOffset()}
     * @throws IllegalStateException if the byte lies outside {@code rawData}
     */
    public int headerByte(int offset) {
        return headerBytes(offset, 1)[0] & 0xFF;
    }

    /**
     * @param offset byte offset relative to {@link #headerOffset()}
     * @throws IllegalStateException if the range lies outside {@code rawData}
     */
    public byte[] headerBytes(int offset, int length) {
        int start = headerOffset() + offset;
        if (offset < 0 || length < 0 || start + length > rawData.length) {
            throw new IllegalStateException(String.format(
                    "Internal header range 0x%X+%d lies outside the %d-byte ROM", start, length, rawData.length));
        }
        return Arrays.copyOfRange(rawData, start, start + length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SnesRom snesRom = (SnesRom) o;
        return sramSize == snesRom.sramSize &&
                hasDsp == snesRom.hasDsp &&
                region == snesRom.region &&
                maker == snesRom.maker &&
                version == snesRom.version &&
                checksum == snesRom.checksum &&
                complement == snesRom.complement &&
                headerBase == snesRom.headerBase &&
                interleavedSource == snesRom.interleavedSource &&
                gd3Headered == snesRom.gd3Headered &&
                Arrays.equals(rawData, snesRom.rawData) &&
                type == snesRom.type &&
                title.equals(snesRom.title);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, sramSize, title, hasDsp, region, maker, version, checksum, complement,
                headerBase, interleavedSource, gd3Headered);
        result = 31 * result + Arrays.hashCode(rawData);
        return result;
    }

    @Override
    public String toString() {
        return "SnesRom{" +
                "type=" + type +
                ", sramSize=" + sramSize +
                ", title='" + title + '\'' +
                ", region=" + region +
                ", size=" + rawData.length +
                ", headerBase=0x" + Integer.toHexString(headerBase) +
                ", interleavedSource=" + interleavedSource +
                ", gd3Headered=" + gd3Headered +
                '}';
    }
}