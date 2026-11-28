package com.largomodo.floppyconvert.snes;

import java.util.Locale;

/**
 * Game Doctor layout of one ROM: padded size, pad strategy, interleave scheme, part count and the size shown in
 * the file name. Single source of truth consumed by the validator, header generator, interleaver and splitter,
 * so a size rule fixed in one place cannot drift from the others; none of them derives sizes from the raw byte
 * count itself; {@link #isLoRom96Size} is the one size test the reader needs before a {@code SnesRom} exists.
 * <p>
 * Pure function of the ROM; the DRAM fitted to the target Game Doctor is not an input. Literal port of
 * ucon64 {@code snes_convert_to_gd} (snes.c 1733-1897, line numbers checked against the ucon64 2.2.3 source),
 * including its rejections and wording.
 * <p>
 * The rules form a chain: reader detection (header base, interleave) feeds the layout, and the layout feeds the
 * header map, interleaver, splitter and file name. Size classes build on one another (padding up to 32 Mbit, then
 * extended HiROM, then the 96 Mbit LoROM class), so the validator and the parity table cover every class while each
 * class closes a different set of rules.
 * <p>
 * Invariants: for HiROM of 4 Mbit and up, {@code paddedSize} is a multiple of 4 Mbit; LoROM is never padded,
 * interleaved or truncated ({@code paddedSize} equals the source size, interleave {@code NONE}); {@code totalParts4Mbit}
 * is at most 16 (24 for the 96 Mbit LoROM class); {@code sizeMbitForName} equals {@code paddedSize / Mbit}.
 * <p>
 * Hardware caveat: the HiROM 10, 12, 20 and 28 Mbit padding and interleave rules reproduce ucon64 and replace the
 * layouts earlier output used for those sizes, which may have booted on a Game Doctor SF7. They are unverified on
 * hardware until re-checked there. The record does
 * not enforce them; {@link #of} produces only conforming layouts and {@code Gd3HardwareValidator} rejects the rest.
 *
 * @param totalParts4Mbit  number of 4 Mbit units after padding (5 becomes 6, 7 becomes 8 for HiROM)
 * @param paddedSize       bytes of the image the Game Doctor receives
 * @param padStrategy      how bytes past the source size are filled
 * @param interleave       block interleave scheme applied to the image
 * @param sizeMbitForName  size digits of the part file names: paddedSize / Mbit, rounded down
 * @param hiRom            true for HiROM, extended or not
 * @param loRom96          true for a LoROM of 24 units with the header at base 0 (the 96 Mbit class)
 */
public record Gd3Layout(int totalParts4Mbit, int paddedSize, PadStrategy padStrategy, Gd3Interleave interleave,
                        int sizeMbitForName, boolean hiRom, boolean loRom96) {

    /**
     * How bytes beyond the source size are filled.
     */
    public enum PadStrategy {
        /** Image equals the source. */
        NONE,
        /** The padding repeats the tail of each interleaved half (parts 3, 6 and 12). */
        MIRROR_HALVES,
        /** The padding is zero filled. */
        ZERO
    }

    private static final int PART_4MBIT = 4 * SnesConstants.MBIT;
    // A 96 Mbit LoROM (Star Ocean, Street Fighter Alpha 2 pre-decompressed builds) is 24 units of 4 Mbit. (ref: DL-012)
    private static final int LOROM_96_PARTS = 24;

    /**
     * @throws UnsupportedHardwareException with ucon64's wording when ucon64 refuses the conversion
     */
    public static Gd3Layout of(SnesRom rom) {
        int size = rom.rawData().length;
        int fullParts = size / PART_4MBIT;
        int parts = fullParts + (size % PART_4MBIT > 0 ? 1 : 0);
        return rom.isHiRom() ? ofHiRom(rom, size, fullParts, parts) : ofLoRom(rom, size, parts);
    }

    /**
     * @return true for the 96 Mbit LoROM class, which only a Game Doctor with 128 Mbit of DRAM can load
     */
    public boolean isLoRom96() {
        return loRom96;
    }

    /**
     * @return true when a raw size of more than 92 and at most 96 Mbit rounds up to 24 units of 4 Mbit
     */
    public static boolean isLoRom96Size(int length) {
        return (length + PART_4MBIT - 1) / PART_4MBIT == LOROM_96_PARTS;
    }

    private static Gd3Layout ofHiRom(SnesRom rom, int size, int fullParts, int parts) {
        boolean sizeConvertible = (size >= 2 * SnesConstants.MBIT && parts <= 8)
                || parts == 10 || parts == 12 || parts == 16;
        if (!sizeConvertible) {
            // %.5f keeps byte resolution, so 32 Mbit + 1 byte reads 32.00001; the message is ucon64's wording.
            throw new UnsupportedHardwareException(String.format(Locale.ROOT,
                    "ROM size is %.5f Mbit -- conversion not yet implemented/verified", (double) size / SnesConstants.MBIT));
        }
        if (parts > 8 && !rom.isExtended()) {
            throw new UnsupportedHardwareException(
                    "Normal ROM > 32 Mbit -- conversion not yet implemented");
        }

        // 20 Mbit HiROMs are padded to 24 Mbit and 28 Mbit HiROMs to 32 Mbit. A 40 Mbit HiROM stays 10 parts:
        // ucon64 ships with PAD_40MBIT_GD3_DUMPS off, so 10 parts are not rounded up to 12. The rounding is ucon64's:
        // 5 parts become 6 and 7 become 8.
        if (parts == 5) {
            parts = 6;
        } else if (parts == 7) {
            parts = 8;
        }

        int paddedSize = fullParts > 0
                ? PART_4MBIT * parts
                : (size + SnesConstants.MBIT - 1) / SnesConstants.MBIT * SnesConstants.MBIT;

        Gd3Interleave interleave;
        if (rom.isExtended()) {
            // The expansion past 32 Mbit is placed before the 32 Mbit base, the order of ucon64's extended block
            // table (snes.c 1818-1855).
            interleave = Gd3Interleave.EXTENDED;
        } else if (parts == 6) {
            interleave = Gd3Interleave.TWO_REGION_24;
        } else {
            interleave = Gd3Interleave.GLOBAL;
        }

        return new Gd3Layout(parts, paddedSize, padStrategy(size, paddedSize, parts), interleave,
                paddedSize / SnesConstants.MBIT, true, false);
    }

    // Mirror padding only for 3, 6 and 12 parts: the mirrored region is the half-sized hole an odd part count leaves
    private static PadStrategy padStrategy(int size, int paddedSize, int parts) {
        if ((paddedSize - size) / 2 > 0 && (parts == 3 || parts == 6 || parts == 12)) {
            return PadStrategy.MIRROR_HALVES;
        }
        return paddedSize > size ? PadStrategy.ZERO : PadStrategy.NONE;
    }

    private static Gd3Layout ofLoRom(SnesRom rom, int size, int parts) {
        if (parts > 8 && !rom.isExtended() && parts != LOROM_96_PARTS) {
            throw new UnsupportedHardwareException(
                    "LoROM > 32 Mbit -- cannot convert");
        }
        // LoROM is never padded, never interleaved and never truncated
        return new Gd3Layout(parts, size, PadStrategy.NONE, Gd3Interleave.NONE, size / SnesConstants.MBIT,
                false, parts == LOROM_96_PARTS && !rom.isExtended());
    }
}
