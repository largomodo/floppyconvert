package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.service.MtoolsDriver;
import com.largomodo.floppyconvert.service.Ucon64Driver;
import com.largomodo.floppyconvert.util.DosNameUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ROM to floppy disk image conversion pipeline orchestrator.
 * <p>
 * Coordinates multi-step workflow:
 * 1. Split ROM into parts (ucon64)
 * 2. Pack parts onto floppy disks by actual size (not fixed count)
 * 3. For each disk: copy template, inject parts with DOS names, move to output
 * 4. Cleanup temp directory
 * <p>
 * Isolated temp directories prevent filename collisions.
 * Cleanup in finally block ensures no disk space leaks on failure.
 */
public class RomProcessor {

    // 1.6MB floppy = 1,638,400 bytes (80 tracks * 2 heads * 20 sectors * 512 bytes)
    // FAT12 overhead: ~18KB (boot sector + 2 FATs + root directory)
    // Usable data area: ~1,620,000 bytes
    // Using 1,600,000 as safe threshold (allows 3 x 512KB parts = 1,536,000 bytes)
    private static final long DISK_CAPACITY_BYTES = 1_600_000;

    /**
     * Process single ROM file through full conversion pipeline.
     * <p>
     * Strategy: Create isolated temp dir, split ROM, pack parts onto disks
     * by actual file size, inject into floppy images, move to output, cleanup.
     * <p>
     * Multi-disk naming: GameName_1.img, GameName_2.img (explicit numbering for manual sorting).
     * Single-disk naming: GameName.img (no suffix needed).
     *
     * @param format             Backup unit format for ucon64 splitting
     * @param romFile            Source ROM file (.sfc format)
     * @param outputBaseDir      Base output directory (per-ROM subdirectory created inside)
     * @param ucon64             ucon64 driver for ROM splitting
     * @param mtools             mtools driver for floppy injection
     * @throws IOException if any pipeline step fails
     */
    public void processRom(
            File romFile,
            Path outputBaseDir,
            Ucon64Driver ucon64,
            MtoolsDriver mtools,
            CopierFormat format
    ) throws IOException {

        // Isolated temp directory prevents filename collisions across concurrent invocations
        // Simplifies cleanup: delete entire tree rather than tracking individual files
        Path tempDir = Files.createTempDirectory("rom-");

        try {
            System.out.println("Processing: " + romFile.getName());

            // ucon64 splits ROM into sequentially numbered parts (.1, .2, .3, ...)
            List<File> parts = ucon64.splitRom(romFile, tempDir, format);

            if (parts.isEmpty()) {
                throw new IOException("ucon64 produced no split parts for " + romFile.getName());
            }

            // Extract base name by removing extension: "SuperMario.sfc" → "SuperMario"
            // Regex removes everything from last dot onward
            String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
            if (baseName.isEmpty()) {
                throw new IOException("Cannot extract base name from ROM file: " + romFile.getName());
            }
            Path gameOutputDir = outputBaseDir.resolve(baseName);
            Files.createDirectories(gameOutputDir);

            // Pack parts onto disks by actual size (not fixed count)
            // GD3 produces ~1MB parts, others produce ~512KB parts
            int totalDisks = calculateTotalDisks(parts);
            int diskNumber = 1;
            int partIdx = 0;

            while (partIdx < parts.size()) {
                long diskUsed = 0;
                List<File> diskParts = new java.util.ArrayList<>();

                // Fill current disk until capacity reached
                while (partIdx < parts.size()) {
                    File part = parts.get(partIdx);
                    long partSize = part.length();

                    // If disk is empty, always add the part (even if oversized - will fail-fast at mcopy)
                    // Otherwise, check if part fits within remaining capacity
                    if (diskUsed + partSize <= DISK_CAPACITY_BYTES || diskParts.isEmpty()) {
                        diskParts.add(part);
                        diskUsed += partSize;
                        partIdx++;
                    } else {
                        break; // Start new disk
                    }
                }
                String diskName;
                if (totalDisks > 1) {
                    // Multi-disk: explicit numbering (_1, _2) for manual sorting clarity
                    diskName = baseName + "_" + diskNumber + ".img";
                } else {
                    // Single disk: no suffix needed (cleaner naming)
                    diskName = baseName + ".img";
                }

                File targetImage = tempDir.resolve(diskName).toFile();

                // Load bundled FAT12 template from JAR classpath
                // Per-disk stream allocation chosen over single shared stream for thread-safety:
                // - Multiple concurrent processRom calls would require stream synchronization
                // - InputStream is stateful (position cursor) - shared instance = race condition
                // - Performance impact negligible: disk creation is I/O-bound (mcopy dominates)
                // - Simplicity wins: stateless method design, no locking required
                //
                // Resource path /1m6.img is absolute classpath reference (leading slash required)
                // Template bundled at build time via maven-resources-plugin (see pom.xml)
                //
                // Null check placement: verify resource exists BEFORE creating target file
                // Avoids orphaned empty files in temp dir if resource missing
                try (InputStream templateStream = getClass().getResourceAsStream("/1m6.img")) {
                    if (templateStream == null) {
                        throw new IOException("Internal resource /1m6.img not found. " +
                                "Ensure application is built correctly.");
                    }
                    Files.copy(templateStream, targetImage.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }

                // Inject each part into floppy image with DOS-compliant name
                Map<String, File> dosNameMap = new LinkedHashMap<>();
                for (File part : diskParts) {
                    String dosName = DosNameUtil.sanitize(part.getName());
                    if (dosNameMap.containsKey(dosName)) {
                        throw new IOException("DOS name collision on disk " + diskNumber + ": " + dosName +
                                " would overwrite " + dosNameMap.get(dosName).getName() +
                                " with " + part.getName() + " (8.3 truncation conflict)");
                    }
                    dosNameMap.put(dosName, part);
                    mtools.copyToImage(targetImage, part, dosName);
                }

                // Move completed disk image to final output directory
                Path finalPath = gameOutputDir.resolve(diskName);
                Files.move(targetImage.toPath(), finalPath,
                        StandardCopyOption.REPLACE_EXISTING);

                System.out.println("  Created: " + diskName +
                        " (" + diskParts.size() + " parts)");
                diskNumber++;
            }

            System.out.println("Success: " + romFile.getName() +
                    " -> " + (diskNumber - 1) + " disk(s) [" + format.name() + "]");

        } finally {
            // Cleanup temp directory even on failure to prevent disk space leaks
            if (Files.exists(tempDir)) {
                try (var stream = Files.walk(tempDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        }
    }

    /**
     * Calculate total number of disks needed for given parts based on size-based packing.
     */
    private int calculateTotalDisks(List<File> parts) {
        int disks = 0;
        int partIdx = 0;

        while (partIdx < parts.size()) {
            long diskUsed = 0;
            boolean diskHasPart = false;

            while (partIdx < parts.size()) {
                long partSize = parts.get(partIdx).length();
                if (diskUsed + partSize <= DISK_CAPACITY_BYTES || !diskHasPart) {
                    diskUsed += partSize;
                    diskHasPart = true;
                    partIdx++;
                } else {
                    break;
                }
            }
            disks++;
        }
        return disks;
    }
}
