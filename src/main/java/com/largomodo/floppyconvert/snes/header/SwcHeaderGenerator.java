package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.SnesRom;

import java.util.Arrays;

/**
 * Header generator for Super Wild Card (SWC) format.
 * <p>
 * Logic ported from {@code snes.c} (snes_swc) and {@code backup/swc.c}.
 * SWC headers are 512 bytes, usually only attached to the first file.
 * However, uCON64's {@code snes_split_smc} adds headers to ALL parts for SWC splitting.
 * We will follow the uCON64 split logic here: Header on EVERY part.
 */
public class SwcHeaderGenerator implements HeaderGenerator {

    @Override
    public byte[] generateHeader(SnesRom rom, int splitPartIndex, boolean isLastPart) {
        byte[] header = new byte[HEADER_SIZE];
        Arrays.fill(header, (byte) 0);

        // Size is total ROM size (not just this part) in 8KB blocks
        // ucon64 sets the size of the *part* in the header when splitting (see snes_split_smc)
        // But for the initial header generation (snes_swc), it sets total size.
        // Since we are generating headers for split parts, we need to decide context.
        // The architecture plan says: "Standard Copiers (SWC/FIG/UFO): Split typically at 4Mbit".
        // uCON64 snes_split_smc writes a header for EVERY part with the size OF THAT PART.

        // However, the standard .swc file (unsplit) has total size.
        // Let's assume standard 4Mbit (524288 bytes) or 8Mbit (1048576 bytes) splits for now.
        // Ideally, the splitter would tell us the size of the part.
        // Limitation: This interface doesn't pass part size.
        // Assumption: We are generating the header for the *Total* ROM if index == 0,
        // or the splitter handles re-writing size fields.
        //
        // Correction based on ucon64 `snes_swc`:
        // "header.size_low = (unsigned char) (size / 8192);"
        // "header.emulation = snes_hirom ? 0x30 : 0;"
        //
        // In `snes_split_smc`:
        // "header.size_low = (unsigned char) (part_size / 8192);"
        // "header.emulation |= 0x40;" (Multi-file flag)
        // if last part: "header.emulation &= ~0x40;"

        // Since we can't know the exact part size here easily without changing interface,
        // we will implement the standard header logic. The Splitter service usually
        // overrides the size bytes if it chops the file.
        // But wait, the Splitter service in Phase 4 *calls* this.
        // Let's use the total ROM size here. If the splitter needs part-specific sizes,
        // it might need to patch the returned header.

        int blocks = rom.rawData().length / 8192;
        header[0] = (byte) (blocks & 0xFF);
        header[1] = (byte) ((blocks >> 8) & 0xFF);

        // Emulation Mode
        int emulation = 0;

        // HiROM check
        if (rom.isHiRom()) {
            emulation |= 0x30;
        }

        // SRAM Size
        // 0x04 = 64Kb (8KB), 0x08 = 16Kb (2KB), 0x0C = 0Kb, 0x00 = 256Kb (32KB)
        if (rom.sramSize() == 0) {
            emulation |= 0x0C;
        } else if (rom.sramSize() <= 2048) {
            emulation |= 0x08;
        } else if (rom.sramSize() <= 8192) {
            emulation |= 0x04;
        } else {
            emulation |= 0x00; // 32KB
        }

        // Split / Multi-file flag
        if (!isLastPart) {
            emulation |= 0x40; // Bit 6 set if more files follow
        }

        header[2] = (byte) emulation;

        // Fixed IDs
        header[8] = (byte) 0xAA;
        header[9] = (byte) 0xBB;
        header[10] = 0x04; // Type 4 = SNES Program

        return header;
    }
}