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
    @ValueSource(strings = {"SF4SUP.078", "SF12ACTA.078", "SF8ABC_XA.078", "SF96SOEL.078", "sf4.078", "SF16CHRB.078"})
    // Split output is collected by name, so the filter must accept every shape ucon64 writes, including the
    // unlettered single-part form.
    void gd3SplitPartFilter_acceptsUcon64PartNames(String name) {
        assertTrue(CopierFormat.GD3.getSplitPartFilter().test(new java.io.File(name)), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo.078", "SF.078", "SF4SUP.079", "SF4TOOLONGNAME.078", "game.sfc"})
    void gd3SplitPartFilter_rejectsOtherFiles(String name) {
        assertFalse(CopierFormat.GD3.getSplitPartFilter().test(new java.io.File(name)), name);
    }

    @ParameterizedTest
    @CsvSource({".Swc,SWC", "Ufo,UFO", ".FiG,FIG"})
    void fromFileExtension_mixedCase_caseInsensitive(String extension, String expectedFormat) {
        Optional<CopierFormat> result = CopierFormat.fromFileExtension(extension);
        assertTrue(result.isPresent());
        assertEquals(CopierFormat.valueOf(expectedFormat), result.get());
    }
}
