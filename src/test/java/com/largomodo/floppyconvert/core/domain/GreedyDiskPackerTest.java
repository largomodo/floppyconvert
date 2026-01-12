package com.largomodo.floppyconvert.core.domain;

import com.largomodo.floppyconvert.core.FloppyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GreedyDiskPackerTest {

    private GreedyDiskPacker packer;

    @BeforeEach
    void setUp() {
        packer = new GreedyDiskPacker();
    }

    @Test
    void testEmptyInput() {
        List<RomPartMetadata> parts = new ArrayList<>();
        List<DiskLayout> result = packer.pack(parts);

        assertTrue(result.isEmpty(), "Empty input should return empty list");
    }

    @Test
    void testSinglePartSmallerThan720k() {
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("test.bin"), 500_000, "TEST.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        assertEquals(1, result.size(), "Should create exactly 1 disk");
        assertEquals(FloppyType.FLOPPY_720K, result.get(0).floppyType(),
            "Should select 720K format for part < 720KB");
        assertEquals(1, result.get(0).contents().size(),
            "Disk should contain exactly 1 part");
    }

    @Test
    void testMultiplePartsSpanningThreeDisks() {
        // Create parts that span across 3 disks
        // Disk 1: 700k + 700k = 1.4MB (fits FLOPPY_144M)
        // Disk 2: 800k + 700k = 1.5MB (fits FLOPPY_160M)
        // Disk 3: 600k (fits FLOPPY_720K)
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("part1.bin"), 700_000, "PART1.BIN"), // Disk 1
            new RomPartMetadata(Path.of("part2.bin"), 700_000, "PART2.BIN"), // Disk 1
            new RomPartMetadata(Path.of("part3.bin"), 800_000, "PART3.BIN"), // Disk 2
            new RomPartMetadata(Path.of("part4.bin"), 700_000, "PART4.BIN"), // Disk 2
            new RomPartMetadata(Path.of("part5.bin"), 600_000, "PART5.BIN")  // Disk 3
        );

        List<DiskLayout> result = packer.pack(parts);

        assertEquals(3, result.size(), "Should create exactly 3 disks");

        // Disk 1: 700k + 700k = 1.4MB
        assertEquals(FloppyType.FLOPPY_144M, result.get(0).floppyType(),
            "First disk should be 1.44MB format");
        assertEquals(2, result.get(0).contents().size());
        assertEquals("PART1.BIN", result.get(0).contents().get(0).dosName());
        assertEquals("PART2.BIN", result.get(0).contents().get(1).dosName());

        // Disk 2: 800k + 700k = 1.5MB
        assertEquals(FloppyType.FLOPPY_160M, result.get(1).floppyType(),
            "Second disk should be 1.6MB format");
        assertEquals(2, result.get(1).contents().size());
        assertEquals("PART3.BIN", result.get(1).contents().get(0).dosName());
        assertEquals("PART4.BIN", result.get(1).contents().get(1).dosName());

        // Disk 3: 600k
        assertEquals(FloppyType.FLOPPY_720K, result.get(2).floppyType(),
            "Third disk should be 720K format");
        assertEquals(1, result.get(2).contents().size());
        assertEquals("PART5.BIN", result.get(2).contents().get(0).dosName());
    }

    @Test
    void testSinglePartExceedingMaximumCapacity() {
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("oversized.bin"),
                FloppyType.FLOPPY_160M.getUsableBytes() + 1,
                "OVERSIZ.BIN")
        );

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> packer.pack(parts),
            "Should throw IllegalArgumentException for oversized part"
        );

        assertTrue(exception.getMessage().contains("exceeds maximum floppy capacity"),
            "Exception message should indicate oversized part");
        assertTrue(exception.getMessage().contains("oversized.bin"),
            "Exception message should include filename");
    }

    @Test
    void testPackingNeverExceedsMaxCapacity() {
        // Create parts that exactly fill 1.6MB when combined
        // Tests boundary condition: packer must respect capacity limit
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("part1.bin"), 800_000, "PART1.BIN"),
            new RomPartMetadata(Path.of("part2.bin"), 800_000, "PART2.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        assertFalse(result.isEmpty(), "Should create at least 1 disk");

        for (DiskLayout disk : result) {
            long totalSize = disk.contents().stream()
                .mapToLong(RomPartMetadata::sizeInBytes)
                .sum();

            assertTrue(totalSize <= FloppyType.FLOPPY_160M.getUsableBytes(),
                "Disk should not exceed maximum capacity: " + totalSize + " bytes");
        }
    }

    @Test
    void testNullInputThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> packer.pack(null),
            "Should throw IllegalArgumentException for null input");
    }

    @Test
    void testMultipleSmallParts() {
        // Test packing multiple small parts that fit in single 720K disk
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("small1.bin"), 100_000, "SMALL1.BIN"),
            new RomPartMetadata(Path.of("small2.bin"), 100_000, "SMALL2.BIN"),
            new RomPartMetadata(Path.of("small3.bin"), 100_000, "SMALL3.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        assertEquals(1, result.size(), "Should fit all parts in 1 disk");
        assertEquals(FloppyType.FLOPPY_720K, result.get(0).floppyType(),
            "Should select smallest format (720K)");
        assertEquals(3, result.get(0).contents().size(),
            "Disk should contain all 3 parts");
    }

    @Test
    void testBoundaryCondition720K() {
        // Test part at exact 720K boundary
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("exact720k.bin"),
                FloppyType.FLOPPY_720K.getUsableBytes(),
                "EXACT.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        assertEquals(1, result.size());
        assertEquals(FloppyType.FLOPPY_720K, result.get(0).floppyType(),
            "Should select 720K for exact 720K size");
    }

    @Test
    void testBoundaryCondition144M() {
        // Test part just above 720K, requiring 1.44M
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("just_over_720k.bin"),
                FloppyType.FLOPPY_720K.getUsableBytes() + 1,
                "OVER720.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        assertEquals(1, result.size());
        assertEquals(FloppyType.FLOPPY_144M, result.get(0).floppyType(),
            "Should select 1.44M for part > 720K");
    }

    @Test
    void testGreedyPackingStrategy() {
        // Verify greedy first-fit: parts are packed in order without reordering
        // 900k + 800k = 1.7MB exceeds 1.6MB max, forcing part3 to second disk
        List<RomPartMetadata> parts = List.of(
            new RomPartMetadata(Path.of("part1.bin"), 900_000, "PART1.BIN"),
            new RomPartMetadata(Path.of("part2.bin"), 800_000, "PART2.BIN"),
            new RomPartMetadata(Path.of("part3.bin"), 100_000, "PART3.BIN")
        );

        List<DiskLayout> result = packer.pack(parts);

        // Greedy: part1 + part2 exceeds 1.6MB, so part2 goes to disk 2
        assertEquals(2, result.size(), "Greedy strategy should create 2 disks");
        assertEquals(1, result.get(0).contents().size(), "First disk has 1 part");
        assertEquals(2, result.get(1).contents().size(), "Second disk has 2 parts");

        // Verify order preservation
        assertEquals("PART1.BIN", result.get(0).contents().get(0).dosName());
        assertEquals("PART2.BIN", result.get(1).contents().get(0).dosName());
        assertEquals("PART3.BIN", result.get(1).contents().get(1).dosName());
    }
}
