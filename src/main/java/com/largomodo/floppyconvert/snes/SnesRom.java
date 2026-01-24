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