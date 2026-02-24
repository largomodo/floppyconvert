package com.largomodo.floppyconvert.service.fat;

import com.largomodo.floppyconvert.core.FloppyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Fat12FormatFactory.
 * <p>
 * Verifies:
 * <ul>
 *   <li>Boot sector structure and geometry-specific BPB values</li>
 *   <li>Correct file size calculation for each floppy type</li>
 *   <li>FAT initialization with proper media descriptors</li>
 *   <li>Boot signature presence (0x55 0xAA)</li>
 * </ul>
 */
class Fat12FormatFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void test720kGeometry() throws IOException {
        Path imagePath = tempDir.resolve("720k.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        factory.createBlankDisk(FloppyType.FLOPPY_720K, imagePath);

        // Verify file size
        assertEquals(737280, Files.size(imagePath), "720KB disk should be exactly 737,280 bytes");

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // Verify boot signature
            assertEquals((byte) 0x55, buffer.get(0x1FE), "Boot signature byte 1 should be 0x55");
            assertEquals((byte) 0xAA, buffer.get(0x1FF), "Boot signature byte 2 should be 0xAA");

            // Verify media descriptor
            assertEquals((byte) 0xF9, buffer.get(0x15), "720KB media descriptor should be 0xF9");

            // Verify sectors per track
            assertEquals((short) 9, buffer.getShort(0x18), "720KB should have 9 sectors per track");

            // Verify root entries
            assertEquals((short) 112, buffer.getShort(0x11), "720KB should have 112 root entries");

            // Verify sectors per FAT
            assertEquals((short) 3, buffer.getShort(0x16), "720KB should have 3 sectors per FAT");
        }
    }

    @Test
    void test144mGeometry() throws IOException {
        Path imagePath = tempDir.resolve("144m.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        factory.createBlankDisk(FloppyType.FLOPPY_144M, imagePath);

        // Verify file size
        assertEquals(1474560, Files.size(imagePath), "1.44MB disk should be exactly 1,474,560 bytes");

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // Verify boot signature
            assertEquals((byte) 0x55, buffer.get(0x1FE), "Boot signature byte 1 should be 0x55");
            assertEquals((byte) 0xAA, buffer.get(0x1FF), "Boot signature byte 2 should be 0xAA");

            // Verify media descriptor
            assertEquals((byte) 0xF0, buffer.get(0x15), "1.44MB media descriptor should be 0xF0");

            // Verify sectors per track
            assertEquals((short) 18, buffer.getShort(0x18), "1.44MB should have 18 sectors per track");

            // Verify root entries
            assertEquals((short) 224, buffer.getShort(0x11), "1.44MB should have 224 root entries");

            // Verify sectors per FAT
            assertEquals((short) 9, buffer.getShort(0x16), "1.44MB should have 9 sectors per FAT");
        }
    }

    @Test
    void test160mGeometry() throws IOException {
        Path imagePath = tempDir.resolve("160m.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        factory.createBlankDisk(FloppyType.FLOPPY_160M, imagePath);

        // Verify file size
        assertEquals(1638400, Files.size(imagePath), "1.6MB disk should be exactly 1,638,400 bytes");

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // Verify boot signature
            assertEquals((byte) 0x55, buffer.get(0x1FE), "Boot signature byte 1 should be 0x55");
            assertEquals((byte) 0xAA, buffer.get(0x1FF), "Boot signature byte 2 should be 0xAA");

            // Verify media descriptor
            assertEquals((byte) 0xF0, buffer.get(0x15), "1.6MB media descriptor should be 0xF0");

            // Verify sectors per track
            assertEquals((short) 20, buffer.getShort(0x18), "1.6MB should have 20 sectors per track");

            // Verify root entries
            assertEquals((short) 224, buffer.getShort(0x11), "1.6MB should have 224 root entries");

            // Verify FAT size in header matches expected
            assertEquals((short) 10, buffer.getShort(0x16), "1.6MB should have 10 sectors per FAT");
        }
    }

    @Test
    void testFatInitialization() throws IOException {
        Path imagePath = tempDir.resolve("fat-init.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        // Test with 1.44MB disk (but any geometry would work)
        factory.createBlankDisk(FloppyType.FLOPPY_144M, imagePath);

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512 * 20); // Read enough for both FATs
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // Get media descriptor from boot sector
            byte mediaDescriptor = buffer.get(0x15);
            assertEquals((byte) 0xF0, mediaDescriptor, "Media descriptor should be 0xF0 for 1.44MB");

            // FAT 1 starts at offset 512 (reserved sector)
            int fat1Start = 512;
            assertEquals(mediaDescriptor, buffer.get(fat1Start), "FAT1 byte 0 should match media descriptor");
            assertEquals((byte) 0xFF, buffer.get(fat1Start + 1), "FAT1 byte 1 should be 0xFF");
            assertEquals((byte) 0xFF, buffer.get(fat1Start + 2), "FAT1 byte 2 should be 0xFF");

            // FAT 2 starts at offset 512 + (9 sectors * 512 bytes)
            int fat2Start = 512 + (9 * 512);
            assertEquals(mediaDescriptor, buffer.get(fat2Start), "FAT2 byte 0 should match media descriptor");
            assertEquals((byte) 0xFF, buffer.get(fat2Start + 1), "FAT2 byte 1 should be 0xFF");
            assertEquals((byte) 0xFF, buffer.get(fat2Start + 2), "FAT2 byte 2 should be 0xFF");
        }
    }

    @Test
    void testBiosParameterBlockStructure() throws IOException {
        Path imagePath = tempDir.resolve("bpb.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        factory.createBlankDisk(FloppyType.FLOPPY_144M, imagePath);

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            channel.read(buffer);
            buffer.flip();

            // Verify jump code
            assertEquals((byte) 0xEB, buffer.get(0x00), "Jump code byte 0 should be 0xEB");
            assertEquals((byte) 0x3C, buffer.get(0x01), "Jump code byte 1 should be 0x3C");
            assertEquals((byte) 0x90, buffer.get(0x02), "Jump code byte 2 should be 0x90");

            // Verify OEM name
            byte[] oemName = new byte[8];
            buffer.position(0x03);
            buffer.get(oemName);
            assertEquals("MSDOS5.0", new String(oemName, java.nio.charset.StandardCharsets.US_ASCII),
                "OEM name should be 'MSDOS5.0'");

            // Verify bytes per sector
            assertEquals((short) 512, buffer.getShort(0x0B), "Bytes per sector should be 512");

            // Verify sectors per cluster
            assertEquals((byte) 1, buffer.get(0x0D), "Sectors per cluster should be 1");

            // Verify reserved sectors
            assertEquals((short) 1, buffer.getShort(0x0E), "Reserved sectors should be 1");

            // Verify number of FATs
            assertEquals((byte) 2, buffer.get(0x10), "Number of FATs should be 2");

            // Verify total sectors
            assertEquals((short) 2880, buffer.getShort(0x13), "Total sectors should be 2880 for 1.44MB");

            // Verify heads
            assertEquals((short) 2, buffer.getShort(0x1A), "Number of heads should be 2");

            // Verify extended boot signature
            assertEquals((byte) 0x29, buffer.get(0x26), "Extended boot signature should be 0x29");

            // Verify volume label
            byte[] volumeLabel = new byte[11];
            buffer.position(0x2B);
            buffer.get(volumeLabel);
            assertEquals("NO NAME    ", new String(volumeLabel, java.nio.charset.StandardCharsets.US_ASCII),
                "Volume label should be 'NO NAME    '");

            // Verify FS type
            byte[] fsType = new byte[8];
            buffer.position(0x36);
            buffer.get(fsType);
            assertEquals("FAT12   ", new String(fsType, java.nio.charset.StandardCharsets.US_ASCII),
                "FS type should be 'FAT12   '");
        }
    }

    @Test
    void testNullFloppyTypeThrowsException() {
        Path imagePath = tempDir.resolve("null.img");
        Fat12FormatFactory factory = new Fat12FormatFactory();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> factory.createBlankDisk(null, imagePath));
        assertEquals("FloppyType cannot be null", ex.getMessage());
    }

    @Test
    void testNullTargetImageThrowsException() {
        Fat12FormatFactory factory = new Fat12FormatFactory();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> factory.createBlankDisk(FloppyType.FLOPPY_144M, null));
        assertEquals("Target image path cannot be null", ex.getMessage());
    }
}
