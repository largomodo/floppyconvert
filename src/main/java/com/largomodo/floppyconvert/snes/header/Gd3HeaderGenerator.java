package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Header generator for Game Doctor (GD3/SF3/SF7) format.
 * <p>
 * Logic ported from {@code snes.c} (snes_set_gd3_header).
 * Header is only present on the first file (file.078).
 */
public class Gd3HeaderGenerator implements HeaderGenerator {

    @Override
    public byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart) {
        // GD3 only puts header on the first part
        if (splitPartIndex != 0) {
            return new byte[0];
        }

        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // 0-15: ID
        byte[] id = "GAME DOCTOR SF 3".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, header, 0, id.length);

        // 16: SRAM Size Code
        // 0x81 = 64kb, 0x82 = 16kb, 0x80 = 0kb or 256kb
        if (rom.sramSize() == 8192) header[16] = (byte) 0x81;
        else if (rom.sramSize() == 2048) header[16] = (byte) 0x82;
        else header[16] = (byte) 0x80;

        // 17-40: Memory Map (0x11 - 0x28)
        // Calculate total 4Mbit parts
        int total4MbParts = (rom.rawData().length + (512 * 1024) - 1) / (512 * 1024);

        if (rom.isHiRom()) {
            setHiRomMap(header, total4MbParts);

            // SRAM mapping for HiROM
            if (rom.sramSize() > 0) {
                header[0x29] = 0x0C;
                header[0x2A] = 0x0C;
            }
        } else {
            setLoRomMap(header, total4MbParts);

            if (rom.hasDsp()) {
                header[0x14] = 0x60;
                header[0x1C] = 0x60;
            }
            if (rom.sramSize() > 0) {
                header[0x24] = 0x40;
                header[0x28] = 0x40;
            }
        }

        return header;
    }

    private void setHiRomMap(byte[] header, int parts) {
        byte[] map;
        if (parts <= 2) map = MAP_HI_8MB;
        else if (parts <= 4) map = MAP_HI_16MB;
        else if (parts <= 6) map = MAP_HI_24MB;
        else if (parts <= 8) map = MAP_HI_32MB;
        else map = MAP_HI_32MB; // Fallback / TODO 40MB+ maps

        System.arraycopy(map, 0, header, 0x11, map.length);
    }

    private void setLoRomMap(byte[] header, int parts) {
        byte[] map;
        if (parts <= 1) map = MAP_LO_4MB;
        else if (parts <= 2) map = MAP_LO_8MB;
        else if (parts <= 4) map = MAP_LO_16MB;
        else if (parts <= 8) map = MAP_LO_32MB;
        else map = MAP_LO_32MB;

        System.arraycopy(map, 0, header, 0x11, map.length);
    }

    // Map Tables (from snes.c)
    private static final byte[] MAP_HI_8MB = {
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22, 0x22
    };
    private static final byte[] MAP_HI_16MB = {
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
            0x22, 0x23, 0x22, 0x23, 0x22, 0x23, 0x22, 0x23
    };
    // ... (abbreviated maps for 24/32 MB, following pattern)
    // 24MB
    private static final byte[] MAP_HI_24MB = {
            0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
            0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
            0x24, 0x25, 0x23, 0x00, 0x24, 0x25, 0x23, 0x00
    };
    // 32MB
    private static final byte[] MAP_HI_32MB = {
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
    };

    // LoROM Maps
    private static final byte[] MAP_LO_4MB = {
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20
    };
    private static final byte[] MAP_LO_8MB = {
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21
    };
    private static final byte[] MAP_LO_16MB = {
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23
    };
    private static final byte[] MAP_LO_32MB = {
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
            0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
    };
}