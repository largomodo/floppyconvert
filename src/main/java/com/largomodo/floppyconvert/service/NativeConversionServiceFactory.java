package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import com.largomodo.floppyconvert.service.fat.Fat12FormatFactory;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.service.split.SplitStrategyFactory;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;

/**
 * Native Java implementation of {@link ConversionServiceFactory}.
 * Instantiates the standard service stack: NativeRomSplitter, Fat12ImageWriter, Fat12FormatFactory.
 * <p>
 * Centralizes concrete service instantiation for FloppyConvert's composition root. (ref: DL-007)
 */
public class NativeConversionServiceFactory implements ConversionServiceFactory {

    @Override
    public RomSplitter createRomSplitter() {
        SnesRomReader reader = new SnesRomReader();
        SnesInterleaver interleaver = new SnesInterleaver();
        HeaderGeneratorFactory headerFactory = new HeaderGeneratorFactory();
        // The splitter owns GD3 padding and interleaving, so the interleaver goes to it and the strategy
        // factory stays free of snes collaborators.
        SplitStrategyFactory strategyFactory = new SplitStrategyFactory();
        return new NativeRomSplitter(reader, headerFactory, strategyFactory, interleaver);
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
