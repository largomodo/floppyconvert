package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CopierFormatTest {

    @ParameterizedTest
    @CsvSource({
        ".fig,FIG",
        "fig,FIG",
        ".FIG,FIG",
        "FIG,FIG",
        ".swc,SWC",
        "swc,SWC",
        ".SWC,SWC",
        "SWC,SWC",
        ".ufo,UFO",
        "ufo,UFO",
        ".UFO,UFO",
        "UFO,UFO"
    })
    void fromFileExtension_recognizedFormats_returnsCorrectFormat(String extension, String expectedFormat) {
        Optional<CopierFormat> result = CopierFormat.fromFileExtension(extension);
        assertTrue(result.isPresent());
        assertEquals(CopierFormat.valueOf(expectedFormat), result.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {".sfc", "sfc", ".078", "078", ".bin", "bin", ""})
    @NullSource
    void fromFileExtension_unrecognizedFormats_returnsEmpty(String extension) {
        Optional<CopierFormat> result = CopierFormat.fromFileExtension(extension);
        assertFalse(result.isPresent());
    }

    @ParameterizedTest
    @CsvSource({".Swc,SWC", "Ufo,UFO", ".FiG,FIG"})
    void fromFileExtension_mixedCase_caseInsensitive(String extension, String expectedFormat) {
        Optional<CopierFormat> result = CopierFormat.fromFileExtension(extension);
        assertTrue(result.isPresent());
        assertEquals(CopierFormat.valueOf(expectedFormat), result.get());
    }
}
