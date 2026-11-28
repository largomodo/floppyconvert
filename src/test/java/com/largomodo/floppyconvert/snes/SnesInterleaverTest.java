package com.largomodo.floppyconvert.snes;

import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import com.largomodo.floppyconvert.snes.generators.ReferenceInterleaver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnesInterleaverTest {

    private static final int CHUNK_8MBIT = 0x100000; // 1MB
    private static final int BLOCK_32KB = 0x8000;
    private static final int MBIT = SnesConstants.MBIT;
    private final SnesInterleaver interleaver = new SnesInterleaver();

    @Property
    void interleavingPreservesDataSize_8MbitAligned(@ForAll("data8MbitAligned") byte[] input) {
        byte[] output = interleaveHiRom(input);

        assertEquals(input.length, output.length,
                "Output size should equal input size for 8Mbit-aligned input");
    }

    @Property
    void interleavingSwapsBlocksCorrectly_FirstChunk(@ForAll("data8Mbit") byte[] input) {
        byte[] output = interleaveHiRom(input);

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
        byte[] output = interleaveHiRom(input);

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
        byte[] output = interleaveHiRom(input);

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
    // Mirror padding repeats the tail of each half; the input blocks are non-zero so a zero fill is detectable.
    void tenMbitHiRomIsMirroredWithinEachHalf() {
        byte[] input = nonZeroBlocks(10 * MBIT);

        byte[] output = interleaveHiRom(input);

        // 10 Mbit pads to 12 Mbit (3 parts); each 6 Mbit half repeats its 4-5 Mbit tail in 5-6 Mbit
        assertEquals(12 * MBIT, output.length);
        int half = output.length / 2;
        assertArrayEquals(slice(output, 4 * MBIT, MBIT), slice(output, 5 * MBIT, MBIT));
        assertArrayEquals(slice(output, half + 4 * MBIT, MBIT), slice(output, half + 5 * MBIT, MBIT));
    }

    @Test
    void twentyMbitHiRomUsesTwoRegionsAndMirrorsTheHoles() {
        byte[] input = nonZeroBlocks(20 * MBIT);

        byte[] output = interleaveHiRom(input);

        assertEquals(24 * MBIT, output.length);
        // Region one: lower 32 KB of block 0 lands at 16 Mbit, upper 32 KB at 0
        assertArrayEquals(slice(input, 0, BLOCK_32KB), slice(output, 16 * MBIT, BLOCK_32KB));
        assertArrayEquals(slice(input, BLOCK_32KB, BLOCK_32KB), slice(output, 0, BLOCK_32KB));
        // Region two starts at source 16 Mbit: lower to 12 Mbit, upper to 8 Mbit
        assertArrayEquals(slice(input, 16 * MBIT, BLOCK_32KB), slice(output, 12 * MBIT, BLOCK_32KB));
        assertArrayEquals(slice(input, 16 * MBIT + BLOCK_32KB, BLOCK_32KB), slice(output, 8 * MBIT, BLOCK_32KB));
        // The 2 Mbit holes at 10-12 and 14-16 Mbit repeat the data before them
        assertArrayEquals(slice(output, 8 * MBIT, 2 * MBIT), slice(output, 10 * MBIT, 2 * MBIT));
        assertArrayEquals(slice(output, 12 * MBIT, 2 * MBIT), slice(output, 14 * MBIT, 2 * MBIT));
    }

    @Test
    void twentyEightMbitHiRomIsZeroPaddedToThirtyTwo() {
        byte[] input = nonZeroBlocks(28 * MBIT);

        byte[] output = interleaveHiRom(input);

        assertEquals(32 * MBIT, output.length);
        long zeros = 0;
        for (byte b : output) {
            if (b == 0) {
                zeros++;
            }
        }
        assertEquals(4L * MBIT, zeros, "4 Mbit of zero fill, no mirroring");
    }

    @Test
    void extendedHiRomPlacesTheExpansionBeforeTheBase() {
        byte[] input = nonZeroBlocks(48 * MBIT);

        byte[] output = interleaveExtended(input);

        int size2 = 16 * MBIT;
        // Expansion (source 32 Mbit on) is written first: its upper 32 KB of block 0 lands at 0 ...
        assertArrayEquals(slice(input, 32 * MBIT + BLOCK_32KB, BLOCK_32KB), slice(output, 0, BLOCK_32KB));
        // ... and the 32 Mbit base follows at size2: upper 32 KB of block 0 at size2, lower at size2 + 16 Mbit
        assertArrayEquals(slice(input, BLOCK_32KB, BLOCK_32KB), slice(output, size2, BLOCK_32KB));
        assertArrayEquals(slice(input, 0, BLOCK_32KB), slice(output, size2 + 16 * MBIT, BLOCK_32KB));
    }

    @Property(tries = 30)
    void interleaveThenDeinterleaveRestoresTheSource(@ForAll("layoutSizes") SnesRom rom) {
        Gd3Layout layout = Gd3Layout.of(rom);

        byte[] interleaved = interleaver.interleave(rom.rawData(), layout);
        byte[] restored = interleaver.deinterleave(interleaved, layout.interleave());

        assertArrayEquals(rom.rawData(), Arrays.copyOf(restored, rom.rawData().length),
                "first " + rom.rawData().length + " bytes survive padding and interleave");
    }

    @Provide
    Arbitrary<SnesRom> layoutSizes() {
        // 21-23 Mbit is left out on purpose: ucon64's second-region interleave of a 24 Mbit image only covers
        // (size - 16 Mbit) and its 10-12 Mbit mirror overwrites the tail of that data, so ucon64 itself cannot
        // restore these sizes. The layout reproduces it; no real ROM has such a size.
        Arbitrary<SnesRom> hiRom = Arbitraries.integers().between(2, 32).filter(m -> m < 21 || m > 23)
                .map(m -> hiRomOf(m, false));
        Arbitrary<SnesRom> extended = Arbitraries.of(40, 48, 64).map(m -> hiRomOf(m, true));
        return Arbitraries.oneOf(hiRom, extended);
    }

    private static SnesRom hiRomOf(int sizeMbit, boolean extended) {
        return rom(nonZeroBlocks(sizeMbit * MBIT), RomType.HiROM, extended);
    }

    private static SnesRom rom(byte[] data, RomType type, boolean extended) {
        return new SnesRom(data, type, 0, "TEST", false, 0, 0, 0, 0x0001, 0xFFFE,
                extended ? SnesRom.EXTENDED_BASE : 0, false, false);
    }

    private byte[] interleaveHiRom(byte[] input) {
        SnesRom rom = rom(input, RomType.HiROM, false);
        return interleaver.interleave(input, Gd3Layout.of(rom));
    }

    private byte[] interleaveExtended(byte[] input) {
        SnesRom rom = rom(input, RomType.HiROM, true);
        return interleaver.interleave(input, Gd3Layout.of(rom));
    }

    private byte[] interleaveLoRom(byte[] input) {
        SnesRom rom = rom(input, RomType.LoROM, false);
        return interleaver.interleave(input, Gd3Layout.of(rom));
    }

    private static byte[] slice(byte[] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    // No zero bytes, and every 32 KB block distinct: a wrong block order or a zero-fill miscount shows up
    private static byte[] nonZeroBlocks(int sizeBytes) {
        byte[] data = new byte[sizeBytes];
        for (int i = 0; i < sizeBytes; i++) {
            data[i] = (byte) (1 + ((i >> 15) * 31 + i * 7) % 255);
        }
        return data;
    }

    @Property
    void testExtendedHiRomSplitInterleaving(@ForAll("exHiRomData") byte[] input) {
        byte[] output = interleaveExtended(input);

        // Split-interleaving preserves all data
        assertEquals(input.length, output.length);

        long inputSum = 0;
        long outputSum = 0;
        for (int i = 0; i < input.length; i++) {
            inputSum += input[i] & 0xFF;
            outputSum += output[i] & 0xFF;
        }
        assertEquals(inputSum, outputSum, "Extended HiROM split-interleaving should preserve all bytes");
    }

    private record SchemeCase(Gd3Interleave scheme, int sizeMbit) {
    }

    // Every 32 KB block starts with its own index, so a wrong block table cannot compare equal
    private static byte[] uniqueBlocks(int sizeBytes) {
        byte[] data = new byte[sizeBytes];
        for (int offset = 0; offset < sizeBytes; offset += BLOCK_32KB) {
            ByteBuffer.wrap(data).putInt(offset, offset / BLOCK_32KB + 1);
            for (int i = offset + 4; i < offset + 64; i++) {
                data[i] = (byte) (i * 31 + offset);
            }
        }
        return data;
    }

    @Property(tries = 40)
    void deinterleaveInvertsReferenceInterleave(@ForAll("schemeCases") SchemeCase testCase) {
        byte[] linear = uniqueBlocks(testCase.sizeMbit() * SnesConstants.MBIT);

        byte[] interleaved = ReferenceInterleaver.interleave(linear, testCase.scheme());

        assertArrayEquals(linear, interleaver.deinterleave(interleaved, testCase.scheme()),
                testCase.scheme() + " at " + testCase.sizeMbit() + " Mbit");
    }

    @Test
    void deinterleaveNoneReturnsInputUnchanged() {
        byte[] input = uniqueBlocks(8 * SnesConstants.MBIT);
        assertSame(input, interleaver.deinterleave(input, Gd3Interleave.NONE));
    }

    @Provide
    Arbitrary<SchemeCase> schemeCases() {
        return Arbitraries.oneOf(
                Arbitraries.of(8, 12, 16, 24, 32, 40, 48, 64).map(size -> new SchemeCase(Gd3Interleave.GLOBAL, size)),
                Arbitraries.just(new SchemeCase(Gd3Interleave.TWO_REGION_24, 24)),
                Arbitraries.of(40, 48, 64).map(size -> new SchemeCase(Gd3Interleave.EXTENDED, size)));
    }

    @Test
    void testLoRomNoInterleaving() {
        byte[] input = new byte[CHUNK_8MBIT];
        Arrays.fill(input, (byte) 0xAA);

        // LoROM data is not interleaved per GD3 copier specification (ucon64 snes.c:1148-1160 interleaves only HiROM)
        byte[] output = interleaveLoRom(input);

        assertArrayEquals(input, output, "LoROM data should not be interleaved");
    }

    @Provide
    Arbitrary<byte[]> data8MbitAligned() {
        return Arbitraries.integers()
                .between(1, 4)
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
    Arbitrary<byte[]> romData8MbitMultiple() {
        return Arbitraries.integers()
                .between(1, 4)
                .map(chunks -> new byte[chunks * CHUNK_8MBIT])
                .map(this::fillWithRandomPattern);
    }

    @Provide
    Arbitrary<byte[]> exHiRomData() {
        return Arbitraries.of(5, 6, 8) // 40, 48 and 64 Mbit: the extended sizes Gd3Layout converts
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
