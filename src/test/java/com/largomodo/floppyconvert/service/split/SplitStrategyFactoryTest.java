// Exhaustive (CopierFormat, RomType) matrix verifying strategy class selection. (ref: DL-004)
package com.largomodo.floppyconvert.service.split;

import com.largomodo.floppyconvert.format.CopierFormat;
import com.largomodo.floppyconvert.snes.RomType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SplitStrategyFactoryTest {

    private final SplitStrategyFactory factory = new SplitStrategyFactory();

    @ParameterizedTest
    @MethodSource("strategyMatrix")
    void strategySelectionMatchesFormatAndRomType(CopierFormat format, RomType romType,
                                                  Class<? extends SplitStrategy> expectedClass) {
        SplitStrategy strategy = factory.get(format, romType);
        assertInstanceOf(expectedClass, strategy);
    }

    static Stream<Arguments> strategyMatrix() {
        return Stream.of(
                // GD3 uses one strategy for both map types because padding and interleaving precede the split.
                Arguments.of(CopierFormat.GD3, RomType.HiROM,   Gd3SplitStrategy.class),
                Arguments.of(CopierFormat.GD3, RomType.LoROM,   Gd3SplitStrategy.class),
                Arguments.of(CopierFormat.UFO, RomType.HiROM,   UfoHiRomSplitStrategy.class),
                Arguments.of(CopierFormat.UFO, RomType.LoROM,   StandardSplitStrategy.class),
                Arguments.of(CopierFormat.FIG, RomType.LoROM,   StandardSplitStrategy.class),
                Arguments.of(CopierFormat.FIG, RomType.HiROM,   StandardSplitStrategy.class),
                Arguments.of(CopierFormat.SWC, RomType.LoROM,   StandardSplitStrategy.class),
                Arguments.of(CopierFormat.SWC, RomType.HiROM,   StandardSplitStrategy.class)
        );
    }
}
