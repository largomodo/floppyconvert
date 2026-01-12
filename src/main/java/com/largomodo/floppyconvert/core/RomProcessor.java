package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.service.MtoolsDriver;
import com.largomodo.floppyconvert.service.Ucon64Driver;
import com.largomodo.floppyconvert.util.DosNameUtil;
import com.largomodo.floppyconvert.core.FloppyType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

        // Extract base name by removing extension: "SuperMario.sfc" → "SuperMario"
        String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
        if (baseName.isEmpty()) {
            throw new IOException("Cannot extract base name from ROM file: " + romFile.getName());
        }

        // Sanitize baseName for use in disk image filenames and directory name
        String sanitizedBaseName = sanitizeForMcopy(baseName);
        if (sanitizedBaseName.isEmpty()) {
            throw new IOException("Sanitized base name is empty for ROM file: " +
                                 romFile.getName() + " (original: " + baseName + ")");
        }

        // Work-in-place pattern: Create per-game subdirectory in final output location - isolates ROM processing artifacts.
        // Each ROM gets outputBaseDir/RomName/ to prevent name collisions (.1/.2/.3 filenames reused by ucon64).
        Path gameOutputDir = outputBaseDir.resolve(sanitizedBaseName);
        Files.createDirectories(gameOutputDir);

        // Track artifacts for cleanup: split parts and intermediate files
        // Artifact tracking pattern: Surgical cleanup preserves final .img files while removing intermediate artifacts
        // List<Path> tracks exactly what we created for finally block cleanup
        // Each created file (except final .img) must be added before the next operation that could throw
        List<Path> artifactsToCleanup = new ArrayList<>();

        try {
            System.out.println("Processing: " + romFile.getName());

            // Invariant 4: preserve pre-existing user files
            // Snapshot existing files BEFORE ucon64.splitRom for comparison (needed for GD3 tracking)
            Set<Path> existingFiles;
            try (var stream = Files.list(gameOutputDir)) {
                existingFiles = stream.collect(Collectors.toSet());
            }

            // ucon64 splits ROM into sequentially numbered parts (.1, .2, .3, ...)
            List<File> parts = ucon64.splitRom(romFile, gameOutputDir, format);

            if (parts.isEmpty()) {
                throw new IOException("ucon64 produced no split parts for " + romFile.getName());
            }

            // Track intermediate file for cleanup
            // splitRom attempts best-effort deletion, but if it fails (Windows lock, permissions),
            // add to cleanup list so finally block can retry. This ensures intermediate files don't
            // pollute output directory even if initial deletion failed.
            //
            // After ucon64.splitRom, track only NEW files (created by this invocation)
            if (format == CopierFormat.GD3) {
                // GD3 uses unpredictable naming - scan for non-split files created after process start
                try (var stream = Files.list(gameOutputDir)) {
                    stream.filter(p -> !existingFiles.contains(p))  // Only new files (preserves user files)
                          .filter(p -> !p.getFileName().toString().startsWith("."))
                          .filter(p -> !format.getSplitPartFilter().test(p.toFile()))
                          .filter(p -> !p.toString().endsWith(".img"))
                          .forEach(artifactsToCleanup::add);
                }
            } else {
                // Standard formats use predictable extension
                Path intermediateFile = gameOutputDir.resolve(baseName + "." + format.getFileExtension());
                if (Files.exists(intermediateFile)) {
                    artifactsToCleanup.add(intermediateFile);
                }
            }

            // Add parts to cleanup list immediately
            // Ensures finally block can clean up even if subsequent operations fail
            for (File part : parts) {
                artifactsToCleanup.add(part.toPath());
            }

            // Sanitize filenames for mcopy compatibility
            List<File> sanitizedParts = new java.util.ArrayList<>();
            for (File part : parts) {
                String sanitizedName = sanitizeForMcopy(part.getName());
                Path sanitizedPath = gameOutputDir.resolve(sanitizedName);
                Files.move(part.toPath(), sanitizedPath, StandardCopyOption.REPLACE_EXISTING);

                // Artifact list references must stay synchronized with filesystem state
                // Rename changed the path - update tracking to reflect new location
                artifactsToCleanup.remove(part.toPath());
                artifactsToCleanup.add(sanitizedPath);

                sanitizedParts.add(sanitizedPath.toFile());
            }
            parts = sanitizedParts;

            // Pack parts onto disks by actual size (not fixed count)
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
                    if (diskUsed + partSize <= FloppyType.FLOPPY_160M.getUsableBytes() || diskParts.isEmpty()) {
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
                    diskName = sanitizedBaseName + "_" + diskNumber + ".img";
                } else {
                    // Single disk: no suffix needed (cleaner naming)
                    diskName = sanitizedBaseName + ".img";
                }

                File targetImage = gameOutputDir.resolve(diskName).toFile();

                // Select smallest floppy template that fits actual payload
                FloppyType floppyType = FloppyType.bestFit(diskUsed);

                // Load bundled FAT12 template from JAR classpath
                // Per-disk stream allocation (not shared) for thread-safety: InputStream is stateful,
                // shared instance would cause race conditions between concurrent processRom calls.
                // Performance impact negligible - disk creation is I/O-bound (mcopy dominates).
                //
                // Resource path is absolute classpath reference (leading slash required).
                // Template bundled at build time via maven-resources-plugin (see pom.xml).
                //
                // Null check before Files.copy: verify resource exists BEFORE creating target file.
                // Work-in-place pattern: Copy template directly to gameOutputDir (image is already at final destination).
                try (InputStream templateStream = getClass().getResourceAsStream(floppyType.getResourcePath())) {
                    if (templateStream == null) {
                        throw new IOException("Internal resource " + floppyType.getResourcePath() + " not found. " +
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

                System.out.println("  Created: " + diskName +
                        " (" + diskParts.size() + " parts)");
                diskNumber++;
            }

            System.out.println("Success: " + romFile.getName() +
                    " -> " + (diskNumber - 1) + " disk(s) [" + format.name() + "]");

        } finally {
            // Surgical cleanup: delete only tracked artifacts, preserve final .img files
            // Invariant 2: cleanup must execute even on exception
            // Invariant 4: delete only files, never directories
            for (Path artifact : artifactsToCleanup) {
                try {
                    Files.deleteIfExists(artifact);
                } catch (IOException e) {
                    // Risk mitigation: mcopy may briefly lock files after process exit - best-effort cleanup
                    // Locked files eventually deleted when lock released (non-critical)
                    System.err.println("Warning: Could not delete artifact " + artifact +
                                     ": " + e.getMessage());
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
                if (diskUsed + partSize <= FloppyType.FLOPPY_160M.getUsableBytes() || !diskHasPart) {
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

    /**
     * Sanitize filename for mcopy compatibility by removing problematic characters.
     * <p>
     * mcopy's argument parser treats certain characters as special, causing path truncation.
     * This method creates a safe temporary filename that mcopy can process correctly.
     * The sanitized name is only used for the source path passed to mcopy; the DOS name
     * inside the floppy image is controlled separately via DosNameUtil.
     * <p>
     * Sanitized characters:
     * <ul>
     *   <li>{@code #} - Comment/hash character</li>
     *   <li>{@code [, ]} - Bracket characters</li>
     *   <li>{@code (, )} - Parenthesis characters</li>
     *   <li>{@code &} - Ampersand (shell background operator)</li>
     *   <li>{@code $} - Dollar sign (shell variable expansion)</li>
     *   <li>{@code !} - Exclamation mark (shell history expansion)</li>
     *   <li>Space - Argument separator</li>
     * </ul>
     *
     * @param originalName Original filename (e.g., "game[#1].sfc.1" or "Ren & Stimpy$! (USA).sfc")
     * @return Sanitized filename safe for mcopy (e.g., "game_1.sfc.1" or "Ren___Stimpy___USA_.sfc")
     */
    private String sanitizeForMcopy(String originalName) {
        // Replace problematic characters that mcopy treats as special
        // These include shell-sensitive characters that cause truncation or parsing issues
        return originalName
                .replace("#", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("(", "_")
                .replace(")", "_")
                .replace("&", "_")
                .replace("$", "_")
                .replace("!", "_")
                .replace(" ", "_");
    }
}
