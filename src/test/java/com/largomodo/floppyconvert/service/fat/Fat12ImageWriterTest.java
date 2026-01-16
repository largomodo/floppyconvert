package com.largomodo.floppyconvert.service.fat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Fat12ImageWriter.
 * <p>
 * Verifies:
 * <ul>
 *   <li>BPB parsing and offset calculations</li>
 *   <li>FAT12 12-bit packing logic (odd/even cluster handling)</li>
 *   <li>Directory entry formatting (8.3 filenames)</li>
 *   <li>Cluster chain linking and allocation</li>
 *   <li>Error handling for full disk/directory scenarios</li>
 * </ul>
 */
class Fat12ImageWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testFat12HeaderParsingAndBasicWrite() throws IOException {
        Path imagePath = tempDir.resolve("test.img");
        createBlank144Floppy(imagePath);

        Path sourceFile = tempDir.resolve("TEST.TXT");
        byte[] content = "Hello World".getBytes(StandardCharsets.US_ASCII);
        Files.write(sourceFile, content);

        Fat12ImageWriter writer = new Fat12ImageWriter();
        writer.write(imagePath.toFile(), List.of(sourceFile.toFile()), Map.of(sourceFile.toFile(), "TEST.TXT"));

        // Verification
        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Verify Directory Entry
            // Root dir starts at: Reserved(1) + 2*Fat(9) = 19 sectors = 19 * 512 = 0x2600
            int rootDirStart = 19 * 512;

            // First entry
            byte[] name = new byte[11];
            buffer.position(rootDirStart);
            buffer.get(name);
            assertEquals("TEST    TXT", new String(name, StandardCharsets.US_ASCII));

            byte attr = buffer.get(rootDirStart + 11);
            assertEquals(0x20, attr); // Archive

            int startCluster = Short.toUnsignedInt(buffer.getShort(rootDirStart + 26));
            assertEquals(2, startCluster); // First available cluster is 2

            int size = buffer.getInt(rootDirStart + 28);
            assertEquals(content.length, size);

            // Verify Data
            // Data starts at RootDirStart + (224 entries * 32 bytes) = 0x2600 + 0x1C00 = 0x4200
            // Cluster 2 is at offset 0 relative to data start.
            int dataStart = rootDirStart + (224 * 32);
            byte[] readContent = new byte[content.length];
            buffer.position(dataStart);
            buffer.get(readContent);
            assertArrayEquals(content, readContent);

