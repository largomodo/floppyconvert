package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import com.largomodo.floppyconvert.service.fat.Fat12FormatFactory;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.service.split.SplitStrategyFactory;
import com.largomodo.floppyconvert.snes.Gd3Memory;
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

    // Held here because the ConversionServiceFactory interface lives in core, which knows nothing of copier DRAM.
    private final Gd3Memory gd3Memory;

    public NativeConversionServiceFactory() {
        this(Gd3Memory.DEFAULT);
    }

    /**
     * @param gd3Memory DRAM of the Game Doctor the GD3 output targets. Held here, not in the
     *                  ConversionServiceFactory interface, so core stays free of copier configuration. (ref: DL-014)
     */
    public NativeConversionServiceFactory(Gd3Memory gd3Memory) {
        this.gd3Memory = gd3Memory;
    }

    @Override
    public RomSplitter createRomSplitter() {
        SnesRomReader reader = new SnesRomReader();
        SnesInterleaver interleaver = new SnesInterleaver();
        HeaderGeneratorFactory headerFactory = new HeaderGeneratorFactory();
        // The splitter owns GD3 padding and interleaving, so the interleaver goes to it and the strategy
        // factory stays free of snes collaborators.
        SplitStrategyFactory strategyFactory = new SplitStrategyFactory();
        return new NativeRomSplitter(reader, headerFactory, strategyFactory, interleaver, gd3Memory);
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
