package com.largomodo.floppyconvert.core;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RomPartComparatorTest {

    private final RomPartComparator comparator = new RomPartComparator();

    @Test
    void testNumericExtensionOrdering() {
        File game1 = new File("Game.1");
        File game2 = new File("Game.2");
        File game10 = new File("Game.10");

        assertTrue(comparator.compare(game1, game2) < 0, "Game.1 should come before Game.2");
        assertTrue(comparator.compare(game2, game10) < 0, "Game.2 should come before Game.10");
        assertTrue(comparator.compare(game1, game10) < 0, "Game.1 should come before Game.10");

        List<File> files = Arrays.asList(game10, game1, game2);
        files.sort(comparator);
        assertEquals(Arrays.asList(game1, game2, game10), files, "Should sort numerically: .1, .2, .10");
    }

    @Test
    void testAlphanumericFilenameOrdering() {
        File chrA = new File("SF32CHRA.078");
        File chrB = new File("SF32CHRB.078");
        File chrC = new File("SF32CHRC.078");

        assertTrue(comparator.compare(chrA, chrB) < 0, "SF32CHRA should come before SF32CHRB");
        assertTrue(comparator.compare(chrB, chrC) < 0, "SF32CHRB should come before SF32CHRC");
        assertTrue(comparator.compare(chrA, chrC) < 0, "SF32CHRA should come before SF32CHRC");

        List<File> files = Arrays.asList(chrC, chrA, chrB);
        files.sort(comparator);
        assertEquals(Arrays.asList(chrA, chrB, chrC), files, "Should sort alphabetically by filename");
    }

    @Test
    void testMixedScenario() {
        File gameA1 = new File("GAMEA.1");
        File gameA2 = new File("GAMEA.2");
        File gameB1 = new File("GAMEB.1");

        assertTrue(comparator.compare(gameA1, gameA2) < 0, "GAMEA.1 should come before GAMEA.2");
        assertTrue(comparator.compare(gameA2, gameB1) < 0, "GAMEA.2 should come before GAMEB.1");
        assertTrue(comparator.compare(gameA1, gameB1) < 0, "GAMEA.1 should come before GAMEB.1");

        List<File> files = Arrays.asList(gameB1, gameA2, gameA1);
        files.sort(comparator);
        assertEquals(Arrays.asList(gameA1, gameA2, gameB1), files,
                "Filename takes precedence over extension");
    }

    @Test
    void testEdgeCases() {
        // Files without extensions
        File noExt1 = new File("game");
        File noExt2 = new File("game");
        assertEquals(0, comparator.compare(noExt1, noExt2), "Files without extensions should be equal");

        File withExt = new File("game.1");
        assertTrue(comparator.compare(noExt1, withExt) < 0,
                "File without extension should come before file with numeric extension");

        // Single character names
        File a = new File("a.1");
        File b = new File("b.1");
        assertTrue(comparator.compare(a, b) < 0, "Single character comparison should work");

        // Empty extensions (files ending with dot)
        File emptyExt1 = new File("game.");
        File emptyExt2 = new File("game.");
        assertEquals(0, comparator.compare(emptyExt1, emptyExt2),
                "Files with empty extensions should be equal");

        // Different basenames with empty extensions
        File gameEmpty = new File("game.");
        File testEmpty = new File("test.");
        assertTrue(comparator.compare(gameEmpty, testEmpty) < 0,
                "Should compare by basename when extensions are empty");
    }

    @Test
    void testUfoGmExtensionOrdering() {
        File game1gm = new File("Game.1gm");
        File game2gm = new File("Game.2gm");
        File game10gm = new File("Game.10gm");

        assertTrue(comparator.compare(game1gm, game2gm) < 0, "Game.1gm should come before Game.2gm");
        assertTrue(comparator.compare(game2gm, game10gm) < 0, "Game.2gm should come before Game.10gm");
        assertTrue(comparator.compare(game1gm, game10gm) < 0, "Game.1gm should come before Game.10gm");

        List<File> files = Arrays.asList(game10gm, game1gm, game2gm);
        files.sort(comparator);
        assertEquals(Arrays.asList(game1gm, game2gm, game10gm), files, "Should sort lexicographically: .1gm, .2gm, .10gm");
    }

    @Test
    void testCopierFormatFromCliArgument() {
        // Case-insensitive lookup
        assertEquals(CopierFormat.GD3, CopierFormat.fromCliArgument("gd3"));
        assertEquals(CopierFormat.GD3, CopierFormat.fromCliArgument("GD3"));
        assertEquals(CopierFormat.GD3, CopierFormat.fromCliArgument("Gd3"));

        assertEquals(CopierFormat.FIG, CopierFormat.fromCliArgument("fig"));
        assertEquals(CopierFormat.SWC, CopierFormat.fromCliArgument("swc"));
        assertEquals(CopierFormat.UFO, CopierFormat.fromCliArgument("ufo"));

        // Invalid input
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CopierFormat.fromCliArgument("invalid"));
        assertTrue(ex.getMessage().contains("Invalid format: invalid"));
        assertTrue(ex.getMessage().contains("Supported: FIG, SWC, UFO, GD3"));

        // Null input
        IllegalArgumentException nullEx = assertThrows(IllegalArgumentException.class,
                () -> CopierFormat.fromCliArgument(null));
        assertTrue(nullEx.getMessage().contains("cannot be null"));
        assertTrue(nullEx.getMessage().contains("Supported: FIG, SWC, UFO, GD3"));
    }

    @Test
    void testCopierFormatGetCmdFlag() {
        assertEquals("--fig", CopierFormat.FIG.getCmdFlag());
        assertEquals("--swc", CopierFormat.SWC.getCmdFlag());
        assertEquals("--ufo", CopierFormat.UFO.getCmdFlag());
        assertEquals("--gd3", CopierFormat.GD3.getCmdFlag());
    }
}
