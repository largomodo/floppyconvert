package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;

import java.util.Locale;

/**
 * Hardware validator for Game Doctor (GD3/SF3/SF7) backup unit format.
 * <p>
 * Rules in order (ref: DL-010):
 * <ol>
 *   <li>Gd3Layout.of rejects what ucon64 snes_convert_to_gd rejects, with ucon64's wording.</li>
 *   <li>At most 16 parts of 4 Mbit (ucon64 GD3_MAX_UNITS, gd.h:32) except the 96 Mbit LoROM class.</li>
 *   <li>The 96 Mbit LoROM class carries no SRAM, or is the named Star Ocean case: any other SRAM map
 *       would be unverified.</li>
 * </ol>
 * The DRAM fitted to the target unit is not checked: a set too large for it fails to load on the copier,
 * which is harmless.
 */
public class Gd3HardwareValidator implements HardwareValidator {

    // ucon64 GD3_MAX_UNITS: the most 4 Mbit parts a Game Doctor holds.
    private static final int GD3_MAX_UNITS = 16;

    @Override
    public void validate(SnesRom rom, CopierFormat format) throws UnsupportedHardwareException {
        if (format != CopierFormat.GD3) {
            return;
        }

        Gd3Layout layout = Gd3Layout.of(rom);
        boolean loRom96 = layout.isLoRom96();

        if (!loRom96 && layout.totalParts4Mbit() > GD3_MAX_UNITS) {
            throw new UnsupportedHardwareException(String.format(Locale.ROOT,
                    "ROM needs %d parts of 4 Mbit; GD3 hardware supports at most %d",
                    layout.totalParts4Mbit(), GD3_MAX_UNITS));
        }
        if (loRom96 && rom.sramSize() > 0 && !SpecialCaseRegistry.isStarOcean96(rom, layout)) {
            throw new UnsupportedHardwareException(
                    "96 Mbit LoROM with SRAM is only converted for Star Ocean: no verified SRAM map for this ROM");
        }
    }
}
