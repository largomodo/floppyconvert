package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Reader for SNES ROM files.
 * <p>
 * This class handles:
 * <ul>
 *   <li>Loading ROM data from disk</li>
 *   <li>Detecting and stripping 512-byte copier headers (SMC/SWC/GD3)</li>
 *   <li>Locating the internal SNES header using a scoring heuristic (LoROM vs HiROM, base 0 or extended)</li>
 *   <li>Parsing metadata (Title, Mapping, SRAM size, etc.)</li>
 * </ul>
 * <p>
 * Two pipelines share the parsing. The legacy pipeline serves FIG, SWC and UFO: it strips a 512-byte header and
 * scores LoROM, HiROM and extended HiROM, nothing else. The GD3
 * pipeline follows ucon64 ({@code snes_handle_backup_header}, {@code snes_check_interleaved},
 * {@code snes_deinterleave}): extended header probe first, GD3 header recognition, interleave detection and
 * deinterleaving. Format-scoped detection keeps the other formats byte-stable: on sd2snes it would flip
 * 3 LoROM-looking ROMs to interleaved. (ref: DL-002)
 * <p>
 * Logic is ported from uCON64's {@code snes.c} to ensure compatibility.
 */
public class SnesRomReader {

    private static final int COPIER_HEADER_SIZE = 512;
    private static final int SNES_HEADER_START = 0x7FB0;
    private static final int HIROM_OFFSET = 0x8000;
    private static final int EXHIROM_OFFSET = SnesRom.EXTENDED_BASE + HIROM_OFFSET; // 4MB + 32KB
    private static final int HEADER_LENGTH = 0x50;
    private static final int CHECKSUM_PAIR_FIELD = 0x2C;
    private static final int EXTENDED_MIN_LENGTH = SnesRom.EXTENDED_BASE + SNES_HEADER_START + HEADER_LENGTH;
    private static final int MIN_INTERLEAVED_LENGTH = 64 * 1024;
    private static final int MAX_DEINTERLEAVE_BLOCKS = 512;
    private static final byte[] GD3_MAGIC = "GAME DOCTOR SF 3".getBytes(StandardCharsets.US_ASCII);

    // First-512-byte CRC32 values that decide interleave detection in ucon64 (snes_check_interleaved, snes.c 3666).
    // The not-interleaved CRC belongs to Yuuyu no Quiz de Go! Go!, a LoROM dump with a HiROM map byte; the map
    // rule alone would misread it. The extended CRCs are Dai Kaiju Monogatari 2, Tales of Phantasia and
    // Trials of Mana as dumped by a Game Doctor. (ref: DL-019)
    private static final long CRC_NOT_INTERLEAVED = 0xc3194ad7L;
    private static final Set<Long> CRC_EXTENDED_GD3 = Set.of(0xd7470b37L, 0xa2c5fd29L, 0x83c45607L);
    private static final Set<Integer> HIROM_MAP_TYPES = Set.of(0x21, 0x31, 0x35, 0x3A);

    private final SnesInterleaver interleaver = new SnesInterleaver();

    /**
     * Load and parse a SNES ROM file with the legacy pipeline.
     *
     * @param path Path to the ROM file.
     * @return Parsed SnesRom object.
     * @throws IOException If file I/O fails or the ROM format is invalid.
     */
    public SnesRom load(Path path) throws IOException {
        return loadLegacy(path);
    }

    /**
     * Load and parse a SNES ROM file for the given target format: the ucon64 pipeline for GD3, the
     * legacy pipeline for every other format.
     */
    public SnesRom load(Path path, CopierFormat format) throws IOException {
        return format == CopierFormat.GD3 ? loadGd3(path) : loadLegacy(path);
    }

    private SnesRom loadLegacy(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);

        // 1. Strip Copier Header if present
        if (data.length % 1024 == COPIER_HEADER_SIZE) {
            data = Arrays.copyOfRange(data, COPIER_HEADER_SIZE, data.length);
        }

        // 2. Detect Bank Type (HiROM vs LoROM) using ucon64 scoring
        int loRomScore = scoreHeader(data, 0); // Offset 0 for LoROM (0x7FB0)
        int hiRomScore = scoreHeader(data, HIROM_OFFSET); // Offset 0x8000 for HiROM (0xFFB0)
        int exHiRomScore = 0;

        // Check for the extended header if file is large enough (> 4MB)
        if (data.length >= 0x400000 + 0x10000) {
            exHiRomScore = scoreHeader(data, EXHIROM_OFFSET);
        }

