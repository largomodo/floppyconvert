package com.largomodo.floppyconvert.snes;

import java.io.IOException;

/**
 * Thrown by SnesRomReader when a file cannot hold an internal SNES header.
 * <p>
 * Extends IOException so the reader's signature is unchanged; the dedicated type lets a batch report classify the
 * failure as HEADER without parsing messages.
 */
public class InvalidRomHeaderException extends IOException {

    public InvalidRomHeaderException(String message) {
        super(message);
    }
}
