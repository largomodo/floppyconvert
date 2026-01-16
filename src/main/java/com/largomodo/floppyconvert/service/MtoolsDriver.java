package com.largomodo.floppyconvert.service;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for mtools mcopy utility (FAT filesystem file injection).
 *
 * @deprecated Use {@link com.largomodo.floppyconvert.service.fat.Fat12ImageWriter} instead.
 *             Fat12ImageWriter provides native Java FAT12 manipulation (no external dependencies).
 *             This class retained as safety net: if undiscovered FAT12 edge cases surface,
 *             rollback requires changing only one line in App.java (FloppyImageWriter instantiation).
 *             Decision rationale: comprehensive unit tests cover all FAT12 operations + E2E tests
 *             validate full pipeline, but git history alone requires code archaeology for rollback.
 *             Zero-cost insurance until production stability proven.
 * <p>
 * Injects files into floppy disk images using mcopy. Validates DOS 8.3 compliance
 * BEFORE execution to fail fast (mcopy error messages are cryptic for invalid names).
 * <p>
 * Batch operation: spawns single mcopy process per disk image with multiple source files,
 * reducing process overhead for multi-part ROMs (typical: 3 parts per disk).
 */
@Deprecated(since = "2.0", forRemoval = true)
public class MtoolsDriver extends ExternalProcessDriver implements FloppyImageWriter {

    private final String mcopyPath;

    public MtoolsDriver(String mcopyPath) {
        this.mcopyPath = mcopyPath;
    }

    @Override
    public void write(File targetImage, List<File> sources, Map<File, String> dosNameMap) throws IOException {
        // Validate inputs
        if (!targetImage.exists()) {
            throw new IllegalArgumentException("Target image does not exist: " + targetImage);
        }

        // Write each file individually to apply DOS name mapping
        // mcopy batch mode does not support per-file target naming
        for (File source : sources) {
            if (!source.exists()) {
                throw new IllegalArgumentException("Source file does not exist: " + source);
            }

            String dosName = dosNameMap.get(source);
            if (dosName == null) {
                throw new IllegalArgumentException("No DOS name mapping for source: " + source);
            }

            // Validate DOS 8.3 format
            if (!dosName.matches("^[A-Z0-9]{1,8}(\\.[A-Z0-9]{1,3})?$")) {
                throw new IllegalArgumentException("Invalid DOS 8.3 name: " + dosName);
            }

            String[] cmd = {
                    mcopyPath,
                    "-i", targetImage.getAbsolutePath(),
                    source.getAbsolutePath(),
                    "::" + dosName
            };

            executeCommand(cmd, DEFAULT_TIMEOUT_MS);
        }
    }

    /**
     * Copy single file into floppy disk image with DOS-compliant name.
     * <p>
     * Backward compatibility wrapper: delegates to batch write() method.
     * Existing code calling copyToImage() continues to work without changes.
     *
     * @param imageFile  Target floppy image (FAT12 formatted, 1.6MB)
     * @param sourceFile Source file to inject
     * @param dosName    Target filename on floppy (must be DOS 8.3: uppercase, alphanumeric, 8.3 format)
     * @throws IllegalArgumentException if dosName violates DOS 8.3 rules
     * @throws IOException              if mcopy fails (disk full, image corrupted, etc.)
     * @deprecated Use {@link #write(File, List, Map)} for batch operations
     */
    @Deprecated
    public void copyToImage(File imageFile, File sourceFile, String dosName) throws IOException {
        Map<File, String> dosNameMap = new HashMap<>();
        dosNameMap.put(sourceFile, dosName);
        write(imageFile, Collections.singletonList(sourceFile), dosNameMap);
    }
}
