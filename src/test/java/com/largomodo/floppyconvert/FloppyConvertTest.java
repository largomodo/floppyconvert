package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for FloppyConvert CLI argument parsing with Picocli.
 * <p>
 * Focus: Positional parameters, smart output defaults, enum parsing, validation.
 * Tests use CommandLine.parseArgs() to populate FloppyConvert fields directly (no reflection).
 */
class FloppyConvertTest {

    @TempDir
    Path tempDir;

    @Test
    void testPositionalParameterFile() throws IOException {
        Path testFile = tempDir.resolve("test.sfc");
        Files.createFile(testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.toAbsolutePath().toString());

        assertEquals(testFile.toAbsolutePath().toString(), floppyConvert.inputPath.getAbsolutePath());
    }

    @Test
    void testPositionalParameterDirectory() throws IOException {
        Path testDir = tempDir.resolve("roms");
        Files.createDirectory(testDir);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testDir.toAbsolutePath().toString());

        assertEquals(testDir.toAbsolutePath().toString(), floppyConvert.inputPath.getAbsolutePath());
    }

    @Test
    void testSmartDefaultOutputForFile() throws IOException {
        Path testFile = tempDir.resolve("test.sfc");
        Files.createFile(testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.toAbsolutePath().toString());

        assertNull(floppyConvert.outputDir, "outputDir should be null until call() computes smart defaults");

        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail during actual execution, but outputDir should be set
        }

        assertEquals(new File(".").getCanonicalPath(), floppyConvert.outputDir.getCanonicalPath(),
                "File input should default outputDir to current directory");
    }

    @Test
    void testSmartDefaultOutputForDirectory() throws IOException {
        Path testDir = tempDir.resolve("roms");
        Files.createDirectory(testDir);

        Path testFile = testDir.resolve("test.sfc");
        Files.createFile(testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testDir.toAbsolutePath().toString());

        assertNull(floppyConvert.outputDir, "outputDir should be null until call() computes smart defaults");

        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail during actual execution, but outputDir should be set
        }

        assertEquals(testDir.resolve("output").toFile().getCanonicalPath(), floppyConvert.outputDir.getCanonicalPath(),
                "Directory input should default outputDir to input/output");
    }

    @Test
    void testExplicitOutputOverridesDefaults() throws IOException {
        Path testFile = tempDir.resolve("test.sfc");
        Files.createFile(testFile);
        Path explicitOutput = tempDir.resolve("custom-output");

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.toAbsolutePath().toString(),
                "-o", explicitOutput.toAbsolutePath().toString());

        assertEquals(explicitOutput.toAbsolutePath().toString(), floppyConvert.outputDir.getAbsolutePath(),
                "Explicit -o flag should override smart defaults");
    }

    @Test
    void testCaseInsensitiveEnumParsing() throws IOException {
        Path testFile = tempDir.resolve("test.sfc");
        Files.createFile(testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.parseArgs(testFile.toAbsolutePath().toString(),
                "--format", "gd3");

        assertEquals(CopierFormat.GD3, floppyConvert.format, "Lowercase 'gd3' should parse to CopierFormat.GD3");
    }

    @Test
    void testNonExistentInputPathThrowsException() {
        File nonExistentFile = tempDir.resolve("nonexistent.sfc").toFile();

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(nonExistentFile.getAbsolutePath());

        ParameterException exception = assertThrows(ParameterException.class, floppyConvert::call,
                "Should throw ParameterException for non-existent input path");
        assertTrue(exception.getMessage().contains("Input path does not exist"),
                "Error message should indicate path does not exist");
    }
}
