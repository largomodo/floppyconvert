package com.largomodo.floppyconvert.service;

import java.util.ArrayList;
import java.util.List;

/**
 * UFO HiROM irregular chunk size calculator.
 * <p>
 * UFO copier has fixed memory bank layout per total ROM size requiring specific chunk sequences.
 * Standard 4Mbit chunks would mismatch bank boundaries causing loader failure.
 * Lookup table encodes copier-specific bank allocation strategies from ucon64 size_to_partsizes.
 * <p>
 * Flag values indicate bank type: 0x40 (4Mbit multi-start), 0x10 (2Mbit multi-continue), 0x00 (last).
 */
public class UfoHiRomChunker {

    /**
     * Chunk specification: size in Mbit and multi-file flag byte.
     */
    public record UfoChunk(int sizeMbit, byte flag) {
        public UfoChunk {
            if (sizeMbit <= 0) {
                throw new IllegalArgumentException("Chunk size must be positive");
            }
        }
    }

    private static class LookupEntry {
        final int sizeMbit;
        final int[] chunkSizesMbit;
        final byte[] flags;

        LookupEntry(int sizeMbit, int[] chunkSizesMbit, byte[] flags) {
            this.sizeMbit = sizeMbit;
            this.chunkSizesMbit = chunkSizesMbit;
            this.flags = flags;
        }
    }

    private static final LookupEntry[] LOOKUP_TABLE = {
            new LookupEntry(2, new int[]{2}, new byte[]{0x10}),
            new LookupEntry(4, new int[]{2, 2}, new byte[]{0x10, 0x00}),
            new LookupEntry(12, new int[]{4, 2, 4, 2}, new byte[]{0x40, 0x10, 0x10, 0x00}),
            new LookupEntry(20, new int[]{4, 4, 2, 4, 4, 2}, new byte[]{0x40, 0x40, 0x10, 0x10, 0x10, 0x00}),
            new LookupEntry(32, new int[]{4, 4, 4, 4, 4, 4, 4, 4}, new byte[]{0x40, 0x40, 0x40, 0x10, 0x10, 0x10, 0x10, 0x00})
    };

    /**
     * Computes chunk sequence for UFO HiROM splitting.
     * <p>
     * For supported sizes (2, 4, 12, 20, 32 Mbit), returns specific chunk sequence.
     * For unsupported sizes (e.g., 48 Mbit), falls back to 32Mbit table (ucon64 reference behavior).
     * <p>
     * Example: 12 Mbit returns [(4, 0x40), (2, 0x10), (4, 0x10), (2, 0x00)]
     *
     * @param totalSizeMbit Total ROM size in Mbit (megabits, where 1 Mbit = 128 KB)
     * @return List of chunks with sizes and multi-file flags
     */
    public static List<UfoChunk> computeChunks(int totalSizeMbit) {
        if (totalSizeMbit <= 0) {
            throw new IllegalArgumentException("ROM size must be positive: " + totalSizeMbit);
        }

        LookupEntry entry = findEntry(totalSizeMbit);

        List<UfoChunk> chunks = new ArrayList<>();
        for (int i = 0; i < entry.chunkSizesMbit.length; i++) {
            chunks.add(new UfoChunk(entry.chunkSizesMbit[i], entry.flags[i]));
        }

        return chunks;
    }

    private static LookupEntry findEntry(int sizeMbit) {
        for (LookupEntry entry : LOOKUP_TABLE) {
            if (entry.sizeMbit == sizeMbit) {
                return entry;
            }
        }
        return LOOKUP_TABLE[4];
    }
}
