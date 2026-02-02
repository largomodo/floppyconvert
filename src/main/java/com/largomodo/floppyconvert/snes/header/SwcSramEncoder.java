package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * SWC format SRAM encoder.
 * Mapping: 256KB → 0x0C, 64KB → 0x08, 8KB → 0x04, 0KB/2KB → 0x00
 */
public class SwcSramEncoder implements SramEncoder {

    @Override
    public void encodeSram(byte[] header, SnesRom rom) {
        int sramBits;

        if (rom.sramSize() == 0) {
            sramBits = 0x0C;
        } else if (rom.sramSize() <= 2048) {
            sramBits = 0x08;
        } else if (rom.sramSize() <= 8192) {
            sramBits = 0x04;
        } else {
            sramBits = 0x00;
        }

        header[2] |= (byte) sramBits;
    }
}
