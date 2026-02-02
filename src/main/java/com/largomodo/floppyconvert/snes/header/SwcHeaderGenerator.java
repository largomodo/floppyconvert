package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.util.Arrays;

/**
 * Header generator for Super Wild Card (SWC) format.
 * <p>
 * SWC headers are 512 bytes and are generated for ALL split parts.
 */
public class SwcHeaderGenerator implements HeaderGenerator {

    private final SramEncoder sramEncoder;

    public SwcHeaderGenerator() {
        this.sramEncoder = new SwcSramEncoder();
    }

    @Override
    public byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // Hardware reads bytes 0-1 to determine sector count for this part
        int blocks = partSize / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // Emulation Mode
        int emulation = 0;

        // HiROM check
        if (rom.isHiRom()) {
            emulation |= 0x30;
        }

        header[2] = (byte) emulation;

        sramEncoder.encodeSram(header, rom);

        // Split / Multi-file flag
        if (!isLastPart) {
            header[2] |= 0x40;
        }

        // Fixed IDs
        header[8] = (byte) 0xAA;
        header[9] = (byte) 0xBB;
        header[10] = 0x04; // Type 4 = SNES Program

        return header;
    }
}