package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.service.split.FilenameProvider;
import com.largomodo.floppyconvert.service.split.SplitStrategy;
import com.largomodo.floppyconvert.service.split.SplitStrategyFactory;
import com.largomodo.floppyconvert.service.split.UfoHiRomChunker;
import com.largomodo.floppyconvert.snes.Gd3HardwareValidator;
import com.largomodo.floppyconvert.snes.Gd3Layout;
import com.largomodo.floppyconvert.snes.HardwareValidator;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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
 *   <li>File naming: .1, .2, .3... for standard; SF-code names with .078 suffix for GD3 (ucon64 naming)</li>
 * </ul>
 */
public class NativeRomSplitter implements RomSplitter {

    private static final Logger logger = LoggerFactory.getLogger(NativeRomSplitter.class);

    private static final int GD3_HEADER_SIZE = 512;

    /**
     * Input to the split strategy: the bytes to cut, the header source and, for GD3, the size the part names show.
     */
    private record Prepared(byte[] data, HeaderGenerator headerGen, int gd3NameBytes) {
    }

    private final SnesRomReader reader;
    private final HeaderGeneratorFactory headerFactory;
    private final SplitStrategyFactory strategyFactory;
    private final SnesInterleaver interleaver;

    /**
     * Constructs a NativeRomSplitter with required dependencies.
     *
     * @param reader          ROM metadata reader
     * @param headerFactory   Factory for format-specific header generators
     * @param strategyFactory Factory for per-format split strategies
     * @param interleaver     GD3 padding and interleaving, applied in prepareData (ref: DL-007)
     */
    public NativeRomSplitter(SnesRomReader reader, HeaderGeneratorFactory headerFactory,
                             SplitStrategyFactory strategyFactory, SnesInterleaver interleaver) {
        this.reader = reader;
        this.headerFactory = headerFactory;
        this.strategyFactory = strategyFactory;
        this.interleaver = interleaver;
    }

    @Override
    public List<File> split(File inputRom, Path workDir, CopierFormat format) throws IOException {
        if (inputRom == null || !inputRom.exists()) {
            throw new IllegalArgumentException("Input ROM does not exist: " + inputRom);
        }
        if (workDir == null || !workDir.toFile().isDirectory()) {
            throw new IllegalArgumentException("Work directory is not a directory: " + workDir);
        }

        SnesRom rom = reader.load(inputRom.toPath(), format);

        HardwareValidator validator = createValidator(format);
        try {
            validator.validate(rom, format);
        } catch (UnsupportedHardwareException e) {
            logger.error("Hardware validation failed: {}", e.getMessage());
            throw e;
        }

        String baseName = getBaseName(inputRom);

        // prepareData separates data preparation from chunk iteration: padding and interleaving happen here
        // for every format, so a split strategy is a pure policy over prepared bytes. (ref: DL-004, DL-007)
        // Dependency direction: NativeRomSplitter (service) depends on SplitStrategy (service.split)
        // which depends on snes/format abstractions. SplitStrategy impls must not import core/.
        Prepared prepared = prepare(inputRom, rom, format);

        // Strategy selection: strategyFactory.get(format, rom.type()) dispatches the (format, isHiRom)
        // combination. (ref: DL-004)
        SplitStrategy strategy = strategyFactory.get(format, rom.type());
        FilenameProvider filenameProvider = format == CopierFormat.GD3
                ? gd3Filenames(inputRom.getName(), prepared.gd3NameBytes())
                : (wd, bn, partIndex, totalParts, r) -> createFilename(wd, bn, format, partIndex, totalParts);

        List<File> parts = strategy.split(rom, prepared.data(), prepared.headerGen(), workDir, baseName, filenameProvider);
        logger.debug("Split {} into {} parts [{}]", inputRom.getName(), parts.size(), format.name());
        return parts;
    }

    private Prepared prepare(File inputRom, SnesRom rom, CopierFormat format) throws IOException {
        // Only GD3 output passes a Game Doctor input through; other formats treat it as a headered ROM.
        if (format == CopierFormat.GD3 && rom.gd3Headered()) {
            return passThroughGd3(inputRom);
        }
        int gd3NameBytes = format == CopierFormat.GD3 ? Gd3Layout.of(rom).sizeMbitForName() * SnesConstants.MBIT : 0;
        return new Prepared(prepareData(rom, format), headerFactory.get(format), gd3NameBytes);
    }

