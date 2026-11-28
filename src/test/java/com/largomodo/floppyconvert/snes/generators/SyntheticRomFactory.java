package com.largomodo.floppyconvert.snes.generators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Factory for generating synthetic SNES ROM files for E2E testing.
 * <p>
 * Generates ROMs with valid internal headers including proper checksums,
 * covering edge cases for ROM size, SRAM configurations, and mapper types.
 */
public class SyntheticRomFactory {

    public static final int SRAM_0KB = 0;
    public static final int SRAM_2KB = 2;
    public static final int SRAM_8KB = 8;
    public static final int SRAM_64KB = 64;
    public static final int SRAM_256KB = 256;
    private static final int LOROM_HEADER_OFFSET = 0x7FB0;
    private static final int HIROM_HEADER_OFFSET = 0xFFB0;
    private static final int EXHIROM_HEADER_OFFSET = 0x40FFB0;
    private static final int EXLOROM_HEADER_OFFSET = 0x407FB0;
    private static final byte MAP_TYPE_LOROM = 0x20;
    private static final byte MAP_TYPE_LOROM_DSP = 0x23;
    private static final byte MAP_TYPE_HIROM = 0x21;
    private static final byte MAP_TYPE_HIROM_DSP = 0x25;
    private static final byte MAP_TYPE_EXHIROM = 0x25;
    private static final int FILL_BLOCK_BYTES = 0x8000;
    private static final long LCG_MULTIPLIER = 6364136223846793005L;
    private static final long LCG_INCREMENT = 1442695040888963407L;
    private static final long SEED_BLOCK_MIX = 0x9E3779B97F4A7C15L;

