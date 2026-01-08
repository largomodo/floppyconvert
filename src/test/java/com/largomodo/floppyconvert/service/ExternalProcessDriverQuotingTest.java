package com.largomodo.floppyconvert.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Verifies that ExternalProcessDriver handles paths with spaces correctly.
 */
class ExternalProcessDriverQuotingTest {

    @Test
    void testPathWithSpaces(@TempDir Path tempDir) throws IOException {
        // Create a directory with spaces in the name
        Path dirWithSpaces = tempDir.resolve("test with spaces");
        dirWithSpaces.toFile().mkdirs();

        // Create a test file in that directory
        Path testFile = dirWithSpaces.resolve("test.txt");
        testFile.toFile().createNewFile();

        // Try to list the directory using ls command
        TestDriver driver = new TestDriver();
        String[] cmd = {"/bin/ls", dirWithSpaces.toString()};

        // This should succeed if quoting is handled correctly
        assertDoesNotThrow(() -> driver.runCommand(cmd, 5000),
                "ls command should succeed with path containing spaces");
    }

    private static class TestDriver extends ExternalProcessDriver {
        public int runCommand(String[] cmd, long timeout) throws IOException {
            return executeCommand(cmd, timeout);
        }
    }
}
