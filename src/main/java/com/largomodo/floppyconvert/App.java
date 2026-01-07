package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.core.RomProcessor;
import com.largomodo.floppyconvert.service.Ucon64Driver;
import com.largomodo.floppyconvert.service.MtoolsDriver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CLI entry point for ROM to floppy disk conversion tool.
 *
 * Parses arguments, validates environment (tool existence, path accessibility),
 * iterates ROM files, orchestrates batch processing with fail-soft error handling
 * (single ROM failure does not stop batch).
 *
 * Usage:
 *   java -jar floppyconvert.jar --input-dir <dir> --output-dir <dir> --empty-image <file>
 *     [--ucon64-path <path>] [--mtools-path <path>]
 */
public class App {

    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);
            runBatch(config);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        }
    }

    /**
     * Parse CLI arguments into configuration record.
     *
     * Uses switch expression pattern (Java 21 feature) for clean argument handling.
     * Defaults: ucon64Path="ucon64", mtoolsPath="mcopy" (assumes PATH availability).
     *
     * @throws IllegalArgumentException if required arguments missing or unknown argument provided
     */
    private static Config parseArgs(String[] args) {
        String inputDir = null;
        String outputDir = null;
        String emptyImage = null;
        String ucon64Path = "ucon64";   // Default assumes binary in PATH
        String mtoolsPath = "mcopy";    // Default assumes binary in PATH
        CopierFormat format = CopierFormat.FIG;  // FIG default provides backward compatibility for existing scripts

        // Iterate with manual index increment to consume values after flags
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input-dir" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--input-dir requires a value");
                    inputDir = args[++i];
                }
                case "--output-dir" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--output-dir requires a value");
                    outputDir = args[++i];
                }
                case "--empty-image" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--empty-image requires a value");
                    emptyImage = args[++i];
                }
                case "--ucon64-path" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--ucon64-path requires a value");
                    ucon64Path = args[++i];
                }
                case "--mtools-path" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--mtools-path requires a value");
                    mtoolsPath = args[++i];
                }
                case "--format" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--format requires a value");
                    // Validate at parseArgs time (fail-fast before I/O operations)
                    // Why early validation: Prevents wasted work (temp directory creation, ROM copying)
                    format = CopierFormat.fromCliArgument(args[++i]);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (inputDir == null || outputDir == null || emptyImage == null) {
            throw new IllegalArgumentException("Missing required arguments");
        }

        return new Config(inputDir, outputDir, emptyImage, ucon64Path, mtoolsPath, format);
    }

    /**
     * Execute batch ROM processing with fail-soft error handling.
     *
     * Strategy: Fail-fast validation (environment issues affect all ROMs),
     * then fail-soft per-ROM processing (single failure logged, batch continues).
     *
     * Sequential processing avoids I/O contention (ucon64/mtools are I/O bound).
     */
    private static void runBatch(Config config) {
        // Fail-fast validation prevents wasting time processing ROMs when environment is broken
        // Check paths, create output dir, validate template accessibility
        validatePaths(config);

        File inputDirFile = new File(config.inputDir);
        File[] romFiles = inputDirFile.listFiles((dir, name) -> name.endsWith(".sfc"));

        if (romFiles == null || romFiles.length == 0) {
            System.out.println("No .sfc files found in " + config.inputDir);
            return;
        }

        System.out.println("Found " + romFiles.length + " ROM file(s)");

        Ucon64Driver ucon64 = new Ucon64Driver(config.ucon64Path);
        MtoolsDriver mtools = new MtoolsDriver(config.mtoolsPath);
        RomProcessor processor = new RomProcessor();

        int successCount = 0;
        int failCount = 0;
        Path failuresLog = Paths.get(config.outputDir, "failures.txt");

        // Process each ROM sequentially to avoid I/O contention (Decision Log: Sequential vs Parallel)
        // ucon64/mtools perform heavy disk I/O; parallelism would cause file system contention
        for (File romFile : romFiles) {
            try {
                processor.processRom(
                    romFile,
                    Paths.get(config.outputDir),
                    new File(config.emptyImage),
                    ucon64,
                    mtools,
                    config.format
                );
                successCount++;
            } catch (Exception e) {
                // Fail-soft: log error, continue batch (good for large ROM libraries with mixed quality)
                failCount++;
                logFailure(failuresLog, romFile.getName(), e);
                System.err.println("FAILED: " + romFile.getName() + " - " + e.getMessage());
            }
        }

        System.out.println("\nBatch complete: " + successCount + " successful, " +
                         failCount + " failed");
        if (failCount > 0) {
            System.out.println("See " + failuresLog + " for failure details");
        }
    }

    /**
     * Validate environment prerequisites before batch processing starts.
     *
     * Fail-fast approach: discover missing tools/paths immediately with clear error,
     * prevents wasting time processing 50 ROMs before discovering mcopy is missing.
     *
     * @throws IllegalArgumentException if validation fails
     */
    private static void validatePaths(Config config) {
        if (!new File(config.inputDir).isDirectory()) {
            throw new IllegalArgumentException("Input directory does not exist: " + config.inputDir);
        }
        if (!new File(config.emptyImage).isFile()) {
            throw new IllegalArgumentException("Empty image template not found: " + config.emptyImage);
        }
        // Create output directory if missing (convenience for user)
        File outputDir = new File(config.outputDir);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalArgumentException("Cannot create output directory (check parent permissions): " + config.outputDir);
        }

        // Validate external tools exist (fail-fast per Decision Log and Known Risks)
        if (!new File(config.ucon64Path).canExecute() && !commandExistsInPath(config.ucon64Path)) {
            throw new IllegalArgumentException("ucon64 not found: install via package manager or specify --ucon64-path");
        }
        if (!new File(config.mtoolsPath).canExecute() && !commandExistsInPath(config.mtoolsPath)) {
            throw new IllegalArgumentException("mcopy not found: install mtools package or specify --mtools-path");
        }
    }

    /**
     * Check if command exists in system PATH.
     */
    private static boolean commandExistsInPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            return false;  // Fail-fast validation will catch missing tools
        }
        return Arrays.stream(pathEnv.split(File.pathSeparator))
            .map(dir -> new File(dir, command))
            .anyMatch(File::canExecute);
    }

    /**
     * Append failure record to failures.txt log.
     *
     * Swallows IOException on log write failure (batch processing continues regardless).
     * Rationale: Logging failure should not halt batch job; console output provides
     * primary failure notification, file log is secondary convenience.
     */
    private static void logFailure(Path logFile, String romName, Exception e) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile.toFile(), true))) {
            writer.println(romName + ": " + e.getMessage());
        } catch (IOException ignored) {
            // Swallow: logging failure should not halt batch processing
            // User still sees console error output
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar floppyconvert.jar [options]");
        System.out.println();
        System.out.println("Required:");
        System.out.println("  --input-dir <path>      Directory containing ROM files (.sfc)");
        System.out.println("  --output-dir <path>     Output directory for floppy images");
        System.out.println("  --empty-image <path>    Pre-formatted 1.6MB FAT12 floppy image template");
        System.out.println();
        System.out.println("Optional:");
        System.out.println("  --ucon64-path <path>    Path to ucon64 binary (default: ucon64)");
        System.out.println("  --mtools-path <path>    Path to mcopy binary (default: mcopy)");
        System.out.println("  --format <fig|swc|ufo|gd3>  Backup unit format (default: fig)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar floppy-convert.jar \\");
        System.out.println("    --input-dir ./roms \\");
        System.out.println("    --output-dir ./output \\");
        System.out.println("    --empty-image ./template.img \\");
        System.out.println("    --format gd3");
    }

    /**
     * Configuration record (Java 21 record feature).
     *
     * Immutable value object for parsed CLI arguments.
     */
    private record Config(String inputDir, String outputDir, String emptyImage,
                         String ucon64Path, String mtoolsPath, CopierFormat format) {}
}
