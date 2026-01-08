package com.largomodo.floppyconvert.service;

import java.io.File;
import java.io.IOException;

/**
 * Wrapper for mtools mcopy utility (FAT filesystem file injection).
 * <p>
 * Injects files into floppy disk images using mcopy. Validates DOS 8.3 compliance
 * BEFORE execution to fail fast (mcopy error messages are cryptic for invalid names).
 */
public class MtoolsDriver extends ExternalProcessDriver {

    private final String mcopyPath;

    public MtoolsDriver(String mcopyPath) {
        this.mcopyPath = mcopyPath;
    }

    /**
     * Copy file into floppy disk image with DOS-compliant name.
     * <p>
     * Fail-fast validation: DOS 8.3 pattern checked before mcopy execution.
     * mcopy's native error for invalid names is cryptic ("Cannot initialize drive");
     * early validation provides clear diagnostic.
     *
     * @param imageFile  Target floppy image (FAT12 formatted, 1.6MB)
     * @param sourceFile Source file to inject
     * @param dosName    Target filename on floppy (must be DOS 8.3: uppercase, alphanumeric, 8.3 format)
     * @throws IllegalArgumentException if dosName violates DOS 8.3 rules
     * @throws IOException              if mcopy fails (disk full, image corrupted, etc.)
     */
    public void copyToImage(File imageFile, File sourceFile, String dosName) throws IOException {
        if (!imageFile.exists()) {
            throw new IllegalArgumentException("Image file does not exist: " + imageFile);
        }
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
        }

        // Fail fast on invalid DOS name (mcopy error message is cryptic: "Cannot initialize")
        // Regex: 1-8 uppercase alphanumeric, optional dot + 1-3 uppercase alphanumeric
        if (!dosName.matches("^[A-Z0-9]{1,8}(\\.[A-Z0-9]{1,3})?$")) {
            throw new IllegalArgumentException(
                    "Invalid DOS 8.3 filename: " + dosName + " (must be uppercase alphanumeric, 8.3 format)"
            );
        }

        String[] cmd = {
                mcopyPath,
                "-i", imageFile.getAbsolutePath(),    // Image file to modify
                sourceFile.getAbsolutePath(),         // Source file on host filesystem
                "::" + dosName                        // Target name on floppy (:: = root of image)
        };

        executeCommand(cmd, DEFAULT_TIMEOUT_MS);
    }
}
