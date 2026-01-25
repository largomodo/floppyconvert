package com.largomodo.floppyconvert.snes;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnesInterleaverTest {

    private final SnesInterleaver interleaver = new SnesInterleaver();

    private static final int CHUNK_8MBIT = 0x100000; // 1MB
    private static final int BLOCK_32KB = 0x8000;

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
        assertTrue(output.length % CHUNK_8MBIT == 0,
                "Output size must be multiple of 8Mbit");
    }

    @Property
    void interleavingSwapsBlocksCorrectly_FirstChunk(@ForAll("data8Mbit") byte[] input) {
        byte[] output = interleaver.interleave(input);

        int halfChunk = CHUNK_8MBIT / 2;
        for (int i = 0; i < 16; i++) {
            int srcBlockOffset = i * (BLOCK_32KB * 2);
            int destLowerOffset = i * BLOCK_32KB;
            int destUpperOffset = halfChunk + (i * BLOCK_32KB);

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

    @Test
    void twelvesMbitMirrorsToSixteen() {
        int size12Mbit = CHUNK_8MBIT + (CHUNK_8MBIT / 2); // 12 Mbit = 1.5 MB
        byte[] input = new byte[size12Mbit];
        
        // Fill first 8Mbit with A
        Arrays.fill(input, 0, CHUNK_8MBIT, (byte) 0xAA);
        // Fill next 4Mbit with B
        Arrays.fill(input, CHUNK_8MBIT, size12Mbit, (byte) 0xBB);

        // When mirroring 12->16, the last 4Mbit (0xBB) should be repeated into the gap
        
        byte[] output = interleaver.interleave(input);
        int size16Mbit = 2 * CHUNK_8MBIT;

        assertEquals(size16Mbit, output.length,
                "12 Mbit input should be extended to 16 Mbit");
        
        // We need to de-interleave mentally or check specific offsets to verify mirroring
        // The mirroring happens BEFORE interleaving.
        // Pre-interleave state:
        // 0.0 - 1.0 MB: 0xAA
        // 1.0 - 1.5 MB: 0xBB
        // 1.5 - 2.0 MB: 0xBB (Mirrored from 1.0-1.5)
        
        // Check the second chunk (1.0 - 2.0 MB) of the OUTPUT
        // In the output, the second chunk (indexes 1MB to 2MB) corresponds to the
        // interleaved version of (0xBB....0xBB).
        // Since the source for the second chunk is all 0xBBs (due to mirroring), 
        // the interleaved output should also be all 0xBBs.
        
        byte[] secondChunk = Arrays.copyOfRange(output, CHUNK_8MBIT, size16Mbit);
        for(byte b : secondChunk) {
            assertEquals((byte) 0xBB, b, "Mirrored section should contain repeated data (0xBB), not zeros");
        }
    }

    @Test
    void oneByteInputMirrorsTo8Mbit() {
        // Extreme case: 1 byte input. Should fill 8Mbit with that 1 byte.
        byte[] input = new byte[]{0x42};

        byte[] output = interleaver.interleave(input);

        assertEquals(CHUNK_8MBIT, output.length);
        
        // Since input is all 0x42, output should be all 0x42
        for(byte b : output) {
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

    private byte[] fillWithRandomPattern(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((i * 13 + 7) % 256);
        }
        return data;
    }
}