        // The extended header wins only on a strictly higher score: FIG, SWC and UFO output depends on
        // this tie-break. (ref: DL-002, DL-003)
        if (exHiRomScore > loRomScore && exHiRomScore > hiRomScore) {
            return parse(data, RomType.HiROM, SnesRom.EXTENDED_BASE, false, false);
        }
        if (hiRomScore > loRomScore) {
            return parse(data, RomType.HiROM, 0, false, false);
        }
        // Default to LoROM if scores are equal or LoROM is higher
        return parse(data, RomType.LoROM, 0, false, false);
    }

    private SnesRom loadGd3(Path path) throws IOException {
        byte[] file = Files.readAllBytes(path);

        boolean copierHeader = file.length % 1024 == COPIER_HEADER_SIZE;
        boolean gd3Headered = copierHeader && startsWith(file, GD3_MAGIC);
        byte[] data = copierHeader ? Arrays.copyOfRange(file, COPIER_HEADER_SIZE, file.length) : file;

        // A 93-96 Mbit image is the 96 Mbit LoROM class whatever its headers say: nothing else of that size converts
        // for a Game Doctor. Street Fighter Alpha 2 repeats its header at 0x7FB0, 0xFFB0, 0x407FB0 and 0x40FFB0,
        // so the extended probe and the scorer would turn it into an unconvertible HiROM. (ref: DL-012)
        boolean loRom96 = Gd3Layout.isLoRom96Size(data.length);
        int headerBase = loRom96 ? 0 : findExtendedBase(data);
        RomType type;
        if (loRom96) {
            type = RomType.LoROM;
        } else if (headerBase == SnesRom.EXTENDED_BASE) {
            type = scoreHeader(data, EXHIROM_OFFSET) > scoreHeader(data, SnesRom.EXTENDED_BASE) ? RomType.HiROM : RomType.LoROM;
        } else {
            type = scoreHeader(data, HIROM_OFFSET) > scoreHeader(data, 0) ? RomType.HiROM : RomType.LoROM;
        }

        boolean interleaved = false;
        if (!loRom96 && headerBase == 0 && type == RomType.LoROM) {
            InterleaveProbe probe = probeInterleave(data);
            headerBase = probe.headerBase();
            interleaved = probe.interleaved();
        }
        if (interleaved) {
            data = interleaver.deinterleave(data, chooseScheme(data.length, headerBase, gd3Headered || !copierHeader));
            // An interleaved dump reads as LoROM only because its HiROM header landed at 0x7FB0
            type = RomType.HiROM;
        }

        return parse(data, type, headerBase, interleaved, gd3Headered);
    }

    // ucon64 snes_deinterleave: the Game Doctor-specific tables apply to GD3 files and headerless files;
    // a file with another copier's header always uses the global scheme.
    private static Gd3Interleave chooseScheme(int length, int headerBase, boolean gameDoctorTables) {
        if (!gameDoctorTables) {
            return Gd3Interleave.GLOBAL;
        }
        if (length == 24 * SnesConstants.MBIT) {
            return Gd3Interleave.TWO_REGION_24;
        }
        return headerBase == SnesRom.EXTENDED_BASE ? Gd3Interleave.EXTENDED : Gd3Interleave.GLOBAL;
    }

    /**
     * ucon64 snes_handle_backup_header: a valid checksum pair at 0x40FFDC (HiROM) or 0x407FDC (LoROM)
     * marks the header base as extended.
     */
    private static int findExtendedBase(byte[] data) {
        if (data.length < EXTENDED_MIN_LENGTH) {
            return 0;
        }
        boolean pairFound = hasChecksumPair(data, EXHIROM_OFFSET + SNES_HEADER_START + CHECKSUM_PAIR_FIELD)
                || hasChecksumPair(data, SnesRom.EXTENDED_BASE + SNES_HEADER_START + CHECKSUM_PAIR_FIELD);
        return pairFound ? SnesRom.EXTENDED_BASE : 0;
    }

    private static boolean hasChecksumPair(byte[] data, int offset) {
        if (offset + 4 > data.length) {
            return false;
        }
        return readWord(data, offset) + readWord(data, offset + 2) == 0xFFFF;
    }

    private record InterleaveProbe(boolean interleaved, int headerBase) {
    }

    /**
     * Port of ucon64 snes_check_interleaved for a dump whose base-0 scoring said LoROM: a HiROM map-type byte
     * at the LoROM header position means the dump is interleaved. The extended CRCs additionally mark the
     * header base as extended.
     */
    private static InterleaveProbe probeInterleave(byte[] data) {
        if (data.length < MIN_INTERLEAVED_LENGTH) {
            return new InterleaveProbe(false, 0);
        }
        CRC32 crc = new CRC32();
        crc.update(data, 0, COPIER_HEADER_SIZE);
        long checksum = crc.getValue();
        if (checksum == CRC_NOT_INTERLEAVED) {
            return new InterleaveProbe(false, 0);
        }

        int headerBase = 0;
        if (CRC_EXTENDED_GD3.contains(checksum) && data.length >= EXTENDED_MIN_LENGTH) {
            headerBase = SnesRom.EXTENDED_BASE;
        }
        int mapType = data[SNES_HEADER_START + 0x25] & 0xFF;
        // ucon64 cannot deinterleave past 16 MB (512 blocks), so such a dump stays linear here as well
        boolean interleaved = HIROM_MAP_TYPES.contains(mapType) && (data.length >>> 16) * 2 <= MAX_DEINTERLEAVE_BLOCKS;
        return new InterleaveProbe(interleaved, headerBase);
    }

    private SnesRom parse(byte[] data, RomType type, int headerBase, boolean interleaved, boolean gd3Headered)
            throws IOException {
        // 3. Parse Header
        int base = headerBase + (type == RomType.HiROM ? HIROM_OFFSET : 0) + SNES_HEADER_START;
        if (base + HEADER_LENGTH > data.length) {
            // The typed exception lets the batch classifier report HEADER.
            throw new InvalidRomHeaderException("ROM is too small to contain a valid SNES header.");
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
                checksumComplement,
                headerBase,
                interleaved,
                gd3Headered
        );
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Scores a header candidate at {@code offset}: checksum validity, printable characters and plausible header
     * values. Scoring is base-0 and modelled on ucon64's check_banktype but is not a port of it: check_banktype
     * also folds in interleave and extended-base probing, which the reader performs as separate steps, and
     * FIG/SWC/UFO output must stay byte-stable on the existing scores. (ref: DL-016)
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

        // Map mode 0x25 is Extended HiROM per SNES specification. Its low nibble fails the check above,
        // so without this an extended header would score below a plain one.
        // The map byte is at 0x25 from SNES_HEADER_START (0x15 from the title at 0x7FC0).
        if ((data[base + 0x25] & 0xFF) == 0x25) {
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

    private static int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private String readString(byte[] data, int offset, int len) {
        // Trim nulls and trailing spaces
        String s = new String(data, offset, len, StandardCharsets.US_ASCII);
        return s.trim();
    }
}