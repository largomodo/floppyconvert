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
 *
 * Executes ucon64 with FIG format flags to split ROM into parts (.1, .2, .3, ...),
 * discovers generated part files via filesystem scan, and returns sorted list.
 *
 * Timeout protection prevents hung processes on corrupted ROMs (60s default).
 */
public class Ucon64Driver extends ExternalProcessDriver {

    private final String ucon64Path;

    public Ucon64Driver(String ucon64Path) {
        this.ucon64Path = ucon64Path;
    }

    /**
     * Split ROM file into parts using ucon64.
     *
     * Strategy: Copy ROM to temp directory, execute format-specific split command,
     * list all output files (excluding source), sort by RomPartComparator.
     *
     * @param format Backup unit format (determines ucon64 flag and output naming)
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

        String[] cmd = {
            ucon64Path,
            format.getCmdFlag(),  // Format-specific flag (--fig, --swc, --ufo, --gd3)
            "--nbak",     // No backup files (prevents .bak clutter in temp dir)
            "--ncol",     // No ANSI color codes (prevents terminal escape pollution in logs)
            "-s",         // Split mode
            "--ssize=4",  // Split size: 4 megabits per part (~500KB, fits 3 per 1.6MB floppy)
            tempRom.toString()
        };

        executeCommand(cmd, DEFAULT_TIMEOUT_MS);

        // List all files except source ROM (ucon64 creates parts in same directory)
        // Why filter-all: Resilient to ucon64 naming changes across formats (no regex maintenance)
        // Relies on temp directory isolation invariant (only source + ucon64 output present)
        String sourceFileName = tempRom.getFileName().toString();

        List<File> parts;
        try (var stream = Files.list(tempDir)) {
            parts = stream
                .map(Path::toFile)
                .filter(f -> !f.getName().equals(sourceFileName))  // Exclude source ROM from parts list
                .map(File::getAbsolutePath)  // Convert to absolute paths for deduplication
                .distinct()  // Deduplicate to prevent mcopy overwrites causing silent data loss
                .map(File::new)  // Convert back to File objects
                .sorted(new RomPartComparator())  // Universal sorting (handles FIG numeric + GD3 alphanumeric)
                .collect(Collectors.toList());
        }

        if (parts.isEmpty()) {
            throw new IOException("ucon64 produced no split parts (ROM too small or corrupted)");
        }

        return parts;
    }
}
