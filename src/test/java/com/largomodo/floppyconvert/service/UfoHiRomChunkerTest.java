package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.service.UfoHiRomChunker.UfoChunk;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for UfoHiRomChunker.
 * <p>
 * Tests verify:
 * - Chunk sequences sum to total ROM size
 * - Flag sequences match expected patterns (first 0x40 or 0x10, last 0x00)
 * - Edge cases (2Mbit, 32Mbit, fallback)
 */
class UfoHiRomChunkerTest {

    @Provide
    Arbitrary<Integer> supportedSizes() {
        return Arbitraries.of(2, 4, 12, 20, 32);
    }

    @Property
    void testAllSupportedSizes_ChunkSumEqualsTotal(@ForAll("supportedSizes") int sizeMbit) {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(sizeMbit);

        int sum = chunks.stream().mapToInt(UfoChunk::sizeMbit).sum();
        assertEquals(sizeMbit, sum, "Sum of chunk sizes must equal total ROM size");
    }

    @Property
    void testAllSupportedSizes_FlagSequenceCorrect(@ForAll("supportedSizes") int sizeMbit) {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(sizeMbit);

        assertFalse(chunks.isEmpty(), "Chunk list must not be empty");

        byte lastFlag = chunks.get(chunks.size() - 1).flag();
        if (sizeMbit == 2) {
            assertEquals((byte) 0x10, lastFlag, "2Mbit single-part must have flag 0x10");
        } else {
            assertEquals((byte) 0x00, lastFlag, "Last chunk must have flag 0x00");
        }

        byte firstFlag = chunks.get(0).flag();
        assertTrue(firstFlag == (byte) 0x40 || firstFlag == (byte) 0x10,
                "First chunk must have flag 0x40 or 0x10");
    }

    @Test
    void test2MbitRom_SinglePartWithContinuationFlag() {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(2);

        assertEquals(1, chunks.size(), "2Mbit ROM should produce 1 chunk");
        assertEquals(2, chunks.get(0).sizeMbit(), "Chunk size should be 2 Mbit");
        assertEquals((byte) 0x10, chunks.get(0).flag(), "2Mbit single-part flag should be 0x10");
    }

    @Test
    void test4MbitRom_TwoPartsWithCorrectFlags() {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(4);

        assertEquals(2, chunks.size(), "4Mbit ROM should produce 2 chunks");
        assertEquals(2, chunks.get(0).sizeMbit(), "First chunk size should be 2 Mbit");
        assertEquals(2, chunks.get(1).sizeMbit(), "Second chunk size should be 2 Mbit");
        assertEquals((byte) 0x10, chunks.get(0).flag(), "First chunk flag should be 0x10");
        assertEquals((byte) 0x00, chunks.get(1).flag(), "Last chunk flag should be 0x00");
    }

    @Test
    void test12MbitRom_FourPartsWithCorrectFlagsAndSizes() {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(12);

        assertEquals(4, chunks.size(), "12Mbit ROM should produce 4 chunks");

        assertEquals(4, chunks.get(0).sizeMbit(), "Part 1 size should be 4 Mbit");
        assertEquals(2, chunks.get(1).sizeMbit(), "Part 2 size should be 2 Mbit");
        assertEquals(4, chunks.get(2).sizeMbit(), "Part 3 size should be 4 Mbit");
        assertEquals(2, chunks.get(3).sizeMbit(), "Part 4 size should be 2 Mbit");

        assertEquals((byte) 0x40, chunks.get(0).flag(), "Part 1 flag should be 0x40");
        assertEquals((byte) 0x10, chunks.get(1).flag(), "Part 2 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(2).flag(), "Part 3 flag should be 0x10");
        assertEquals((byte) 0x00, chunks.get(3).flag(), "Part 4 flag should be 0x00");
    }

    @Test
    void test20MbitRom_SixPartsWithCorrectFlagsAndSizes() {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(20);

        assertEquals(6, chunks.size(), "20Mbit ROM should produce 6 chunks");

        assertEquals(4, chunks.get(0).sizeMbit(), "Part 1 size should be 4 Mbit");
        assertEquals(4, chunks.get(1).sizeMbit(), "Part 2 size should be 4 Mbit");
        assertEquals(2, chunks.get(2).sizeMbit(), "Part 3 size should be 2 Mbit");
        assertEquals(4, chunks.get(3).sizeMbit(), "Part 4 size should be 4 Mbit");
        assertEquals(4, chunks.get(4).sizeMbit(), "Part 5 size should be 4 Mbit");
        assertEquals(2, chunks.get(5).sizeMbit(), "Part 6 size should be 2 Mbit");

        assertEquals((byte) 0x40, chunks.get(0).flag(), "Part 1 flag should be 0x40");
        assertEquals((byte) 0x40, chunks.get(1).flag(), "Part 2 flag should be 0x40");
        assertEquals((byte) 0x10, chunks.get(2).flag(), "Part 3 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(3).flag(), "Part 4 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(4).flag(), "Part 5 flag should be 0x10");
        assertEquals((byte) 0x00, chunks.get(5).flag(), "Part 6 flag should be 0x00");
    }

    @Test
    void test32MbitRom_EightPartsWithCorrectFlagsAndSizes() {
        List<UfoChunk> chunks = UfoHiRomChunker.computeChunks(32);

        assertEquals(8, chunks.size(), "32Mbit ROM should produce 8 chunks");

        for (int i = 0; i < 8; i++) {
            assertEquals(4, chunks.get(i).sizeMbit(), "All chunks should be 4 Mbit");
        }

        assertEquals((byte) 0x40, chunks.get(0).flag(), "Part 1 flag should be 0x40");
        assertEquals((byte) 0x40, chunks.get(1).flag(), "Part 2 flag should be 0x40");
        assertEquals((byte) 0x40, chunks.get(2).flag(), "Part 3 flag should be 0x40");
        assertEquals((byte) 0x10, chunks.get(3).flag(), "Part 4 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(4).flag(), "Part 5 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(5).flag(), "Part 6 flag should be 0x10");
        assertEquals((byte) 0x10, chunks.get(6).flag(), "Part 7 flag should be 0x10");
        assertEquals((byte) 0x00, chunks.get(7).flag(), "Part 8 flag should be 0x00");
    }

    @Test
    void testUnsupportedSize_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> UfoHiRomChunker.computeChunks(48));

        assertTrue(exception.getMessage().contains("UFO hardware does not support HiROM size: 48 Mbit"));
    }

    @Test
    void testNegativeSize_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                UfoHiRomChunker.computeChunks(-1)
        );
        assertTrue(exception.getMessage().contains("ROM size must be positive"));
    }

    @Test
    void testZeroSize_ThrowsException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                UfoHiRomChunker.computeChunks(0)
        );
        assertTrue(exception.getMessage().contains("ROM size must be positive"));
    }
}
