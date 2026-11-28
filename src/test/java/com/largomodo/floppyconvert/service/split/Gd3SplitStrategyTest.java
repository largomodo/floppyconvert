// Unit tests for Gd3SplitStrategy: ucon64 snes_split_gd3 part sizes (HiROM <= 16 Mbit in two halves,
// otherwise 8 Mbit parts plus remainder) and header placement. (ref: DL-007)
package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesConstants;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.header.HeaderGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Gd3SplitStrategyTest {

    private static final int MBIT = SnesConstants.MBIT;

    private final Gd3SplitStrategy strategy = new Gd3SplitStrategy();

    @TempDir
    Path tempDir;

    private SnesRom rom(RomType type, int size) {
        return new SnesRom(new byte[size], type, 0, "TEST ROM", false, 0, 0, 0, 0x0001, 0xFFFE);
    }

    private List<Integer> splitSizes(RomType type, int sizeMbit) throws IOException {
        byte[] data = new byte[sizeMbit * MBIT];
        List<Integer> sizes = new ArrayList<>();
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> {
            sizes.add(partSize);
            return new byte[0];
        };
        strategy.split(rom(type, data.length), data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());
        return sizes;
    }

    @ParameterizedTest(name = "HiROM {0} Mbit")
    @CsvSource({"2,1,1", "4,2,2", "8,4,4", "12,6,6", "16,8,8"})
    // ucon64 halves HiROM up to 16 Mbit, so 12 Mbit gives 6+6 and not 8+4.
    void hiRomUpToSixteenMbitSplitsInTwoHalves(int sizeMbit, int firstHalfMbit, int secondHalfMbit) throws IOException {
        assertEquals(List.of(firstHalfMbit * MBIT, secondHalfMbit * MBIT), splitSizes(RomType.HiROM, sizeMbit));
    }

    @Test
    void hiRom24MbitSplitsInThreeEightMbitParts() throws IOException {
        assertEquals(List.of(8 * MBIT, 8 * MBIT, 8 * MBIT), splitSizes(RomType.HiROM, 24));
    }

    @Test
    void hiRom32MbitSplitsInFourEightMbitParts() throws IOException {
        assertEquals(List.of(8 * MBIT, 8 * MBIT, 8 * MBIT, 8 * MBIT), splitSizes(RomType.HiROM, 32));
    }

    @Test
    void loRom12MbitSplitsInEightPlusFour() throws IOException {
        assertEquals(List.of(8 * MBIT, 4 * MBIT), splitSizes(RomType.LoROM, 12));
    }

    @Test
    void loRom16MbitIsNotHalved() throws IOException {
        assertEquals(List.of(8 * MBIT, 8 * MBIT), splitSizes(RomType.LoROM, 16));
        assertEquals(List.of(8 * MBIT), splitSizes(RomType.LoROM, 8));
    }

    @Test
    void chunkBytesConcatenateToThePreparedData() throws IOException {
        int size = 24 * MBIT;
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i & 0xFF);
        }
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> new byte[0];

        List<File> parts = strategy.split(rom(RomType.HiROM, size), data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());

        int offset = 0;
        for (File part : parts) {
            byte[] chunk = Files.readAllBytes(part.toPath());
            for (int b = 0; b < chunk.length; b++) {
                assertEquals(data[offset + b], chunk[b], "Chunk byte at offset " + (offset + b));
            }
            offset += chunk.length;
        }
        assertEquals(size, offset);
    }

    @Test
    void headerIsWrittenOnPartZeroOnly() throws IOException {
        byte[] data = new byte[16 * MBIT];
        HeaderGenerator headerGen = (r, partSize, idx, isLast, flag) -> idx == 0 ? new byte[512] : new byte[0];

        List<File> parts = strategy.split(rom(RomType.HiROM, data.length), data, headerGen, tempDir, "test",
                (wd, bn, idx, total, r) -> wd.resolve("part" + idx + ".078").toFile());

        assertEquals(8 * MBIT + 512, parts.get(0).length());
        assertEquals(8 * MBIT, parts.get(1).length());
    }
}
