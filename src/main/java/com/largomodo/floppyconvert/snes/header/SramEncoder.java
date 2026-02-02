package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * Strategy interface for format-specific SRAM encoding in copier headers.
 * <p>
 * Each copier format has different SRAM size encodings and byte positions.
 * Hardware constraints: SWC uses 4-bit encoding at byte 2, FIG splits across bytes 4-5,
 * UFO requires mapping controls at bytes 20-23, GD3 uses single-byte codes at byte 16.
 */
public interface SramEncoder {
    /**
     * Encode SRAM size from ROM into format-specific header bytes.
     * <p>
     * <b>Idempotence:</b> Implementations use bitwise OR operations.
     * Caller must zero-initialize SRAM bit positions before first call.
     * Repeated calls on same header accumulate bits (non-idempotent).
     * <p>
     * Caller is responsible for initializing header with non-SRAM bits before calling.
     *
     * @param header Target header buffer (512 bytes), pre-initialized with non-SRAM data
     * @param rom    Source ROM with SRAM metadata
     */
    void encodeSram(byte[] header, SnesRom rom);
}
