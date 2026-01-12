package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that Ucon64Driver can handle ROM files with spaces in the path.
 */
class Ucon64DriverSpacesTest {

    @Test
    void testSplitRomWithSpacesInPath(@TempDir Path tempDir) throws IOException, URISyntaxException {
        // Check if ucon64 is available
        var ucon64Resource = getClass().getResource("/ucon64");
        assumeTrue(ucon64Resource != null, "ucon64 not found in test resources");

        File ucon64File = new File(ucon64Resource.toURI());
        assumeTrue(ucon64File.canExecute(), "ucon64 not executable");

        // Get Chrono Trigger ROM
        var romResource = getClass().getResource("/snes/Chrono Trigger (USA).sfc");
        assumeTrue(romResource != null, "Chrono Trigger ROM not found");

        File romFile = new File(romResource.toURI());
        assumeTrue(romFile.exists(), "ROM file doesn't exist");

        // Create temp dir with spaces in path
        Path splitDir = tempDir.resolve("split with spaces");
        Files.createDirectories(splitDir);

        // Split the ROM
        Ucon64Driver driver = new Ucon64Driver(ucon64File.getAbsolutePath());
        List<File> parts = driver.split(romFile, splitDir, CopierFormat.FIG);

        // Verify parts were created
        assertFalse(parts.isEmpty(), "Should have created split parts");
        assertTrue(parts.size() >= 4, "Chrono Trigger should create at least 4 parts (4MB ROM)");

        // Verify all parts exist
        for (File part : parts) {
            assertTrue(part.exists(), "Part file should exist: " + part.getName());
            assertTrue(part.length() > 0, "Part file should not be empty: " + part.getName());
        }
    }
}
