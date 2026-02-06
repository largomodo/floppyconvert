package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "game.fig,FIG",
            "game.swc,SWC",
            "game.ufo,UFO",
            "game.FIG,FIG",
            "game.SWC,SWC",
            "game.UFO,UFO"
    })
    void testAutoDetectFormatFromExtension(String filename, String expectedFormat) throws Exception {
        Path romSource = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "AUTODET", tempDir);
        Path testFile = tempDir.resolve(filename);
        Files.copy(romSource, testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        // Explicitly set output to tempDir to avoid CWD pollution and locking
        cmd.parseArgs(testFile.toAbsolutePath().toString(), "-o", tempDir.toString());
        floppyConvert.call();

        assertEquals(CopierFormat.valueOf(expectedFormat), floppyConvert.getEffectiveFormat());
    }

    @Test
    void testExplicitFormatOverridesAutoDetection() throws Exception {
        Path romSource = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "OVERRIDE", tempDir);
        Path testFile = tempDir.resolve("game.swc");
        Files.copy(romSource, testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        // Explicitly set output to tempDir
        cmd.parseArgs(testFile.toAbsolutePath().toString(), "--format", "GD3", "-o", tempDir.toString());
        floppyConvert.call();

        assertEquals(CopierFormat.GD3, floppyConvert.getEffectiveFormat());
    }

    @Test
    void testFallbackToFigForUnrecognizedExtension() throws Exception {
        Path romSource = SyntheticRomFactory.generateLoRom(4, 0, DspChipset.ABSENT, "FALLBACK", tempDir);
        Path testFile = tempDir.resolve("game.sfc");
        Files.copy(romSource, testFile);

        FloppyConvert floppyConvert = new FloppyConvert();
        CommandLine cmd = new CommandLine(floppyConvert);
        // Explicitly set output to tempDir
        cmd.parseArgs(testFile.toAbsolutePath().toString(), "-o", tempDir.toString());
        floppyConvert.call();

        assertEquals(CopierFormat.FIG, floppyConvert.getEffectiveFormat());
    }
}
