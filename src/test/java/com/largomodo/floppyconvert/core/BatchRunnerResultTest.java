package com.largomodo.floppyconvert.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchRunnerResultTest {

    @Test
    // The exit code hangs on this: a tree that could not be walked completely may hide ROMs that were never tried.
    void resultIsCleanOnlyWithoutFailuresAndWithACompleteTraversal() {
        assertTrue(new BatchRunner.Result(1, 0, true).isClean());
        assertFalse(new BatchRunner.Result(1, 1, true).isClean());
        assertFalse(new BatchRunner.Result(1, 0, false).isClean());
    }
}