    /**
     * Generates a LoROM file with specified parameters.
     *
     * @param sizeMbit   ROM size in megabits (8, 12, 16, etc.)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateLoRom(int sizeMbit, int sramSizeKb, DspChipset dsp, Path outputDir) throws IOException {
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        // Title pattern encodes ROM characteristics to prevent DOS 8.3 collisions in GD3 format
        String title = String.format("SYNLO%dT%d%s", sizeMbit, sramSizeKb, hasDsp ? "D" : "");
        byte mapType = hasDsp ? MAP_TYPE_LOROM_DSP : MAP_TYPE_LOROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_lorom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, LOROM_HEADER_OFFSET, title, outputDir.resolve(filename));
    }

    /**
     * Generates a HiROM file with specified parameters.
     *
     * @param sizeMbit   ROM size in megabits (8, 12, 16, etc.)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateHiRom(int sizeMbit, int sramSizeKb, DspChipset dsp, Path outputDir) throws IOException {
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        // Title pattern encodes ROM characteristics to prevent DOS 8.3 collisions in GD3 format
        String title = String.format("SYNHI%dT%d%s", sizeMbit, sramSizeKb, hasDsp ? "D" : "");
        byte mapType = hasDsp ? MAP_TYPE_HIROM_DSP : MAP_TYPE_HIROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_hirom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, HIROM_HEADER_OFFSET, title, outputDir.resolve(filename));
    }

    /**
     * Generates a LoROM file with custom title.
     *
     * @param sizeMbit   ROM size in megabits (8, 12, 16, etc.)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param title      Custom 21-character ASCII title
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateLoRom(int sizeMbit, int sramSizeKb, DspChipset dsp, String title, Path outputDir) throws IOException {
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        byte mapType = hasDsp ? MAP_TYPE_LOROM_DSP : MAP_TYPE_LOROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_lorom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, LOROM_HEADER_OFFSET, title, outputDir.resolve(filename));
    }

    /**
     * Generates a LoROM file whose internal header carries a raw chipset byte (offset 0x26).
     * Needed for chipset values that {@link DspChipset} cannot express, such as 0xF6 (Seta DSP).
     *
     * @param sizeMbit    ROM size in megabits
     * @param sramSizeKb  SRAM size in kilobytes (use SRAM_* constants)
     * @param chipsetByte raw value written at internal header offset 0x26
     * @param title       Custom 21-character ASCII title
     * @param outputDir   directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateLoRom(int sizeMbit, int sramSizeKb, int chipsetByte, String title, Path outputDir) throws IOException {
        String filename = String.format("synthetic_lorom_%dmbit_%dkb_chip%02x.sfc", sizeMbit, sramSizeKb, chipsetByte);
        return generateRom(sizeMbit, sramSizeKb, MAP_TYPE_LOROM, (byte) chipsetByte, LOROM_HEADER_OFFSET, title, outputDir.resolve(filename));
    }

    /**
     * Generates a HiROM file with custom title.
     *
     * @param sizeMbit   ROM size in megabits (8, 12, 16, etc.)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param title      Custom 21-character ASCII title
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateHiRom(int sizeMbit, int sramSizeKb, DspChipset dsp, String title, Path outputDir) throws IOException {
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        byte mapType = hasDsp ? MAP_TYPE_HIROM_DSP : MAP_TYPE_HIROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_hirom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, HIROM_HEADER_OFFSET, title, outputDir.resolve(filename));
    }

    /**
     * Generates an extended HiROM file with custom title.
     * The header is written at 0xFFB0 and at 0x40FFB0, map mode 0x25 per SNES dev docs (same as HiROM+DSP;
     * extended memory map differentiates at runtime).
     *
     * @param sizeMbit   ROM size in megabits (40-64)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param title      Custom 21-character ASCII title
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateExHiRom(int sizeMbit, int sramSizeKb, DspChipset dsp, String title, Path outputDir) throws IOException {
        if (sizeMbit < 40 || sizeMbit > 64) {
            throw new IllegalArgumentException("extended HiROM requires 40-64 Mbit (hardware constraint per Gd3HardwareValidator)");
        }
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        byte mapType = MAP_TYPE_EXHIROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_exhirom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType,
                new int[]{HIROM_HEADER_OFFSET, EXHIROM_HEADER_OFFSET}, title, outputDir.resolve(filename));
    }

    /**
     * Generates an extended LoROM file with custom title (header at 0x7FB0 and at 0x407FB0).
     *
     * @param sizeMbit   ROM size in megabits (40-64)
     * @param sramSizeKb SRAM size in kilobytes (use SRAM_* constants)
     * @param dsp        DSP chipset presence
     * @param title      Custom 21-character ASCII title
     * @param outputDir  directory to write the ROM file
     * @return Path to the generated ROM file
     * @throws IOException if file writing fails
     */
    public static Path generateExLoRom(int sizeMbit, int sramSizeKb, DspChipset dsp, String title, Path outputDir) throws IOException {
        if (sizeMbit < 40 || sizeMbit > 64) {
            throw new IllegalArgumentException("Extended LoROM requires 40-64 Mbit (hardware constraint per Gd3HardwareValidator)");
        }
        boolean hasDsp = (dsp == DspChipset.PRESENT);
        byte mapType = hasDsp ? MAP_TYPE_LOROM_DSP : MAP_TYPE_LOROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_exlorom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType,
                new int[]{LOROM_HEADER_OFFSET, EXLOROM_HEADER_OFFSET}, title, outputDir.resolve(filename));
    }

    private static Path generateRom(int sizeMbit, int sramSizeKb, byte mapType, byte romType,
                                    int headerOffset, String title, Path outputPath) throws IOException {
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, new int[]{headerOffset}, title, outputPath);
    }

    // Extended ROMs carry the same header at several offsets, as real carts do: the base-0 copy is what a
    // legacy reader scores, the 0x400000-based copy is what the extended probe finds. The fill is seeded
    // with the last offset so a single-header ROM and its two-header sibling share every body byte. (ref: DL-024)
    private static Path generateRom(int sizeMbit, int sramSizeKb, byte mapType, byte romType,
                                    int[] headerOffsets, String title, Path outputPath) throws IOException {
        int sizeBytes = sizeMbit * 1024 * 1024 / 8;
        byte[] data = new byte[sizeBytes];
        fillBody(data, sizeMbit, headerOffsets[headerOffsets.length - 1]);

        for (int headerOffset : headerOffsets) {
            writeString(data, headerOffset + 0x10, title, 21);

            data[headerOffset + 0x25] = mapType;
            data[headerOffset + 0x26] = romType;
            data[headerOffset + 0x27] = calculateRomSizeByte(sizeMbit);
            data[headerOffset + 0x28] = calculateSramSizeByte(sramSizeKb);
            data[headerOffset + 0x29] = 0x01;
            data[headerOffset + 0x2A] = 0x01;
            data[headerOffset + 0x2B] = 0x00;
        }

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        for (int headerOffset : headerOffsets) {
            writeWord(data, headerOffset + 0x2C, complement);
            writeWord(data, headerOffset + 0x2E, checksum);

            data[headerOffset + 0x3C] = (byte) 0x00;
            data[headerOffset + 0x3D] = (byte) 0x80;
        }

        Files.write(outputPath, data);
        return outputPath;
    }

    // A repeating fill gives a 20 Mbit ROM only 2 distinct 32 KB blocks out of 80, so a wrong interleave
    // or split order would still compare equal. One LCG stream per 32 KB block (the unit both interleave
    // schemes move) makes every block unique; seeding with sizeMbit and headerOffset also keeps a LoROM and
    // a HiROM fixture of the same size apart. Long arithmetic is bit-identical on every JVM and OS, which
    // the committed digest tables rely on. (ref: DL-023)
    private static void fillBody(byte[] data, int sizeMbit, int headerOffset) {
        for (int start = 0, block = 0; start < data.length; start += FILL_BLOCK_BYTES, block++) {
            long state = ((long) block * SEED_BLOCK_MIX) ^ ((long) sizeMbit << 32) ^ headerOffset;
            int end = Math.min(start + FILL_BLOCK_BYTES, data.length);
            for (int i = start; i < end; i += 4) {
                state = state * LCG_MULTIPLIER + LCG_INCREMENT;
                int word = (int) (state >>> 32);
                for (int k = 0; k < 4 && i + k < end; k++) {
                    data[i + k] = (byte) (word >>> (8 * k));
                }
            }
        }
    }

    private static byte calculateRomSizeByte(int sizeMbit) {
        int sizeKb = sizeMbit * 1024 / 8;
        return (byte) (Math.log(sizeKb) / Math.log(2));
    }

    private static byte calculateSramSizeByte(int sramSizeKb) {
        if (sramSizeKb == 0) {
            return 0x00;
        }
        return (byte) (Math.log(sramSizeKb) / Math.log(2));
    }

    private static void writeString(byte[] data, int offset, String s, int maxLen) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, data, offset, len);
        for (int i = len; i < maxLen; i++) {
            data[offset + i] = (byte) ' ';
        }
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF;
    }

    /**
     * Generates the full test suite of synthetic ROMs.
     * <p>
     * Creates 6 edge case ROMs covering:
     * - 8 Mbit (1 chunk for GD3)
     * - 16 Mbit (2 chunks for GD3)
     * - 12 Mbit (padding test)
     * - Various SRAM sizes (0KB, 2KB, 8KB, 64KB, 256KB)
     * - DSP chipset variations
     * - Both LoROM and HiROM mappers
     *
     * @param outputDir directory to write ROM files (typically src/test/resources/snes/synthetic)
     * @throws IOException if file writing fails
     */
    public static void generateTestSuite(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        generateLoRom(8, SRAM_0KB, DspChipset.ABSENT, outputDir);
        generateHiRom(8, SRAM_64KB, DspChipset.ABSENT, outputDir);
        generateLoRom(16, SRAM_8KB, DspChipset.ABSENT, outputDir);
        generateHiRom(16, SRAM_256KB, DspChipset.ABSENT, outputDir);
        generateLoRom(12, SRAM_2KB, DspChipset.PRESENT, outputDir);
        generateHiRom(12, SRAM_0KB, DspChipset.PRESENT, outputDir);
        generateExHiRom(48, SRAM_0KB, DspChipset.ABSENT, "SYNEX48T0", outputDir);
    }

    /**
     * Main method for standalone generation of test ROMs.
     */
    public static void main(String[] args) throws IOException {
        Path outputDir = Path.of("src/test/resources/snes/synthetic");
        generateTestSuite(outputDir);
        System.out.println("Generated synthetic test ROMs in: " + outputDir.toAbsolutePath());
    }

    public enum DspChipset {
        PRESENT, ABSENT
    }
}