            // Verify FAT
            // FAT1 starts at 512 (0x200). Cluster 2 offset: 2 + (2/2) = 3.
            // 2 is EndOfChain (0xFFF).
            // Cluster 2 (even): low 12 bits of (byte 3, byte 4).
            // Byte 3 = 0xFF, Byte 4 (low nibble) = 0xF.
            int fatOffset = 512;
            int offset = fatOffset + (2 * 3) / 2; // 512 + 3 = 515
            int b0 = Byte.toUnsignedInt(buffer.get(offset));
            int b1 = Byte.toUnsignedInt(buffer.get(offset + 1));
            int val = b0 | ((b1 & 0x0F) << 8);
            assertEquals(0xFFF, val);
        }
    }

    @Test
    void testFat12PackingOddEvenClusters() throws IOException {
        // This tests the tricky 12-bit packing logic.
        // We write two 1-byte files. They should occupy Cluster 2 and Cluster 3.
        // Both should be marked EOF (0xFFF).
        // Cluster 2 (Even): 0xFFF
        // Cluster 3 (Odd):  0xFFF
        // Layout in bytes (3 bytes for 2 entries):
        // Byte 0: Clus 2 Low (0xFF)
        // Byte 1: Clus 2 High (0xF) | Clus 3 Low (0xF) << 4  => 0xFF
        // Byte 2: Clus 3 High (0xFF)
        // Result bytes: FF FF FF

        Path imagePath = tempDir.resolve("packing.img");
        createBlank144Floppy(imagePath);

        Path f1 = tempDir.resolve("F1");
        Files.write(f1, new byte[]{1});
        Path f2 = tempDir.resolve("F2");
        Files.write(f2, new byte[]{2});

        Fat12ImageWriter writer = new Fat12ImageWriter();
        writer.write(imagePath.toFile(), List.of(f1.toFile(), f2.toFile()),
                Map.of(f1.toFile(), "F1", f2.toFile(), "F2"));

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(1024); // Just read header + FAT
            channel.read(buffer);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int fatStart = 512;
            // Cluster 2 index starts at 512 + 3 = 515.
            int base = fatStart + 3;

            byte b0 = buffer.get(base);     // FF
            byte b1 = buffer.get(base + 1); // FF
            byte b2 = buffer.get(base + 2); // FF

            assertEquals((byte) 0xFF, b0);
            assertEquals((byte) 0xFF, b1);
            assertEquals((byte) 0xFF, b2);
        }
    }

    @Test
    void testFatChainLinking() throws IOException {
        // Test a file that spans 2 clusters.
        // Cluster 2 -> 3
        // Cluster 3 -> EOF (0xFFF)

        Path imagePath = tempDir.resolve("chain.img");
        createBlank144Floppy(imagePath);

        // 512 bytes per cluster. Write 513 bytes.
        byte[] content = new byte[513];
        Path f1 = tempDir.resolve("BIG");
        Files.write(f1, content);

        Fat12ImageWriter writer = new Fat12ImageWriter();
        writer.write(imagePath.toFile(), List.of(f1.toFile()), Map.of(f1.toFile(), "BIG"));

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Read FAT entry for Cluster 2
            // Should point to 3 (0x003).
            int fatStart = 512;
            int offset = fatStart + 3; // 2 * 1.5

            int b0 = Byte.toUnsignedInt(buffer.get(offset));
            int b1 = Byte.toUnsignedInt(buffer.get(offset + 1));
            // Even cluster unpacking: Low 8 + Low 4 of next
            int c2Value = b0 | ((b1 & 0x0F) << 8);

            assertEquals(3, c2Value);

            // Read FAT entry for Cluster 3
            // Should be EOF (0xFFF).
            // Odd cluster unpacking: offset is still same base range
            // Cluster 3 starts at 3 * 1.5 = 4.5 -> offset 4.
            // But we read 3 bytes starting at 3.
            // Cluster 2: bytes at 0, 1. (Indices relative to FAT start + 3)
            // Cluster 3: bytes at 1, 2.

            int bOffset1 = Byte.toUnsignedInt(buffer.get(offset + 1)); // Shared byte
            int bOffset2 = Byte.toUnsignedInt(buffer.get(offset + 2));

            // Odd cluster unpacking: High 4 of prev + All 8 of next
            int c3Value = (bOffset1 >> 4) | (bOffset2 << 4);

            assertEquals(0xFFF, c3Value);
        }
    }

    @Test
    void testFatMirroring() throws IOException {
        Path imagePath = tempDir.resolve("mirror.img");
        createBlank144Floppy(imagePath);

        Path f1 = tempDir.resolve("A");
        Files.write(f1, new byte[]{1});
        new Fat12ImageWriter().write(imagePath.toFile(), List.of(f1.toFile()), Map.of(f1.toFile(), "A"));

        try (FileChannel channel = FileChannel.open(imagePath, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(512 * 20); // Enough for header + FATs
            channel.read(buffer);

            int fat1Start = 512;
            int fat2Start = 512 + (9 * 512); // Reserved(1) + SectorsPerFat(9)

            for (int i = 0; i < 512 * 9; i++) {
                assertEquals(buffer.get(fat1Start + i), buffer.get(fat2Start + i), "Mismatch at byte " + i);
            }
        }
    }

    @Test
    void testDirectoryFull() throws IOException {
        Path imagePath = tempDir.resolve("dirfull.img");
        // Create floppy with only 2 root entries
        createFloppy(imagePath, (short) 2);

        File f1 = tempDir.resolve("1").toFile();
        f1.createNewFile();
        File f2 = tempDir.resolve("2").toFile();
        f2.createNewFile();
        File f3 = tempDir.resolve("3").toFile();
        f3.createNewFile();

        Fat12ImageWriter writer = new Fat12ImageWriter();

        List<File> files = List.of(f1, f2, f3);
        Map<File, String> names = Map.of(f1, "1", f2, "2", f3, "3");

        IOException ex = assertThrows(IOException.class, () -> writer.write(imagePath.toFile(), files, names));
        assertTrue(ex.getMessage().contains("Root directory full"));
    }

    @Test
    void testDiskFull() throws IOException {
        Path imagePath = tempDir.resolve("diskfull.img");
        createBlank144Floppy(imagePath);

        // 1.44MB floppy has ~2847 usable clusters (2880 total sectors - 33 reserved/fat/root).
        // 2847 * 512 = 1,457,664 bytes.
        // Try writing 1.5MB
        byte[] bigData = new byte[1500000];
        Path bigFile = tempDir.resolve("BIG");
        Files.write(bigFile, bigData);

        Fat12ImageWriter writer = new Fat12ImageWriter();
        List<File> files = List.of(bigFile.toFile());
        Map<File, String> names = Map.of(bigFile.toFile(), "BIG");

        IOException ex = assertThrows(IOException.class, () -> writer.write(imagePath.toFile(), files, names));
        assertTrue(ex.getMessage().contains("Disk full"));
    }

    private void createBlank144Floppy(Path path) throws IOException {
        createFloppy(path, (short) 224);
    }

    private void createFloppy(Path path, short rootEntries) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1474560); // 1.44MB
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // BPB
        buffer.putShort(11, (short) 512); // Bytes per sector
        buffer.put(13, (byte) 1);         // Sectors per cluster
        buffer.putShort(14, (short) 1);   // Reserved sectors
        buffer.put(16, (byte) 2);         // Number of FATs
        buffer.putShort(17, rootEntries); // Root entries
        buffer.putShort(19, (short) 2880);// Total sectors
        buffer.put(21, (byte) 0xF0);      // Media descriptor
        buffer.putShort(22, (short) 9);   // Sectors per FAT

        // FAT initialization (ID bytes)
        // FAT 1 at 512
        buffer.put(512, (byte) 0xF0);
        buffer.put(513, (byte) 0xFF);
        buffer.put(514, (byte) 0xFF);

        // FAT 2 at 512 + 9*512 = 5120
        buffer.put(5120, (byte) 0xF0);
        buffer.put(5121, (byte) 0xFF);
        buffer.put(5122, (byte) 0xFF);

        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(buffer);
        }
    }
}