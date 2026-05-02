package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Functional interface for format-specific filename construction. (ref: DL-004)
 * <p>
 * NativeRomSplitter passes a lambda wrapping createFilename() to each SplitStrategy.
 * Keeps filename construction (format extension mapping, split-part filter convention)
 * in one place rather than duplicated across strategy implementations.
 */
@FunctionalInterface
public interface FilenameProvider {

    File provide(Path workDir, String baseName, int partIndex, int totalParts, SnesRom rom) throws IOException;
}
