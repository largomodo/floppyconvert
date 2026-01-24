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

    @Override
    public byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // 0-1: Size in 8KB blocks
        int blocks = rom.rawData().length / 8192;
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

        // 4-5: Emulation Bytes (Ported from snes_set_fig_header)
        int emu1 = 0;
        int emu2 = 0;

        if (rom.hasDsp()) { // Uses DSP, SuperFX, etc.
            // Special case logic from snes.c
            // This is simplified; ucon64 has complex checks for ROM type 0x10 vs others.
            // Assuming standard DSP behavior for now.
        }

        if (rom.isHiRom()) {
            // HiROM Defaults
            emu2 |= 0x02;
            if (rom.hasDsp()) {
                emu1 |= 0xF0;
            }
            if (rom.sramSize() > 0) {
                emu1 |= 0xDD; // 1101 1101
            }
        } else {
            // LoROM Defaults
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

        return header;
    }
}