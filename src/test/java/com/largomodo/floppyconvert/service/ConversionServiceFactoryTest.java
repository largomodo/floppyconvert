package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the wiring NativeConversionServiceFactory performs: the splitter it hands out interleaves GD3 data.
 */
class ConversionServiceFactoryTest {

    private static final int GD3_HEADER_SIZE = 512;
    private static final int HALF_BANK = 0x8000;

    private final ConversionServiceFactory factory = new NativeConversionServiceFactory();

    @Test
    // A splitter built without the interleaver would still cut two parts, so the bytes are what prove the wiring:
    // GD3 HiROM puts the upper half of each 64 KB bank first.
    void createRomSplitter_wiresGd3InterleavingIntoTheSplitter(@TempDir Path tempDir) throws IOException {
        Path rom = SyntheticRomFactory.generateHiRom(8, SyntheticRomFactory.SRAM_0KB,
                SyntheticRomFactory.DspChipset.ABSENT, tempDir);
        byte[] source = Files.readAllBytes(rom);
        Path out = Files.createDirectories(tempDir.resolve("out"));

        List<File> parts = factory.createRomSplitter().split(rom.toFile(), out, CopierFormat.GD3);

        assertEquals(2, parts.size(), "An 8 Mbit HiROM splits in two 4 Mbit halves");
        File partA = parts.stream().min(Comparator.comparing(File::getName)).orElseThrow();
        byte[] body = Files.readAllBytes(partA.toPath());
        assertArrayEquals(
                Arrays.copyOfRange(source, HALF_BANK, 2 * HALF_BANK),
                Arrays.copyOfRange(body, GD3_HEADER_SIZE, GD3_HEADER_SIZE + HALF_BANK),
                "Part A must start with the upper half of bank 0");
    }
}
