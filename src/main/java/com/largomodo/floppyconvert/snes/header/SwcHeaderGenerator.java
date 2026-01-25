package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.util.Arrays;

/**
 * Header generator for Super Wild Card (SWC) format.
 * <p>
 * SWC headers are 512 bytes and are generated for ALL split parts.
 */
public class SwcHeaderGenerator implements HeaderGenerator {

    @Override
    public byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // Size in 8KB blocks (total ROM size)
        int blocks = rom.rawData().length / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // Emulation Mode
        int emulation = 0;

        // HiROM check
        if (rom.isHiRom()) {
            emulation |= 0x30;
        }

        // SRAM Size
        // 0x04 = 64Kb (8KB), 0x08 = 16Kb (2KB), 0x0C = 0Kb, 0x00 = 256Kb (32KB)
        if (rom.sramSize() == 0) {
            emulation |= 0x0C;
        } else if (rom.sramSize() <= 2048) {
            emulation |= 0x08;
        } else if (rom.sramSize() <= 8192) {
            emulation |= 0x04;
        } else {
            emulation |= 0x00; // 32KB
        }

        // Split / Multi-file flag
        if (!isLastPart) {
            emulation |= 0x40; // Bit 6 set if more files follow
        }

        header[2] = (byte) emulation;

        // Fixed IDs
        header[8] = (byte) 0xAA;
        header[9] = (byte) 0xBB;
        header[10] = 0x04; // Type 4 = SNES Program

        return header;
    }
}