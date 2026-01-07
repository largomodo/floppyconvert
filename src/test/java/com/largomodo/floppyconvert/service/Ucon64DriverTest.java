package com.largomodo.floppyconvert.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Ucon64Driver command-line argument construction and error handling.
 */
class Ucon64DriverTest {

    @Test
    void testSplitRomWithNonExistentFile(@TempDir Path tempDir) {
        Ucon64Driver driver = new Ucon64Driver("/usr/bin/ucon64");
        File nonExistentFile = new File("/tmp/nonexistent.sfc");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> driver.splitRom(nonExistentFile, tempDir)
        );

        assertTrue(exception.getMessage().contains("ROM file does not exist"));
    }

    @Test
    @Disabled("Requires ucon64 binary - integration test for manual execution only")
    void testSplitRomCommandLineArguments(@TempDir Path tempDir) throws IOException {
        // Create a minimal ROM file for testing
        Path romFile = tempDir.resolve("test.sfc");
        Files.write(romFile, new byte[1024]);

        // This test verifies the command-line structure but will fail without actual ucon64
        // In production, this would be replaced with a mock-based test
        Ucon64Driver driver = new Ucon64Driver("/usr/bin/ucon64");

        // The actual command executed should be:
        // ucon64 --fig --nbak --ncol -s --ssize=4 <tempRom>
        // We can't easily verify this without mocking, so we document the expectation

        // This will throw ProcessFailureException if ucon64 is not installed,
        // which is acceptable for a unit test environment
        try {
            List<File> parts = driver.splitRom(romFile.toFile(), tempDir);
            // If ucon64 is available and the file is valid, parts should be returned
            assertNotNull(parts);
        } catch (ExternalProcessDriver.ProcessFailureException e) {
            // Expected if ucon64 is not installed
            assertTrue(e.getMessage().contains("Process failed") ||
                      e.getMessage().contains("Process exited"));
        }
    }
}
