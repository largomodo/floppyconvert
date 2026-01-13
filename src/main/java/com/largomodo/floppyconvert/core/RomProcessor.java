package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.service.FloppyImageWriter;
import com.largomodo.floppyconvert.service.RomSplitter;
import com.largomodo.floppyconvert.core.FloppyType;
import com.largomodo.floppyconvert.core.domain.DiskLayout;
import com.largomodo.floppyconvert.core.domain.DiskPacker;
import com.largomodo.floppyconvert.core.domain.RomPartMetadata;
import com.largomodo.floppyconvert.core.workspace.ConversionWorkspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ROM to floppy disk image conversion pipeline orchestrator.
 * <p>
 * Coordinates multi-step workflow:
 * 1. Split ROM into parts (via RomSplitter)
 * 2. Pack parts onto floppy disks by actual size (not fixed count)
 * 3. For each disk: copy template, inject parts with DOS names, move to output
 * 4. Cleanup temp directory
 * <p>
 * Isolated temp directories prevent filename collisions.
 * Cleanup in finally block ensures no disk space leaks on failure.
 * <p>
 * <b>Dependency Injection:</b> Constructor injection ensures immutability and fail-fast validation.
 * Dependencies stored as private final fields for thread-safety.
 */
public class RomProcessor {

    private final DiskPacker packer;
    private final RomSplitter splitter;
    private final FloppyImageWriter writer;
    private final DiskTemplateFactory templateFactory;
    private final RomPartNormalizer normalizer;

    /**
     * Construct RomProcessor with required dependencies.
     * <p>
     * Constructor injection pattern: dependencies become private final fields,
     * enabling immutability, thread-safety, and fail-fast validation.
     *
     * @param packer   Disk packing strategy (e.g., GreedyDiskPacker)
     * @param splitter ROM splitting service (e.g., Ucon64Driver)
     * @param writer   Floppy image writer (e.g., MtoolsDriver)
     * @param templateFactory Floppy disk template provider (mockable for testing)
     * @param normalizer ROM part filename normalizer (stateless utility)
     * @throws IllegalArgumentException if any dependency is null
     */
    public RomProcessor(DiskPacker packer, RomSplitter splitter, FloppyImageWriter writer,
                        DiskTemplateFactory templateFactory, RomPartNormalizer normalizer) {
        if (packer == null || splitter == null || writer == null ||
            templateFactory == null || normalizer == null) {
            throw new IllegalArgumentException("All dependencies must not be null");
        }
        this.packer = packer;
        this.splitter = splitter;
        this.writer = writer;
        this.templateFactory = templateFactory;
        this.normalizer = normalizer;
    }

