package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

import java.io.File;
import java.io.IOException;
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
    void testPositionalParameterFile() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.getAbsolutePath());

        assertEquals(testFile.getAbsolutePath(), floppyConvert.inputPath.getAbsolutePath());
    }

    @Test
    void testPositionalParameterDirectory() throws Exception {
        File testDir = tempDir.resolve("roms").toFile();
        testDir.mkdir();

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testDir.getAbsolutePath());

        assertEquals(testDir.getAbsolutePath(), floppyConvert.inputPath.getAbsolutePath());
    }

    @Test
    void testSmartDefaultOutputForFile() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        File mockUcon64 = tempDir.resolve("ucon64").toFile();
        mockUcon64.createNewFile();
        mockUcon64.setExecutable(true);

        // Fat12ImageWriter handles image operations natively; ucon64 splits large ROMs into floppy-sized parts

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.getAbsolutePath(),
                "--ucon64-path", mockUcon64.getAbsolutePath());

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
    void testSmartDefaultOutputForDirectory() throws Exception {
        File testDir = tempDir.resolve("roms").toFile();
        testDir.mkdir();

        File testFile = new File(testDir, "test.sfc");
        testFile.createNewFile();

        File mockUcon64 = tempDir.resolve("ucon64").toFile();
        mockUcon64.createNewFile();
        mockUcon64.setExecutable(true);

        // Fat12ImageWriter handles image operations natively; ucon64 splits large ROMs into floppy-sized parts

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testDir.getAbsolutePath(),
                "--ucon64-path", mockUcon64.getAbsolutePath());

        assertNull(floppyConvert.outputDir, "outputDir should be null until call() computes smart defaults");

        try {
            floppyConvert.call();
        } catch (Exception e) {
            // Expected to fail during actual execution, but outputDir should be set
        }

        assertEquals(new File(testDir, "output").getCanonicalPath(), floppyConvert.outputDir.getCanonicalPath(),
                "Directory input should default outputDir to input/output");
    }

    @Test
    void testExplicitOutputOverridesDefaults() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();
        File explicitOutput = tempDir.resolve("custom-output").toFile();

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.parseArgs(testFile.getAbsolutePath(),
                "-o", explicitOutput.getAbsolutePath());

        assertEquals(explicitOutput.getAbsolutePath(), floppyConvert.outputDir.getAbsolutePath(),
                "Explicit -o flag should override smart defaults");
    }

    @Test
    void testCaseInsensitiveEnumParsing() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.parseArgs(testFile.getAbsolutePath(),
                "--format", "gd3");

        assertEquals(CopierFormat.GD3, floppyConvert.format, "Lowercase 'gd3' should parse to CopierFormat.GD3");
    }

    @Test
    void testNonExistentInputPathThrowsException() throws IOException {
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
