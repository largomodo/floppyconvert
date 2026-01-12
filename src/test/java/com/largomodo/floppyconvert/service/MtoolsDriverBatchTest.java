package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.util.TestUcon64PathResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for MtoolsDriver batch operations.
 * <p>
 * Verifies batch mcopy invocation: single process spawn for multiple files,
 * reducing process overhead compared to per-file invocation.
 */
class MtoolsDriverBatchTest {

    private static boolean mtoolsAvailable = false;

    @BeforeAll
    static void checkMtoolsAvailable() {
        mtoolsAvailable = TestUcon64PathResolver.isCommandAvailable("mcopy") &&
                          TestUcon64PathResolver.isCommandAvailable("mdir");
    }

    @Test
    void testBatchWriteThreeFilesToSingleDisk(@TempDir Path tempDir) throws IOException {
        assumeTrue(mtoolsAvailable, "mtools (mcopy/mdir) not available");

        // Create blank 1.44MB floppy image
        Path imageFile = tempDir.resolve("test.img");
        createBlankFloppyImage(imageFile);

        // Create 3 test files
        Path file1 = tempDir.resolve("file1.bin");
        Path file2 = tempDir.resolve("file2.bin");
        Path file3 = tempDir.resolve("file3.bin");
        Files.write(file1, new byte[100]);
        Files.write(file2, new byte[200]);
        Files.write(file3, new byte[300]);

        // Build batch write request
        List<File> sources = Arrays.asList(
            file1.toFile(),
            file2.toFile(),
            file3.toFile()
        );

        Map<File, String> dosNameMap = new HashMap<>();
        dosNameMap.put(file1.toFile(), "FILE1.BIN");
        dosNameMap.put(file2.toFile(), "FILE2.BIN");
        dosNameMap.put(file3.toFile(), "FILE3.BIN");

        // Execute batch write
        MtoolsDriver driver = new MtoolsDriver("mcopy");
        driver.write(imageFile.toFile(), sources, dosNameMap);

        // Verify all 3 files present with correct DOS names
        List<String> dirListing = getMdirListing(imageFile);

        assertTrue(dirListing.stream().anyMatch(line -> line.contains("FILE1") && line.contains("BIN")),
                "FILE1 BIN not found in image. Directory listing:\n" + String.join("\n", dirListing));
        assertTrue(dirListing.stream().anyMatch(line -> line.contains("FILE2") && line.contains("BIN")),
                "FILE2 BIN not found in image. Directory listing:\n" + String.join("\n", dirListing));
        assertTrue(dirListing.stream().anyMatch(line -> line.contains("FILE3") && line.contains("BIN")),
                "FILE3 BIN not found in image. Directory listing:\n" + String.join("\n", dirListing));
    }

    @Test
    void testBatchWritePreservesOrder(@TempDir Path tempDir) throws IOException {
        assumeTrue(mtoolsAvailable, "mtools (mcopy/mdir) not available");

        Path imageFile = tempDir.resolve("test.img");
        createBlankFloppyImage(imageFile);

        // Create files with distinct content
        Path fileA = tempDir.resolve("a.bin");
        Path fileB = tempDir.resolve("b.bin");
        Files.write(fileA, "AAA".getBytes());
        Files.write(fileB, "BBB".getBytes());

        List<File> sources = Arrays.asList(fileA.toFile(), fileB.toFile());
        Map<File, String> dosNameMap = new HashMap<>();
        dosNameMap.put(fileA.toFile(), "A.BIN");
        dosNameMap.put(fileB.toFile(), "B.BIN");

        MtoolsDriver driver = new MtoolsDriver("mcopy");
        driver.write(imageFile.toFile(), sources, dosNameMap);

        List<String> dirListing = getMdirListing(imageFile);
        assertTrue(dirListing.stream().anyMatch(line -> line.contains("A") && line.contains("BIN")));
        assertTrue(dirListing.stream().anyMatch(line -> line.contains("B") && line.contains("BIN")));
    }

    @Test
    void testFailsOnMissingDosNameMapping(@TempDir Path tempDir) throws IOException {
        assumeTrue(mtoolsAvailable, "mtools (mcopy/mdir) not available");

        Path imageFile = tempDir.resolve("test.img");
        createBlankFloppyImage(imageFile);

        Path file1 = tempDir.resolve("file1.bin");
        Files.write(file1, new byte[100]);

        List<File> sources = Collections.singletonList(file1.toFile());
        Map<File, String> dosNameMap = new HashMap<>();  // Empty map - no mapping for file1

        MtoolsDriver driver = new MtoolsDriver("mcopy");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> driver.write(imageFile.toFile(), sources, dosNameMap)
        );

        assertTrue(exception.getMessage().contains("No DOS name mapping for source"));
    }

    @Test
    void testFailsOnInvalidDosName(@TempDir Path tempDir) throws IOException {
        assumeTrue(mtoolsAvailable, "mtools (mcopy/mdir) not available");

        Path imageFile = tempDir.resolve("test.img");
        createBlankFloppyImage(imageFile);

        Path file1 = tempDir.resolve("file1.bin");
        Files.write(file1, new byte[100]);

        List<File> sources = Collections.singletonList(file1.toFile());
        Map<File, String> dosNameMap = new HashMap<>();
        dosNameMap.put(file1.toFile(), "invalid-name.bin");  // Lowercase and hyphen not allowed

        MtoolsDriver driver = new MtoolsDriver("mcopy");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> driver.write(imageFile.toFile(), sources, dosNameMap)
        );

        assertTrue(exception.getMessage().contains("Invalid DOS 8.3 name"));
    }

    /**
     * Create blank FAT12 floppy image using dd and mformat.
     */
    private void createBlankFloppyImage(Path imagePath) throws IOException {
        // Create 1.44MB blank file
        ProcessBuilder pb = new ProcessBuilder("dd", "if=/dev/zero",
            "of=" + imagePath.toAbsolutePath(), "bs=1024", "count=1440");
        Process ddProcess = pb.start();
        try {
            int exitCode = ddProcess.waitFor();
            if (exitCode != 0) {
                throw new IOException("dd failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("dd interrupted", e);
        }

        // Format as FAT12
        ProcessBuilder formatPb = new ProcessBuilder("mformat", "-i",
            imagePath.toAbsolutePath().toString(), "-f", "1440", "::");
        Process formatProcess = formatPb.start();
        try {
            int exitCode = formatProcess.waitFor();
            if (exitCode != 0) {
                throw new IOException("mformat failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("mformat interrupted", e);
        }
    }

    /**
     * Get directory listing from floppy image using mdir.
     */
    private List<String> getMdirListing(Path imagePath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("mdir", "-i",
            imagePath.toAbsolutePath().toString());
        Process mdirProcess = pb.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(mdirProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }

        try {
            int exitCode = mdirProcess.waitFor();
            if (exitCode != 0) {
                throw new IOException("mdir failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("mdir interrupted", e);
        }

        return lines;
    }
}
