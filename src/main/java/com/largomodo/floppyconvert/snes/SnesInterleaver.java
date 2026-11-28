package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.snes.Gd3Layout.PadStrategy;

import java.util.Arrays;

/**
 * Handles SNES ROM interleaving for Game Doctor (GD3) format.
 * <p>
 * The image the Game Doctor receives is described by a {@link Gd3Layout}: the layout names the padded size,
 * the pad strategy and the interleave scheme, and this class only moves bytes accordingly. Padding follows
 * ucon64's per-size rules (zero fill, or mirrored halves for 3, 6 and 12 parts) so the SNES memory map stays
 * valid where the console shadows addresses.
 * <p>
 * Logic ported from uCON64's {@code snes_int_blocks} (snes.c 1429) and {@code snes_mirror} (snes.c 1447).
 */
public class SnesInterleaver {

    private static final int BLOCK_64KB = 0x10000;
    private static final int BLOCK_32KB = 0x8000;
    private static final int MAX_DEINTERLEAVE_BLOCKS = 512; // ucon64 snes_deinterleave block table: 512 x 32 KB = 16 MB

    /**
     * Pads and interleaves a linear ROM into the Game Doctor image described by layout.
     * <p>
     * Per scheme (ucon64 snes_convert_to_gd, snes.c 1818-1855):
     * <ul>
     *   <li>GLOBAL: one pass, lower 32 KB of every 64 KB block to the second half; with mirror padding the
     *       last partial 4 Mbit of each half is repeated up to the next 1 Mbit boundary.</li>
     *   <li>TWO_REGION_24: a 16 Mbit region then an 8 Mbit region, each interleaved on its own; mirror padding
     *       fills 10 to 12 Mbit and 14 to 16 Mbit.</li>
     *   <li>EXTENDED: the expansion past 32 Mbit goes first, the 32 Mbit base after it.</li>
     *   <li>NONE: the input is returned untouched.</li>
     * </ul>
     *
     * @param linear Raw linear ROM data
     * @param layout layout computed by {@link Gd3Layout#of(SnesRom)} for this ROM
     * @return image of {@code layout.paddedSize()} bytes
     */
    public byte[] interleave(byte[] linear, Gd3Layout layout) {
        if (layout.interleave() == Gd3Interleave.NONE) {
            return linear;
        }

        int size = linear.length;
        int newSize = layout.paddedSize();
        // Zero fill beyond the source: both unmirrored padding and the holes mirroring later overwrites
        byte[] source = Arrays.copyOf(linear, newSize);
        byte[] dest = new byte[newSize];
        boolean mirror = layout.padStrategy() == PadStrategy.MIRROR_HALVES;

        switch (layout.interleave()) {
            case GLOBAL -> interleaveGlobal(source, dest, size, mirror);
            case TWO_REGION_24 -> interleaveTwoRegion(source, dest, size, mirror);
            case EXTENDED -> interleaveExtended(source, dest, mirror);
            default -> throw new IllegalArgumentException("Unsupported interleave scheme: " + layout.interleave());
        }
        return dest;
    }

    // Whole-image pass; mirror padding repeats the tail of each half up to the next 1 Mbit boundary.
    private static void interleaveGlobal(byte[] source, byte[] dest, int size, boolean mirror) {
        int newSize = dest.length;
        int half = newSize / 2;
        intBlocks(source, 0, dest, half, 0, newSize / BLOCK_64KB);
        if (mirror) {
            int halfSize4Mbit = (size / 2) & ~(4 * SnesConstants.MBIT - 1);
            int halfSize1Mbit = (size / 2 + SnesConstants.MBIT - 1) & ~(SnesConstants.MBIT - 1);
            mirror(dest, halfSize4Mbit, halfSize1Mbit, half);
            mirror(dest, half + halfSize4Mbit, half + halfSize1Mbit, newSize);
        }
    }

    // The second region uses the unpadded size, as ucon64 does: padding blocks come from the mirror, not from the source
    private static void interleaveTwoRegion(byte[] source, byte[] dest, int size, boolean mirror) {
        int mbit = SnesConstants.MBIT;
        intBlocks(source, 0, dest, 16 * mbit, 0, 16 * mbit / BLOCK_64KB);
        intBlocks(source, 16 * mbit, dest, 12 * mbit, 8 * mbit, (size - 16 * mbit) / BLOCK_64KB);
        if (mirror) {
            mirror(dest, 8 * mbit, 10 * mbit, 12 * mbit);
            mirror(dest, 12 * mbit, 14 * mbit, 16 * mbit);
        }
    }

    // Expansion first (banks $40-$7D), 32 Mbit base after it (banks $C0-$FF): the order the Game Doctor expects
    private static void interleaveExtended(byte[] source, byte[] dest, boolean mirror) {
        int mbit = SnesConstants.MBIT;
        int size2 = dest.length - 32 * mbit;
        intBlocks(source, 0, dest, size2 + 16 * mbit, size2, 32 * mbit / BLOCK_64KB);
        intBlocks(source, 32 * mbit, dest, size2 / 2, 0, size2 / BLOCK_64KB);
        if (mirror) {
            mirror(dest, 0, 4 * mbit, 8 * mbit);
            mirror(dest, 8 * mbit, 12 * mbit, 16 * mbit);
        }
    }

