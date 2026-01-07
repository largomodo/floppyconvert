package com.largomodo.floppyconvert.service;

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
     * Strategy: Copy ROM to temp directory (ucon64 modifies source directory),
     * execute split command, scan for .1/.2/.3 files, sort by numeric extension.
     *
     * @param romFile Source ROM file (.sfc format expected)
     * @param tempDir Isolated temp directory for split output
     * @return Sorted list of part files (ordered by numeric extension: .1, .2, .3)
     * @throws IOException if ucon64 fails or no parts generated
     */
    public List<File> splitRom(File romFile, Path tempDir) throws IOException {
        if (!romFile.exists()) {
            throw new IllegalArgumentException("ROM file does not exist: " + romFile);
        }

        // Copy ROM to temp dir (ucon64 creates part files in same directory as input)
        // Isolation prevents polluting source directory with .1/.2/.3 files
        Path tempRom = tempDir.resolve(romFile.getName());
        Files.copy(romFile.toPath(), tempRom);

        String[] cmd = {
            ucon64Path,
            "--fig",      // FIG format (Doctor V64 format for SNES copiers)
            "--nbak",     // No backup files (prevents .bak clutter in temp dir)
            "--ncol",     // No ANSI color codes (prevents terminal escape pollution in logs)
            "-s",         // Split mode
            "--ssize=4",  // Split size: 4 megabits per part (~500KB, fits 3 per 1.6MB floppy)
            tempRom.toString()
        };

        executeCommand(cmd, DEFAULT_TIMEOUT_MS);

        // ucon64 creates .1, .2, .3, etc. files in same directory as input
        // Numeric sorting ensures part order for multi-disk spanning (disk 1 gets .1/.2/.3, disk 2 gets .4/.5/.6)
        // Extract base name for filtering: "game.sfc" → "game"
        String baseName = tempRom.getFileName().toString().replaceFirst("\\.[^.]+$", "");

        List<File> parts;
        try (var stream = Files.list(tempDir)) {
            parts = stream
                .map(Path::toFile)
                .filter(f -> f.getName().matches(baseName + "\\.\\d+$"))  // Match only ROM-specific parts
                .sorted((a, b) -> {
                    int numA = Integer.parseInt(a.getName().replaceAll(".*\\.(\\d+)$", "$1"));
                    int numB = Integer.parseInt(b.getName().replaceAll(".*\\.(\\d+)$", "$1"));
                    return Integer.compare(numA, numB);
                })
                .collect(Collectors.toList());
        }

        if (parts.isEmpty()) {
            throw new IOException("ucon64 produced no split parts (ROM too small or corrupted)");
        }

        return parts;
    }
}
