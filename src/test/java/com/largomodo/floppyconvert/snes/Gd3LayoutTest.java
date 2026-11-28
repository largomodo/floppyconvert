package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.snes.Gd3Layout.PadStrategy;
import net.jqwik.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gd3Layout against ucon64 snes_convert_to_gd (snes.c 1733-1897): the table pins one row per size class, the
 * properties pin the invariants of the ported rules: 5 parts round to 6 and 7 to 8, mirror padding only for 3, 6
 * and 12 parts, LoROM is never padded.
 */
class Gd3LayoutTest {

    private static final int MBIT = SnesConstants.MBIT;

    private static SnesRom rom(boolean hiRom, boolean extended, int sizeBytes) {
        return new SnesRom(new byte[sizeBytes], hiRom ? RomType.HiROM : RomType.LoROM, 0, "TEST", false, 0, 0, 0,
                0x0001, 0xFFFE, extended ? SnesRom.EXTENDED_BASE : 0, false, false);
    }

    static Stream<Arguments> hiRomTable() {
        return Stream.of(
                // sizeMbit, parts, paddedMbit, pad, interleave, nameMbit
                Arguments.of(2, 1, 2, PadStrategy.NONE, Gd3Interleave.GLOBAL, 2),
                Arguments.of(3, 1, 3, PadStrategy.NONE, Gd3Interleave.GLOBAL, 3),
                Arguments.of(4, 1, 4, PadStrategy.NONE, Gd3Interleave.GLOBAL, 4),
                Arguments.of(8, 2, 8, PadStrategy.NONE, Gd3Interleave.GLOBAL, 8),
                Arguments.of(10, 3, 12, PadStrategy.MIRROR_HALVES, Gd3Interleave.GLOBAL, 12),
                Arguments.of(12, 3, 12, PadStrategy.NONE, Gd3Interleave.GLOBAL, 12),
                Arguments.of(16, 4, 16, PadStrategy.NONE, Gd3Interleave.GLOBAL, 16),
                Arguments.of(20, 6, 24, PadStrategy.MIRROR_HALVES, Gd3Interleave.TWO_REGION_24, 24),
                Arguments.of(24, 6, 24, PadStrategy.NONE, Gd3Interleave.TWO_REGION_24, 24),
                Arguments.of(28, 8, 32, PadStrategy.ZERO, Gd3Interleave.GLOBAL, 32),
                Arguments.of(32, 8, 32, PadStrategy.NONE, Gd3Interleave.GLOBAL, 32)
        );
    }

    @ParameterizedTest(name = "HiROM {0} Mbit")
    @MethodSource("hiRomTable")
    void hiRomLayoutMatchesUcon64(int sizeMbit, int parts, int paddedMbit, PadStrategy pad, Gd3Interleave interleave,
                                  int nameMbit) {
        Gd3Layout layout = Gd3Layout.of(rom(true, false, sizeMbit * MBIT));

        assertEquals(new Gd3Layout(parts, paddedMbit * MBIT, pad, interleave, nameMbit, true, false), layout);
    }

    static Stream<Arguments> extendedTable() {
        return Stream.of(
                Arguments.of(true, 40, 10),
                Arguments.of(true, 48, 12),
                Arguments.of(true, 64, 16),
                Arguments.of(false, 40, 10),
                Arguments.of(false, 48, 12),
                Arguments.of(false, 64, 16)
        );
    }

    @ParameterizedTest(name = "extended hiRom={0} {1} Mbit")
    @MethodSource("extendedTable")
    void extendedLayoutIsNeverPadded(boolean hiRom, int sizeMbit, int parts) {
        Gd3Layout layout = Gd3Layout.of(rom(hiRom, true, sizeMbit * MBIT));

        assertEquals(parts, layout.totalParts4Mbit());
        assertEquals(sizeMbit * MBIT, layout.paddedSize());
        assertEquals(PadStrategy.NONE, layout.padStrategy());
        assertEquals(hiRom ? Gd3Interleave.EXTENDED : Gd3Interleave.NONE, layout.interleave());
        assertEquals(sizeMbit, layout.sizeMbitForName());
    }

