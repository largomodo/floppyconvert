package com.largomodo.floppyconvert.snes;

import java.util.Set;

/**
 * DRAM capacity of the target Game Doctor in Mbit. Only decides accept or reject; the layout of a ROM does not
 * depend on it. (ref: DL-001, DL-014)
 * <p>
 * The SF7 the output is verified on carries 128 Mbit. 64 Mbit is the default because it is what an unmodified
 * SF3/SF6 holds; 32 Mbit models the smallest units.
 */
public record Gd3Memory(int dramMbit) {

    // Declared before DEFAULT: the compact constructor reads it while DEFAULT is being built
    private static final Set<Integer> SUPPORTED_MBIT = Set.of(32, 64, 128);

    /**
     * 64 Mbit, the DRAM of an unmodified SF3/SF6; used when no DRAM size is given.
     */
    public static final Gd3Memory DEFAULT = new Gd3Memory(64);

    public Gd3Memory {
        if (!SUPPORTED_MBIT.contains(dramMbit)) {
            throw new IllegalArgumentException("Game Doctor DRAM must be 32, 64 or 128 Mbit, was " + dramMbit);
        }
    }

    /**
     * @param dramMbit DRAM capacity in Mbit
     * @return the memory model for {@code dramMbit}
     * @throws IllegalArgumentException if {@code dramMbit} is not 32, 64 or 128
     */
    public static Gd3Memory of(int dramMbit) {
        return new Gd3Memory(dramMbit);
    }

    /**
     * @return capacity in bytes, the bound a padded image must fit within
     */
    public int dramBytes() {
        return dramMbit * SnesConstants.MBIT;
    }
}
