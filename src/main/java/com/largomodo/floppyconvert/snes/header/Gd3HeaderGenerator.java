package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.Gd3Layout;
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
    // 96 Mbit LoROM (Star Ocean, Street Fighter Alpha 2 builds): 24 sequential 4 Mbit units
    // Unit i of the 96 Mbit image maps to 0x20 + i.
    private static final byte[] MAP_LO_96 = {
            0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
            0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F,
            0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37
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
        // The layout owns the part count: a padded 20 Mbit ROM selects the 24 Mbit map, so the header never
        // disagrees with the bytes the splitter emits.
        Gd3Layout layout = Gd3Layout.of(rom);
        int total4MbParts = layout.totalParts4Mbit();

        if (rom.isHiRom()) {
            setHiRomMap(header, total4MbParts, rom);

            applyHiRomSram(header, rom);
        } else {
            setLoRomMap(header, layout);

            applyLoRomSpecials(header, rom, layout);
        }

        return header;
    }

    // SRAM mapping for HiROM (ucon64 snes_set_gd3_header 1645-1667): only extended-header ROMs with maker 0x33
    // and the Tales of Phantasia / Dai Kaiju Monogatari 2 ID bytes get the special map.
    private static void applyHiRomSram(byte[] header, SnesRom rom) {
        if (rom.sramSize() <= 0) {
            return;
        }
        boolean isTop = SpecialCaseRegistry.isTalesOfPhantasia(rom);
        boolean isDkm2 = SpecialCaseRegistry.isDaiKaijuMonogatari2(rom);
        if (!isTop && !isDkm2) {
            header[0x29] = 0x0C;
            header[0x2A] = 0x0C;
            return;
        }
        if (isTop) {
            header[0x17] = 0x40;
            header[0x18] = 0x40;
            header[0x23] = 0x40;
            header[0x24] = 0x40;
        }
        header[0x29] = 0x00;
        header[0x2A] = 0x0F;
    }

    private static void applyLoRomSpecials(byte[] header, SnesRom rom, Gd3Layout layout) {
        // ucon64 snes_set_gd3_header (snes.c 1717-1721, DL-009): chipset byte 0x26 in {0x03, 0x05 (DSP),
        // 0xF6 Seta DSP} sets header bytes 0x14 and 0x1C to 0x60. hasDsp() is true for SuperFX and
        // false for the Seta DSP (0xF6), so it would write the DSP bytes for the wrong chips.
        int chipset = rom.headerByte(0x26);
        if (chipset == 0x03 || chipset == 0x05 || chipset == 0xF6) {
            header[0x14] = 0x60;
            header[0x1C] = 0x60;
        }
        if (layout.isLoRom96()) {
            // The ordinary LoROM SRAM bytes do not apply to the 96 Mbit class. Star Ocean is the one verified
            // SRAM case: map entry 19 (offset 0x24) is 0x00 and the byte at 0x29 is 0x08. (ref: DL-012)
            if (SpecialCaseRegistry.isStarOcean96(rom, layout)) {
                header[0x24] = 0x00;
                header[0x29] = 0x08;
            }
        } else if (rom.sramSize() > 0) {
            header[0x24] = 0x40;
            header[0x28] = 0x40;
        }
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

    private void setLoRomMap(byte[] header, Gd3Layout layout) {
        int parts = layout.totalParts4Mbit();
        byte[] map;
        if (layout.isLoRom96()) map = MAP_LO_96;
        else if (parts <= 1) map = MAP_LO_4MB;
        else if (parts <= 2) map = MAP_LO_8MB;
        else if (parts <= 4) map = MAP_LO_16MB;
        else if (parts <= 8) map = MAP_LO_32MB;
        else map = MAP_LO_64MB; // Extended LoROM support (rare)

        System.arraycopy(map, 0, header, 0x11, map.length);
    }
}