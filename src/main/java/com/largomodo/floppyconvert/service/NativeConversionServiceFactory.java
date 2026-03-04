package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import com.largomodo.floppyconvert.service.fat.Fat12FormatFactory;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;

/**
 * Native Java implementation of {@link ConversionServiceFactory}.
 * Instantiates the standard service stack: NativeRomSplitter, Fat12ImageWriter, Fat12FormatFactory.
 * <p>
 * Centralizes concrete class instantiation; FloppyConvert depends only on the factory interface. (ref: DL-007)
 */
public class NativeConversionServiceFactory implements ConversionServiceFactory {

    @Override
    public RomSplitter createRomSplitter() {
        SnesRomReader reader = new SnesRomReader();
        SnesInterleaver interleaver = new SnesInterleaver();
        HeaderGeneratorFactory headerFactory = new HeaderGeneratorFactory();
        return new NativeRomSplitter(reader, interleaver, headerFactory);
    }

    @Override
    public FloppyImageWriter createFloppyImageWriter() {
        return new Fat12ImageWriter();
    }

    @Override
    public DiskTemplateFactory createDiskTemplateFactory() {
        return new Fat12FormatFactory();
    }
}
