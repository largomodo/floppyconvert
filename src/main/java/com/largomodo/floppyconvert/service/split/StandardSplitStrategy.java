package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size chunk split policy for FIG, SWC, UFO LoROM, GD3 LoROM, FIG/SWC HiROM. (ref: DL-004)
 * <p>
 * Caller (NativeRomSplitter.prepareData) must pre-pad data to an Mbit boundary before passing
 * to split(). Chunk size is set at construction: MBIT_4 for FIG/SWC/UFO (default), MBIT_8
 * for GD3 LoROM. Last chunk receives chunkFlag 0x00; earlier chunks receive 0x40.
 */
public class StandardSplitStrategy implements SplitStrategy {

    static final int MBIT_4 = 512 * 1024;
    static final int MBIT_8 = 1024 * 1024;

    private final int chunkSize;

    public StandardSplitStrategy() {
        this(MBIT_4);
    }

    public StandardSplitStrategy(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    @Override
    public List<File> split(SnesRom rom, byte[] preparedData, HeaderGenerator headerGen,
                            Path workDir, String baseName, FilenameProvider filenameProvider) throws IOException {
        int chunkCount = (int) Math.ceil((double) preparedData.length / chunkSize);
        List<File> parts = new ArrayList<>(chunkCount);

        for (int i = 0; i < chunkCount; i++) {
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, preparedData.length - offset);
            boolean isLastPart = (i == chunkCount - 1);
            byte chunkFlag = (byte) (isLastPart ? 0x00 : 0x40);

            byte[] header = headerGen.generateHeader(rom, length, i, isLastPart, chunkFlag);

            File outputFile = filenameProvider.provide(workDir, baseName, i, chunkCount, rom);
            writeChunk(outputFile, header, preparedData, offset, length);

            parts.add(outputFile);
        }

        return parts;
    }

    static void writeChunk(File outputFile, byte[] header, byte[] data, int offset, int length) throws IOException {
        try (FileChannel channel = FileChannel.open(outputFile.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            if (header != null && header.length > 0) {
                channel.write(ByteBuffer.wrap(header));
            }
            channel.write(ByteBuffer.wrap(data, offset, length));
        }
    }
}
