package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.ConversionFacade;
import com.largomodo.floppyconvert.format.CopierFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Default implementation delegating to ROM splitter and floppy image writer services.
 * <p>
 * Pure delegation without additional logic - service implementations contain business logic.
 */
public class DefaultConversionFacade implements ConversionFacade {

    private final RomSplitter splitter;
    private final FloppyImageWriter writer;

    /**
     * Construct facade with service dependencies.
     */
    public DefaultConversionFacade(RomSplitter splitter, FloppyImageWriter writer) {
        this.splitter = splitter;
        this.writer = writer;
    }

    @Override
    public List<File> splitRom(File inputRom, Path workDir, CopierFormat format) throws IOException {
        return splitter.split(inputRom, workDir, format);
    }

    @Override
    public void write(File targetImage, List<File> sourceParts, Map<File, String> dosNames) throws IOException {
        writer.write(targetImage, sourceParts, dosNames);
    }
}
