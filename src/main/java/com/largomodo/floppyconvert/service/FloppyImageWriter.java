package com.largomodo.floppyconvert.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service interface for writing files into floppy disk images.
 * <p>
 * Abstracts the image injection process to enable alternative implementations beyond mtools.
 * Primary use cases:
 * <ul>
 *   <li>Production: mtools mcopy for FAT12 image manipulation (current implementation)</li>
 *   <li>Testing: Mock implementations for unit testing without external dependencies</li>
 *   <li>Future: Direct FAT12 implementation (no external tools) or alternative image formats</li>
 * </ul>
 * <p>
 * <b>Contract Guarantees:</b>
 * <ul>
 *   <li>Target image file modified in-place (FAT12 filesystem updated)</li>
 *   <li>Source files remain unmodified (read-only access)</li>
 *   <li>DOS 8.3 naming enforced: implementation must validate dosNameMap values</li>
 *   <li>Atomic operation per file: each source written independently (partial writes allowed on disk full)</li>
 * </ul>
 * <p>
 * <b>Implementation Requirements:</b>
 * <ul>
 *   <li>Fail-fast validation: check DOS 8.3 compliance before attempting writes</li>
 *   <li>Capacity awareness: detect and report disk full errors clearly</li>
 *   <li>Order preservation: write sources in list order (enables sequential part injection)</li>
 *   <li>Name mapping: use dosNameMap for target filenames on image (not source file names)</li>
 * </ul>
 */
public interface FloppyImageWriter {

    /**
     * Write multiple files into a floppy disk image with DOS-compliant names.
     * <p>
     * Each source file is injected into the target image using the corresponding DOS name
     * from the dosNameMap. The map key is the source file, the value is the 8.3 filename
     * to use on the floppy image.
     * <p>
     * <b>DOS 8.3 Naming Rules:</b>
     * <ul>
     *   <li>Format: [1-8 chars].[1-3 chars] (extension optional)</li>
     *   <li>Characters: A-Z, 0-9 only (uppercase, no special characters)</li>
     *   <li>Examples: GAME.1 (valid), STARTREK.DAT (valid), game.bin (invalid - lowercase)</li>
     * </ul>
     * <p>
     * <b>Error Handling:</b> Implementation should throw IOException with clear diagnostic
     * for common failure modes:
     * <ul>
     *   <li>Disk full: specify remaining capacity or which file caused overflow</li>
     *   <li>Invalid DOS name: specify which name violated 8.3 rules</li>
     *   <li>Image corruption: specify nature of FAT12 structure error</li>
     * </ul>
     *
     * @param targetImage Target floppy disk image file (FAT12 formatted, modified in-place)
     * @param sources     Ordered list of source files to inject (written in list order)
     * @param dosNameMap  Mapping from source file to DOS 8.3 target filename on image
     * @throws IOException              if write fails (disk full, image corrupted, I/O error)
     * @throws IllegalArgumentException if targetImage/source does not exist or dosName violates 8.3 rules
     */
    void write(File targetImage, List<File> sources, Map<File, String> dosNameMap) throws IOException;
}
