package com.largomodo.floppyconvert;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
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
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * CLI entry point for SNES ROM to floppy image conversion.
 *
 * Uses Picocli framework for argument parsing with automatic help generation
 * and type-safe validation. Accepts a single positional input path (file or
 * directory) and determines processing mode via runtime inspection (no
 * mutual exclusivity validation needed).
 *
 * Smart defaults:
 *   - File input without -o: outputs to current working directory
 *   - Directory input without -o: outputs to <input>/output subdirectory
 *   - Explicit -o flag: overrides all defaults
 */
@Command(name = "floppyconvert", mixinStandardHelpOptions = true, version = "1.0",
         description = "Converts SNES ROM files to floppy disk images for backup units (FIG/SWC/UFO/GD3).")
public class App implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "INPUT",
                description = "ROM file (.sfc) or directory containing ROM files")
    File inputPath;

    @Option(names = {"-o", "--output-dir"},
            description = "Output directory for floppy images (default: . for files, <input>/output for directories)")
    File outputDir;

    @Option(names = "--format", defaultValue = "FIG",
            description = "Backup unit format (default: FIG). Valid values: ${COMPLETION-CANDIDATES}")
    CopierFormat format;

    @Option(names = "--ucon64-path", defaultValue = "ucon64",
            description = "Path to ucon64 binary (default: ucon64)")
    private String ucon64Path;

    @Option(names = "--mtools-path", defaultValue = "mcopy",
            description = "Path to mcopy binary from mtools (default: mcopy)")
    private String mtoolsPath;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!inputPath.exists()) {
            throw new ParameterException(spec.commandLine(),
                "Input path does not exist: " + inputPath.getAbsolutePath());
        }

        if (!inputPath.canRead()) {
            throw new ParameterException(spec.commandLine(),
                "Input path is not readable (check permissions): " + inputPath.getAbsolutePath());
        }

        if (outputDir == null) {
            if (inputPath.isFile()) {
                outputDir = new File(".");
            } else {
                outputDir = new File(inputPath, "output");
            }
        }

        if (outputDir.exists() && !outputDir.isDirectory()) {
            throw new ParameterException(spec.commandLine(),
                "Output path must be a directory, not a file: " + outputDir.getAbsolutePath());
        }
        if (outputDir.exists() && !outputDir.canWrite()) {
            throw new ParameterException(spec.commandLine(),
                "Output directory is not writable (check permissions): " + outputDir.getAbsolutePath());
        }

        Files.createDirectories(outputDir.toPath());

        if (inputPath.isFile()) {
            runSingleFile(
                new Config(null, outputDir.getAbsolutePath(),
                           ucon64Path, mtoolsPath, format, inputPath.getAbsolutePath()),
                outputDir.toPath()
            );
        } else {
            runBatch(
                new Config(inputPath.getAbsolutePath(), outputDir.getAbsolutePath(),
                           ucon64Path, mtoolsPath, format, null)
            );
        }

        return 0;
    }

    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new App());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }


    /**
     * Execute batch ROM processing with fail-soft error handling.
     * <p>
     * Strategy: Fail-fast validation (environment issues affect all ROMs),
     * then fail-soft per-ROM processing (single failure logged, batch continues).
     * <p>
     * Sequential processing avoids I/O contention (ucon64/mtools are I/O bound).
     */
    private static void runBatch(Config config) throws IOException {
        Path inputRoot = Paths.get(config.inputDir);
        Path outputRoot = Paths.get(config.outputDir);

        validateExternalTools(config);

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

        validateExternalTools(config);

        Ucon64Driver ucon64 = new Ucon64Driver(config.ucon64Path);
        MtoolsDriver mtools = new MtoolsDriver(config.mtoolsPath);
        RomProcessor processor = new RomProcessor();

        // Process ROM with provided outputBase (enables test isolation)
        processor.processRom(
                inputPath.toFile(),
                outputBase,
                ucon64,
                mtools,
                config.format
        );

        System.out.println("Conversion complete: " + inputPath.getFileName());
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
     * Validate external tool availability (ucon64 and mcopy).
     * Fail-fast validation prevents processing attempts when tools are missing.
     */
    private static void validateExternalTools(Config config) throws IOException {
        File ucon64 = new File(config.ucon64Path);
        if (!ucon64.canExecute() && !commandExistsInPath(config.ucon64Path)) {
            throw new IOException("ucon64 not found: install via package manager or specify --ucon64-path");
        }
        File mcopy = new File(config.mtoolsPath);
        if (!mcopy.canExecute() && !commandExistsInPath(config.mtoolsPath)) {
            throw new IOException("mcopy not found: install via package manager (mtools) or specify --mtools-path");
        }
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


    /**
     * Bridges Picocli field-based arguments to runBatch/runSingleFile method
     * signatures (avoids cascading changes to RomProcessor integration points).
     */
    private record Config(String inputDir, String outputDir,
                          String ucon64Path, String mtoolsPath, CopierFormat format, String inputFile) {
    }
}
