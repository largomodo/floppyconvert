package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;

/**
 * Factory interface for creating native service implementations.
 * Decouples the CLI entry point from concrete service and SNES classes.
 * <p>
 * Placed in the service package (not core) to preserve the acyclic dependency direction:
 * FloppyConvert -> core -> service -> snes -> format. (ref: DL-001, DL-007)
 */
public interface ConversionServiceFactory {

    /**
     * Creates a configured ROM splitter.
     *
     * @return a ready-to-use {@link RomSplitter} instance
     */
    RomSplitter createRomSplitter();

    /**
     * Creates a configured floppy image writer.
     *
     * @return a ready-to-use {@link FloppyImageWriter} instance
     */
    FloppyImageWriter createFloppyImageWriter();

    /**
     * Creates a configured disk template factory.
     *
     * @return a ready-to-use {@link DiskTemplateFactory} instance
     */
    DiskTemplateFactory createDiskTemplateFactory();
}
