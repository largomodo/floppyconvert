package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * UFO format SRAM encoder.
 * <p>
 * UFO copier requires SRAM mapping controls (A15, A20-A23) at bytes 20-22
 * for bank allocation. SRAM type encoding (byte 23) differs for HiROM vs LoROM.
 */
public class UfoSramEncoder implements SramEncoder {

    @Override
    public void encodeSram(byte[] header, SnesRom rom) {
        header[16] = (byte) (rom.sramSize() > 0 ? 1 : 0);

        int sramCode = 0;
        if (rom.sramSize() > 32768) sramCode = 8;
        else if (rom.sramSize() > 8192) sramCode = 3;
        else if (rom.sramSize() > 2048) sramCode = 2;
        else if (rom.sramSize() > 0) sramCode = 1;
        header[19] = (byte) sramCode;

        if (rom.isHiRom()) {
            header[23] = 0;

            if (rom.sramSize() > 0) {
                header[21] = 0x0C;
            }
            header[22] = 0x02;
        } else {
            header[23] = 3;

            if (rom.sramSize() == 0) {
                if (rom.hasDsp()) {
                    header[20] = 1;
                    header[21] = 0x0C;
                } else {
                    header[22] = 2;
                    header[23] = 0;
                }
            } else {
                header[20] = 2;
                header[21] = 0x0F;
                header[22] = 3;
            }
        }
    }
}