    /**
     * Port of snes_int_blocks: the lower 32 KB of each 64 KB source block goes to the lower-offset stream, the
     * upper 32 KB to the upper-offset stream.
     */
    private static void intBlocks(byte[] source, int sourceOffset, byte[] dest, int lowerOffset, int upperOffset,
                                  int blocks) {
        for (int i = 0; i < blocks; i++) {
            System.arraycopy(source, sourceOffset, dest, lowerOffset, BLOCK_32KB);
            System.arraycopy(source, sourceOffset + BLOCK_32KB, dest, upperOffset, BLOCK_32KB);
            sourceOffset += BLOCK_64KB;
            lowerOffset += BLOCK_32KB;
            upperOffset += BLOCK_32KB;
        }
    }

    /**
     * Port of snes_mirror: repeats dest[start, dataEnd) until dest reaches mirrorEnd.
     */
    private static void mirror(byte[] dest, int start, int dataEnd, int mirrorEnd) {
        int dataSize = dataEnd - start;
        int totalSize = mirrorEnd - start;
        if (dataSize <= 0 || dataSize >= totalSize) {
            return;
        }
        int repeats = totalSize / dataSize - 1;
        int surplus = totalSize % dataSize;
        while (repeats-- > 0) {
            System.arraycopy(dest, start, dest, dataEnd, dataSize);
            dataEnd += dataSize;
        }
        if (surplus > 0) {
            System.arraycopy(dest, start, dest, dataEnd, mirrorEnd - dataEnd);
        }
    }

    /**
     * Restores linear ROM order from a Game Doctor interleaved image. Literal port of ucon64
     * {@code snes_deinterleave} (snes.c 3859): the scheme selects a table that maps each 32 KB output block to
     * the interleaved block it came from. An image over 16 MB (more than 512 blocks of 32 KB) is returned as is,
     * the same limit ucon64's fixed block table imposes.
     *
     * @param interleaved image in interleaved order
     * @param scheme      how it was interleaved; {@link Gd3Interleave#NONE} returns the input
     * @return linear image of the same length
     */
    public byte[] deinterleave(byte[] interleaved, Gd3Interleave scheme) {
        int nblocks = interleaved.length >>> 16;
        if (scheme == Gd3Interleave.NONE || nblocks * 2 > MAX_DEINTERLEAVE_BLOCKS) {
            return interleaved;
        }

        int[] blocks = new int[nblocks * 2];
        switch (scheme) {
            case GLOBAL -> fillGlobalBlocks(blocks, nblocks);
            case TWO_REGION_24 -> fillTwoRegionBlocks(blocks, nblocks);
            case EXTENDED -> fillExtendedBlocks(blocks, interleaved.length);
            default -> throw new IllegalArgumentException("Unsupported interleave scheme: " + scheme);
        }

        byte[] linear = interleaved.clone();
        for (int i = 0; i < blocks.length; i++) {
            int source = blocks[i] * BLOCK_32KB;
            if (source + BLOCK_32KB > interleaved.length) {
                throw new IllegalArgumentException(
                        scheme + " table points past the end of a " + interleaved.length + "-byte image");
            }
            System.arraycopy(interleaved, source, linear, i * BLOCK_32KB, BLOCK_32KB);
        }
        return linear;
    }

    private static void fillGlobalBlocks(int[] blocks, int nblocks) {
        for (int i = 0; i < nblocks; i++) {
            blocks[i * 2] = i + nblocks;
            blocks[i * 2 + 1] = i;
        }
    }

    private static void fillTwoRegionBlocks(int[] blocks, int nblocks) {
        int firstRegionBlocks = (16 * SnesConstants.MBIT) >> 16;
        int blocksPerMbit = SnesConstants.MBIT >> 15;
        for (int i = 0; i < nblocks; i++) {
            blocks[i * 2] = i + (i < firstRegionBlocks ? 16 : 4) * blocksPerMbit;
            blocks[i * 2 + 1] = i;
        }
    }

    private static void fillExtendedBlocks(int[] blocks, int length) {
        int size2 = length - 32 * SnesConstants.MBIT;
        int baseBlocks = (32 * SnesConstants.MBIT) >> 16;
        int i = 0;
        for (; i < baseBlocks; i++) {
            blocks[i * 2] = i + baseBlocks + (size2 >> 15);
            blocks[i * 2 + 1] = i + (size2 >> 15);
        }
        int expansionBlocks = size2 >> 16;
        for (; i < expansionBlocks + baseBlocks; i++) {
            blocks[i * 2] = i + expansionBlocks - baseBlocks;
            blocks[i * 2 + 1] = i - baseBlocks;
        }
    }
}
