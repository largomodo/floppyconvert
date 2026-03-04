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
     * <p><b>Implementation Note:</b> SWC/FIG/UFO use partSize for block calculation in bytes 0-1.
     * GD3 ignores partSize (uses total ROM size for memory map). UFO uses partSize for bytes 0-1
     * but total ROM size for byte 17.</p>
     *
     * @param rom            The parsed SNES ROM metadata and data.
     * @param partSize       The size of ROM data in THIS split part (bytes). Used to calculate block counts for bytes 0-1.
     * @param splitPartIndex The 0-based index of the split part being written (0 = first file).
     * @param isLastPart     True if this is the final part of the split set.
     * @param chunkFlag      Multi-file flag byte (used by UFO for byte 2, ignored by others).
     *                       UFO format requires per-part flags from lookup table (0x40/0x10/0x00).
     * @return A 512-byte byte array containing the header, or an empty array if no header
     * is required for this specific part index (e.g. SWC parts > 0).
     */
    byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag);

    /**
     * Encodes the part size as a block count into bytes 0-1 of the header (little-endian).
     * One block = 8192 bytes (8 KB). Used by SWC, FIG, and UFO formats.
     * GD3 format does not use block count in bytes 0-1 and does not call this method. (ref: DL-002)
     *
     * @param header   the 512-byte header array to write into
     * @param partSize the size of ROM data in this split part (bytes)
     */
    static void encodeBlockCount(byte[] header, int partSize) {
        int blocks = partSize / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);
    }
}