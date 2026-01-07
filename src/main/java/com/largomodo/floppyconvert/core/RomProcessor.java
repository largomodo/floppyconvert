package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.service.Ucon64Driver;
import com.largomodo.floppyconvert.service.MtoolsDriver;
import com.largomodo.floppyconvert.util.DosNameUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Comparator;

/**
 * ROM to floppy disk image conversion pipeline orchestrator.
 *
 * Coordinates multi-step workflow:
 *   1. Split ROM into parts (ucon64)
 *   2. Calculate disk count (3 parts per 1.6MB disk)
 *   3. For each disk: copy template, inject parts with DOS names, move to output
 *   4. Cleanup temp directory
 *
 * Isolated temp directories prevent filename collisions.
 * Cleanup in finally block ensures no disk space leaks on failure.
 */
public class RomProcessor {

    // Conservative limit: 1.6MB floppy / 500KB avg part = 3.2 theoretical max
    // Using 3 provides safety margin for FAT12 filesystem overhead
    // Prevents mcopy "disk full" errors that would require complex retry logic
    private static final int PARTS_PER_DISK = 3;

    /**
     * Process single ROM file through full conversion pipeline.
     *
     * Strategy: Create isolated temp dir, split ROM, partition parts across disks
     * (3 per disk), inject into floppy images, move to output, cleanup.
     *
     * Multi-disk naming: GameName_1.img, GameName_2.img (explicit numbering for manual sorting).
     * Single-disk naming: GameName.img (no suffix needed).
     *
     * @param romFile Source ROM file (.sfc format)
     * @param outputBaseDir Base output directory (per-ROM subdirectory created inside)
     * @param emptyImageTemplate Pre-formatted FAT12 floppy template (1.6MB)
     * @param ucon64 ucon64 driver for ROM splitting
     * @param mtools mtools driver for floppy injection
     * @throws IOException if any pipeline step fails
     */
    public void processRom(
        File romFile,
        Path outputBaseDir,
        File emptyImageTemplate,
        Ucon64Driver ucon64,
        MtoolsDriver mtools
    ) throws IOException {

        // Isolated temp directory prevents filename collisions across concurrent invocations
        // Simplifies cleanup: delete entire tree rather than tracking individual files
        Path tempDir = Files.createTempDirectory("rom-");

        try {
            System.out.println("Processing: " + romFile.getName());

            // ucon64 splits ROM into sequentially numbered parts (.1, .2, .3, ...)
            List<File> parts = ucon64.splitRom(romFile, tempDir);

            if (parts.isEmpty()) {
                throw new IOException("ucon64 produced no split parts for " + romFile.getName());
            }

            // Calculate floppy disk count: 3 parts per 1.6MB disk (conservative capacity planning)
            // ceil(7 parts / 3) = 3 disks
            int totalDisks = (int) Math.ceil((double) parts.size() / PARTS_PER_DISK);

            // Extract base name by removing extension: "SuperMario.sfc" → "SuperMario"
            // Regex removes everything from last dot onward
            String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
            if (baseName.isEmpty()) {
                throw new IOException("Cannot extract base name from ROM file: " + romFile.getName());
            }
            Path gameOutputDir = outputBaseDir.resolve(baseName);
            Files.createDirectories(gameOutputDir);

            // Process each floppy disk sequentially (I/O-bound, parallelism would cause contention)
            for (int diskIdx = 0; diskIdx < totalDisks; diskIdx++) {
                String diskName;
                if (totalDisks > 1) {
                    // Multi-disk: explicit numbering (_1, _2) for manual sorting clarity
                    // Starts at 1 (not 0) to match user mental model (disk 1, disk 2)
                    diskName = baseName + "_" + (diskIdx + 1) + ".img";
                } else {
                    // Single disk: no suffix needed (cleaner naming)
                    diskName = baseName + ".img";
                }

                File targetImage = tempDir.resolve(diskName).toFile();

                // Copy empty FAT12 template as base for this disk
                // Template must be pre-formatted (tool does not format filesystems)
                Files.copy(emptyImageTemplate.toPath(), targetImage.toPath(),
                          StandardCopyOption.REPLACE_EXISTING);

                // Partition parts across disks: disk 0 gets parts [0,3), disk 1 gets [3,6), etc.
                int startIdx = diskIdx * PARTS_PER_DISK;
                int endIdx = Math.min(startIdx + PARTS_PER_DISK, parts.size());
                List<File> diskParts = parts.subList(startIdx, endIdx);

                // Inject each part into floppy image with DOS-compliant name
                for (File part : diskParts) {
                    String dosName = DosNameUtil.sanitize(part.getName());
                    mtools.copyToImage(targetImage, part, dosName);
                }

                // Move completed disk image to final output directory
                // Ensures partial images don't pollute output on failure (atomic move)
                Path finalPath = gameOutputDir.resolve(diskName);
                Files.move(targetImage.toPath(), finalPath,
                          StandardCopyOption.REPLACE_EXISTING);

                System.out.println("  Created: " + diskName +
                                 " (" + diskParts.size() + " parts)");
            }

            System.out.println("Success: " + romFile.getName() +
                             " -> " + totalDisks + " disk(s)");

        } finally {
            // Cleanup temp directory even on failure to prevent disk space leaks
            // Reverse order traversal deletes files before parent directories
            if (Files.exists(tempDir)) {
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        }
    }
}