    /**
     * Process single ROM file through full conversion pipeline.
     * <p>
     * Strategy: Create isolated temp dir with unique suffix, split ROM, pack parts onto disks
     * by actual file size, inject into floppy images, move to output, cleanup.
     * <p>
     * Multi-disk naming: GameName_1.img, GameName_2.img (explicit numbering for manual sorting).
     * Single-disk naming: GameName.img (no suffix needed).
     *
     * @param romFile            Source ROM file (.sfc format)
     * @param outputBaseDir      Base output directory (final .img location)
     * @param uniqueSuffix       Unique suffix to prevent workspace collisions (e.g., UUID for concurrent execution)
     * @param format             Backup unit format for ROM splitting
     * @return Number of disk images created (for observer reporting)
     * @throws IOException if any pipeline step fails
     */
    public int processRom(
            File romFile,
            Path outputBaseDir,
            String uniqueSuffix,
            CopierFormat format
    ) throws IOException {

        // Extract base name by removing extension: "SuperMario.sfc" → "SuperMario"
        String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
        if (baseName.isEmpty()) {
            throw new IOException("Cannot extract base name from ROM file: " + romFile.getName());
        }

        // Sanitize baseName for use in disk image filenames and directory name
        String sanitizedBaseName = normalizer.sanitizeName(baseName);
        if (sanitizedBaseName.isEmpty()) {
            throw new IOException("Sanitized base name is empty for ROM file: " +
                                 romFile.getName() + " (original: " + baseName + ")");
        }

        try (ConversionWorkspace ws = new ConversionWorkspace(outputBaseDir, sanitizedBaseName, uniqueSuffix)) {
            Path gameOutputDir = ws.getWorkDir();
            Files.createDirectories(gameOutputDir);

            // Track workspace directory for cleanup (deleted last due to reverse-order)
            ws.track(gameOutputDir);

            System.out.println("Processing: " + romFile.getName());

            // Invariant 4: preserve pre-existing user files
            // Snapshot existing files BEFORE splitter.split for comparison (needed for GD3 tracking)
            Set<Path> existingFiles;
            try (var stream = Files.list(gameOutputDir)) {
                existingFiles = stream.collect(Collectors.toSet());
            }

            // Split ROM into sequentially numbered parts (.1, .2, .3, ...)
            List<File> parts = splitter.split(romFile, gameOutputDir, format);

            if (parts.isEmpty()) {
                throw new IOException("ROM splitter produced no split parts for " + romFile.getName());
            }

            // Track intermediate file for cleanup
            // splitRom attempts best-effort deletion, but if it fails (Windows lock, permissions),
            // add to cleanup list so finally block can retry. This ensures intermediate files don't
            // pollute output directory even if initial deletion failed.
            //
            // After splitter.split, track only NEW files (created by this invocation)
            if (format == CopierFormat.GD3) {
                // GD3 uses unpredictable naming - scan for non-split files created after process start
                try (var stream = Files.list(gameOutputDir)) {
                    stream.filter(p -> !existingFiles.contains(p))  // Only new files (preserves user files)
                          .filter(p -> !p.getFileName().toString().startsWith("."))
                          .filter(p -> !format.getSplitPartFilter().test(p.toFile()))
                          .filter(p -> !p.toString().endsWith(".img"))
                          .forEach(ws::track);
                }
            } else {
                // Standard formats use predictable extension
                Path intermediateFile = gameOutputDir.resolve(baseName + "." + format.getFileExtension());
                if (Files.exists(intermediateFile)) {
                    ws.track(intermediateFile);
                }
            }

            // Add parts to cleanup list immediately
            // Ensures finally block can clean up even if subsequent operations fail
            for (File part : parts) {
                ws.track(part.toPath());
            }

            // Sanitize filenames and generate metadata for disk packing
            List<RomPartMetadata> partMetadata = normalizer.normalize(parts, ws);

            // Pack parts onto disks by actual size (not fixed count)
            List<DiskLayout> diskLayouts = packer.pack(partMetadata);

            int diskNumber = 1;
            List<Path> createdImages = new java.util.ArrayList<>();
            for (DiskLayout layout : diskLayouts) {
                String diskName;
                if (diskLayouts.size() > 1) {
                    // Multi-disk: explicit numbering (_1, _2) for manual sorting clarity
                    diskName = sanitizedBaseName + "_" + diskNumber + ".img";
                } else {
                    // Single disk: no suffix needed (cleaner naming)
                    diskName = sanitizedBaseName + ".img";
                }

                File targetImage = gameOutputDir.resolve(diskName).toFile();

                // Create blank FAT12 floppy disk image from template
                templateFactory.createBlankDisk(layout.floppyType(), targetImage.toPath());

                // Track .img file for cleanup in case subsequent operations fail
                ws.track(targetImage.toPath());
                
                // Protect from cleanup immediately after creation
                ws.markAsOutput(targetImage.toPath());
                createdImages.add(targetImage.toPath());

                // Inject each part into floppy image with DOS-compliant name
                Map<File, String> dosNameMap = new LinkedHashMap<>();
                for (RomPartMetadata partMeta : layout.contents()) {
                    File part = partMeta.originalPath().toFile();
                    String dosName = partMeta.dosName();
                    
                    if (dosNameMap.containsKey(part)) {
                        throw new IOException("Duplicate part in layout for disk " + diskNumber + ": " + 
                                part.getName());
                    }
                    
                    // Check for DOS name collisions
                    for (Map.Entry<File, String> entry : dosNameMap.entrySet()) {
                        if (entry.getValue().equals(dosName)) {
                            throw new IOException("DOS name collision on disk " + diskNumber + ": " + dosName +
                                    " would overwrite " + entry.getKey().getName() +
                                    " with " + part.getName() + " (8.3 truncation conflict)");
                        }
                    }
                    
                    dosNameMap.put(part, dosName);
                }
                
                // Batch write all parts to the image
                writer.write(targetImage, new ArrayList<>(dosNameMap.keySet()), dosNameMap);

                System.out.println("  Created: " + diskName +
                        " (" + layout.contents().size() + " parts)");
                diskNumber++;
            }

            // Promote disk images from workspace to final output directory
            Path finalGameDir = outputBaseDir.resolve(baseName);
            Files.createDirectories(finalGameDir);

            for (Path imgFile : createdImages) {
                ws.promoteToFinal(imgFile, finalGameDir);
            }

            System.out.println("Success: " + romFile.getName() +
                    " -> " + (diskNumber - 1) + " disk(s) [" + format.name() + "]");
            
            return diskLayouts.size();
        }
    }

}
