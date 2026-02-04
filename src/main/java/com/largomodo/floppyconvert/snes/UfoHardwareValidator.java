package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;

/**
 * Hardware validator for Super UFO backup unit format.
 * <p>
 * UFO hardware constraints from ucon64 snes.c:1445:
 * - HiROM maximum: 32Mbit (4,194,304 bytes) due to bank mapping limitations beyond $40
 * - LoROM: Max 9 parts at 4Mbit chunks (36Mbit ceiling) per ucon64 snes_split_ufo lines 2537-2551
 */
public class UfoHardwareValidator implements HardwareValidator {

    private static final int MAX_HIROM_SIZE = 32 * SnesConstants.MBIT;

    @Override
    public void validate(SnesRom rom, CopierFormat format) throws UnsupportedHardwareException {
        if (format != CopierFormat.UFO) {
            return;
        }

        if (rom.isHiRom() && rom.rawData().length > MAX_HIROM_SIZE) {
            int sizeBytes = rom.rawData().length;
            String sizeStr = sizeBytes < SnesConstants.MBIT ?
                    String.format("%d bytes (< 1 Mbit)", sizeBytes) :
                    String.format("%d Mbit", sizeBytes / SnesConstants.MBIT);
            throw new UnsupportedHardwareException(
                    String.format("UFO hardware does not support HiROM > 32Mbit: ROM is %s", sizeStr)
            );
        }

        if (!rom.isHiRom() && rom.rawData().length > 9 * 4 * SnesConstants.MBIT) {
            int sizeMbit = rom.rawData().length / SnesConstants.MBIT;
            throw new UnsupportedHardwareException(
                String.format("UFO hardware supports max 9 parts (36Mbit) for LoROM: ROM is %d Mbit", sizeMbit)
            );
        }
    }
}
