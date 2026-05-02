// DosName contract tests: construction validation, sanitization parity with DosNameUtil.sanitize,
// equality on sanitized value, and format-specific extension preservation. (ref: DL-002)
package com.largomodo.floppyconvert.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DosNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void emptyOrBlankInputThrowsIllegalArgumentException(String input) {
        assertThrows(IllegalArgumentException.class, () -> DosName.of(input));
    }

    @Test
    void nullInputThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> DosName.of(null));
    }

    @ParameterizedTest
    @CsvSource({
            "game.sfc, GAME.SFC",
            "my game.sfc, MYGAME.SFC",
            "UPPER.SFC, UPPER.SFC",
            "longfilename.sfc, LONGFILE.SFC",
            "a.b.c.sfc, ABC.SFC",
    })
    void asciiNameProducesExpectedDosName(String input, String expected) {
        DosName dosName = DosName.of(input);
        assertEquals(expected, dosName.value());
    }

    @ParameterizedTest
    @CsvSource({
            "game[hack].sfc, GAMEHACK.SFC",
            "game#1.sfc, GAME1.SFC",
            "game (v2).sfc, GAMEV2.SFC",
    })
    void specialCharactersAreStripped(String input, String expected) {
        DosName dosName = DosName.of(input);
        assertEquals(expected, dosName.value());
    }

    @Test
    void overlongNameTruncatesTo8Chars() {
        DosName dosName = DosName.of("verylongfilename.sfc");
        String base = dosName.value().split("\\.")[0];
        assertTrue(base.length() <= 8, "Base name must be at most 8 characters");
    }

    @Test
    void overlongExtensionTruncatesTo3Chars() {
        DosName dosName = DosName.of("game.longext");
        String[] parts = dosName.value().split("\\.");
        if (parts.length > 1) {
            assertTrue(parts[1].length() <= 3, "Extension must be at most 3 characters");
        }
    }

    @ParameterizedTest
    @CsvSource({
            "game.1, GAME.1",
            "game.2, GAME.2",
            "game.078, GAME.078",
    })
    void numericExtensionsPreserved(String input, String expected) {
        DosName dosName = DosName.of(input);
        assertEquals(expected, dosName.value());
    }

    @Test
    void equalityComparesOnSanitizedValue() {
        DosName a = DosName.of("game.sfc");
        DosName b = DosName.of("GAME.SFC");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toStringReturnsValue() {
        DosName dosName = DosName.of("game.sfc");
        assertEquals(dosName.value(), dosName.toString());
    }

    @Test
    void dosNameMatchesDosNameUtilSanitize() {
        String[] inputs = {"game.sfc", "my game.sfc", "TITLE.1", "SF4CHR__.078"};
        for (String input : inputs) {
            assertEquals(DosNameUtil.sanitize(input), DosName.of(input).value(),
                    "DosName.of must produce same result as DosNameUtil.sanitize for: " + input);
        }
    }
}
