package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.*;
import com.largomodo.floppyconvert.service.fat.Fat12FormatFactory;
import com.largomodo.floppyconvert.core.domain.DiskPacker;
import com.largomodo.floppyconvert.core.domain.GreedyDiskPacker;
import com.largomodo.floppyconvert.core.workspace.CleanupException;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.DefaultConversionFacade;
import com.largomodo.floppyconvert.service.FloppyImageWriter;
import com.largomodo.floppyconvert.service.NativeRomSplitter;
import com.largomodo.floppyconvert.service.RomSplitter;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;
import com.largomodo.floppyconvert.util.SnesRomMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CLI entry point for SNES ROM to floppy image conversion.
 * <p>
 * Uses Picocli framework for argument parsing with automatic help generation
 * and type-safe validation. Accepts a single positional input path (file or
 * directory) and determines processing mode via runtime inspection (no
 * mutual exclusivity validation needed).
 * <p>
 * Smart defaults:
 * - File input without -o: outputs to current working directory
 * - Directory input without -o: outputs to <input>/output subdirectory
 * - Explicit -o flag: overrides all defaults
 */
@Command(
        name = "floppyconvert",
        mixinStandardHelpOptions = true,
        resourceBundle = "floppyconvert.floppyconvert",
        version = "${bundle:application.version}",
        header = "Converts SNES ROM files to floppy disk images.",
        description = {
                "Automates the conversion of SNES ROM files (.sfc, .fig, .swc, .ufo) into FAT12 floppy disk" +
                        " images compatible with retro backup units.",
                "",
                "This tool uses a native ROM splitting engine and FAT12 writer to create .img files.",
                "It supports recursive directory processing and batch conversion."
        },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
                "0:Successful completion",
                "1:General execution error (I/O, missing tools, etc.)",
                "2:Invalid command line arguments"
        },
        footerHeading = "%nSee Also:%n",
        footer = {
                "Project home: ${bundle:application.url}"
        }
)
public class FloppyConvert implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(FloppyConvert.class);

    @Parameters(index = "0", paramLabel = "INPUT",
            description = {
                    "The source ROM file (.sfc, .fig, .swc, .ufo) to convert, or a directory to process.",
                    "If a directory is provided, the tool scans it recursively for ROM files and " +
                            "converts them in batch mode, preserving the directory structure."
            })
    File inputPath;

    @Option(names = {"-o", "--output-dir"},
            description = {
                    "The destination directory for generated floppy images.",
                    "If omitted, defaults apply:",
                    "  - Single file input: Defaults to the current directory ('.').",
                    "  - Directory input: Defaults to a folder named 'output' inside the input directory.",
                    "Necessary subdirectories will be created automatically."
            })
    File outputDir;
    @Option(names = "--format", defaultValue = "FIG",
            description = {
                    "Backup unit format.",
                    "Valid values: ${COMPLETION-CANDIDATES}", // This auto-expands to FIG, SWC, UFO, GD3
                    "Default: ${DEFAULT-VALUE}"
            })
    CopierFormat format;
    private CopierFormat effectiveFormat;

    @Spec
    CommandSpec spec;
    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;


    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new FloppyConvert());
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }

    /**
     * Factory method for creating ROM splitter with native Java implementation.
     * Instantiates NativeRomSplitter with required dependencies.
     *
     * @return configured NativeRomSplitter instance
     */
    private static RomSplitter createRomSplitter() {
        SnesRomReader reader = new SnesRomReader();
        SnesInterleaver interleaver = new SnesInterleaver();
        HeaderGeneratorFactory headerFactory = new HeaderGeneratorFactory();
        return new NativeRomSplitter(reader, interleaver, headerFactory);
    }

    // Package-private: enables test observability without exposing to CLI users
    CopierFormat getEffectiveFormat() {
        return effectiveFormat;
    }

    /**
     * Extracts file extension from a filename, returning empty if no extension exists.
     * Helper method reduces nesting depth for readability.
     *
     * @param fileName the filename to extract from
     * @return Optional containing extension (including dot), or empty if no extension
     */
    private Optional<String> extractExtension(String fileName) {
        // lastIndexOf: handles multiple dots (e.g., "game.backup.swc" → ".swc")
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {  // > 0 rejects hidden files like ".gitignore"
            return Optional.of(fileName.substring(dotIndex));
        }
        return Optional.empty();
    }

    /**
     * Resolves the effective format for single-file conversion by checking if the user explicitly
     * specified --format via Picocli's ParseResult API, or auto-detecting from file extension.
     * <p>
     * Uses ParseResult.hasMatchedOption() to distinguish between explicit --format flag and default
     * value, preserving defaultValue visibility in --help output.
     * <p>
     * For unrecognized extensions (e.g., .sfc), falls back to the format field's default value (FIG)
     * to maintain backward compatibility.
     */
    private CopierFormat resolveEffectiveFormat() {
        // ParseResult API only checks CLI arguments, not config file defaults (project has no config files)
        boolean explicitFormat = spec.commandLine()
                .getParseResult()
                .hasMatchedOption("--format");

        if (explicitFormat) {
            return format;  // User override takes precedence
        }

        // Auto-detect from extension, fallback to FIG for .sfc/unrecognized
        return extractExtension(inputPath.getName())
                .flatMap(CopierFormat::fromFileExtension)
                .orElse(format);
    }

    /**
     * Execute batch ROM processing with concurrent execution and fail-soft error handling.
     * <p>
     * Strategy: Fixed thread pool sized to CPU cores prevents oversubscription.
     * Bounded queue provides backpressure. CallerRunsPolicy throttles submission.
     * Each ROM processed in isolated workspace directory (UUID suffix prevents collisions).
     * <p>
     * Thread pool sizing: coreCount threads prevents CPU oversubscription (ucon64 is CPU-bound).
     * Bounded queue (2 * coreCount) limits memory footprint on large ROM libraries (prevents OOM).
     * CallerRunsPolicy throttles submission when queue full (main thread processes ROM).
     */
    private static void runBatch(Config config) throws IOException {
        Path inputRoot = Paths.get(config.inputDir);
        Path outputRoot = Paths.get(config.outputDir);

        // Dependency injection: instantiate service implementations
        DiskPacker packer = new GreedyDiskPacker();
        RomSplitter splitter = createRomSplitter();
        FloppyImageWriter writer = new Fat12ImageWriter();
        ConversionFacade facade = new DefaultConversionFacade(splitter, writer);
        DiskTemplateFactory templateFactory = new Fat12FormatFactory();
        RomPartNormalizer normalizer = new RomPartNormalizer();
        RomProcessor processor = new RomProcessor(packer, facade, templateFactory, normalizer);

        ExecutorService executor = createBatchExecutor();
        registerShutdownHook(executor);

        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        ConversionObserver observer = createObserver(inputRoot, successCount, failCount);

        try (Stream<Path> stream = Files.walk(inputRoot)) {
            List<Path> romFiles = collectRomFiles(stream, inputRoot);

            for (Path romPath : romFiles) {
                submitRomProcessing(executor, romPath, inputRoot, outputRoot, observer, processor, config.format);
            }
        } catch (UncheckedIOException e) {
            log.error("WARNING: Directory traversal interrupted - {}", e.getCause().getMessage());
            log.error("Batch incomplete: {} successful, {} failed, some directories skipped",
                    successCount.get(), failCount.get());
            return;
        } catch (IOException e) {
            log.error("ERROR: Cannot traverse input directory: {}", e.getMessage());
            return;
        } finally {
            try {
                awaitBatchCompletion(executor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("Batch complete: {} successful, {} failed", successCount.get(), failCount.get());
    }

    /**
     * Create thread pool for batch processing.
     * Fixed pool sized to CPU cores prevents oversubscription.
     */
    private static ExecutorService createBatchExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                coreCount,
                coreCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2 * coreCount),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Register shutdown hook for graceful SIGINT handling.
     * Ensures executor shutdown on interrupt - prevents orphaned tasks.
     */
    private static void registerShutdownHook(ExecutorService executor) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!executor.isShutdown()) {
                log.info("Interrupt received, shutting down gracefully...");
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                }
            }
        }));
    }

    /**
     * Wait for batch completion with timeout.
     * Two-phase shutdown: graceful then forceful.
     */
    private static void awaitBatchCompletion(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }
    }

    /**
     * Collect ROM files from directory tree.
     * Extracts file filtering logic for testability.
     *
     * @param stream    Stream of paths to filter
     * @param inputRoot Root directory for relative path logging
     */
    private static List<Path> collectRomFiles(Stream<Path> stream, Path inputRoot) {
        return stream.filter(path -> {
                    try {
                        return Files.isRegularFile(path);
                    } catch (UncheckedIOException e) {
                        log.warn("WARNING: Cannot access {} - skipping", inputRoot.relativize(path));
                        return false;
                    }
                })
                .filter(SnesRomMatcher::isRom)
                .collect(Collectors.toList());
    }

    /**
     * Create observer for batch processing events.
     * Extracts observer creation logic with thread-safe counters.
     */
    private static ConversionObserver createObserver(Path inputRoot, AtomicInteger successCount, AtomicInteger failCount) {
        return new ConversionObserver() {
            @Override
            public void onSuccess(Path rom, int diskCount) {
                successCount.incrementAndGet();
            }

            @Override
            public void onFailure(Path rom, Exception e) {
                failCount.incrementAndGet();
                log.error("FAILED: {} - {}", inputRoot.relativize(rom), e.getMessage());
            }
        };
    }

    /**
     * Submit ROM processing task to executor.
     * Extracts ROM processing lambda for testability.
     */
    private static void submitRomProcessing(ExecutorService executor, Path romPath,
                                            Path inputRoot, Path outputRoot, ConversionObserver observer,
                                            RomProcessor processor, CopierFormat format) {
        executor.submit(() -> {
            try {
                MDC.put("rom", romPath.getFileName().toString());
                String uniqueSuffix = UUID.randomUUID().toString();
                Path relativePath = inputRoot.relativize(romPath.getParent());
                Path targetBaseDir = outputRoot.resolve(relativePath);
                Files.createDirectories(targetBaseDir);

                observer.onStart(romPath);
                int diskCount = processor.processRom(
                        romPath.toFile(),
                        targetBaseDir,
                        uniqueSuffix,
                        format
                );
                observer.onSuccess(romPath, diskCount);
            } catch (CleanupException e) {
                // Log all suppressed cleanup failures for diagnostics
                log.error("Cleanup failed for {}", romPath, e);
                observer.onFailure(romPath, e);
            } catch (Exception e) {
                observer.onFailure(romPath, e);
            } finally {
                MDC.clear();
            }
            return null;
        });
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

        if (!SnesRomMatcher.isRom(inputPath)) {
            throw new IOException("Input file is not a recognized SNES ROM format: " + inputPath);
        }

        // Dependency injection: instantiate service implementations
        DiskPacker packer = new GreedyDiskPacker();
        RomSplitter splitter = createRomSplitter();
        FloppyImageWriter writer = new Fat12ImageWriter();
        // Facade layer enables dependency inversion - core depends on abstraction
        ConversionFacade facade = new DefaultConversionFacade(splitter, writer);
        DiskTemplateFactory templateFactory = new Fat12FormatFactory();
        RomPartNormalizer normalizer = new RomPartNormalizer();
        RomProcessor processor = new RomProcessor(packer, facade, templateFactory, normalizer);

        // Process ROM with provided outputBase (enables test isolation)
        // Single file processing uses static suffix (no concurrent workspace collisions)
        try {
            processor.processRom(
                    inputPath.toFile(),
                    outputBase,
                    "single",
                    config.format
            );
            log.info("Conversion complete: {}", inputPath.getFileName());
        } catch (CleanupException e) {
            // Log all suppressed cleanup failures for diagnostics
            log.error("Cleanup failed during conversion", e);
            log.error("FAILED: Cleanup encountered {} error(s)", e.getSuppressed().length);
            System.exit(1);
        }
    }

    @Override
    public Integer call() throws Exception {
        if (verbose) {
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(ch.qos.logback.classic.Level.DEBUG);
        }

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
            // Single-file only: batch mode uses explicit format for predictability
            effectiveFormat = resolveEffectiveFormat();
            runSingleFile(
                    new Config(null, outputDir.getAbsolutePath(),
                            effectiveFormat, inputPath.getAbsolutePath()),
                    outputDir.toPath()
            );
        } else {
            runBatch(
                    new Config(inputPath.getAbsolutePath(), outputDir.getAbsolutePath(),
                            format, null)
            );
        }

        return 0;
    }

    /**
     * Bridges Picocli field-based arguments to runBatch/runSingleFile method
     * signatures (avoids cascading changes to RomProcessor integration points).
     */
    private record Config(String inputDir, String outputDir,
                          CopierFormat format, String inputFile) {
    }
}
