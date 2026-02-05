package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.E2ETestRomRegistry.RomSpec;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Provides ROMs for E2E tests with automatic fallback to synthetic ROMs.
 * Enables tests to run on clean checkouts without copyrighted ROM files.
 */
public class TestRomProvider {

    /**
     * Returns a ROM file for testing, using real ROM if available or synthetic fallback.
     *
     * @param resourcePath Real ROM resource path (e.g., "/snes/Chrono Trigger (USA).sfc")
     * @param outputDir    Directory to write ROM file
     * @return Path to real or synthetic ROM file
     * @throws IOException if ROM creation fails
     */
    public static Path getRomOrSynthetic(String resourcePath, Path outputDir) throws IOException {
        // Try to load real ROM from resources
        Optional<Path> realRom = getRealRom(resourcePath, outputDir);
        if (realRom.isPresent()) {
            return realRom.get();
        }

        // Fallback: create synthetic ROM based on registry spec
        RomSpec spec = E2ETestRomRegistry.getSpec(resourcePath);
        return createSyntheticRom(spec, outputDir);
    }

    /**
     * Attempts to load real ROM from classpath resources.
     *
     * @param resourcePath Real ROM resource path
     * @param outputDir    Directory to write ROM file
     * @return Optional containing Path if real ROM found, empty otherwise
     * @throws IOException if ROM copy fails
     */
    private static Optional<Path> getRealRom(String resourcePath, Path outputDir) throws IOException {
        try (InputStream is = TestRomProvider.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return Optional.empty();
            }

            // Extract filename from resource path
            String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            Path romPath = outputDir.resolve(filename);
            Files.copy(is, romPath);
            return Optional.of(romPath);
        }
    }

    /**
     * Creates a synthetic ROM based on specification.
     *
     * @param spec      ROM specification from registry
     * @param outputDir Directory to write ROM file
     * @return Path to synthetic ROM file
     * @throws IOException if ROM generation fails
     */
    private static Path createSyntheticRom(RomSpec spec, Path outputDir) throws IOException {
        return switch (spec.type()) {
            case LoROM -> SyntheticRomFactory.generateLoRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), outputDir);
            case HiROM -> SyntheticRomFactory.generateHiRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), outputDir);
            case ExHiROM -> SyntheticRomFactory.generateExHiRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), outputDir);
        };
    }
}
