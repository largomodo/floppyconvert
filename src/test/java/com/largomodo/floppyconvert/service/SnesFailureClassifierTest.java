package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.core.FailureCategory;
import com.largomodo.floppyconvert.snes.InvalidChecksumException;
import com.largomodo.floppyconvert.snes.InvalidRomHeaderException;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException;
import com.largomodo.floppyconvert.snes.UnsupportedHardwareException.Reason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnesFailureClassifierTest {

    private final SnesFailureClassifier classifier = new SnesFailureClassifier();

    // Mapping is by exception type, so rewording a message cannot change the category.
    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new InvalidChecksumException(0x0001, 0x0000), FailureCategory.CHECKSUM),
                Arguments.of(new UnsupportedHardwareException(Reason.SIZE, "size"), FailureCategory.SIZE),
                Arguments.of(new UnsupportedHardwareException("legacy constructor"), FailureCategory.SIZE),
                Arguments.of(new UnsupportedHardwareException(Reason.HARDWARE, "dram"), FailureCategory.HARDWARE),
                Arguments.of(new InvalidRomHeaderException("too small"), FailureCategory.HEADER),
                Arguments.of(new IOException("disk full"), FailureCategory.OTHER),
                Arguments.of(new IllegalArgumentException("plain"), FailureCategory.OTHER),
                Arguments.of(new RuntimeException("wrapped", new InvalidChecksumException(0x0001, 0x0000)),
                        FailureCategory.CHECKSUM),
                Arguments.of(new IOException("wrapped", new UnsupportedHardwareException(Reason.HARDWARE, "dram")),
                        FailureCategory.HARDWARE)
        );
    }

    @ParameterizedTest
    @MethodSource("failures")
    void mapsExceptionToCategory(Exception failure, FailureCategory expected) {
        assertEquals(expected, classifier.classify(failure));
    }
}
