package com.largomodo.floppyconvert.core;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory for creating blank floppy disk images.
 * <p>
 * Abstraction enables testability (RomProcessor tests can mock without requiring JAR resources)
 * and extensibility (future implementations: network-fetched templates, dynamic generation).
 * Interface pattern follows existing RomSplitter/FloppyImageWriter abstractions.
 */
public interface DiskTemplateFactory {
    /**
     * Create blank floppy disk image at target path.
     *
     * @param type        Floppy format (determines capacity and template source)
     * @param targetImage Destination path for disk image
     * @throws IOException if template cannot be loaded or copied
     */
    void createBlankDisk(FloppyType type, Path targetImage) throws IOException;
}
