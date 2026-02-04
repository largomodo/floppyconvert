package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;

/**
 * Hardware validator for Game Doctor (GD3/SF3/SF7) backup unit format.
 * <p>
 * GD3 hardware constraints from ucon64 snes.c:
 * - Standard HiROM maximum: 32Mbit (memory map tables extend only to 32Mbit)
 * - ExHiROM allowed: Up to 64Mbit (extended memory maps present in Gd3HeaderGenerator)
 * - LoROM maximum: 32Mbit (ucon64 snes.c:1863 "ERROR: LoROM > 32 Mbit -- cannot convert")
 */
public class Gd3HardwareValidator implements HardwareValidator {

    private static final int MAX_STANDARD_SIZE = 32 * SnesConstants.MBIT;
    private static final int MAX_EXHIROM_SIZE = 64 * SnesConstants.MBIT;

    @Override
    public void validate(SnesRom rom, CopierFormat format) throws UnsupportedHardwareException {
        if (format != CopierFormat.GD3) {
            return;
        }

        int sizeMbit = rom.rawData().length / SnesConstants.MBIT;

        if (rom.type() == RomType.ExHiROM) {
            if (rom.rawData().length > MAX_EXHIROM_SIZE) {
                throw new UnsupportedHardwareException(
                        String.format("GD3 hardware does not support ExHiROM > 64Mbit: ROM is %d Mbit", sizeMbit)
                );
            }
            return;
        }

        if (rom.rawData().length > MAX_STANDARD_SIZE) {
            String romTypeStr = rom.type() == RomType.ExHiROM ? "ExHiROM" : (rom.isHiRom() ? "HiROM" : "LoROM");
            throw new UnsupportedHardwareException(
                    String.format("GD3 hardware does not support %s > 32Mbit: ROM is %d Mbit", romTypeStr, sizeMbit)
            );
        }
    }
}
