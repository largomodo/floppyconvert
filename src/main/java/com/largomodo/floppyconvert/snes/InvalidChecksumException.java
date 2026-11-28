package com.largomodo.floppyconvert.snes;

/**
 * Thrown when a ROM's checksum and complement do not sum to 0xFFFF.
 * <p>
 * Extends IllegalArgumentException so callers that already handle the SnesRom invariant keep working; the
 * dedicated type lets a batch report classify the failure without parsing messages.
 * <p>
 * Batch runs count rejected ROMs under CHECKSUM. The invariant is not relaxed because every format path relies on
 * it; loosening it would change which ROMs reach FIG, SWC and UFO as well as GD3.
 */
public class InvalidChecksumException extends IllegalArgumentException {

    public InvalidChecksumException(int checksum, int complement) {
        super(String.format("Invalid checksum/complement pair: checksum=0x%04X complement=0x%04X (sum must be 0xFFFF)",
                checksum, complement));
    }
}
