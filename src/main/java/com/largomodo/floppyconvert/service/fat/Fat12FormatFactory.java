package com.largomodo.floppyconvert.service.fat;

import com.largomodo.floppyconvert.core.DiskTemplateFactory;
import com.largomodo.floppyconvert.core.FloppyType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Factory for creating blank FAT12-formatted floppy disk images.
 * <p>
 * Dynamically constructs disk images by writing a valid MS-DOS Boot Sector,
 * BIOS Parameter Block (BPB), and initializing FAT tables. Eliminates dependency
 * on binary .img resources and enables support for non-standard geometries.
 */
public class Fat12FormatFactory implements DiskTemplateFactory {

    @Override
    public void createBlankDisk(FloppyType type, Path targetImage) throws IOException {
        if (type == null) {
            throw new IllegalArgumentException("FloppyType cannot be null");
        }
        if (targetImage == null) {
            throw new IllegalArgumentException("Target image path cannot be null");
        }

        long totalCapacity = type.getTotalCapacity();
        ByteBuffer buffer = ByteBuffer.allocate((int) totalCapacity);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        writeBootSector(buffer, type);
        initializeFats(buffer, type);

        buffer.rewind();
        try (FileChannel channel = FileChannel.open(targetImage,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.write(buffer);
        }
    }

    /**
     * Writes MS-DOS Boot Sector with BIOS Parameter Block (BPB) to sector 0.
     */
    private void writeBootSector(ByteBuffer buffer, FloppyType type) {
        buffer.position(0);
        writeJumpCodeAndOem(buffer);
        writeBiosParameterBlock(buffer, type);
        writeExtendedBootRecord(buffer);
        writeBootSignature(buffer);
    }

    /**
     * Writes jump code and OEM name (0x00-0x0A).
     */
    private void writeJumpCodeAndOem(ByteBuffer buffer) {
        buffer.put((byte) 0xEB);
        buffer.put((byte) 0x3C);
        buffer.put((byte) 0x90);
        buffer.put("MSDOS5.0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /**
     * Writes BIOS Parameter Block fields (0x0B-0x23).
     */
    private void writeBiosParameterBlock(ByteBuffer buffer, FloppyType type) {
        buffer.putShort((short) 512);
        buffer.put((byte) 1);
        buffer.putShort((short) 1);
        buffer.put((byte) 2);
        buffer.putShort((short) type.getRootEntryCount());

        long totalSectors = type.getTotalCapacity() / 512;
        if (totalSectors < 65536) {
            buffer.putShort((short) totalSectors);
        } else {
            buffer.putShort((short) 0);
        }

        buffer.put(type.getMediaDescriptor());
        buffer.putShort(type.getSectorsPerFat());
        buffer.putShort(type.getSectorsPerTrack());
        buffer.putShort(type.getHeads());
        buffer.putInt(0);

        if (totalSectors >= 65536) {
            buffer.putInt((int) totalSectors);
        } else {
            buffer.putInt(0);
        }
    }

    /**
     * Writes extended boot record fields (0x24-0x3D).
     */
    private void writeExtendedBootRecord(ByteBuffer buffer) {
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x29);
        buffer.putInt(0x12345678);
        buffer.put("NO NAME    ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.put("FAT12   ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    /**
     * Writes boot signature (0x55/0xAA at 0x1FE).
     */
    private void writeBootSignature(ByteBuffer buffer) {
        buffer.position(0x1FE);
        buffer.put((byte) 0x55);
        buffer.put((byte) 0xAA);
    }

    /**
     * Initializes both FAT tables with media descriptor and EOF markers.
     */
    private void initializeFats(ByteBuffer buffer, FloppyType type) {
        byte mediaDescriptor = type.getMediaDescriptor();
        int fat1Start = 512;
        int fat2Start = 512 + (type.getSectorsPerFat() * 512);

        // FAT 1
        buffer.position(fat1Start);
        buffer.put(mediaDescriptor);
        buffer.put((byte) 0xFF);
        buffer.put((byte) 0xFF);

        // FAT 2
        buffer.position(fat2Start);
        buffer.put(mediaDescriptor);
        buffer.put((byte) 0xFF);
        buffer.put((byte) 0xFF);
    }
}
