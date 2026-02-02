package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.format.CopierFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Facade abstracting ROM conversion services for dependency inversion.
 * <p>
 * Core package depends on abstraction, service package provides implementation.
 * Enables RomProcessor orchestration logic to remain independent of service implementations.
 */
public interface ConversionFacade {

    /**
     * Split a ROM file into backup unit format parts.
     *
     * @param inputRom Source ROM file
     * @param workDir  Target directory for split parts
     * @param format   Backup unit format (FIG/SWC/UFO/GD3)
     * @return Ordered list of split part files
     * @throws IOException if splitting fails
     */
    List<File> splitRom(File inputRom, Path workDir, CopierFormat format) throws IOException;

    /**
     * Write parts to floppy disk image.
     *
     * @param targetImage Target floppy image path
     * @param sourceParts ROM parts to write
     * @param dosNames    DOS 8.3 name mapping for each part
     * @throws IOException if write operation fails
     */
    void write(File targetImage, List<File> sourceParts, Map<File, String> dosNames) throws IOException;
}
