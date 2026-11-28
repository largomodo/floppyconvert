package com.largomodo.floppyconvert.snes.generators;

import com.largomodo.floppyconvert.snes.Gd3Interleave;
import com.largomodo.floppyconvert.snes.SnesConstants;

/**
 * Test-side reference for the block interleave of ucon64 {@code snes_convert_to_gd} (snes.c 1818-1855) over
 * unpadded images. Independent of SnesInterleaver so the deinterleave tables are checked against a forward
 * implementation instead of against themselves.
 */
public final class ReferenceInterleaver {

    private static final int BLOCK_32KB = 0x8000;

    private ReferenceInterleaver() {
    }

    public static byte[] interleave(byte[] linear, Gd3Interleave scheme) {
        int size = linear.length;
        byte[] out = new byte[size];
        switch (scheme) {
            case NONE -> System.arraycopy(linear, 0, out, 0, size);
            case GLOBAL -> blocks(linear, 0, out, size / 2, 0, size / 0x10000);
            case TWO_REGION_24 -> {
                blocks(linear, 0, out, 16 * SnesConstants.MBIT, 0, 16 * SnesConstants.MBIT / 0x10000);
                blocks(linear, 16 * SnesConstants.MBIT, out, 12 * SnesConstants.MBIT, 8 * SnesConstants.MBIT,
                        (size - 16 * SnesConstants.MBIT) / 0x10000);
            }
            case EXTENDED -> {
                int size2 = size - 32 * SnesConstants.MBIT;
                blocks(linear, 0, out, size2 + 16 * SnesConstants.MBIT, size2, 32 * SnesConstants.MBIT / 0x10000);
                blocks(linear, 32 * SnesConstants.MBIT, out, size2 / 2, 0, size2 / 0x10000);
            }
        }
        return out;
    }

    private static void blocks(byte[] src, int srcOffset, byte[] dst, int lowerOffset, int upperOffset, int count) {
        for (int i = 0; i < count; i++) {
            System.arraycopy(src, srcOffset, dst, lowerOffset, BLOCK_32KB);
            System.arraycopy(src, srcOffset + BLOCK_32KB, dst, upperOffset, BLOCK_32KB);
            srcOffset += 2 * BLOCK_32KB;
            lowerOffset += BLOCK_32KB;
            upperOffset += BLOCK_32KB;
        }
    }
}
