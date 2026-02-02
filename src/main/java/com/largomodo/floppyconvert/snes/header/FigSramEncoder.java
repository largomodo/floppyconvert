package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * FIG format SRAM encoder.
 * <p>
 * FIG copier uses split encoding: byte 4 for emulation mode with DSP flag,
 * byte 5 for SRAM presence. HiROM and LoROM require different bit patterns.
 */
public class FigSramEncoder implements SramEncoder {

    @Override
    public void encodeSram(byte[] header, SnesRom rom) {
        int emu1 = header[4] & 0xFF;
        int emu2 = header[5] & 0xFF;

        if (rom.isHiRom()) {
            emu2 |= 0x02;
            if (rom.hasDsp()) {
                emu1 |= 0xF0;
            }
            if (rom.sramSize() > 0) {
                emu1 |= 0xDD;
            }
        } else {
            if (rom.sramSize() == 0) {
                emu1 = 0x77;
                emu2 = 0x83;
            } else if (rom.sramSize() <= 8192) {
                emu2 = 0x80;
            }

            if (rom.hasDsp()) {
                emu1 &= 0x0F;
                emu1 |= 0x40;
            }
        }

        header[4] = (byte) emu1;
        header[5] = (byte) emu2;
    }
}
