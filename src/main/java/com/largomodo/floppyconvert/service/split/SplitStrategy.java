package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Per-format ROM split policy. (ref: DL-004, DL-005)
 * <p>
 * Each implementation encodes one (CopierFormat, isHiRom) combination, mirroring
 * the HeaderGenerator pattern in snes/header. NativeRomSplitter.split() selects a strategy
 * via SplitStrategyFactory and delegates all chunk iteration and data preparation to it.
 * Implementations live in service.split to preserve the service->snes/format dependency
 * direction (DL-005); no snes or format class imports service.split.
 * Filename construction is delegated back to NativeRomSplitter via FilenameProvider
 * to keep naming logic in one place.
 */
public interface SplitStrategy {

    /**
     * @param filenameProvider lambda delegating filename construction to NativeRomSplitter.createFilename
     */
    List<File> split(SnesRom rom, byte[] preparedData, HeaderGenerator headerGen,
                     Path workDir, String baseName, FilenameProvider filenameProvider) throws IOException;
}
