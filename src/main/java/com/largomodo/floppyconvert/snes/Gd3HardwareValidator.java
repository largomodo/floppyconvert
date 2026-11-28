package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException.Reason;

import java.util.Locale;

/**
 * Hardware validator for Game Doctor (GD3/SF3/SF7) backup unit format.
 * <p>
 * Rules in order (ref: DL-010):
 * <ol>
 *   <li>Gd3Layout.of rejects what ucon64 snes_convert_to_gd rejects, with ucon64's wording (SIZE).</li>
 *   <li>At most 16 parts of 4 Mbit (ucon64 GD3_MAX_UNITS, gd.h:32) except the 96 Mbit LoROM class (SIZE).</li>
 *   <li>The 96 Mbit LoROM class carries no SRAM, or is the named Star Ocean case (SIZE): any other SRAM map
 *       would be unverified.</li>
 *   <li>The padded image fits the configured DRAM (HARDWARE): a larger configuration would convert it.</li>
 * </ol>
 */
public class Gd3HardwareValidator implements HardwareValidator {

    // ucon64 GD3_MAX_UNITS: the most 4 Mbit parts a Game Doctor holds.
    private static final int GD3_MAX_UNITS = 16;
    // Game Doctor DRAM sizes in ascending order, used to name the smallest option that fits.
    private static final int[] DRAM_SIZES_MBIT = {32, 64, 128};

    private final Gd3Memory memory;

    public Gd3HardwareValidator() {
        this(Gd3Memory.DEFAULT);
    }

    public Gd3HardwareValidator(Gd3Memory memory) {
        this.memory = memory;
    }

    @Override
    public void validate(SnesRom rom, CopierFormat format) throws UnsupportedHardwareException {
        if (format != CopierFormat.GD3) {
            return;
        }

        Gd3Layout layout = Gd3Layout.of(rom);
        boolean loRom96 = layout.isLoRom96();

        if (!loRom96 && layout.totalParts4Mbit() > GD3_MAX_UNITS) {
            throw new UnsupportedHardwareException(Reason.SIZE, String.format(Locale.ROOT,
                    "ROM needs %d parts of 4 Mbit; GD3 hardware supports at most %d",
                    layout.totalParts4Mbit(), GD3_MAX_UNITS));
        }
        if (loRom96 && rom.sramSize() > 0 && !SpecialCaseRegistry.isStarOcean96(rom, layout)) {
            throw new UnsupportedHardwareException(Reason.SIZE,
                    "96 Mbit LoROM with SRAM is only converted for Star Ocean: no verified SRAM map for this ROM");
        }
        if (layout.paddedSize() > memory.dramBytes()) {
            int neededMbit = layout.paddedSize() / SnesConstants.MBIT;
            throw new UnsupportedHardwareException(Reason.HARDWARE, String.format(Locale.ROOT,
                    "ROM needs %d Mbit of Game Doctor DRAM but %d Mbit is configured; rerun with --gd3-dram %d",
                    neededMbit, memory.dramMbit(), smallestDramFor(neededMbit)));
        }
    }

    private static int smallestDramFor(int neededMbit) {
        for (int dram : DRAM_SIZES_MBIT) {
            if (dram >= neededMbit) {
                return dram;
            }
        }
        return DRAM_SIZES_MBIT[DRAM_SIZES_MBIT.length - 1];
    }
}
