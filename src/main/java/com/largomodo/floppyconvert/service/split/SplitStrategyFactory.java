package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.RomType;

/**
 * Factory for per-format split strategies. (ref: DL-004, DL-005)
 * <p>
 * Mirrors HeaderGeneratorFactory: singleton instances wired once, selected by
 * (CopierFormat, RomType) switch. Placement in service.split preserves the dependency
 * direction (service depends on snes/format abstractions, not the reverse); this mirrors
 * the snes/header subpackage layout. (ref: DL-005)
 * GD3 pads and interleaves before the strategy runs, so one Gd3SplitStrategy serves HiROM and LoROM. (ref: DL-007)
 */
public class SplitStrategyFactory {

    private final StandardSplitStrategy standard;
    private final UfoHiRomSplitStrategy ufoHiRom;
    // One GD3 strategy serves HiROM and LoROM because padding and interleaving happen before the split.
    private final Gd3SplitStrategy gd3;

    public SplitStrategyFactory() {
        this.standard = new StandardSplitStrategy();
        this.ufoHiRom = new UfoHiRomSplitStrategy();
        this.gd3 = new Gd3SplitStrategy();
    }

    /**
     * Returns the strategy for the given (format, romType) combination.
     * GD3 + any type -> Gd3SplitStrategy.
     * UFO + HiROM (extended or not) -> UfoHiRomSplitStrategy.
     * Everything else -> StandardSplitStrategy with MBIT_4.
     */
    public SplitStrategy get(CopierFormat format, RomType romType) {
        return switch (format) {
            case GD3 -> gd3;
            case UFO -> romType == RomType.HiROM ? ufoHiRom : standard;
            case FIG, SWC -> standard;
        };
    }
}
