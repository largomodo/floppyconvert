package com.largomodo.floppyconvert.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DosNameUtilTest {

    @Test
    void testSimpleSanitization() {
        assertEquals("MARIO.FIG", DosNameUtil.sanitize("mario.fig"));
        assertEquals("GAME.SFC", DosNameUtil.sanitize("game.sfc"));
    }

    @Test
    void testTruncation() {
        assertEquals("SUPERMAR.SFC", DosNameUtil.sanitize("Super Mario.sfc"));
        assertEquals("LONGGAME.1", DosNameUtil.sanitize("LongGameName.1"));
        assertEquals("VERYLONG.SFC", DosNameUtil.sanitize("VeryLongGameName.sfc"));
    }

    @Test
    void testSpecialCharacterRemoval() {
        assertEquals("ZELDALIN.2", DosNameUtil.sanitize("Zelda: Link.2"));
        assertEquals("GAME.FIG", DosNameUtil.sanitize("Game!@#$.fig"));
        assertEquals("FINALFAN.SFC", DosNameUtil.sanitize("Final-Fantasy.sfc"));
    }

    @Test
    void testNumericExtensions() {
        assertEquals("GAME.1", DosNameUtil.sanitize("game.1"));
        assertEquals("GAME.2", DosNameUtil.sanitize("game.2"));
        assertEquals("GAME.99", DosNameUtil.sanitize("game.99"));
    }

    @Test
    void testMultipleDots() {
        assertEquals("ABC.SFC", DosNameUtil.sanitize("A.B.C.sfc"));
        assertEquals("GAMEV10.ROM", DosNameUtil.sanitize("game.v1.0.rom"));
    }

    @Test
    void testEdgeCases() {
        assertThrows(IllegalArgumentException.class, () -> DosNameUtil.sanitize(null));
        assertThrows(IllegalArgumentException.class, () -> DosNameUtil.sanitize(""));
        assertThrows(IllegalArgumentException.class, () -> DosNameUtil.sanitize("   "));

        // No extension cases
        assertEquals("NOEXT", DosNameUtil.sanitize("noext"));

        // All special characters
        assertEquals("A.B", DosNameUtil.sanitize("!@#$a%^&*.!@#b$%^"));
    }
}