    /**
     * An input that already is a Game Doctor file is split as it is: its own 512-byte header goes on part A and its
     * body is cut without re-detection or re-interleaving. This is what {@code ucon64 -s} does, and it reproduces
     * the hardware-verified disk sets. The file is read again because SnesRom holds the linear body only; carrying
     * the 512 header bytes in it would break the invariant that {@code rawData} is linear.
     */
    private Prepared passThroughGd3(File inputRom) throws IOException {
        byte[] file = Files.readAllBytes(inputRom.toPath());
        byte[] sourceHeader = Arrays.copyOf(file, GD3_HEADER_SIZE);
        byte[] body = Arrays.copyOfRange(file, GD3_HEADER_SIZE, file.length);
        HeaderGenerator sourceHeaderGen = (rom, partSize, partIndex, isLastPart, chunkFlag) ->
                partIndex == 0 ? sourceHeader : new byte[0];
        return new Prepared(body, sourceHeaderGen, body.length);
    }

    private byte[] prepareData(SnesRom rom, CopierFormat format) {
        if (format == CopierFormat.GD3) {
            // The layout decides padding and interleave; LoROM comes back unpadded
            return interleaver.interleave(rom.rawData(), Gd3Layout.of(rom));
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
     * Creates a filename for a split part of a non-GD3 format.
     * <ul>
     *   <li>FIG/SWC: baseName.1, baseName.2, ...</li>
     *   <li>UFO: baseName.1gm, baseName.2gm, ...</li>
     * </ul>
     * GD3 names depend on the full source file name and the image size; see {@link #gd3Filenames}.
     */
    private File createFilename(Path workDir, String baseName, CopierFormat format, int partIndex, int totalParts) {
        String filename = switch (format) {
            case FIG, SWC -> baseName + "." + (partIndex + 1);
            case UFO -> baseName + "." + (partIndex + 1) + "gm";
            case GD3 -> throw new IllegalArgumentException("GD3 part names come from gd3Filenames");
        };
        return workDir.resolve(filename).toFile();
    }

    /**
     * Part names for GD3: a literal port of ucon64 {@code gd_make_name} (snes.c 1471) and the naming half of
     * {@code snes_gd_make_names} (snes.c 2401), so a set built here has the names ucon64 gives it.
     *
     * @param sourceFileName full input file name including its extension
     * @param sizeBytes      size of the image being split; its Mbit count is the size digits of the name
     */
    private static FilenameProvider gd3Filenames(String sourceFileName, int sizeBytes) {
        return (workDir, baseName, partIndex, totalParts, rom) ->
                workDir.resolve(gd3PartName(sourceFileName, sizeBytes, rom.isHiRom(), partIndex, totalParts) + ".078").toFile();
    }

    // Package-private so the naming rules are testable without a split.
    static String gd3PartName(String sourceFileName, int sizeBytes, boolean hiRom, int partIndex, int totalParts) {
        boolean below10Mbit = sizeBytes < 10 * SnesConstants.MBIT;

        // "sf" + size digits + first 3 characters of the file name, cut to 7 characters
        String head = sourceFileName.length() > 3 ? sourceFileName.substring(0, 3) : sourceFileName;
        String name = cut("sf" + (sizeBytes / SnesConstants.MBIT) + head + "__", 7);
        // A source already named sf<size>... keeps its own name (this is how ucon64 re-splits its own output)
        if (startsWithIgnoreCase(name, sourceFileName, below10Mbit ? 3 : 4)) {
            name = cut(sourceFileName + "___", 7);
        }
        name = cut(name, below10Mbit ? 6 : 7);

        // Spaces and dots after "sf" and the first size digit would break the 8.3 name
        StringBuilder stem = new StringBuilder(name);
        for (int i = stem.length() - 1; i >= 3; i--) {
            if (stem.charAt(i) == ' ' || stem.charAt(i) == '.') {
                stem.setCharAt(i, '_');
            }
        }
        String upper = stem.toString().toUpperCase(Locale.ROOT);
        if (totalParts == 1) {
            return upper;
        }

        // HiROM below 10 Mbit pads the 6-character stem with X before the part letter (the firmware checks for it).
        // LoROM below 10 Mbit with several parts (9 Mbit only) gets '_' instead: ucon64 writes the letter after
        // the end of the string, so both parts would collide and the second would overwrite the first. The '_' at
        // index 6 is the one name that differs from ucon64's output; it keeps the part set usable.
        String separator = below10Mbit ? (hiRom ? "X" : "_") : "";
        return upper + separator + (char) ('A' + partIndex);
    }

    private static String cut(String s, int maxLength) {
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    // strnicmp semantics: comparison stops at the end of both strings, a shorter string only matches an equally short one
    private static boolean startsWithIgnoreCase(String a, String b, int count) {
        for (int i = 0; i < count; i++) {
            char x = i < a.length() ? Character.toLowerCase(a.charAt(i)) : 0;
            char y = i < b.length() ? Character.toLowerCase(b.charAt(i)) : 0;
            if (x != y) {
                return false;
            }
            if (x == 0) {
                return true;
            }
        }
        return true;
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
     * Used by FIG, SWC and UFO LoROM; GD3 pads through its Gd3Layout. UFO HiROM uses
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
