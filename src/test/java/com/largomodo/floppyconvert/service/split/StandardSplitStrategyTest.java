// Unit tests for StandardSplitStrategy: chunk count, flag sequence, byte-level output. (ref: DL-004)
package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesConstants;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StandardSplitStrategyTest {

    private final StandardSplitStrategy strategy = new StandardSplitStrategy();

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16, 32})
    void chunkCountIsCorrectForStandardSizes(int sizeMbit) throws IOException {
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        SnesRom rom = createLoRom(data);
        HeaderGenerator headerGen = mockHeader();
        int expectedChunks = (int) Math.ceil((double) data.length / StandardSplitStrategy.MBIT_4);

        List<File> parts = strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1)).toFile());

        assertEquals(expectedChunks, parts.size());
    }

    @Test
    void allChunksExceptLastHaveFlag0x40() throws IOException {
        byte[] data = new byte[8 * SnesConstants.MBIT];
        SnesRom rom = createLoRom(data);
        List<byte[]> capturedFlags = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            capturedFlags.add(new byte[]{flag});
            return new byte[512];
        };

        strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1)).toFile());

        int lastIdx = capturedFlags.size() - 1;
        for (int i = 0; i < lastIdx; i++) {
            assertEquals((byte) 0x40, capturedFlags.get(i)[0], "Non-last chunk flag must be 0x40");
        }
        assertEquals((byte) 0x00, capturedFlags.get(lastIdx)[0], "Last chunk flag must be 0x00");
    }

    @Test
    void lastChunkMayBeSmallerThanMbit4() throws IOException {
        byte[] data = new byte[6 * SnesConstants.MBIT];
        SnesRom rom = createLoRom(data);
        HeaderGenerator headerGen = mockHeader();

        List<File> parts = strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1)).toFile());

        assertEquals(2, parts.size(), "6 Mbit splits into 2 chunks at 4 Mbit each");
        assertTrue(parts.get(1).length() <= StandardSplitStrategy.MBIT_4 + 512,
                "Last chunk plus header should not exceed MBIT_4 + header size");
    }

    @Test
    void chunkBytesMatchInputDataForKnownPattern() throws IOException {
        // Byte-level verification: each byte in the written chunk matches the source data at the correct offset
        int sizeMbit = 8;
        byte[] data = new byte[sizeMbit * SnesConstants.MBIT];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        SnesRom rom = createLoRom(data);
        // No header so chunk files contain only raw data bytes
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> new byte[0];

        List<File> parts = strategy.split(rom, data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve(bn + "." + (idx + 1)).toFile());

        for (int p = 0; p < parts.size(); p++) {
            byte[] chunk = Files.readAllBytes(parts.get(p).toPath());
            int expectedOffset = p * StandardSplitStrategy.MBIT_4;
            int expectedLength = Math.min(StandardSplitStrategy.MBIT_4, data.length - expectedOffset);
            assertEquals(expectedLength, chunk.length, "Chunk " + p + " length mismatch");
            for (int b = 0; b < expectedLength; b++) {
                assertEquals(data[expectedOffset + b], chunk[b],
                        "Chunk " + p + " byte " + b + " offset mismatch");
            }
        }
    }

    private SnesRom createLoRom(byte[] data) {
        return new SnesRom(data, RomType.LoROM, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    private HeaderGenerator mockHeader() {
        HeaderGenerator hg = mock(HeaderGenerator.class);
        try {
            when(hg.generateHeader(any(), anyInt(), anyInt(), anyBoolean(), anyByte()))
                    .thenReturn(new byte[512]);
        } catch (Exception ignored) {}
        return hg;
    }
}
