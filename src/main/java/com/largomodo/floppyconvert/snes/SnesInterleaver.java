package com.largomodo.floppyconvert.snes;

import java.util.Arrays;

/**
 * Handles SNES ROM interleaving for Game Doctor (GD3) format.
 * <p>
 * The Game Doctor copier requires HiROM games to be interleaved when split into 8Mbit chunks.
 * The interleaving algorithm swaps 32KB blocks within each 64KB segment.
 * <p>
 * Logic ported from uCON64's {@code snes_int_blocks} function.
 */
public class SnesInterleaver {

    private static final int BLOCK_64KB = 0x10000;
    private static final int BLOCK_32KB = 0x8000;
    private static final int CHUNK_8MBIT = 0x100000; // 1MB

    /**
     * Interleaves a ROM byte array according to GD3 HiROM specifications.
     * <p>
     * Transformation:
     * For every 8Mbit (1MB) chunk:
     *   The first 4Mbit (512KB) of destination receives the UPPER 32KB of each 64KB source block.
     *   The second 4Mbit (512KB) of destination receives the LOWER 32KB of each 64KB source block.
     *
     * @param input Raw linear ROM data
     * @return Interleaved data
     */
    public byte[] interleave(byte[] input) {
        // 1. Pad input to nearest 8Mbit boundary if necessary (GD3 requirement)
        byte[] source = padTo8Mbit(input);
        byte[] dest = new byte[source.length];

        int num8MbitChunks = source.length / CHUNK_8MBIT;

        for (int chunk = 0; chunk < num8MbitChunks; chunk++) {
            int chunkOffset = chunk * CHUNK_8MBIT;
            int halfChunk = CHUNK_8MBIT / 2; // 512KB

            // Within this 1MB chunk, process 64KB blocks
            // There are 16 64KB blocks in 1MB
            for (int i = 0; i < 16; i++) {
                int srcBlockOffset = chunkOffset + (i * BLOCK_64KB);

                // Source Lower 32KB -> Dest Upper Half (offset + 512KB)
                // Source Upper 32KB -> Dest Lower Half (offset + 0)
                //
                // Wait, re-reading uCON64 snes_int_blocks logic:
                // memmove(ipl, deintptr, 0x8000);        // ipl = dest + half. Src 0..32k -> Dst Half
                // memmove(iph, deintptr + 0x8000, 0x8000); // iph = dest + 0.    Src 32k..64k -> Dst 0
                //
                // So:
                // Src[0..32k] (Lower) -> Dst[Half..]
                // Src[32k..64k] (Upper) -> Dst[0..]

                int destLowerOffset = chunkOffset + (i * BLOCK_32KB);           // 0..512k range
                int destUpperOffset = chunkOffset + halfChunk + (i * BLOCK_32KB); // 512k..1MB range

                // Copy Src Lower 32K to Dest Upper Half
                System.arraycopy(source, srcBlockOffset, dest, destUpperOffset, BLOCK_32KB);

                // Copy Src Upper 32K to Dest Lower Half
                System.arraycopy(source, srcBlockOffset + BLOCK_32KB, dest, destLowerOffset, BLOCK_32KB);
            }
        }

        return dest;
    }

    private byte[] padTo8Mbit(byte[] input) {
        if (input.length % CHUNK_8MBIT == 0) {
            return input;
        }
        int newSize = ((input.length / CHUNK_8MBIT) + 1) * CHUNK_8MBIT;
        return Arrays.copyOf(input, newSize);
    }
}