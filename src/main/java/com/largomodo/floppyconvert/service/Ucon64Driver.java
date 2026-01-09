package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.core.RomPartComparator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper for ucon64 ROM splitting tool.
 * <p>
 * Executes ucon64 with FIG format flags to split ROM into parts (.1, .2, .3, ...),
 * discovers generated part files via filesystem scan, and returns sorted list.
 * <p>
 * Timeout protection prevents hung processes on corrupted ROMs (60s default).
 */
public class Ucon64Driver extends ExternalProcessDriver {

    private final String ucon64Path;

    public Ucon64Driver(String ucon64Path) {
        this.ucon64Path = ucon64Path;
    }

    /**
     * Split ROM file into parts using ucon64.
     * <p>
     * Strategy: Two-step process to ensure correct format handling:
     * 1. Convert .sfc to target format (--fig/--swc/--ufo/--gd3)
     * 2. Split the converted file into parts
     *
     * @param format  Backup unit format (determines ucon64 flag and output naming)
     * @param romFile Source ROM file (.sfc format expected)
     * @param tempDir Isolated temp directory for split output
     * @return Sorted list of part files (ordered by numeric extension: .1, .2, .3)
     * @throws IOException if ucon64 fails or no parts generated
     */
    public List<File> splitRom(File romFile, Path tempDir, CopierFormat format) throws IOException {
        if (!romFile.exists()) {
            throw new IllegalArgumentException("ROM file does not exist: " + romFile);
        }

        // Copy ROM to temp dir (ucon64 creates part files in same directory as input)
        // Isolation prevents polluting source directory with .1/.2/.3 files
        Path tempRom = tempDir.resolve(romFile.getName());
        Files.copy(romFile.toPath(), tempRom);

        // Step 1: Convert .sfc to target format
        String[] convertCmd = {
                ucon64Path,
                format.getCmdFlag(),
                "--nbak",
                "--ncol",
                tempRom.toString()
        };

        executeCommand(convertCmd, DEFAULT_TIMEOUT_MS, tempDir.toFile());

        // Step 2: Find the converted file (GD3 uses special naming, others use .ext)
        Path convertedFile;
        if (format == CopierFormat.GD3) {
            try (var stream = Files.list(tempDir)) {
                convertedFile = stream
                        .filter(p -> !p.equals(tempRom))
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .findFirst()
                        .orElseThrow(() -> new IOException("Conversion failed: no GD3 file created"));
            }
        } else {
            String baseName = tempRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            String formatExt = format.getFileExtension();
            convertedFile = tempDir.resolve(baseName + "." + formatExt);
            if (!Files.exists(convertedFile)) {
                throw new IOException("Conversion failed: expected file not created: " + convertedFile);
            }
        }

        // Step 3: Split converted file with retry mechanism for large ROMs
        executeSplitWithRetry(convertedFile, tempDir, format);

        // Discover split parts using format-specific filter
        List<File> parts;
        try (var stream = Files.list(tempDir)) {
            parts = stream
                    .map(Path::toFile)
                    .filter(format.getSplitPartFilter())
                    .map(File::getAbsolutePath)
                    .distinct()
                    .map(File::new)
                    .sorted(new RomPartComparator())
                    .collect(Collectors.toList());
        }

        if (parts.isEmpty()) {
            // No split parts found - check if converted file exists (ROM <=4 Mbit, too small to split)
            // In this case, return the converted file itself as the single "part"
            if (Files.exists(convertedFile)) {
                return List.of(convertedFile.toFile());
            }
            throw new IOException("ucon64 produced no split parts (ROM may be corrupted or invalid)");
        }

        return parts;
    }

    /**
     * Execute ROM split with automatic retry for large ROMs.
     * <p>
     * Retry strategy: Linear escalation (4 Mbit → 12 Mbit).
     * Try 4 Mbit split first (optimal for normal ROMs ≤48 Mbit).
     * If ucon64 fails with "maximum number of parts" error,
     * retry with 12 Mbit split (fits ROMs up to 144 Mbit).
     *
     * @param convertedFile Path to converted ROM file
     * @param tempDir       Temp directory for split output
     * @param format        Backup unit format (for identifying split part files during cleanup)
     * @throws IOException if split fails with non-recoverable error
     */
    private void executeSplitWithRetry(Path convertedFile, Path tempDir, CopierFormat format) throws IOException {
        try {
            executeSplit(convertedFile, tempDir, 4);
        } catch (ProcessFailureException e) {
            if (e.getMessage().contains("more than the maximum number")) {
                System.err.println("WARNING: Large ROM detected, retrying with 12Mbit split...");
                // Clean up partial files from failed 4Mbit attempt
                try (var stream = Files.list(tempDir)) {
                    stream.map(Path::toFile)
                          .filter(format.getSplitPartFilter())
                          .forEach(f -> { try { Files.deleteIfExists(f.toPath()); } catch (IOException ignored) {} });
                }
                executeSplit(convertedFile, tempDir, 12);
            } else if (e.getMessage().contains("ROM size is smaller than or equal to 4 Mbit -- will not be split")) {
                // This is okay, do nothing
            } else {
                throw e;
            }
        }
    }

    /**
     * Execute ucon64 split command with specified size.
     *
     * @param convertedFile Path to converted ROM file
     * @param tempDir       Temp directory for split output
     * @param size          Split size in Mbit (4 or 12)
     * @throws IOException if split command fails
     */
    private void executeSplit(Path convertedFile, Path tempDir, int size) throws IOException {
        String[] splitCmd = {
                ucon64Path,
                "--nbak",
                "--ncol",
                "-s",
                "--ssize=" + size,
                convertedFile.toString()
        };

        executeCommand(splitCmd, DEFAULT_TIMEOUT_MS, tempDir.toFile());
    }
}
