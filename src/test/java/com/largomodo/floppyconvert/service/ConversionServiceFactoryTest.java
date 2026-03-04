package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import org.junit.jupiter.api.Test;

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
