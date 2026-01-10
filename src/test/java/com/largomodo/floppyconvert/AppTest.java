package com.largomodo.floppyconvert;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;
import com.largomodo.floppyconvert.core.CopierFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for App CLI argument parsing with Picocli.
 * <p>
 * Focus: Positional parameters, smart output defaults, enum parsing, validation.
 * Tests use CommandLine.parseArgs() to populate App fields directly (no reflection).
 */
class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void testPositionalParameterFile() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(testFile.getAbsolutePath());

        assertEquals(testFile.getAbsolutePath(), app.inputPath.getAbsolutePath());
    }

    @Test
    void testPositionalParameterDirectory() throws Exception {
        File testDir = tempDir.resolve("roms").toFile();
        testDir.mkdir();

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(testDir.getAbsolutePath());

        assertEquals(testDir.getAbsolutePath(), app.inputPath.getAbsolutePath());
    }

    @Test
    void testSmartDefaultOutputForFile() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        File mockUcon64 = tempDir.resolve("ucon64").toFile();
        mockUcon64.createNewFile();
        mockUcon64.setExecutable(true);

        File mockMtools = tempDir.resolve("mcopy").toFile();
        mockMtools.createNewFile();
        mockMtools.setExecutable(true);

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(testFile.getAbsolutePath(),
                      "--ucon64-path", mockUcon64.getAbsolutePath(),
                      "--mtools-path", mockMtools.getAbsolutePath());

        assertNull(app.outputDir, "outputDir should be null until call() computes smart defaults");

        try {
            app.call();
        } catch (Exception e) {
            // Expected to fail during actual execution, but outputDir should be set
        }

        assertEquals(new File(".").getCanonicalPath(), app.outputDir.getCanonicalPath(),
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

        File mockMtools = tempDir.resolve("mcopy").toFile();
        mockMtools.createNewFile();
        mockMtools.setExecutable(true);

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(testDir.getAbsolutePath(),
                      "--ucon64-path", mockUcon64.getAbsolutePath(),
                      "--mtools-path", mockMtools.getAbsolutePath());

        assertNull(app.outputDir, "outputDir should be null until call() computes smart defaults");

        try {
            app.call();
        } catch (Exception e) {
            // Expected to fail during actual execution, but outputDir should be set
        }

        assertEquals(new File(testDir, "output").getCanonicalPath(), app.outputDir.getCanonicalPath(),
                     "Directory input should default outputDir to input/output");
    }

    @Test
    void testExplicitOutputOverridesDefaults() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();
        File explicitOutput = tempDir.resolve("custom-output").toFile();

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(testFile.getAbsolutePath(),
                      "-o", explicitOutput.getAbsolutePath());

        assertEquals(explicitOutput.getAbsolutePath(), app.outputDir.getAbsolutePath(),
                     "Explicit -o flag should override smart defaults");
    }

    @Test
    void testCaseInsensitiveEnumParsing() throws Exception {
        File testFile = tempDir.resolve("test.sfc").toFile();
        testFile.createNewFile();

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.parseArgs(testFile.getAbsolutePath(),
                      "--format", "gd3");

        assertEquals(CopierFormat.GD3, app.format, "Lowercase 'gd3' should parse to CopierFormat.GD3");
    }

    @Test
    void testNonExistentInputPathThrowsException() throws IOException {
        File nonExistentFile = tempDir.resolve("nonexistent.sfc").toFile();

        App app = new App();
        CommandLine cmd = new CommandLine(app);
        cmd.parseArgs(nonExistentFile.getAbsolutePath());

        ParameterException exception = assertThrows(ParameterException.class, app::call,
                "Should throw ParameterException for non-existent input path");
        assertTrue(exception.getMessage().contains("Input path does not exist"),
                   "Error message should indicate path does not exist");
    }
}
