// Unit tests for Gd3HiRomSplitStrategy: chunk size threshold (<=16Mbit forces MBIT_4),
// byte-level interleave output verification. (ref: DL-004)
package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesInterleaver;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Gd3HiRomSplitStrategyTest {

    private final SnesInterleaver realInterleaver = new SnesInterleaver();
    private final Gd3HiRomSplitStrategy strategy = new Gd3HiRomSplitStrategy(realInterleaver);

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = {8, 16})
    void smallHiRomForcesChunkSize4Mbit(int sizeMbit) throws IOException {
        // GD3 HiROM <= 16Mbit forces MBIT_4 chunks to trigger X-padding for ucon64 copier naming
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);

        List<Integer> capturedPartSizes = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            capturedPartSizes.add(partSize);
            return new byte[512];
        };

        strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());

        for (int partSize : capturedPartSizes) {
            assertTrue(partSize <= Gd3HiRomSplitStrategy.MBIT_4_BYTES,
                    "Part size must not exceed MBIT_4 for <= 16Mbit HiROM, was: " + partSize);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 32})
    void largeHiRomUsesChunkSize8Mbit(int sizeMbit) throws IOException {
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);

        List<Integer> capturedPartSizes = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            capturedPartSizes.add(partSize);
            return new byte[512];
        };

        strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());

        // First N-1 parts should be MBIT_8; last may be smaller
        for (int i = 0; i < capturedPartSizes.size() - 1; i++) {
            assertEquals(Gd3HiRomSplitStrategy.MBIT_8_BYTES, capturedPartSizes.get(i),
                    "Non-last part size must be MBIT_8 for > 16Mbit HiROM");
        }
    }

    @Test
    void chunkBytesMatchInterleavedDataForKnownPattern() throws IOException {
        // Byte-level verification: written chunks concatenate to the interleaved data produced by the interleaver
        int sizeMbit = 8;
        byte[] rawData = new byte[sizeMbit * SnesConstants.MBIT];
        for (int i = 0; i < rawData.length; i++) {
            rawData[i] = (byte) (i & 0xFF);
        }
        SnesRom rom = new SnesRom(rawData, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);
        // No header so chunk files contain only interleaved data bytes
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> new byte[0];

        List<File> parts = strategy.split(rom, rawData, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());

        byte[] expectedInterleaved = realInterleaver.interleave(rawData, RomType.HiROM);

        int offset = 0;
        for (File part : parts) {
            byte[] chunk = Files.readAllBytes(part.toPath());
            for (int b = 0; b < chunk.length; b++) {
                assertEquals(expectedInterleaved[offset + b], chunk[b],
                        "Chunk byte at offset " + (offset + b) + " mismatch after interleaving");
            }
            offset += chunk.length;
        }
        assertEquals(expectedInterleaved.length, offset, "Total bytes written must equal interleaved data length");
    }
}
