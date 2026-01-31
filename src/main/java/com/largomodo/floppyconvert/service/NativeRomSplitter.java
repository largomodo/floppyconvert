package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.CopierFormat;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import com.largomodo.floppyconvert.snes.header.HeaderGeneratorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native Java implementation of ROM splitting for SNES backup unit formats.
 * <p>
 * Processes SNES ROMs using pure Java logic:
 * <ul>
 *   <li>Direct ROM parsing using {@link SnesRomReader}</li>
 *   <li>Conditional interleaving for GD3 HiROM ROMs via {@link SnesInterleaver}</li>
 *   <li>Format-specific header generation via {@link HeaderGeneratorFactory}</li>
 *   <li>Efficient I/O using {@link FileChannel}</li>
 * </ul>
 * <p>
 * <b>Splitting Strategy:</b>
 * <ul>
 *   <li>Standard formats (FIG/SWC/UFO): 4 Mbit (512KB) chunks</li>
 *   <li>Game Doctor (GD3): 8 Mbit (1MB) chunks</li>
 *   <li>File naming: .1, .2, .3... for standard; .078 suffix for GD3 with 8.3 enforcement</li>
 * </ul>
 */
public class NativeRomSplitter implements RomSplitter {

    private static final Logger logger = LoggerFactory.getLogger(NativeRomSplitter.class);

    private static final int MBIT_4 = 512 * 1024;  // 4 Mbit = 512 KB
    private static final int MBIT_8 = 1024 * 1024; // 8 Mbit = 1 MB

    private final SnesRomReader reader;
    private final SnesInterleaver interleaver;
    private final HeaderGeneratorFactory headerFactory;

    /**
     * Constructs a NativeRomSplitter with required dependencies.
     *
     * @param reader        ROM metadata reader
     * @param interleaver   Interleaver for GD3 HiROM transformation
     * @param headerFactory Factory for format-specific header generators
     */
    public NativeRomSplitter(SnesRomReader reader, SnesInterleaver interleaver, HeaderGeneratorFactory headerFactory) {
        this.reader = reader;
        this.interleaver = interleaver;
        this.headerFactory = headerFactory;
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

        byte[] data = rom.rawData();
        if (format == CopierFormat.GD3 && rom.isHiRom()) {
            logger.debug("Applying GD3 HiROM interleaving for: {}", inputRom.getName());
            data = interleaver.interleave(data);
        }

        int chunkSize = (format == CopierFormat.GD3) ? MBIT_8 : MBIT_4;
        int chunkCount = (int) Math.ceil((double) data.length / chunkSize);

        HeaderGenerator headerGen = headerFactory.get(format);
        String baseName = getBaseName(inputRom);

        List<File> parts = new ArrayList<>(chunkCount);

        for (int i = 0; i < chunkCount; i++) {
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, data.length - offset);
            boolean isLastPart = (i == chunkCount - 1);

            byte[] header = headerGen.generateHeader(rom, length, i, isLastPart);

            File outputFile = createFilename(workDir, baseName, format, i, chunkCount, rom);
            writeChunk(outputFile, header, data, offset, length);

            parts.add(outputFile);
            logger.debug("Created split part {}/{}: {}", i + 1, chunkCount, outputFile.getName());
        }

        return parts;
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
     *       HiROM ≤16Mbit: X-padding before sequence letter (e.g., SF16CHRXA.078).
     *       Follows ucon64 `snes_gd_make_names` logic for hardware compatibility.</li>
     * </ul>
     */
    private File createFilename(Path workDir, String baseName, CopierFormat format, int partIndex, int totalParts, SnesRom rom) {
        String filename;

        switch (format) {
            case FIG, SWC -> filename = baseName + "." + (partIndex + 1);
            case UFO -> {
                String ext = (partIndex + 1) + "gm";
                filename = baseName + "." + ext;
            }
            case GD3 -> {
                // GD3 SF-Code format: SF + Mbit + 3-char name + suffix
                int sizeMbit = rom.rawData().length / 131072;
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
                    // ucon64 snes_gd_make_names inserts X for HiROM ≤16Mbit (hardware firmware checks this pattern)
                    if (rom.isHiRom() && sizeMbit <= 16) {
                        sb.append('X');
                    }
                    sb.append((char) ('A' + partIndex));
                }

                filename = sb.toString() + ".078";
            }
            default -> throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return workDir.resolve(filename).toFile();
    }

    /**
     * Writes a ROM chunk to disk with optional header.
     *
     * @param outputFile Target file
     * @param header     512-byte header (may be empty for headerless parts)
     * @param data       ROM data buffer
     * @param offset     Offset into data buffer
     * @param length     Number of bytes to write
     * @throws IOException if write fails
     */
    private void writeChunk(File outputFile, byte[] header, byte[] data, int offset, int length) throws IOException {
        try (FileChannel channel = FileChannel.open(outputFile.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            if (header != null && header.length > 0) {
                channel.write(ByteBuffer.wrap(header));
            }

            ByteBuffer dataBuffer = ByteBuffer.wrap(data, offset, length);
            channel.write(dataBuffer);
        }
    }

    /**
     * Extracts the base filename (without extension) from a ROM file.
     */
    private String getBaseName(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot > 0) ? name.substring(0, lastDot) : name;
    }
}
