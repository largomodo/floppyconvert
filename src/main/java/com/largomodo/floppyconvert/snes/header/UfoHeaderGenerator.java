package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Header generator for Super UFO format.
 * <p>
 * Logic ported from {@code snes.c} (snes_ufo).
 * UFO headers are 512 bytes.
 * For splits, ucon64 {@code snes_split_ufo} generates headers for every part.
 */
public class UfoHeaderGenerator implements HeaderGenerator {

    private final SramEncoder sramEncoder;

    public UfoHeaderGenerator() {
        this.sramEncoder = new UfoSramEncoder();
    }

    @Override
    public byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // Bytes 0-1 encode THIS part size (hardware reads header for sector count)
        int blocks = partSize / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // 2: Multi-file flag from lookup table (UFO HiROM irregular chunk support)
        // 0x40 = multi-start (4Mbit bank), 0x10 = multi-continue (2Mbit bank), 0x00 = last
        header[2] = chunkFlag;

        // 8-15: "SUPERUFO"
        byte[] id = "SUPERUFO".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, header, 8, id.length);

        // Byte 17 encodes TOTAL ROM size (hardware uses for memory map selection)
        int sizeMbit = rom.rawData().length / 131072;
        header[17] = (byte) sizeMbit;

        // 18: Bank Type (0=HiROM, 1=LoROM)
        header[18] = (byte) (rom.isHiRom() ? 0 : 1);

        sramEncoder.encodeSram(header, rom);

        return header;
    }
}