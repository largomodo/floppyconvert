package com.largomodo.floppyconvert.snes;

import com.largomodo.floppyconvert.format.CopierFormat;

/**
 * Validates SNES ROM compatibility with specific backup unit hardware constraints.
 * <p>
 * Different copier formats have distinct memory mapping limitations and size ceilings.
 * Interface enables format-specific validation strategies following HeaderGenerator pattern.
 */
public interface HardwareValidator {

    /**
     * Validates ROM compatibility with target copier format hardware.
     * <p>
     * Throws exception for hardware-incompatible combinations (e.g., UFO HiROM > 32Mbit).
     *
     * @param rom    ROM metadata and data
     * @param format Target backup unit format
     * @throws UnsupportedHardwareException if ROM exceeds hardware constraints
     */
    void validate(SnesRom rom, CopierFormat format) throws UnsupportedHardwareException;
}
