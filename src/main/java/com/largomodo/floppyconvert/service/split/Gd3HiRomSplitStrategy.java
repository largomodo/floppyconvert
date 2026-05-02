package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GD3 HiROM and ExHiROM split policy with interleaving. (ref: DL-004)
 * <p>
 * Interleaving is applied internally via SnesInterleaver; because mirrorTo8Mbit subsumes
 * alignment, GD3 HiROM skips padToMbitBoundary in NativeRomSplitter.prepareData. Chunks
 * default to MBIT_8; when interleaved data length <= 16 Mbit (2 MB) chunks are forced
 * to MBIT_4 to trigger X-padding for ucon64 copier-naming compatibility.
 */
public class Gd3HiRomSplitStrategy implements SplitStrategy {

    static final int MBIT_4_BYTES = 512 * 1024;
    static final int MBIT_8_BYTES = 1024 * 1024;
    private static final int SIXTEEN_MBIT_BYTES = 2 * 1024 * 1024;

    private final SnesInterleaver interleaver;

    public Gd3HiRomSplitStrategy(SnesInterleaver interleaver) {
        this.interleaver = interleaver;
    }

    @Override
    public List<File> split(SnesRom rom, byte[] preparedData, HeaderGenerator headerGen,
                            Path workDir, String baseName, FilenameProvider filenameProvider) throws IOException {
        byte[] interleavedData = interleaver.interleave(preparedData, rom.type());

        // Force 4Mbit chunks for GD3 HiROM <= 16Mbit to trigger X-padding for ucon64 copier-naming
        int chunkSize = interleavedData.length <= SIXTEEN_MBIT_BYTES ? MBIT_4_BYTES : MBIT_8_BYTES;
        int chunkCount = (int) Math.ceil((double) interleavedData.length / chunkSize);
        List<File> parts = new ArrayList<>(chunkCount);

        for (int i = 0; i < chunkCount; i++) {
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, interleavedData.length - offset);
            boolean isLastPart = (i == chunkCount - 1);
            byte chunkFlag = (byte) (isLastPart ? 0x00 : 0x40);

            byte[] header = headerGen.generateHeader(rom, length, i, isLastPart, chunkFlag);

            File outputFile = filenameProvider.provide(workDir, baseName, i, chunkCount, rom);
            StandardSplitStrategy.writeChunk(outputFile, header, interleavedData, offset, length);

            parts.add(outputFile);
        }

        return parts;
    }
}
