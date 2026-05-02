package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.split.FilenameProvider;
import com.largomodo.floppyconvert.service.split.SplitStrategy;
import com.largomodo.floppyconvert.service.split.SplitStrategyFactory;
import com.largomodo.floppyconvert.snes.Gd3HardwareValidator;
import com.largomodo.floppyconvert.snes.HardwareValidator;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.UfoHardwareValidator;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Native Java implementation of ROM splitting for SNES backup unit formats.
 * <p>
 * Processes SNES ROMs using pure Java logic:
 * <ul>
 *   <li>Direct ROM parsing using {@link SnesRomReader}</li>
 *   <li>Format-specific header generation via {@link HeaderGeneratorFactory}</li>
 *   <li>Split policy delegated to SplitStrategy implementations via {@link SplitStrategyFactory}</li>
 * </ul>
 * <p>
 * <b>Splitting Strategy:</b>
 * <ul>
 *   <li>Chunk sizes and interleaving are strategy-determined per format and ROM type</li>
 *   <li>File naming: .1, .2, .3... for standard; .078 suffix for GD3 with 8.3 enforcement</li>
 * </ul>
 */
public class NativeRomSplitter implements RomSplitter {

    private static final Logger logger = LoggerFactory.getLogger(NativeRomSplitter.class);

    private final SnesRomReader reader;
    private final HeaderGeneratorFactory headerFactory;
    private final SplitStrategyFactory strategyFactory;

    /**
     * Constructs a NativeRomSplitter with required dependencies.
     *
     * @param reader          ROM metadata reader
     * @param headerFactory   Factory for format-specific header generators
     * @param strategyFactory Factory for per-format split strategies
     */
    public NativeRomSplitter(SnesRomReader reader,
                             HeaderGeneratorFactory headerFactory, SplitStrategyFactory strategyFactory) {
        this.reader = reader;
        this.headerFactory = headerFactory;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public List<File> split(File inputRom, Path workDir, CopierFormat format) throws IOException {
        if (inputRom == null || !inputRom.exists()) {
            throw new IllegalArgumentException("Input ROM does not exist: " + inputRom);
        }
        if (workDir == null || !workDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Work directory is not a directory: " + workDir);
        }

        SnesRom rom = reader.load(inputRom.toPath());

        HardwareValidator validator = createValidator(format);
        try {
            validator.validate(rom, format);
        } catch (UnsupportedHardwareException e) {
            logger.error("Hardware validation failed: {}", e.getMessage());
            throw e;
        }

        HeaderGenerator headerGen = headerFactory.get(format);
        String baseName = getBaseName(inputRom);

        // prepareData separates data preparation from chunk iteration; Gd3HiRomSplitStrategy
        // handles its own interleaving so receives unprepared rawData. (ref: DL-004)
        // Dependency direction: NativeRomSplitter (service) depends on SplitStrategy (service.split)
        // which depends on snes/format abstractions. SplitStrategy impls must not import core/.
        byte[] preparedData = prepareData(rom, format);

        // Strategy selection: strategyFactory.get(format, rom.type()) dispatches the (format, isHiRom)
        // combination. (ref: DL-004)
        SplitStrategy strategy = strategyFactory.get(format, rom.type());
        FilenameProvider filenameProvider = (wd, bn, partIndex, totalParts, r) ->
                createFilename(wd, bn, format, partIndex, totalParts, r);

        List<File> parts = strategy.split(rom, preparedData, headerGen, workDir, baseName, filenameProvider);
        logger.debug("Split {} into {} parts [{}]", inputRom.getName(), parts.size(), format.name());
        return parts;
    }

    private byte[] prepareData(SnesRom rom, CopierFormat format) {
        if (format == CopierFormat.GD3 && rom.isHiRom()) {
            // Gd3HiRomSplitStrategy applies interleaving internally
            return rom.rawData();
        }
        if (format == CopierFormat.UFO && rom.isHiRom()) {
            return padToUfoHiRomBoundary(rom.rawData());
        }
        return padToMbitBoundary(rom.rawData());
    }

    /**
     * Creates format-specific hardware validator for ROM compatibility check.
     */
    private HardwareValidator createValidator(CopierFormat format) {
        return switch (format) {
            case UFO -> new UfoHardwareValidator();
            case GD3 -> new Gd3HardwareValidator();
            case FIG, SWC -> (rom, fmt) -> { };
        };
    }

    /**
     * Creates a format-specific filename for a split part.
     * <p>
     * Naming conventions:
     * <ul>
     *   <li>FIG/SWC: baseName.1, baseName.2, ...</li>
     *   <li>UFO: baseName.1gm, baseName.2gm, ...</li>
     *   <li>GD3: SF-Code format (SF + Mbit + 3-char name + suffix + .078).
     *       Single-file: underscore-padded to 8 chars (e.g., SF4SUP__.078).
     *       Multi-file: sequence letters A, B, C... (e.g., SF16STRA.078).
     *       HiROM &lt;10Mbit: X-padding inserted before the sequence letter (e.g., {@code SF8TESXA.078}).
     *       HiROM ≥10Mbit: no X-padding (e.g., {@code SF16CHRA.078}).
     *       Follows ucon64 {@code snes_gd_make_names} logic for hardware compatibility.</li>
     * </ul>
     */
    private File createFilename(Path workDir, String baseName, CopierFormat format, int partIndex, int totalParts, SnesRom rom) {
        String filename = switch (format) {
            case FIG, SWC -> baseName + "." + (partIndex + 1);
            case UFO -> baseName + "." + (partIndex + 1) + "gm";
            case GD3 -> {
                // GD3 SF-Code format: SF + Mbit + 3-char name + suffix
                int sizeMbit = rom.rawData().length / SnesConstants.MBIT;
                String cleanName = baseName.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);

                String shortName;
                if (cleanName.length() >= 3) {
                    shortName = cleanName.substring(0, 3);
                } else {
                    shortName = String.format("%-3s", cleanName).replace(' ', '_');
                }

                String sfBase = "SF" + sizeMbit + shortName;
                StringBuilder sb = new StringBuilder(sfBase);

                if (totalParts == 1) {
                    // Single-file names padded to 8 chars with underscores
                    while (sb.length() < 8) {
                        sb.append('_');
                    }
                } else {
                    // ucon64 snes_gd_make_names inserts X for HiROM <10Mbit (hardware firmware checks this pattern)
                    if (rom.isHiRom() && sizeMbit < 10) {
                        sb.append('X');
                    }
                    sb.append((char) ('A' + partIndex));
                }

                yield sb + ".078";
            }
        };
        return workDir.resolve(filename).toFile();
    }

