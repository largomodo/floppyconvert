package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

/**
 * Interface for generating backup unit specific headers.
 */
public interface HeaderGenerator {

    int HEADER_SIZE = 512;

    /**
     * Generates a 512-byte header for the specific copier format.
     *
     * @param rom            The parsed SNES ROM metadata and data.
     * @param splitPartIndex The 0-based index of the split part being written (0 = first file).
     * @param isLastPart     True if this is the final part of the split set.
     * @return A 512-byte byte array containing the header, or an empty array if no header
     *         is required for this specific part index (e.g. SWC parts > 0).
     */
    byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart);
}