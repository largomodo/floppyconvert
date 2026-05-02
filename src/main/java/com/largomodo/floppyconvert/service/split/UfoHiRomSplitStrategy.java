package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.service.UfoHiRomChunker;
import com.largomodo.floppyconvert.service.UfoHiRomChunker.UfoChunk;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * UFO HiROM split policy using irregular chunk sizes from the UfoHiRomChunker lookup table. (ref: DL-004)
 * <p>
 * Caller (NativeRomSplitter.prepareData) must pre-pad data to a supported size
 * ({2, 4, 12, 20, 32} Mbit) before invoking split(). Chunk flags are sourced from
 * UfoHiRomChunker.computeChunks per the ucon64 size_to_partsizes table.
 */
public class UfoHiRomSplitStrategy implements SplitStrategy {

    @Override
    public List<File> split(SnesRom rom, byte[] preparedData, HeaderGenerator headerGen,
                            Path workDir, String baseName, FilenameProvider filenameProvider) throws IOException {
        int totalSizeMbit = preparedData.length / SnesConstants.MBIT;
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(totalSizeMbit);
        List<File> parts = new ArrayList<>(chunks.size());

        int offset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            UfoChunk chunk = chunks.get(i);
            int chunkBytes = chunk.sizeMbit() * SnesConstants.MBIT;
            int length = Math.min(chunkBytes, preparedData.length - offset);
            boolean isLastPart = (i == chunks.size() - 1);

            byte[] header = headerGen.generateHeader(rom, length, i, isLastPart, chunk.flag());

            File outputFile = filenameProvider.provide(workDir, baseName, i, chunks.size(), rom);
            StandardSplitStrategy.writeChunk(outputFile, header, preparedData, offset, length);

            parts.add(outputFile);
            offset += length;
        }

        return parts;
    }
}
