// Unit tests for UfoHiRomSplitStrategy: chunk sequence matches lookup table, flags, byte output. (ref: DL-004)
package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.service.UfoHiRomChunker;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UfoHiRomSplitStrategyTest {

    private final UfoHiRomSplitStrategy strategy = new UfoHiRomSplitStrategy();

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 12, 20, 32})
    void chunkSequenceMatchesLookupTable(int sizeMbit) throws IOException {
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);

        List<byte[]> capturedFlags = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            capturedFlags.add(new byte[]{flag});
            return new byte[512];
        };

        List<File> parts = strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1) + "gm").toFile());

        List<UfoHiRomChunker.UfoChunk> expectedChunks = UfoHiRomChunker.computeChunks(sizeMbit);
        assertEquals(expectedChunks.size(), parts.size(),
                "Part count must match lookup table for " + sizeMbit + " Mbit");

        for (int i = 0; i < expectedChunks.size(); i++) {
            assertEquals(expectedChunks.get(i).flag(), capturedFlags.get(i)[0],
                    "Chunk flag must match lookup table at index " + i + " for " + sizeMbit + " Mbit");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 12, 20, 32})
    void lastPartFlagIs0x00ForMultiChunkSizes(int sizeMbit) throws IOException {
        // 2 Mbit is a single-chunk size whose lookup flag is 0x10 (not 0x00); excluded here.
        // All multi-chunk sizes in the lookup table end with 0x00.
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);
        List<byte[]> capturedFlags = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            capturedFlags.add(new byte[]{flag});
            return new byte[512];
        };

        strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1) + "gm").toFile());

        assertEquals((byte) 0x00, capturedFlags.get(capturedFlags.size() - 1)[0],
                "Last part flag must be 0x00 for multi-chunk size " + sizeMbit + " Mbit");
    }

    @Test
    void chunkBytesMatchInputDataForKnownPattern() throws IOException {
        // Byte-level verification: data at each chunk offset matches the source array
        int sizeMbit = 4;
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        SnesRom rom = new SnesRom(data, RomType.HiROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);
        // No header so chunk files contain only raw data bytes
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> new byte[0];

        List<File> parts = strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1) + "gm").toFile());

        int offset = 0;
        for (File part : parts) {
            byte[] chunk = Files.readAllBytes(part.toPath());
            for (int b = 0; b < chunk.length; b++) {
                assertEquals(data[offset + b], chunk[b], "Chunk byte at offset " + (offset + b) + " mismatch");
            }
            offset += chunk.length;
        }
        assertEquals(data.length, offset, "Total bytes written must equal input data length");
    }
}