    /**
     * Extracts the base filename (without extension) from a ROM file.
     */
    private String getBaseName(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(0, lastDot) : name;
    }

    /**
     * Pads ROM data to the nearest power-of-2 Mbit boundary {2, 4, 8, 16, 32} using data mirroring.
     * If data is already at a boundary, returns it unmodified.
     * ROMs smaller than 2 Mbit are padded to 2 Mbit.
     * Mirroring repeats source bytes cyclically to fill the padding gap.
     * Used by FIG, SWC, UFO LoROM, and GD3 LoROM. UFO HiROM uses
     * {@link #padToUfoHiRomBoundary(byte[])} instead.
     */
    private byte[] padToMbitBoundary(byte[] data) {
        int[] boundaries = {2 * SnesConstants.MBIT, 4 * SnesConstants.MBIT, 8 * SnesConstants.MBIT,
                16 * SnesConstants.MBIT, 32 * SnesConstants.MBIT};
        int targetSize = boundaries[boundaries.length - 1];
        for (int boundary : boundaries) {
            if (data.length <= boundary) {
                targetSize = boundary;
                break;
            }
        }
        if (data.length == targetSize) {
            return data;
        }
        return mirrorTo(data, targetSize);
    }

    /**
     * Pads ROM data to the nearest UfoHiRomChunker-supported size {2, 4, 12, 20, 32} Mbit using data mirroring.
     * If data is already at a supported size, returns it unmodified.
     * ROMs smaller than 2 Mbit are padded to 2 Mbit.
     * Supported sizes match {@link UfoHiRomChunker#computeChunks(int)} input domain;
     * power-of-2 padding would produce unsupported sizes (e.g., 8, 16 Mbit) causing
     * {@code IllegalArgumentException}. UFO HiROM uses irregular chunk sizes defined by the
     * {@link UfoHiRomChunker#computeChunks(int)} lookup table; power-of-2 padding would produce
     * unsupported sizes (e.g., 8, 16 Mbit) that the chunker does not recognize.
     */
    private byte[] padToUfoHiRomBoundary(byte[] data) {
        int[] boundaries = {2 * SnesConstants.MBIT, 4 * SnesConstants.MBIT, 12 * SnesConstants.MBIT,
                20 * SnesConstants.MBIT, 32 * SnesConstants.MBIT};
        int targetSize = boundaries[boundaries.length - 1];
        for (int boundary : boundaries) {
            if (data.length <= boundary) {
                targetSize = boundary;
                break;
            }
        }
        if (data.length == targetSize) {
            return data;
        }
        return mirrorTo(data, targetSize);
    }

    /**
     * Fills a new byte array of targetSize by mirroring source data cyclically.
     */
    private byte[] mirrorTo(byte[] source, int targetSize) {
        byte[] result = new byte[targetSize];
        for (int i = 0; i < targetSize; i++) {
            result[i] = source[i % source.length];
        }
        return result;
    }
}
