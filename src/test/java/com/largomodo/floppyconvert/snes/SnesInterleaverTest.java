package com.largomodo.floppyconvert.snes;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnesInterleaverTest {

    private static final int CHUNK_8MBIT = 0x100000; // 1MB
    private static final int BLOCK_32KB = 0x8000;
    private final SnesInterleaver interleaver = new SnesInterleaver();

    @Property
    void interleavingPreservesDataSize_8MbitAligned(@ForAll("data8MbitAligned") byte[] input) {
        byte[] output = interleaver.interleave(input);

        assertEquals(input.length, output.length,
                "Output size should equal input size for 8Mbit-aligned input");
    }

    @Property
    void interleavingAlignsTo8Mbit_NonAligned(@ForAll("dataNonAligned") byte[] input) {
        byte[] output = interleaver.interleave(input);

        int expectedSize = ((input.length / CHUNK_8MBIT) + 1) * CHUNK_8MBIT;
        assertEquals(expectedSize, output.length,
                "Output should be aligned to nearest 8Mbit boundary");
        assertEquals(0, output.length % CHUNK_8MBIT, "Output size must be multiple of 8Mbit");
    }

    @Property
    void interleavingSwapsBlocksCorrectly_FirstChunk(@ForAll("data8Mbit") byte[] input) {
        byte[] output = interleaver.interleave(input);

        int halfSize = input.length / 2;
        int numBlocks = input.length / (BLOCK_32KB * 2);

        for (int i = 0; i < numBlocks; i++) {
            int srcBlockOffset = i * (BLOCK_32KB * 2);
            int destLowerOffset = i * BLOCK_32KB;
            int destUpperOffset = halfSize + (i * BLOCK_32KB);

            byte[] srcLower32K = Arrays.copyOfRange(input, srcBlockOffset, srcBlockOffset + BLOCK_32KB);
            byte[] srcUpper32K = Arrays.copyOfRange(input, srcBlockOffset + BLOCK_32KB, srcBlockOffset + (BLOCK_32KB * 2));

            byte[] destUpper32K = Arrays.copyOfRange(output, destUpperOffset, destUpperOffset + BLOCK_32KB);
            byte[] destLower32K = Arrays.copyOfRange(output, destLowerOffset, destLowerOffset + BLOCK_32KB);

            assertArrayEquals(srcLower32K, destUpper32K,
                    "Block " + i + ": Source Lower 32KB should map to Dest Upper Half");
            assertArrayEquals(srcUpper32K, destLower32K,
                    "Block " + i + ": Source Upper 32KB should map to Dest Lower Half");
        }
    }

    @Property
    void testGlobalInterleavingPreservesAllData(@ForAll("romData8MbitMultiple") byte[] input) {
        byte[] output = interleaver.interleave(input);

        assertEquals(input.length, output.length,
                "Output length should equal input length for 8Mbit-aligned input");

        long inputSum = 0;
        long outputSum = 0;
        for (int i = 0; i < input.length; i++) {
            inputSum += input[i] & 0xFF;
            outputSum += output[i] & 0xFF;
        }
        assertEquals(inputSum, outputSum,
                "Sum of all bytes should be equal - no data corruption");

        Map<Byte, Integer> inputCounts = new HashMap<>();
        Map<Byte, Integer> outputCounts = new HashMap<>();
        for (int i = 0; i < input.length; i++) {
            inputCounts.merge(input[i], 1, Integer::sum);
            outputCounts.merge(output[i], 1, Integer::sum);
        }
        assertEquals(inputCounts, outputCounts,
                "Every input byte should appear in output with same frequency");
    }

    @Property
    void testBlockPairMappingCorrect(@ForAll("romData8MbitMultiple") byte[] input) {
        byte[] output = interleaver.interleave(input);

        int halfSize = input.length / 2;
        int numBlocks = input.length / (BLOCK_32KB * 2);

        for (int i = 0; i < numBlocks; i++) {
            int srcBlockOffset = i * (BLOCK_32KB * 2);
            int destLowerOffset = i * BLOCK_32KB;
            int destUpperOffset = halfSize + (i * BLOCK_32KB);

            byte[] srcLower32K = Arrays.copyOfRange(input, srcBlockOffset, srcBlockOffset + BLOCK_32KB);
            byte[] srcUpper32K = Arrays.copyOfRange(input, srcBlockOffset + BLOCK_32KB, srcBlockOffset + (BLOCK_32KB * 2));

            byte[] destUpper32K = Arrays.copyOfRange(output, destUpperOffset, destUpperOffset + BLOCK_32KB);
            byte[] destLower32K = Arrays.copyOfRange(output, destLowerOffset, destLowerOffset + BLOCK_32KB);

            assertArrayEquals(srcUpper32K, destLower32K,
                    "Block " + i + ": output[i*32KB] should come from input[i*64KB+32KB]");
            assertArrayEquals(srcLower32K, destUpper32K,
                    "Block " + i + ": output[n + i*32KB] should come from input[i*64KB] where n = size/2");
        }
    }

    @Test
    void twelvesMbitMirrorsToSixteen() {
        int size12Mbit = CHUNK_8MBIT + (CHUNK_8MBIT / 2); // 12 Mbit = 1.5 MB
        byte[] input = new byte[size12Mbit];

        // Fill first 8Mbit with A
        Arrays.fill(input, 0, CHUNK_8MBIT, (byte) 0xAA);
        // Fill next 4Mbit with B
        Arrays.fill(input, CHUNK_8MBIT, size12Mbit, (byte) 0xBB);

        byte[] output = interleaver.interleave(input);
        int size16Mbit = 2 * CHUNK_8MBIT;

        assertEquals(size16Mbit, output.length,
                "12 Mbit input should be extended to 16 Mbit");

        // Global interleaving redistributes blocks across the entire ROM.
        // After mirroring: [0xAA (8Mbit), 0xBB (4Mbit), 0xBB (4Mbit mirrored)]
        // The algorithm uses halfSize = totalSize / 2 = 1MB as the global split point.
        //
        // For the first 64KB block (all 0xAA):
        //   - Lower 32KB (0xAA) -> output[1MB] (second half start)
        //   - Upper 32KB (0xAA) -> output[0] (first half start)
        //
        // This means output will contain BOTH 0xAA and 0xBB data mixed across the address space.
        // We verify that all data is preserved (no loss) and both values are present.

        Map<Byte, Integer> counts = new HashMap<>();
        for (byte b : output) {
            counts.merge(b, 1, Integer::sum);
        }

        // After mirroring, we have:
        // - 8Mbit of 0xAA = 1MB = 1048576 bytes
        // - 8Mbit of 0xBB (4Mbit original + 4Mbit mirrored) = 1MB = 1048576 bytes
        assertEquals(CHUNK_8MBIT, counts.get((byte) 0xAA),
                "Should have exactly 1MB of 0xAA data after global interleaving");
        assertEquals(CHUNK_8MBIT, counts.get((byte) 0xBB),
                "Should have exactly 1MB of 0xBB data (including mirrored section)");

        // Verify global interleaving mixed the data (not per-chunk isolated)
        // The second chunk should contain BOTH 0xAA and 0xBB
        byte[] secondChunk = Arrays.copyOfRange(output, CHUNK_8MBIT, size16Mbit);
        boolean hasAA = false, hasBB = false;
        for (byte b : secondChunk) {
            if (b == (byte) 0xAA) hasAA = true;
            if (b == (byte) 0xBB) hasBB = true;
        }
        assertTrue(hasAA, "Global interleaving should place some 0xAA data in second half");
        assertTrue(hasBB, "Global interleaving should place some 0xBB data in second half");
    }

    @Test
    void oneByteInputMirrorsTo8Mbit() {
        // Extreme case: 1 byte input. Should fill 8Mbit with that 1 byte.
        byte[] input = new byte[]{0x42};

        byte[] output = interleaver.interleave(input);

        assertEquals(CHUNK_8MBIT, output.length);

        // Since input is all 0x42, output should be all 0x42
        for (byte b : output) {
            assertEquals((byte) 0x42, b, "Data should be mirrored to fill the chunk");
        }
    }

    @Provide
    Arbitrary<byte[]> data8MbitAligned() {
        return Arbitraries.integers()
                .between(1, 8)
                .map(chunks -> new byte[chunks * CHUNK_8MBIT])
                .map(this::fillWithRandomPattern);
    }

    @Provide
    Arbitrary<byte[]> data8Mbit() {
        return Arbitraries.just(CHUNK_8MBIT)
                .map(size -> new byte[size])
                .map(this::fillWithRandomPattern);
    }

    @Provide
    Arbitrary<byte[]> dataNonAligned() {
        return Arbitraries.integers()
                .between(1, CHUNK_8MBIT - 1)
                .map(size -> new byte[size])
                .map(this::fillWithRandomPattern);
    }

    @Provide
    Arbitrary<byte[]> romData8MbitMultiple() {
        return Arbitraries.integers()
                .between(1, 4)
                .map(chunks -> new byte[chunks * CHUNK_8MBIT])
                .map(this::fillWithRandomPattern);
    }

    private byte[] fillWithRandomPattern(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 13 + 7) % 256);
        }
        return data;
    }
}
