package com.largomodo.floppyconvert.snes;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable representation of a SNES ROM image and its metadata.
 * <p>
 * This record holds the raw ROM data (stripped of any copier headers) and the
 * metadata parsed from the internal SNES header.
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
        int complement
) {
    public SnesRom {
        Objects.requireNonNull(rawData, "rawData must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        // (checksum + complement) & 0xFFFF == 0xFFFF holds for all real SNES ROMs;
        // SnesRomReader already asserts this during scoring and all E2E fixture ROMs satisfy it.
        // Enforcing it here makes validity a property of the type, matching the null-check pattern
        // for reference fields. (ref: DL-003)
        if (((checksum + complement) & 0xFFFF) != 0xFFFF) {
            throw new IllegalArgumentException(
                    String.format("Invalid checksum/complement pair: checksum=0x%04X complement=0x%04X (sum must be 0xFFFF)", checksum, complement));
        }
    }

    /**
     * @return true if the ROM uses HiROM or ExHiROM mapping.
     */
    public boolean isHiRom() {
        return type == RomType.HiROM || type == RomType.ExHiROM;
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
                Arrays.equals(rawData, snesRom.rawData) &&
                type == snesRom.type &&
                title.equals(snesRom.title);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, sramSize, title, hasDsp, region, maker, version, checksum, complement);
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
                '}';
    }
}