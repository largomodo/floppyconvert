package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Verifies that NativeConversionServiceFactory returns correctly-typed, non-null instances.
 * Each call returns a new instance (factory does not cache). (ref: DL-009)
 */
class ConversionServiceFactoryTest {

    private final ConversionServiceFactory factory = new NativeConversionServiceFactory();

    @Test
    void createRomSplitter_returnsNonNullSplitter() {
        RomSplitter splitter = factory.createRomSplitter();
        assertNotNull(splitter);
    }

    @Test
    // The factory hands the interleaver to the splitter, which pads and interleaves GD3 data.
    void createRomSplitter_wiresGd3InterleavingIntoTheSplitter(@TempDir Path tempDir) throws IOException {
        Path rom = SyntheticRomFactory.generateHiRom(8, SyntheticRomFactory.SRAM_0KB,
                SyntheticRomFactory.DspChipset.ABSENT, tempDir);

        List<File> parts = factory.createRomSplitter().split(rom.toFile(), tempDir, CopierFormat.GD3);

        assertEquals(2, parts.size(), "An 8 Mbit HiROM splits in two 4 Mbit halves");
    }

    @Test
    void createFloppyImageWriter_returnsNonNullWriter() {
        FloppyImageWriter writer = factory.createFloppyImageWriter();
        assertNotNull(writer);
    }

    @Test
    void createDiskTemplateFactory_returnsNonNullFactory() {
        DiskTemplateFactory templateFactory = factory.createDiskTemplateFactory();
        assertNotNull(templateFactory);
    }

    @Test
    void createRomSplitter_returnsNewInstanceEachCall() {
        RomSplitter first = factory.createRomSplitter();
        RomSplitter second = factory.createRomSplitter();
        assertNotSame(first, second);
    }

    @Test
    void createFloppyImageWriter_returnsNewInstanceEachCall() {
        FloppyImageWriter first = factory.createFloppyImageWriter();
        FloppyImageWriter second = factory.createFloppyImageWriter();
        assertNotSame(first, second);
    }
}
