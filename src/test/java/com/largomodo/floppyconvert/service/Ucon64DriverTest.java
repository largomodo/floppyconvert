package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;
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
        Ucon64Driver driver = new Ucon64Driver("/usr/local/bin/ucon64");
        File nonExistentFile = new File("/tmp/nonexistent.sfc");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> driver.splitRom(nonExistentFile, tempDir, CopierFormat.FIG)
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
        Ucon64Driver driver = new Ucon64Driver("/usr/local/bin/ucon64");

        // The actual command executed should be:
        // ucon64 --fig --nbak --ncol -s --ssize=4 <tempRom>
        // We can't easily verify this without mocking, so we document the expectation

        // This will throw ProcessFailureException if ucon64 is not installed,
        // which is acceptable for a unit test environment
        try {
            List<File> parts = driver.splitRom(romFile.toFile(), tempDir, CopierFormat.FIG);
            // If ucon64 is available and the file is valid, parts should be returned
            assertNotNull(parts);
        } catch (ExternalProcessDriver.ProcessFailureException e) {
            // Expected if ucon64 is not installed
            assertTrue(e.getMessage().contains("Process failed") ||
                    e.getMessage().contains("Process exited"));
        }
    }

    @Test
    void testSourceExclusionFromParts(@TempDir Path tempDir) throws IOException {
        Path romFile = tempDir.resolve("game.sfc");
        Files.write(romFile, new byte[1024]);

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        Ucon64Driver driver = new Ucon64Driver("/mock/ucon64") {
            @Override
            protected int executeCommand(String[] cmd, long timeoutMs, File workingDir) throws IOException {
                // Mock: two-step process - first convert, then split
                // Step 1: Conversion creates game.fig from game.sfc
                // Step 2: Split creates game.1, game.2 from game.fig
                String lastArg = cmd[cmd.length - 1];
                if (lastArg.endsWith("game.sfc")) {
                    // Conversion step - create .fig file
                    Files.write(workingDir.toPath().resolve("game.fig"), new byte[512]);
                } else if (lastArg.endsWith("game.fig")) {
                    // Split step - create part files
                    Files.write(workingDir.toPath().resolve("game.1"), new byte[512]);
                    Files.write(workingDir.toPath().resolve("game.2"), new byte[512]);
                }
                return 0;
            }
        };

        List<File> parts = driver.splitRom(romFile.toFile(), workDir, CopierFormat.FIG);

        assertEquals(2, parts.size());
        assertTrue(parts.stream().noneMatch(f -> f.getName().equals("game.sfc")));
        assertTrue(parts.stream().anyMatch(f -> f.getName().equals("game.1")));
        assertTrue(parts.stream().anyMatch(f -> f.getName().equals("game.2")));
    }

    @Test
    void testAlphanumericSorting(@TempDir Path tempDir) throws IOException {
        Path romFile = tempDir.resolve("game.sfc");
        Files.write(romFile, new byte[1024]);

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        Ucon64Driver driver = new Ucon64Driver("/mock/ucon64") {
            @Override
            protected int executeCommand(String[] cmd, long timeoutMs, File workingDir) throws IOException {
                // Mock: two-step process for GD3 format
                String lastArg = cmd[cmd.length - 1];
                if (lastArg.endsWith("game.sfc")) {
                    // Conversion step - GD3 creates sf<hash> file (without extension)
                    Files.write(workingDir.toPath().resolve("sf32gam"), new byte[512]);
                } else {
                    // Split step - create GD3 part files with alphanumeric suffixes
                    Files.write(workingDir.toPath().resolve("SF32CHRB.078"), new byte[512]);
                    Files.write(workingDir.toPath().resolve("SF32CHRA.078"), new byte[512]);
                }
                return 0;
            }
        };

        List<File> parts = driver.splitRom(romFile.toFile(), workDir, CopierFormat.GD3);

        assertEquals(2, parts.size());
        assertEquals("SF32CHRA.078", parts.get(0).getName());
        assertEquals("SF32CHRB.078", parts.get(1).getName());
    }

    @Test
    void testRetrySplitOnMaxPartsError(@TempDir Path tempDir) throws IOException {
        Path romFile = tempDir.resolve("largegame.sfc");
        Files.write(romFile, new byte[1024]);

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        Ucon64Driver driver = new Ucon64Driver("/mock/ucon64") {
            private int splitCallCount = 0;

            @Override
            protected int executeCommand(String[] cmd, long timeoutMs, File workingDir) throws IOException {
                String lastArg = cmd[cmd.length - 1];

                if (lastArg.endsWith("largegame.sfc")) {
                    // Step 1: Conversion creates largegame.fig
                    Files.write(workingDir.toPath().resolve("largegame.fig"), new byte[512]);
                    return 0;
                } else if (lastArg.endsWith("largegame.fig")) {
                    splitCallCount++;
                    
                    if (splitCallCount == 1) {
                        // Step 2: First split attempt with --ssize=4 fails with max parts error
                        // Verify 4Mb split size is present
                        boolean hasSsize4 = false;
                        for (String arg : cmd) {
                            if (arg.equals("--ssize=4")) {
                                hasSsize4 = true;
                                break;
                            }
                        }
                        if (!hasSsize4) {
                            throw new IOException("Expected --ssize=4 in first split attempt");
                        }
                        
                        throw new ExternalProcessDriver.ProcessFailureException(
                            "Process exited with code 1: ucon64: error: maximum number of parts reached"
                        );
                    } else if (splitCallCount == 2) {
                        // Step 3: Retry with --ssize=12 succeeds
                        // Verify 12Mb split size is present
                        boolean hasSsize12 = false;
                        for (String arg : cmd) {
                            if (arg.equals("--ssize=12")) {
                                hasSsize12 = true;
                                break;
                            }
                        }
                        if (!hasSsize12) {
                            throw new IOException("Expected --ssize=12 in retry split attempt");
                        }
                        
                        // Create mock split part files for discovery
                        Files.write(workingDir.toPath().resolve("largegame.1"), new byte[512]);
                        Files.write(workingDir.toPath().resolve("largegame.2"), new byte[512]);
                        return 0;
                    }
                }
                return 0;
            }
        };

        List<File> parts = driver.splitRom(romFile.toFile(), workDir, CopierFormat.FIG);

        assertEquals(2, parts.size());
        assertEquals("largegame.1", parts.get(0).getName());
        assertEquals("largegame.2", parts.get(1).getName());
    }

    @Test
    void testSplitFailsOnGenericError(@TempDir Path tempDir) throws IOException {
        Path romFile = tempDir.resolve("game.sfc");
        Files.write(romFile, new byte[1024]);

        Path workDir = tempDir.resolve("work");
        Files.createDirectories(workDir);

        Ucon64Driver driver = new Ucon64Driver("/mock/ucon64") {
            @Override
            protected int executeCommand(String[] cmd, long timeoutMs, File workingDir) throws IOException {
                String lastArg = cmd[cmd.length - 1];

                if (lastArg.endsWith("game.sfc")) {
                    // Conversion creates game.fig
                    Files.write(workingDir.toPath().resolve("game.fig"), new byte[512]);
                    return 0;
                } else if (lastArg.endsWith("game.fig")) {
                    // Split fails with generic error (e.g., disk full)
                    throw new ExternalProcessDriver.ProcessFailureException(
                        "Process exited with code 1: ucon64: error: disk full"
                    );
                }
                return 0;
            }
        };

        ExternalProcessDriver.ProcessFailureException exception = assertThrows(
                ExternalProcessDriver.ProcessFailureException.class,
                () -> driver.splitRom(romFile.toFile(), workDir, CopierFormat.FIG)
        );

        assertTrue(exception.getMessage().contains("disk full"));
    }
}
