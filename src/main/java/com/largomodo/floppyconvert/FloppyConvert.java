package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.core.BatchRunner;
import com.largomodo.floppyconvert.core.ConversionFacade;
import com.largomodo.floppyconvert.core.RomPartNormalizer;
import com.largomodo.floppyconvert.core.RomProcessor;
import com.largomodo.floppyconvert.core.domain.GreedyDiskPacker;
import com.largomodo.floppyconvert.core.workspace.CleanupException;
import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.ConversionServiceFactory;
import com.largomodo.floppyconvert.service.DefaultConversionFacade;
import com.largomodo.floppyconvert.service.NativeConversionServiceFactory;
import com.largomodo.floppyconvert.service.SnesFailureClassifier;
import com.largomodo.floppyconvert.util.SnesRomMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

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
                "It supports recursive directory processing and batch conversion.",
                "",
                "GD3 (Game Doctor) output is byte-identical to uCON64 2.2.3 for every ROM uCON64 converts: part names are" +
                        " SF<Mbit><name>.078 with a part letter (after an X or '_' below 10 Mbit), a HiROM up to 16 Mbit is split" +
                        " in two halves and every other ROM in 8 Mbit parts. A file that already is a Game Doctor file is split as it is.",
                "",
                "In batch mode a ROM that fails is logged to failures.log as 'FAILED: <path> - <CATEGORY> - <message>'," +
                        " CATEGORY being CHECKSUM, SIZE, HEADER or OTHER."
        },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
                "0:Successful completion",
                "1:Execution error (I/O, unsupported ROM), or at least one ROM failed in batch mode",
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
     * Composition root: the one place that wires the conversion pipeline together.
     * Both single-file and batch mode use it.
     */
    private static RomProcessor createProcessor() {
        ConversionServiceFactory serviceFactory = new NativeConversionServiceFactory();
        ConversionFacade facade = new DefaultConversionFacade(
                serviceFactory.createRomSplitter(),
                serviceFactory.createFloppyImageWriter());
        return new RomProcessor(
                new GreedyDiskPacker(),
                facade,
                serviceFactory.createDiskTemplateFactory(),
                new RomPartNormalizer());
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
     * Converts one ROM, writing its images directly into the output directory.
     *
     * @return process exit code
     * @throws IOException if the input is not a ROM or the conversion fails
     */
    private int runSingleFile(CopierFormat format) throws IOException {
        Path rom = inputPath.toPath();
        if (!SnesRomMatcher.isRom(rom)) {
            throw new IOException("Input file is not a recognized SNES ROM format: " + rom.toAbsolutePath());
        }

        try {
            // A fixed workspace suffix is enough: nothing else converts concurrently in this mode.
            createProcessor().processRom(inputPath, outputDir.toPath(), "single", format, true);
            return 0;
        } catch (CleanupException e) {
            // Logged with the exception so the suppressed cleanup failures reach the log file
            log.error("Cleanup failed during conversion", e);
            log.error("FAILED: Cleanup encountered {} error(s)", e.getSuppressed().length);
            return 1;
        }
    }

    /**
     * Converts every ROM under the input directory. Exit code 1 when any ROM failed
     * or the directory could not be walked completely.
     */
    private int runBatch() {
        // Batch mode classifies failures with the snes-aware classifier; single-file mode has no log line to classify.
        BatchRunner.Result result = new BatchRunner(createProcessor(), new SnesFailureClassifier())
                .run(inputPath.toPath(), outputDir.toPath(), format);
        return result.isClean() ? 0 : 1;
    }

    @Override
    public Integer call() throws Exception {
        // Four named phases: each addresses one distinct concern (logging, validation,
        // output-dir defaulting, dispatch). (ref: DL-006)
        // Field injection (verbose, inputPath, outputDir, spec, format) completes before call()
        // executes per Picocli's contract; all four methods read the same injected fields.
        configureLogging();
        validateInput();
        resolveOutputDirectory();
        return dispatchProcessing();
    }

    /**
     * Relaxes root logger level and STDOUT ThresholdFilter to DEBUG when --verbose is set.
     * Both gates must be relaxed together; ThresholdFilter is registered in reflect-config.json
     * for GraalVM native-image compatibility.
     */
    private void configureLogging() {
        if (verbose) {
            // Root logger level controls which events are generated.
            // The STDOUT appender ThresholdFilter(INFO) is a second gate that blocks
            // DEBUG events from reaching the console even when root level is DEBUG.
            // Both must be relaxed together for --verbose to produce visible console output.
            // ThresholdFilter is already registered in reflect-config.json for native-image.
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(ch.qos.logback.classic.Level.DEBUG);
            ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> stdoutAppender =
                    root.getAppender("STDOUT");
            if (stdoutAppender instanceof ch.qos.logback.core.UnsynchronizedAppenderBase) {
                for (ch.qos.logback.core.filter.Filter<?> filter :
                        stdoutAppender.getCopyOfAttachedFiltersList()) {
                    if (filter instanceof ch.qos.logback.classic.filter.ThresholdFilter tf) {
                        tf.setLevel("DEBUG");
                        break;
                    }
                }
            }
        }
    }

    private void validateInput() {
        // Existence checked before readability: avoids a confusing "not readable" error
        // when the real problem is the file does not exist.
        if (!inputPath.exists()) {
            throw new ParameterException(spec.commandLine(),
                    "Input path does not exist: " + inputPath.getAbsolutePath());
        }
        if (!inputPath.canRead()) {
            throw new ParameterException(spec.commandLine(),
                    "Input path is not readable (check permissions): " + inputPath.getAbsolutePath());
        }
    }

    private void resolveOutputDirectory() throws IOException {
        // Default: file input -> current working directory; directory input -> <input>/output.
        // outputDir field is mutated here; dispatchProcessing reads the resolved value.
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
    }

    private int dispatchProcessing() throws IOException {
        // Single-file mode infers the format from the file extension unless --format is given.
        // Batch mode applies --format (default FIG) to every ROM, whatever its extension.
        if (inputPath.isFile()) {
            effectiveFormat = resolveEffectiveFormat();
            return runSingleFile(effectiveFormat);
        }
        return runBatch();
    }
}
