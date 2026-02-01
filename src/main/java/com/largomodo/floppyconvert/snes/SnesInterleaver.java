package com.largomodo.floppyconvert.snes;

import java.util.Arrays;

/**
 * Handles SNES ROM interleaving for Game Doctor (GD3) format.
 * <p>
 * The Game Doctor copier requires HiROM games to be interleaved when split into 8Mbit chunks.
 * The interleaving algorithm swaps 32KB blocks within each 64KB segment.
 * <p>
 * <b>Mirroring:</b>
 * If the ROM size is not aligned to 8Mbit, the data is extended using mirroring (replication)
 * rather than zero-filling. This ensures that the SNES memory map remains valid when the
 * console attempts to access addresses in the extended range (shadowing).
 * <p>
 * Logic ported from uCON64's {@code snes_int_blocks} and {@code snes_mirror} functions.
 */
public class SnesInterleaver {

    private static final int BLOCK_64KB = 0x10000;
    private static final int BLOCK_32KB = 0x8000;
    private static final int CHUNK_8MBIT = 0x100000; // 1MB

    /**
     * Interleaves a ROM byte array according to GD3 HiROM specifications.
     * <p>
     * Transformation:
     * 1. Align data to 8Mbit boundary using mirroring (e.g., 12Mbit -> 16Mbit by repeating last 4Mbit).
     * 2. For every 8Mbit chunk:
     *    The first 4Mbit (512KB) of destination receives the UPPER 32KB of each 64KB source block.
     *    The second 4Mbit (512KB) of destination receives the LOWER 32KB of each 64KB source block.
     *
     * @param input Raw linear ROM data
     * @return Interleaved and mirrored data
     */
    public byte[] interleave(byte[] input) {
        // 1. Extend input to nearest 8Mbit boundary using mirroring (Hardware requirement)
        byte[] source = mirrorTo8Mbit(input);
        byte[] dest = new byte[source.length];

        // Global interleaving: process all 64KB blocks in single pass
        // GD3 copier expects symmetric block swap across entire ROM address space
        int halfSize = source.length / 2;
        int numBlocks = source.length / BLOCK_64KB;

        for (int i = 0; i < numBlocks; i++) {
            int srcBlockOffset = i * BLOCK_64KB;

            // Mapping logic matches uCON64 snes_int_blocks global offset (n = size/2):
            // Src[0..32k] (Lower) -> Dst[n + i*32k]
            // Src[32k..64k] (Upper) -> Dst[i*32k]

            int destLowerOffset = i * BLOCK_32KB;
            int destUpperOffset = halfSize + (i * BLOCK_32KB);

            // Copy Src Lower 32K to Dest Upper Half
            System.arraycopy(source, srcBlockOffset, dest, destUpperOffset, BLOCK_32KB);

            // Copy Src Upper 32K to Dest Lower Half
            System.arraycopy(source, srcBlockOffset + BLOCK_32KB, dest, destLowerOffset, BLOCK_32KB);
        }

        return dest;
    }

    /**
     * Aligns data to the next 8Mbit boundary by mirroring the tail of the data.
     * <p>
     * Example: A 12Mbit ROM is extended to 16Mbit. The 4Mbit gap is filled by
     * copying the *last* 4Mbit of the original data into the new space.
     * This simulates hardware address mirroring.
     */
    private byte[] mirrorTo8Mbit(byte[] input) {
        if (input.length % CHUNK_8MBIT == 0) {
            return input;
        }

        int newSize = ((input.length / CHUNK_8MBIT) + 1) * CHUNK_8MBIT;
        int gap = newSize - input.length;
        byte[] dest = new byte[newSize];

        // Copy original data
        System.arraycopy(input, 0, dest, 0, input.length);

        // Perform Mirroring: Copy the last 'gap' bytes of the input to the end
        // If input is smaller than gap (e.g. 1 byte input, 1MB target), we repeat
        // the available data until filled.
        int bytesWritten = 0;
        while (bytesWritten < gap) {
            int sourceOffset = Math.max(0, input.length - (gap - bytesWritten));
            int copyLength = Math.min(input.length, gap - bytesWritten);
            
            // If the gap is larger than the entire file, we mirror the whole file repeatedly
            if (copyLength == 0 && input.length > 0) {
                // Fallback for very small files being aligned up
                sourceOffset = 0;
                copyLength = input.length;
            }

            System.arraycopy(input, sourceOffset, dest, input.length + bytesWritten, copyLength);
            bytesWritten += copyLength;
        }

        return dest;
    }
}