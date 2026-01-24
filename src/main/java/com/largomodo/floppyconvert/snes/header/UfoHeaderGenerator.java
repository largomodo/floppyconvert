package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Header generator for Super UFO format.
 * <p>
 * Logic ported from {@code snes.c} (snes_ufo).
 * UFO headers are 512 bytes.
 * For splits, ucon64 {@code snes_split_ufo} generates headers for every part.
 */
public class UfoHeaderGenerator implements HeaderGenerator {

    @Override
    public byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // 0-1: Size in 8KB blocks (filled by splitter usually, using total for now)
        int blocks = rom.rawData().length / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // 2: Multi / Split flag
        // HiROM splits are complex in UFO (see snes_split_ufo size_to_flags),
        // but LoROM uses 0x40 for multi.
        // Simplified logic:
        if (!isLastPart) {
            header[2] = 0x40;
        }

        // 8-15: "SUPERUFO"
        byte[] id = "SUPERUFO".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, header, 8, id.length);

        // 16: Uses SRAM
        header[16] = (byte) (rom.sramSize() > 0 ? 1 : 0);

        // 17: Size code (Mbit)
        int sizeMbit = rom.rawData().length / 131072;
        header[17] = (byte) sizeMbit;

        // 18: Bank Type (0=HiROM, 1=LoROM)
        header[18] = (byte) (rom.isHiRom() ? 0 : 1);

        // 19: SRAM Size Code
        // 1=16kb, 2=64kb, 3=256kb, 8=>32kb
        int sramCode = 0;
        if (rom.sramSize() > 32768) sramCode = 8; // >256kbit
        else if (rom.sramSize() > 8192) sramCode = 3; // 256kbit
        else if (rom.sramSize() > 2048) sramCode = 2; // 64kbit
        else if (rom.sramSize() > 0) sramCode = 1; // 16kbit
        header[19] = (byte) sramCode;

        // 20-22: SRAM Mapping Controls (A15, A20/A21, A22/A23)
        // 23: SRAM Type
        if (rom.isHiRom()) {
            header[23] = 0; // HiROM SRAM type

            // Map Control (HiROM)
            // Based on snes_ufo logic for HiROM
            if (rom.sramSize() > 0) {
                header[21] = 0x0C; // A20/A21
            }
            header[22] = 0x02; // A22/A23
        } else {
            header[23] = 3; // LoROM SRAM type

            if (rom.sramSize() == 0) {
                if (rom.hasDsp()) {
                    header[20] = 1; // A15
                    header[21] = 0x0C; // A20/A21
                } else {
                    header[22] = 2;
                    header[23] = 0;
                }
            } else {
                header[20] = 2; // A15=0 selects SRAM
                header[21] = 0x0F; // A20=0, A21=1
                header[22] = 3; // A22=1, A23=0
            }
        }

        return header;
    }
}