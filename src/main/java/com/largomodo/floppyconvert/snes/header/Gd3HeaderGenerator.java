package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SpecialCaseRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Header generator for Game Doctor (GD3/SF3/SF7) format.
 * <p>
 * Logic ported from {@code snes.c} (snes_set_gd3_header).
 * Header is only present on the first file (file.078).
 */
public class Gd3HeaderGenerator implements HeaderGenerator {

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
    private static final byte[] MAP_HI_24MB = {
            0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
            0x20, 0x21, 0x22, 0x00, 0x20, 0x21, 0x22, 0x00,
            0x24, 0x25, 0x23, 0x00, 0x24, 0x25, 0x23, 0x00
    };
    private static final byte[] MAP_HI_32MB = {
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27
    };
    // New Extended Maps
    private static final byte[] MAP_HI_40MB = {
            0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20,
            0x22, 0x23, 0x24, 0x25, 0x22, 0x23, 0x24, 0x25,
            0x21, 0x21, 0x21, 0x21, 0x26, 0x27, 0x28, 0x29
    };
    private static final byte[] MAP_HI_48MB = {
            0x20, 0x21, 0x20, 0x21, 0x20, 0x21, 0x20, 0x21,
            0x24, 0x25, 0x26, 0x27, 0x24, 0x25, 0x26, 0x27,
            0x22, 0x23, 0x22, 0x23, 0x28, 0x29, 0x2A, 0x2B
    };
    private static final byte[] MAP_HI_64MB = {
            0x20, 0x21, 0x22, 0x23, 0x20, 0x21, 0x22, 0x23,
            0x28, 0x29, 0x2A, 0x2B, 0x28, 0x29, 0x2A, 0x2B,
            0x24, 0x25, 0x26, 0x27, 0x2C, 0x2D, 0x2E, 0x2F
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
    // Extended LoROM map (48Mbit+ support)
    private static final byte[] MAP_LO_64MB = {
            0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
            0x2C, 0x2D, 0x2E, 0x2F, 0x24, 0x25, 0x26, 0x27
    };
    private final SramEncoder sramEncoder;

    public Gd3HeaderGenerator() {
        this.sramEncoder = new Gd3SramEncoder();
    }

    @Override
    public byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag) {
        // GD3 headers only on first part, describe entire ROM layout. partSize is ignored.
        if (splitPartIndex != 0) {
            return new byte[0];
        }

        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // 0-15: ID
        byte[] id = "GAME DOCTOR SF 3".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, header, 0, id.length);

        sramEncoder.encodeSram(header, rom);

        // 17-40: Memory Map (0x11 - 0x28)
        // Calculate total 4Mbit parts
        int total4MbParts = (rom.rawData().length + (512 * 1024) - 1) / (512 * 1024);

        if (rom.isHiRom()) {
            setHiRomMap(header, total4MbParts, rom);

            // SRAM mapping for HiROM
            if (rom.sramSize() > 0) {
                boolean isTop = SpecialCaseRegistry.isTalesOfPhantasia(rom);
                boolean isDkm2 = SpecialCaseRegistry.isDaiKaijuMonogatari2(rom);

                if (isTop || isDkm2) {
                    if (isTop) {
                        header[0x17] = 0x40;
                        header[0x18] = 0x40;
                        header[0x23] = 0x40;
                        header[0x24] = 0x40;
                    }
                    header[0x29] = 0x00;
                    header[0x2A] = 0x0F;
                } else {
                    header[0x29] = 0x0C;
                    header[0x2A] = 0x0C;
                }
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

    private void setHiRomMap(byte[] header, int parts, SnesRom rom) {
        // Memory map based on TOTAL ROM size, not part size. Hardware decodes entire game layout from first part header.
        byte[] map;
        if (parts <= 2) map = MAP_HI_8MB;
        else if (parts <= 4) map = MAP_HI_16MB;
        else if (parts <= 6) map = MAP_HI_24MB;
        else if (parts <= 8) map = MAP_HI_32MB;
        else if (parts <= 10) map = MAP_HI_40MB; // Dai Kaiju Monogatari 2
        else if (parts <= 12) map = MAP_HI_48MB; // Tales of Phantasia
        else map = MAP_HI_64MB; // Extended HiROM (e.g. Star Ocean)

        System.arraycopy(map, 0, header, 0x11, map.length);
    }

    private void setLoRomMap(byte[] header, int parts) {
        byte[] map;
        if (parts <= 1) map = MAP_LO_4MB;
        else if (parts <= 2) map = MAP_LO_8MB;
        else if (parts <= 4) map = MAP_LO_16MB;
        else if (parts <= 8) map = MAP_LO_32MB;
        else map = MAP_LO_64MB; // Extended LoROM support (rare)

        System.arraycopy(map, 0, header, 0x11, map.length);
    }
}