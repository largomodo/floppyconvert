package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GD3 split policy over already padded and interleaved bytes. (ref: DL-007)
 * <p>
 * Port of ucon64 {@code snes_split_gd3} (snes.c 2445): a HiROM image of up to 16 Mbit is cut into two halves,
 * every other image into 8 Mbit parts plus the remainder. Only part 0 carries a header; the header generator
 * returns an empty array for the other parts.
 */
public class Gd3SplitStrategy implements SplitStrategy {

    private static final int MBIT_8_BYTES = 8 * SnesConstants.MBIT;
    private static final int MBIT_16_BYTES = 16 * SnesConstants.MBIT;

    @Override
    public List<File> split(SnesRom rom, byte[] preparedData, HeaderGenerator headerGen,
                            Path workDir, String baseName, FilenameProvider filenameProvider) throws IOException {
        int[] lengths = partLengths(preparedData.length, rom.isHiRom());
        List<File> parts = new ArrayList<>(lengths.length);

        int offset = 0;
        for (int i = 0; i < lengths.length; i++) {
            int length = lengths[i];
            boolean isLastPart = (i == lengths.length - 1);
            byte chunkFlag = (byte) (isLastPart ? 0x00 : 0x40);

            byte[] header = headerGen.generateHeader(rom, length, i, isLastPart, chunkFlag);

            File outputFile = filenameProvider.provide(workDir, baseName, i, lengths.length, rom);
            StandardSplitStrategy.writeChunk(outputFile, header, preparedData, offset, length);

            parts.add(outputFile);
            offset += length;
        }
        return parts;
    }

    /**
     * A HiROM image of up to 16 Mbit becomes two halves;
     * everything else is cut into 8 Mbit parts with the remainder last.
     *
     * @return byte length of each part, in order
     */
    private static int[] partLengths(int total, boolean hiRom) {
        if (hiRom && total <= MBIT_16_BYTES) {
            return new int[]{total / 2, total - total / 2};
        }
        int fullParts = total / MBIT_8_BYTES;
        int surplus = total % MBIT_8_BYTES;
        int[] lengths = new int[fullParts + (surplus > 0 ? 1 : 0)];
        for (int i = 0; i < fullParts; i++) {
            lengths[i] = MBIT_8_BYTES;
        }
        if (surplus > 0) {
            lengths[fullParts] = surplus;
        }
        return lengths;
    }
}
