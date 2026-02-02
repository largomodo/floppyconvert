package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SnesRomReaderTest {

    private final SnesRomReader reader = new SnesRomReader();

    @Property
    void loRomDataIsDetectedAsLoRom(@ForAll("loRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(RomType.LoROM, rom.type(), "LoROM data should be detected as LoROM");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void hiRomDataIsDetectedAsHiRom(@ForAll("hiRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(RomType.HiROM, rom.type(), "HiROM data should be detected as HiROM");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void copierHeaderIsStrippedCorrectly(@ForAll("loRomDataWithCopierHeader") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertEquals(data.length - 512, rom.rawData().length,
                    "Copier header should be stripped, leaving original ROM size");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void sramSizeIsCalculatedCorrectly(@ForAll("sramSizeByte") int sramSizeByte) throws Exception {
        byte[] data = createRomWithSramSize((byte) sramSizeByte);
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            int expectedSram = (sramSizeByte == 0) ? 0 : (1024 << sramSizeByte);
            assertEquals(expectedSram, rom.sramSize(),
                    "SRAM size should be calculated as: sramSizeByte == 0 ? 0 : 1KB << sramSizeByte");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void dspDetectionWorksForKnownTypes(@ForAll("dspRomType") int romTypeByte) throws Exception {
        byte[] data = createRomWithRomType((byte) romTypeByte);
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            assertTrue(rom.hasDsp(), "ROM type " + String.format("0x%02X", romTypeByte) + " should be detected as DSP");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Property
    void checksumValidationWorks(@ForAll("loRomData") byte[] data) throws Exception {
        Path tempFile = writeToTempFile(data);
        try {
            SnesRom rom = reader.load(tempFile);
            int sum = rom.checksum() + rom.complement();
            assertEquals(0xFFFF, sum, "Checksum + Complement should equal 0xFFFF");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void chronoTriggerIsDetectedAsHiRom() throws IOException {
        Path romPath = Paths.get("src/test/resources/snes/Chrono Trigger (USA).sfc");
        if (!Files.exists(romPath)) {
            return;
        }

        SnesRom rom = reader.load(romPath);
        assertEquals(RomType.HiROM, rom.type(), "Chrono Trigger should be detected as HiROM");
        assertTrue(rom.sramSize() > 0, "Chrono Trigger should have SRAM");
    }

    @Test
    void superMarioWorldIsDetectedAsLoRom() throws IOException {
        Path romPath = Paths.get("src/test/resources/snes/Super Mario World (USA).sfc");
        if (!Files.exists(romPath)) {
            return;
        }

        SnesRom rom = reader.load(romPath);
        assertEquals(RomType.LoROM, rom.type(), "Super Mario World should be detected as LoROM");
    }

    @Test
    void tooSmallRomThrowsIOException() {
        byte[] tooSmall = new byte[100];
        Path tempFile = null;
        try {
            tempFile = writeToTempFile(tooSmall);
            Path finalTempFile = tempFile;
            assertThrows(IOException.class, () -> reader.load(finalTempFile),
                    "ROM too small to contain valid header should throw IOException");
        } catch (IOException e) {
            fail("Failed to create temp file for test");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Provide
    Arbitrary<byte[]> loRomData() {
        return RomDataGenerator.loRomData();
    }

    @Provide
    Arbitrary<byte[]> hiRomData() {
        return RomDataGenerator.hiRomData();
    }

    @Provide
    Arbitrary<byte[]> loRomDataWithCopierHeader() {
        return RomDataGenerator.withCopierHeader(RomDataGenerator.loRomData());
    }

    @Provide
    Arbitrary<Integer> sramSizeByte() {
        return Arbitraries.integers().between(0, 7);
    }

    @Provide
    Arbitrary<Integer> dspRomType() {
        return Arbitraries.of(0x03, 0x05, 0x13, 0x14, 0x15, 0x1A);
    }

    private Path writeToTempFile(byte[] data) throws IOException {
        Path tempFile = Files.createTempFile("snes-rom-test-", ".sfc");
        Files.write(tempFile, data);
        return tempFile;
    }

    private byte[] createRomWithSramSize(byte sramSize) {
        int headerBase = 0x7FB0;
        int size = headerBase + 0x50;
        byte[] data = new byte[size];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }

        writeString(data, headerBase + 0x10, "TEST ROM            ", 21);
        data[headerBase + 0x25] = 0x20;
        data[headerBase + 0x26] = 0x00;
        data[headerBase + 0x27] = 0x09;
        data[headerBase + 0x28] = sramSize;
        data[headerBase + 0x29] = 0x01;
        data[headerBase + 0x2A] = 0x00;
        data[headerBase + 0x2B] = 0x00;

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        writeWord(data, headerBase + 0x2C, complement);
        writeWord(data, headerBase + 0x2E, checksum);

        data[headerBase + 0x3C] = (byte) 0x00;
        data[headerBase + 0x3D] = (byte) 0x80;

        return data;
    }

    private byte[] createRomWithRomType(byte romType) {
        int headerBase = 0x7FB0;
        int size = headerBase + 0x50;
        byte[] data = new byte[size];

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }

        writeString(data, headerBase + 0x10, "TEST ROM            ", 21);
        data[headerBase + 0x25] = 0x20;
        data[headerBase + 0x26] = romType;
        data[headerBase + 0x27] = 0x09;
        data[headerBase + 0x28] = 0x00;
        data[headerBase + 0x29] = 0x01;
        data[headerBase + 0x2A] = 0x00;
        data[headerBase + 0x2B] = 0x00;

        int checksum = calculateChecksum(data);
        int complement = checksum ^ 0xFFFF;
        writeWord(data, headerBase + 0x2C, complement);
        writeWord(data, headerBase + 0x2E, checksum);

        data[headerBase + 0x3C] = (byte) 0x00;
        data[headerBase + 0x3D] = (byte) 0x80;

        return data;
    }

    private void writeString(byte[] data, int offset, String s, int maxLen) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, data, offset, len);
    }

    private void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF;
    }
}
