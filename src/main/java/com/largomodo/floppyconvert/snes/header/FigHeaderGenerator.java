package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.util.Arrays;

/**
 * Header generator for Pro Fighter (FIG) format.
 * <p>
 * Logic ported from {@code snes.c} (snes_fig/snes_set_fig_header).
 * FIG headers are 512 bytes and MUST be attached to EVERY split file.
 */
public class FigHeaderGenerator implements HeaderGenerator {

    private final SramEncoder sramEncoder;

    public FigHeaderGenerator() {
        this.sramEncoder = new FigSramEncoder();
    }

    @Override
    public byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // Hardware reads bytes 0-1 to determine sector count for this part
        int blocks = partSize / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // 2: Multi (0x40 = more files, 0x00 = last file)
        if (!isLastPart) {
            header[2] = 0x40;
        } else {
            header[2] = 0x00;
        }

        // 3: HiROM (0x80 = HiROM, 0x00 = LoROM)
        if (rom.isHiRom()) {
            header[3] = (byte) 0x80;
        }

        sramEncoder.encodeSram(header, rom);

        return header;
    }
}