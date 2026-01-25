package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.*;
import com.largomodo.floppyconvert.core.domain.DiskPacker;
import com.largomodo.floppyconvert.core.domain.GreedyDiskPacker;
import com.largomodo.floppyconvert.service.FloppyImageWriter;
import com.largomodo.floppyconvert.service.RomSplitter;
import com.largomodo.floppyconvert.service.NativeRomSplitter;
import com.largomodo.floppyconvert.service.fat.Fat12ImageWriter;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
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
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
                "Automates the conversion of SNES ROM files (.sfc, .fig, .smw, .swc, .ufo) into FAT12 floppy disk" +
                        " images compatible with retro backup units.",
                "",
                "This tool uses 'ucon64' to split ROMs and a native FAT12 engine to create .img files.",
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
                "ucon64(1)",
                "",
                "Project home: ${bundle:application.url}"
        }
)
public class FloppyConvert implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(FloppyConvert.class);

    @Parameters(index = "0", paramLabel = "INPUT",
            description = {
                    "The source ROM file (.sfc) to convert, or a directory to process.",
                    "If a directory is provided, the tool scans it recursively for .sfc files and " +
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
        // Native Java FAT12 engine with no external dependencies (comprehensive E2E test coverage validates safety)
        FloppyImageWriter writer = new Fat12ImageWriter();
        DiskTemplateFactory templateFactory = new ResourceDiskTemplateFactory();
        RomPartNormalizer normalizer = new RomPartNormalizer();
        RomProcessor processor = new RomProcessor(packer, splitter, writer, templateFactory, normalizer);

        // Fixed thread pool: one thread per CPU core (no context switching overhead)
        int coreCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = new ThreadPoolExecutor(
                coreCount,              // Core pool size: one thread per CPU core
                coreCount,              // Max pool size: fixed (no elasticity needed)
                0L,                     // Keep-alive: 0 (threads never time out)
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(2 * coreCount),  // Bounded queue: limits memory for queued tasks
                new ThreadPoolExecutor.CallerRunsPolicy()  // Overflow policy: main thread provides backpressure
        );

        // AtomicInteger for thread-safe counters (multiple workers increment concurrently)
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);

        ConversionObserver observer = new ConversionObserver() {
            @Override
            public void onSuccess(Path rom, int diskCount) {
                successCount.incrementAndGet();  // Thread-safe increment
            }

            @Override
            public void onFailure(Path rom, Exception e) {
                failCount.incrementAndGet();  // Thread-safe increment
                log.error("FAILED: {} - {}", inputRoot.relativize(rom), e.getMessage());
            }
        };

        // Shutdown hook for graceful SIGINT handling
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

        try (Stream<Path> stream = Files.walk(inputRoot)) {
            stream.filter(path -> {
                        try {
                            return Files.isRegularFile(path);
                        } catch (UncheckedIOException e) {
                            // File attributes unreadable (broken symlink, permission denied on file)
                            log.warn("WARNING: Cannot access {} - skipping", inputRoot.relativize(path));
                            return false;
                        }
                    })
                    .filter(SnesRomMatcher::isRom)
                    .forEach(romPath -> {
                        executor.submit(() -> {
                            try {
                                MDC.put("rom", romPath.getFileName().toString());
                                // UUID suffix creates thread-unique workspace (prevents ucon64 .1/.2/.3 collisions)
                                String uniqueSuffix = UUID.randomUUID().toString();
                                Path relativePath = inputRoot.relativize(romPath.getParent());
                                Path targetBaseDir = outputRoot.resolve(relativePath);
                                Files.createDirectories(targetBaseDir);

                                observer.onStart(romPath);
                                int diskCount = processor.processRom(
                                        romPath.toFile(),
                                        targetBaseDir,
                                        uniqueSuffix,
                                        config.format
                                );
                                observer.onSuccess(romPath, diskCount);
                            } catch (Exception e) {
                                // Catch all exceptions to prevent worker thread death (batch continues)
                                observer.onFailure(romPath, e);
                            } finally {
                                MDC.clear();
                            }
                            return null;
                        });
                    });
        } catch (UncheckedIOException e) {
            /*
             * IOException during directory traversal (mid-traversal).
             *
             * Occurs when nested subdirectory becomes inaccessible during stream traversal
             * (e.g., permission denied on subdirectory prevents listing contents).
             * Successfully processed ROMs counted, error logged with specific path from exception.
             * Fail-soft: partial batch completion better than aborting already-completed work.
             */
            log.error("WARNING: Directory traversal interrupted - {}", e.getCause().getMessage());
            log.error("Batch incomplete: {} successful, {} failed, some directories skipped",
                    successCount.get(), failCount.get());
            return;
        } catch (IOException e) {
            /*
             * IOException during walk() initialization is fail-fast.
             *
             * Indicates fundamental access problem (input directory unreadable).
             * Fails before any ROM processing begins.
             */
            log.error("ERROR: Cannot traverse input directory: {}", e.getMessage());
            return;
        } finally {
            // Standard two-phase shutdown: graceful (5 min) then forceful
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                    executor.shutdownNow();  // Interrupt in-flight tasks if timeout exceeded
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Batch complete: {} successful, {} failed", successCount.get(), failCount.get());
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
        // Native Java FAT12 engine with no external dependencies (comprehensive E2E test coverage validates safety)
        FloppyImageWriter writer = new Fat12ImageWriter();
        DiskTemplateFactory templateFactory = new ResourceDiskTemplateFactory();
        RomPartNormalizer normalizer = new RomPartNormalizer();
        RomProcessor processor = new RomProcessor(packer, splitter, writer, templateFactory, normalizer);

        // Process ROM with provided outputBase (enables test isolation)
        // Single file processing uses static suffix (no concurrent workspace collisions)
        processor.processRom(
                inputPath.toFile(),
                outputBase,
                "single",
                config.format
        );

        log.info("Conversion complete: {}", inputPath.getFileName());
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
            runSingleFile(
                    new Config(null, outputDir.getAbsolutePath(),
                            format, inputPath.getAbsolutePath()),
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
