package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesInterleaver;

/**
 * Factory for per-format split strategies. (ref: DL-004, DL-005)
 * <p>
 * Mirrors HeaderGeneratorFactory: singleton instances wired once, selected by
 * (CopierFormat, RomType) switch. Placement in service.split preserves the dependency
 * direction (service depends on snes/format abstractions, not the reverse); this mirrors
 * the snes/header subpackage layout. (ref: DL-005)
 * SnesInterleaver is injected at construction to avoid repeated allocations per split call.
 * GD3 LoROM and standard paths share StandardSplitStrategy with different chunk sizes.
 */
public class SplitStrategyFactory {

    private final StandardSplitStrategy standard;
    private final StandardSplitStrategy gd3LoRom;
    private final UfoHiRomSplitStrategy ufoHiRom;
    private final Gd3HiRomSplitStrategy gd3HiRom;

    public SplitStrategyFactory(SnesInterleaver interleaver) {
        this.standard = new StandardSplitStrategy();
        this.gd3LoRom = new StandardSplitStrategy(StandardSplitStrategy.MBIT_8);
        this.ufoHiRom = new UfoHiRomSplitStrategy();
        this.gd3HiRom = new Gd3HiRomSplitStrategy(interleaver);
    }

    /**
     * Returns the strategy for the given (format, romType) combination.
     * GD3 + (HiROM or ExHiROM) -> Gd3HiRomSplitStrategy.
     * GD3 + LoROM -> StandardSplitStrategy with MBIT_8 (existing GD3 chunk size).
     * UFO + (HiROM or ExHiROM) -> UfoHiRomSplitStrategy.
     * Everything else -> StandardSplitStrategy with MBIT_4.
     */
    public SplitStrategy get(CopierFormat format, RomType romType) {
        return switch (format) {
            case GD3 -> (romType == RomType.HiROM || romType == RomType.ExHiROM)
                    ? gd3HiRom : gd3LoRom;
            case UFO -> (romType == RomType.HiROM || romType == RomType.ExHiROM)
                    ? ufoHiRom : standard;
            case FIG, SWC -> standard;
        };
    }
}
