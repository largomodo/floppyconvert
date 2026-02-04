package com.largomodo.floppyconvert.snes.generators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

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
    private static final byte MAP_TYPE_LOROM = 0x20;
    private static final byte MAP_TYPE_LOROM_DSP = 0x23;
    private static final byte MAP_TYPE_HIROM = 0x21;
    private static final byte MAP_TYPE_HIROM_DSP = 0x25;

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
        byte mapType = hasDsp ? MAP_TYPE_LOROM_DSP : MAP_TYPE_LOROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_lorom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, LOROM_HEADER_OFFSET,
                "SYNTHETIC LOROM", outputDir.resolve(filename));
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
        byte mapType = hasDsp ? MAP_TYPE_HIROM_DSP : MAP_TYPE_HIROM;
        byte romType = hasDsp ? (byte) 0x03 : (byte) 0x00;
        String filename = String.format("synthetic_hirom_%dmbit_%dkb%s.sfc",
                sizeMbit, sramSizeKb, hasDsp ? "_dsp" : "");
        return generateRom(sizeMbit, sramSizeKb, mapType, romType, HIROM_HEADER_OFFSET,
                "SYNTHETIC HIROM", outputDir.resolve(filename));
    }

    private static Path generateRom(int sizeMbit, int sramSizeKb, byte mapType, byte romType,
                                    int headerOffset, String title, Path outputPath) throws IOException {
        int sizeBytes = sizeMbit * 1024 * 1024 / 8;
        byte[] data = new byte[sizeBytes];
        Arrays.fill(data, (byte) 0xFF);

        writeString(data, headerOffset + 0x10, title, 21);

        data[headerOffset + 0x25] = mapType;
        data[headerOffset + 0x26] = romType;
        data[headerOffset + 0x27] = calculateRomSizeByte(sizeMbit);
        data[headerOffset + 0x28] = calculateSramSizeByte(sramSizeKb);
        data[headerOffset + 0x29] = 0x01;
        data[headerOffset + 0x2A] = 0x01;
        data[headerOffset + 0x2B] = 0x00;

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        writeWord(data, headerOffset + 0x2C, complement);
        writeWord(data, headerOffset + 0x2E, checksum);

        data[headerOffset + 0x3C] = (byte) 0x00;
        data[headerOffset + 0x3D] = (byte) 0x80;

        Files.write(outputPath, data);
        return outputPath;
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