    @ParameterizedTest(name = "LoROM {0} Mbit")
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 4, 8, 9, 10, 12, 16, 20, 24, 28, 32})
    void loRomIsNeverPaddedNorInterleaved(int sizeMbit) {
        Gd3Layout layout = Gd3Layout.of(rom(false, false, sizeMbit * MBIT));

        assertEquals(sizeMbit * MBIT, layout.paddedSize());
        assertEquals(PadStrategy.NONE, layout.padStrategy());
        assertEquals(Gd3Interleave.NONE, layout.interleave());
        assertEquals(sizeMbit, layout.sizeMbitForName());
        assertFalse(layout.isLoRom96());
    }

    @org.junit.jupiter.api.Test
    void loRom96MbitIsTheNamedExceptionClass() {
        Gd3Layout layout = Gd3Layout.of(rom(false, false, 96 * MBIT));

        assertTrue(layout.isLoRom96());
        assertEquals(24, layout.totalParts4Mbit());
        assertEquals(96, layout.sizeMbitForName());
        assertEquals(Gd3Interleave.NONE, layout.interleave());
    }

    static Stream<Arguments> rejections() {
        return Stream.of(
                Arguments.of(true, false, MBIT, "ROM size is 1.00000 Mbit -- conversion not yet implemented/verified"),
                Arguments.of(true, false, 33 * MBIT, "ROM size is 33.00000 Mbit -- conversion not yet implemented/verified"),
                Arguments.of(true, false, 32 * MBIT + 1, "ROM size is 32.00001 Mbit -- conversion not yet implemented/verified"),
                Arguments.of(true, true, 44 * MBIT, "ROM size is 44.00000 Mbit -- conversion not yet implemented/verified"),
                Arguments.of(true, false, 40 * MBIT, "Normal ROM > 32 Mbit -- conversion not yet implemented"),
                Arguments.of(false, false, 33 * MBIT, "LoROM > 32 Mbit -- cannot convert"),
                Arguments.of(false, false, 40 * MBIT, "LoROM > 32 Mbit -- cannot convert")
        );
    }

    @ParameterizedTest
    @MethodSource("rejections")
    void rejectionsCarryUcon64Wording(boolean hiRom, boolean extended, int sizeBytes, String message) {
        SnesRom rom = rom(hiRom, extended, sizeBytes);

        UnsupportedHardwareException e = assertThrows(UnsupportedHardwareException.class, () -> Gd3Layout.of(rom));

        assertEquals(message, e.getMessage());
    }

    @Property(tries = 60)
    void layoutInvariantsHold(@ForAll("layoutInputs") LayoutInput input) {
        Gd3Layout layout = Gd3Layout.of(rom(input.hiRom(), input.extended(), input.sizeBytes()));

        assertEquals(layout.paddedSize() / MBIT, layout.sizeMbitForName());
        assertTrue(layout.paddedSize() >= input.sizeBytes(), "padding never truncates");
        assertTrue(layout.totalParts4Mbit() <= 16, "GD3_MAX_UNITS");
        if (!layout.hiRom()) {
            assertEquals(input.sizeBytes(), layout.paddedSize(), "LoROM is never padded");
        } else if (input.sizeBytes() >= 4 * MBIT) {
            assertEquals(0, layout.paddedSize() % (4 * MBIT), "HiROM from 4 Mbit pads to whole 4 Mbit parts");
            assertEquals(layout.paddedSize(), layout.totalParts4Mbit() * 4 * MBIT);
        } else {
            assertEquals(0, layout.paddedSize() % MBIT, "HiROM below 4 Mbit rounds up to 1 Mbit");
        }
    }

    record LayoutInput(boolean hiRom, boolean extended, int sizeBytes) {
    }

    @Provide
    Arbitrary<LayoutInput> layoutInputs() {
        Arbitrary<LayoutInput> hiRom = Arbitraries.integers().between(2, 32).map(m -> new LayoutInput(true, false, m * MBIT));
        Arbitrary<LayoutInput> loRom = Arbitraries.integers().between(1, 32).map(m -> new LayoutInput(false, false, m * MBIT));
        Arbitrary<LayoutInput> extended = Combinators.combine(Arbitraries.of(true, false), Arbitraries.of(40, 48, 64))
                .as((hi, m) -> new LayoutInput(hi, true, m * MBIT));
        return Arbitraries.oneOf(hiRom, loRom, extended);
    }
}
