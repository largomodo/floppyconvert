package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * GD3 format SRAM encoder.
 * <p>
 * GD3 copier uses single-byte encoding at byte 16.
 * Hardware limitation: only supports 0KB, 16KB (2KB), and 64KB (8KB) SRAM sizes.
 */
public class Gd3SramEncoder implements SramEncoder {

    @Override
    public void encodeSram(byte[] header, SnesRom rom) {
        if (rom.sramSize() == 8192) {
            header[16] = (byte) 0x81;
        } else if (rom.sramSize() == 2048) {
            header[16] = (byte) 0x82;
        } else {
            header[16] = (byte) 0x80;
        }
    }
}
