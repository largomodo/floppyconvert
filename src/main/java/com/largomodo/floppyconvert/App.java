package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.core.RomProcessor;
import com.largomodo.floppyconvert.service.MtoolsDriver;
import com.largomodo.floppyconvert.service.Ucon64Driver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * CLI entry point for ROM to floppy disk conversion tool.
 * <p>
 * Parses arguments, validates environment (tool existence, path accessibility),
 * iterates ROM files, orchestrates batch processing with fail-soft error handling
 * (single ROM failure does not stop batch).
 * <p>
 * Usage:
 * java -jar floppyconvert.jar --input-dir <dir> --output-dir <dir> --empty-image <file>
 * [--ucon64-path <path>] [--mtools-path <path>]
 */
public class App {

    public static void main(String[] args) {
        try {
            Config config = parseArgs(args);

            // Route based on input mode (mutually exclusive by validation invariant)
            if (config.inputFile != null) {
                runSingleFile(config, Paths.get("."));  // CWD output for single-file mode
            } else {
                runBatch(config);  // config.inputDir guaranteed non-null here
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Parse CLI arguments into configuration record.
     * <p>
     * Uses switch expression pattern (Java 21 feature) for clean argument handling.
     * Defaults: ucon64Path="ucon64", mtoolsPath="mcopy" (assumes PATH availability).
     *
     * @throws IllegalArgumentException if required arguments missing or unknown argument provided
     */
    private static Config parseArgs(String[] args) {
        String inputDir = null;
        String outputDir = null;
        String inputFile = null;
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
                case "--input-file" -> {
                    if (i + 1 >= args.length) throw new IllegalArgumentException("--input-file requires a value");
                    inputFile = args[++i];
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

        // Enforces Config validity invariant: (inputDir XOR inputFile) AND emptyImage AND (inputFile OR outputDir)
        if (inputDir != null && inputFile != null) {
            throw new IllegalArgumentException("Both --input-dir and --input-file provided. Use only one.");
        }

        if (inputDir == null && inputFile == null) {
            throw new IllegalArgumentException("Must provide either --input-dir or --input-file");
        }

        if (emptyImage == null) {
            throw new IllegalArgumentException("Missing required argument: --empty-image");
        }

        // Batch mode needs explicit output to prevent CWD clutter; single-file mode defaults to CWD
        if (inputDir != null && outputDir == null) {
            throw new IllegalArgumentException("Batch mode (--input-dir) requires --output-dir");
        }

        // Single-file mode always outputs to CWD; explicit outputDir defeats UX goal and causes confusion
        if (inputFile != null && outputDir != null) {
            throw new IllegalArgumentException("Single-file mode (--input-file) does not support --output-dir. Output is always placed in current directory.");
        }

        return new Config(inputDir, outputDir, emptyImage, ucon64Path, mtoolsPath, format, inputFile);
    }

    /**
     * Execute batch ROM processing with fail-soft error handling.
     * <p>
     * Strategy: Fail-fast validation (environment issues affect all ROMs),
     * then fail-soft per-ROM processing (single failure logged, batch continues).
     * <p>
     * Sequential processing avoids I/O contention (ucon64/mtools are I/O bound).
     */
    private static void runBatch(Config config) {
        // Fail-fast validation prevents wasting time processing ROMs when environment is broken
        // Check paths, create output dir, validate template accessibility
        validatePaths(config);

        Path inputRoot = Paths.get(config.inputDir);
        Path outputRoot = Paths.get(config.outputDir);

        Ucon64Driver ucon64 = new Ucon64Driver(config.ucon64Path);
        MtoolsDriver mtools = new MtoolsDriver(config.mtoolsPath);
        RomProcessor processor = new RomProcessor();

        // Counters must be effectively final for lambda capture
        // AtomicInteger enables mutation within forEach while maintaining thread-safety
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        Path failuresLog = Paths.get(config.outputDir, "failures.txt");

        /*
         * Recursive directory traversal with lazy evaluation.
         *
         * Files.walk() streams one path at a time → constant memory usage regardless
         * of directory tree depth → avoids OOM for large ROM libraries (10k+ files).
         *
         * Sequential forEach (not parallel stream) required: ucon64 and mtools are
         * I/O bound → parallel execution causes file system contention → degrades
         * performance and risks file corruption from concurrent writes.
         */
        try (Stream<Path> stream = Files.walk(inputRoot)) {
            stream.filter(path -> {
                      try {
                          return Files.isRegularFile(path);
                      } catch (UncheckedIOException e) {
                          // File attributes unreadable (broken symlink, permission denied on file)
                          // Skip this file, continue batch with remaining files
                          System.err.println("WARNING: Cannot access " + inputRoot.relativize(path) + " - skipping");
                          return false;
                      }
                  })
                  .filter(path -> path.toString().toLowerCase().endsWith(".sfc"))
                  .forEach(romPath -> {
                      try {
                          /*
                           * Path relativization mirrors source structure in output.
                           *
                           * inputRoot.relativize(romPath.getParent()) calculates
                           * subdirectory path from input root. For root-level ROMs,
                           * returns empty path → outputRoot.resolve(emptyPath) returns
                           * outputRoot unchanged → root-level ROMs correctly output
                           * to base directory without intermediate nesting.
                           */
                          Path relativePath = inputRoot.relativize(romPath.getParent());
                          Path targetBaseDir = outputRoot.resolve(relativePath);

                          // Files.createDirectories() is idempotent (safe for repeated calls)
                          Files.createDirectories(targetBaseDir);

                          processor.processRom(
                                  romPath.toFile(),
                                  targetBaseDir,
                                  new File(config.emptyImage),
                                  ucon64,
                                  mtools,
                                  config.format
                          );
                          successCount.incrementAndGet();
                      } catch (Exception e) {
                          /*
                           * Fail-soft per-ROM error handling.
                           *
                           * try-catch scoped to individual ROM: exception logged to failures.txt,
                           * counters updated, batch continues with remaining ROMs. Prevents single
                           * corrupted file from blocking entire library conversion.
                           */
                          failCount.incrementAndGet();
                          logFailure(failuresLog, inputRoot.relativize(romPath).toString(), e);
                          System.err.println("FAILED: " + inputRoot.relativize(romPath) + " - " + e.getMessage());
                      }
                  });
        } catch (UncheckedIOException e) {
            /*
             * IOException during directory traversal (mid-traversal).
             *
             * Occurs when nested subdirectory becomes inaccessible during stream traversal
             * (e.g., permission denied on subdirectory prevents listing contents).
             * Successfully processed ROMs counted, error logged with specific path from exception.
             * Fail-soft: partial batch completion better than aborting already-completed work.
             * Note: File-level access errors (broken symlinks) caught in filter lambda above.
             */
            System.err.println("WARNING: Directory traversal interrupted - " + e.getCause().getMessage());
            System.err.println("Batch incomplete: " + successCount.get() + " successful, " +
                    failCount.get() + " failed, some directories skipped");
            return;
        } catch (IOException e) {
            /*
             * IOException during walk() initialization is fail-fast.
             *
             * Indicates fundamental access problem (input directory unreadable).
             * Fails before any ROM processing begins.
             */
            System.err.println("ERROR: Cannot traverse input directory: " + e.getMessage());
            return;
        }

        System.out.println("\nBatch complete: " + successCount.get() + " successful, " +
                failCount.get() + " failed");
        if (failCount.get() > 0) {
            System.out.println("See " + failuresLog + " for failure details");
        }
    }

    /**
     * Execute single-file ROM processing with fail-fast error handling.
     * <p>
     * Accepts outputBase as parameter for testability: production passes Paths.get("."),
     * tests pass tempDir to avoid filesystem pollution and enable parallel execution.
     * <p>
     * Validates input file existence/type, then delegates to RomProcessor.
     * Exceptions propagate to main() (enables fail-fast with exit code 1).
     *
     * @param config     Configuration containing inputFile path and tool paths
     * @param outputBase Directory where output will be written (CWD in production, tempDir in tests)
     * @throws IOException if input file validation fails or processing encounters I/O error
     */
    private static void runSingleFile(Config config, Path outputBase) throws IOException {
        Path inputPath = Paths.get(config.inputFile);

        // Fail-fast validation with clear error messages
        // Validation order: existence before type check (prevents confusing "not a file" for missing paths)
        if (!Files.exists(inputPath)) {
            throw new IOException("Input file does not exist: " + inputPath);
        }

        if (!Files.isRegularFile(inputPath)) {
            throw new IOException("Input path is not a file: " + inputPath);
        }

        // Validate template accessibility (fail-fast before attempting processing)
        Path templatePath = Paths.get(config.emptyImage);
        if (!Files.isRegularFile(templatePath)) {
            throw new IOException("Empty image template not found: " + templatePath);
        }

        // Validate external tools (reuse batch mode validation logic)
        if (!new File(config.ucon64Path).canExecute() && !commandExistsInPath(config.ucon64Path)) {
            throw new IOException("ucon64 not found: install via package manager or specify --ucon64-path");
        }
        if (!new File(config.mtoolsPath).canExecute() && !commandExistsInPath(config.mtoolsPath)) {
            throw new IOException("mcopy not found: install mtools package or specify --mtools-path");
        }

        Ucon64Driver ucon64 = new Ucon64Driver(config.ucon64Path);
        MtoolsDriver mtools = new MtoolsDriver(config.mtoolsPath);
        RomProcessor processor = new RomProcessor();

        // Process ROM with provided outputBase (enables test isolation)
        processor.processRom(
                inputPath.toFile(),
                outputBase,
                templatePath.toFile(),
                ucon64,
                mtools,
                config.format
        );

        System.out.println("Conversion complete: " + inputPath.getFileName());
    }

    /**
     * Validate environment prerequisites before batch processing starts.
     * <p>
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
     * <p>
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
        System.out.println("Modes (choose one):");
        System.out.println("  BATCH MODE:");
        System.out.println("    --input-dir <path>      Directory containing ROM files (.sfc)");
        System.out.println("    --output-dir <path>     Output directory for floppy images (required for batch)");
        System.out.println();
        System.out.println("  SINGLE FILE MODE:");
        System.out.println("    --input-file <path>     Single ROM file (output to current directory; --output-dir not supported)");
        System.out.println();
        System.out.println("Required (both modes):");
        System.out.println("  --empty-image <path>    Pre-formatted 1.6MB FAT12 floppy image template");
        System.out.println();
        System.out.println("Optional:");
        System.out.println("  --ucon64-path <path>    Path to ucon64 binary (default: ucon64)");
        System.out.println("  --mtools-path <path>    Path to mcopy binary (default: mcopy)");
        System.out.println("  --format <fig|swc|ufo|gd3>  Backup unit format (default: fig)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  Batch mode:");
        System.out.println("  java -jar floppyconvert.jar \\");
        System.out.println("    --input-dir ./roms \\");
        System.out.println("    --output-dir ./output \\");
        System.out.println("    --empty-image ./template.img \\");
        System.out.println("    --format gd3");
        System.out.println();
        System.out.println("  Single file mode:");
        System.out.println("  java -jar floppyconvert.jar \\");
        System.out.println("    --input-file ./roms/ChronoTrigger.sfc \\");
        System.out.println("    --empty-image ./template.img \\");
        System.out.println("    --format gd3");
    }

    /**
     * Configuration record (Java 21 record feature).
     * <p>
     * Immutable value object for parsed CLI arguments.
     */
    private record Config(String inputDir, String outputDir, String emptyImage,
                          String ucon64Path, String mtoolsPath, CopierFormat format, String inputFile) {
    }
}
