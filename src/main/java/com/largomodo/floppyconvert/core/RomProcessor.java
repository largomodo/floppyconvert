package com.largomodo.floppyconvert.core;

import com.largomodo.floppyconvert.core.domain.DiskLayout;
import com.largomodo.floppyconvert.core.domain.DiskPacker;
import com.largomodo.floppyconvert.core.domain.RomPartMetadata;
import com.largomodo.floppyconvert.core.workspace.ConversionWorkspace;
import com.largomodo.floppyconvert.format.CopierFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ROM to floppy disk image conversion pipeline orchestrator.
 * <p>
 * Coordinates multi-step workflow via ConversionFacade:
 * 1. Split ROM into parts
 * 2. Pack parts onto disks by actual size (not fixed count)
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

    private static final Logger log = LoggerFactory.getLogger(RomProcessor.class);

    private final DiskPacker packer;
    private final ConversionFacade facade;
    private final DiskTemplateFactory templateFactory;
    private final RomPartNormalizer normalizer;

    /**
     * Construct RomProcessor with required dependencies.
     * <p>
     * Constructor injection pattern: dependencies become private final fields,
     * enabling immutability, thread-safety, and fail-fast validation.
     *
     * @param packer          Disk packing strategy (e.g., GreedyDiskPacker)
     * @param facade          Conversion facade abstracting splitting and writing services
     * @param templateFactory Floppy disk template provider (mockable for testing)
     * @param normalizer      ROM part filename normalizer (stateless utility)
     * @throws IllegalArgumentException if any dependency is null
     */
    public RomProcessor(DiskPacker packer, ConversionFacade facade,
                        DiskTemplateFactory templateFactory, RomPartNormalizer normalizer) {
        if (packer == null || facade == null ||
                templateFactory == null || normalizer == null) {
            throw new IllegalArgumentException("All dependencies must not be null");
        }
        this.packer = packer;
        this.facade = facade;
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
     * @param romFile       Source ROM file (.sfc format)
     * @param outputBaseDir Base output directory (final .img location)
     * @param uniqueSuffix  Unique suffix to prevent workspace collisions (e.g., UUID for concurrent execution)
     * @param format        Backup unit format for ROM splitting
     * @return Number of disk images created (for observer reporting)
     * @throws IOException                                          if any pipeline step fails
     * @throws com.largomodo.floppyconvert.core.workspace.CleanupException if workspace cleanup fails
     */
    public int processRom(
            File romFile,
            Path outputBaseDir,
            String uniqueSuffix,
            CopierFormat format
    ) throws IOException {

        String sanitizedBaseName = extractAndSanitizeBaseName(romFile);

        try (ConversionWorkspace ws = new ConversionWorkspace(outputBaseDir, sanitizedBaseName, uniqueSuffix)) {
            Path gameOutputDir = ws.getWorkDir();
            Files.createDirectories(gameOutputDir);
            ws.track(gameOutputDir);

            log.info("Processing: {}", romFile.getName());

            Set<Path> existingFiles;
            try (var stream = Files.list(gameOutputDir)) {
                existingFiles = stream.collect(Collectors.toSet());
            }

            List<File> parts = facade.splitRom(romFile, gameOutputDir, format);

            if (parts.isEmpty()) {
                throw new IOException("ROM splitter produced no split parts for " + romFile.getName());
            }

            trackIntermediateFiles(ws, format, gameOutputDir, existingFiles, sanitizedBaseName, parts);

            List<RomPartMetadata> partMetadata = normalizer.normalize(parts, ws);
            List<DiskLayout> diskLayouts = packer.pack(partMetadata);

            List<Path> createdImages = writeDisksFromLayout(diskLayouts, gameOutputDir, sanitizedBaseName, ws);

            promoteFinalOutputs(createdImages, outputBaseDir, sanitizedBaseName, ws);

            log.info("Success: {} -> {} disk(s) [{}]", romFile.getName(), diskLayouts.size(), format.name());

            return diskLayouts.size();
        }
    }

    /**
     * Extract and sanitize base filename without extension.
     * <p>
     * Sanitization prevents shell escaping issues in output directory names.
     * Empty basename after sanitization indicates filesystem incompatibility.
     */
    private String extractAndSanitizeBaseName(File romFile) throws IOException {
        String baseName = romFile.getName().replaceFirst("\\.[^.]+$", "");
        if (baseName.isEmpty()) {
            throw new IOException("Cannot extract base name from ROM file: " + romFile.getName());
        }
        String sanitized = normalizer.sanitizeName(baseName);
        if (sanitized.isEmpty()) {
            throw new IOException("Sanitized base name is empty for ROM file: " +
                    romFile.getName() + " (original: " + baseName + ")");
        }
        return sanitized;
    }

    /**
     * Track intermediate files created by split operation for cleanup.
     * Handles format-specific naming conventions (GD3 SF-Code vs standard extensions).
     */
    private void trackIntermediateFiles(ConversionWorkspace ws, CopierFormat format, Path gameOutputDir,
                                        Set<Path> existingFiles, String baseName, List<File> parts) throws IOException {
        if (format == CopierFormat.GD3) {
            try (var stream = Files.list(gameOutputDir)) {
                stream.filter(p -> !existingFiles.contains(p))
                        .filter(p -> !p.getFileName().toString().startsWith("."))
                        .filter(p -> !format.getSplitPartFilter().test(p.toFile()))
                        .filter(p -> !p.toString().endsWith(".img"))
                        .forEach(ws::track);
            }
        } else {
            Path intermediateFile = gameOutputDir.resolve(baseName + "." + format.getFileExtension());
            if (Files.exists(intermediateFile)) {
                ws.track(intermediateFile);
            }
        }

        for (File part : parts) {
            ws.track(part.toPath());
        }
    }

    /**
     * Create DOS 8.3 name mapping for ROM parts.
     * <p>
     * RomPartMetadata provides pre-computed DOS names from RomPartNormalizer.
     * Centralized mapping enables validation of DOS name collisions per disk.
     */
    private Map<File, String> createDosNameMapping(List<RomPartMetadata> parts) {
        Map<File, String> dosNameMap = new LinkedHashMap<>();
        for (RomPartMetadata partMeta : parts) {
            dosNameMap.put(partMeta.originalPath().toFile(), partMeta.dosName());
        }
        return dosNameMap;
    }

    /**
     * Validate no DOS name collisions exist.
     * 8.3 truncation can cause different filenames to map to same DOS name.
     */
    private void validateNoDosCollisions(Map<File, String> dosNameMap, int diskNumber) throws IOException {
        Map<String, File> dosToFile = new HashMap<>();
        for (Map.Entry<File, String> entry : dosNameMap.entrySet()) {
            File sourceFile = entry.getKey();
            String dosName = entry.getValue();

            if (dosToFile.containsKey(dosName)) {
                File conflictingFile = dosToFile.get(dosName);
                throw new IOException("DOS name collision on disk " + diskNumber + ": " + dosName +
                        " would overwrite " + conflictingFile.getName() +
                        " with " + sourceFile.getName() + " (8.3 truncation conflict)");
            }
            dosToFile.put(dosName, sourceFile);
        }
    }

    /**
     * Write disk images from layouts.
     * Extracts disk writing loop with per-layout DOS name mapping.
     */
    private List<Path> writeDisksFromLayout(List<DiskLayout> layouts, Path gameOutputDir,
                                            String baseName, ConversionWorkspace ws) throws IOException {
        List<Path> createdImages = new ArrayList<>();
        int diskNumber = 1;

        for (DiskLayout layout : layouts) {
            String diskName = createDiskName(baseName, diskNumber, layouts.size());

            File targetImage = gameOutputDir.resolve(diskName).toFile();
            templateFactory.createBlankDisk(layout.floppyType(), targetImage.toPath());

            ws.track(targetImage.toPath());
            ws.markAsOutput(targetImage.toPath());
            createdImages.add(targetImage.toPath());

            Map<File, String> dosNameMap = createDosNameMapping(layout.contents());
            validateNoDosCollisions(dosNameMap, diskNumber);

            List<File> diskParts = new ArrayList<>();
            for (RomPartMetadata partMeta : layout.contents()) {
                diskParts.add(partMeta.originalPath().toFile());
            }

            facade.write(targetImage, diskParts, dosNameMap);
            log.info("  Created: {} ({} parts)", diskName, layout.contents().size());
            diskNumber++;
        }

        return createdImages;
    }

    /**
     * Create disk image filename with appropriate numbering.
     * Single disk: baseName.img, Multiple disks: baseName_1.img, baseName_2.img
     */
    private String createDiskName(String baseName, int diskNumber, int totalDisks) {
        if (totalDisks > 1) {
            return baseName + "_" + diskNumber + ".img";
        } else {
            return baseName + ".img";
        }
    }

    /**
     * Promote disk images from workspace to final output directory.
     * Uses atomic move to ensure outputs appear complete.
     */
    private void promoteFinalOutputs(List<Path> createdImages, Path outputBaseDir,
                                     String sanitizedBaseName, ConversionWorkspace ws) throws IOException {
        String baseName = sanitizedBaseName;
        Path finalGameDir = outputBaseDir.resolve(baseName);
        Files.createDirectories(finalGameDir);

        for (Path imgFile : createdImages) {
            ws.promoteToFinal(imgFile, finalGameDir);
        }
    }

}
