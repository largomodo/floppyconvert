package com.largomodo.floppyconvert.snes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reader for SNES ROM files.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Loading ROM data from disk</li>
 *   <li>Detecting and stripping 512-byte copier headers (SMC/SWC)</li>
 *   <li>Locating the internal SNES header using a scoring heuristic (LoROM vs HiROM)</li>
 *   <li>Parsing metadata (Title, Mapping, SRAM size, etc.)</li>
 * </ul>
 * <p>
 * Logic is ported from uCON64's {@code snes.c} to ensure compatibility.
 */
public class SnesRomReader {

    private static final int COPIER_HEADER_SIZE = 512;
    private static final int SNES_HEADER_START = 0x7FB0;
    private static final int HIROM_OFFSET = 0x8000;
    private static final int EXHIROM_OFFSET = 0x400000 + 0x8000; // 4MB + 32KB

    /**
     * Load and parse a SNES ROM file.
     *
     * @param path Path to the ROM file.
     * @return Parsed SnesRom object.
     * @throws IOException If file I/O fails or the ROM format is invalid.
     */
    public SnesRom load(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);

        // 1. Strip Copier Header if present
        if (data.length % 1024 == 512) {
            data = Arrays.copyOfRange(data, 512, data.length);
        }

        // 2. Detect Bank Type (HiROM vs LoROM) using ucon64 scoring
        int loRomScore = scoreHeader(data, 0); // Offset 0 for LoROM (0x7FB0)
        int hiRomScore = scoreHeader(data, HIROM_OFFSET); // Offset 0x8000 for HiROM (0xFFB0)
        int exHiRomScore = 0;

        // Check for ExHiROM if file is large enough (> 4MB)
        if (data.length >= 0x400000 + 0x10000) {
            exHiRomScore = scoreHeader(data, EXHIROM_OFFSET);
        }

        int headerOffset;
        RomType type;

        // Determine best match
        if (exHiRomScore > loRomScore && exHiRomScore > hiRomScore) {
            headerOffset = EXHIROM_OFFSET;
            type = RomType.ExHiROM;
        } else if (hiRomScore > loRomScore) {
            headerOffset = HIROM_OFFSET;
            type = RomType.HiROM;
        } else {
            // Default to LoROM if scores are equal or LoROM is higher
            headerOffset = 0;
            type = RomType.LoROM;
        }

        // 3. Parse Header
        int base = SNES_HEADER_START + headerOffset;
        if (base + 0x50 > data.length) {
            throw new IOException("ROM is too small to contain a valid SNES header.");
        }

        String title = readString(data, base + 0x10, 21);
        int romSpeedMap = data[base + 0x25] & 0xFF;
        int romType = data[base + 0x26] & 0xFF;
        int romSizeByte = data[base + 0x27] & 0xFF;
        int sramSizeByte = data[base + 0x28] & 0xFF;
        int region = data[base + 0x29] & 0xFF;
        int maker = data[base + 0x2A] & 0xFF;
        int version = data[base + 0x2B] & 0xFF;
        int checksumComplement = readWord(data, base + 0x2C);
        int checksum = readWord(data, base + 0x2E);

        // SRAM Size calculation: 0 = 0, otherwise 1KB << size
        int sramSize = (sramSizeByte == 0) ? 0 : (1024 << sramSizeByte);

        // DSP detection (SuperFX, SA-1, etc.)
        // ROM Type: 0x03=DSP, 0x05=DSP, 0x13=MarioChip, 0x14=GSU, 0x15=GSU, 0x1A=GSU
        boolean hasDsp = (romType == 0x03 || romType == 0x05 ||
                (romType >= 0x13 && romType <= 0x15) || romType == 0x1A);

        return new SnesRom(
                data,
                type,
                sramSize,
                title,
                hasDsp,
                region,
                maker,
                version,
                checksum,
                checksumComplement
        );
    }

    /**
     * Port of ucon64's check_banktype scoring algorithm.
     * Checks checksum validity, printable characters, and reasonable header values.
     */
    private int scoreHeader(byte[] data, int offset) {
        int base = SNES_HEADER_START + offset;
        if (base + 0x50 > data.length) {
            return 0;
        }

        int score = 0;

        // Title contains printable chars (ucon64: score += 1)
        if (isPrintable(data, base + 0x10, 21)) {
            score += 1;
        }

        // Map Type (0x25) low nibble < 4 (ucon64: score += 2)
        int mapType = data[base + 0x25] & 0x0F;
        if (mapType < 4) {
            score += 2;
        }

        // Map Mode byte at 0x15 with value 0x25 indicates Extended HiROM mapping per SNES specification
        if ((data[base + 0x15] & 0xFF) == 0x25) {
            score += 10;
        }

        // ROM Size reasonable (<= 64Mbit) (ucon64: score += 1)
        int romSize = data[base + 0x27] & 0xFF;
        if (romSize >= 7 && romSize <= 13) { // 1Mbit to 64Mbit
            score += 1;
        }

        // SRAM Size reasonable (<= 256Kbit) (ucon64: score += 1)
        int sramSize = data[base + 0x28] & 0xFF;
        if (sramSize <= 5) { // up to 256Kbit
            score += 1;
        }

        // Country reasonable (<= 13) (ucon64: score += 1)
        int country = data[base + 0x29] & 0xFF;
        if (country <= 13) {
            score += 1;
        }

        // Maker check (0x33 or printable) (ucon64: score += 2)
        int maker = data[base + 0x2A] & 0xFF;
        if (maker == 0x33 || isPrintable(data, base + 0x2A, 2)) { // Maker is 1 byte, but title check logic applies
            score += 2;
        }

        // Version (ucon64: score += 2)
        int version = data[base + 0x2B] & 0xFF;
        if (version <= 2) { // arbitrary heuristic from ucon64
            score += 2;
        }

        // Checksum + Complement == 0xFFFF (ucon64: score += 4 or 3)
        int comp = readWord(data, base + 0x2C);
        int sum = readWord(data, base + 0x2E);
        if ((sum + comp) == 0xFFFF) {
            score += 4;
        }

        // Reset Vector (0x3D) points to ROM (high bit set) (ucon64: score += 3)
        // Offset 0x3C is low byte, 0x3D is high byte of vector
        // Vector is relative to 65816 bank. ROM is usually $8000-$FFFF.
        int resetVectorHigh = data[base + 0x3D] & 0xFF;
        if ((resetVectorHigh & 0x80) != 0) {
            score += 3;
        }

        return score;
    }

    private boolean isPrintable(byte[] data, int offset, int len) {
        for (int i = 0; i < len; i++) {
            int c = data[offset + i] & 0xFF;
            if (c < 0x20 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    private int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private String readString(byte[] data, int offset, int len) {
        // Trim nulls and trailing spaces
        String s = new String(data, offset, len, StandardCharsets.US_ASCII);
        return s.trim();
    }
}