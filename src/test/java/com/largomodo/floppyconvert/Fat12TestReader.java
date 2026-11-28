package com.largomodo.floppyconvert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal FAT12 reader for tests, written from the FAT specification and sharing no code with the production
 * writer, so a test that reads an image back does not inherit the writer's own assumptions.
 */
final class Fat12TestReader {

    private final byte[] image;
    private final int bytesPerSector;
    private final int clusterBytes;
    private final int fatStart;
    private final int rootStart;
    private final int rootEntries;
    private final int dataStart;

    Fat12TestReader(Path imageFile) throws IOException {
        image = Files.readAllBytes(imageFile);
        bytesPerSector = u16(11);
        clusterBytes = (image[13] & 0xFF) * bytesPerSector;
        int reservedSectors = u16(14);
        int fatCount = image[16] & 0xFF;
        rootEntries = u16(17);
        int sectorsPerFat = u16(22);
        fatStart = reservedSectors * bytesPerSector;
        rootStart = fatStart + fatCount * sectorsPerFat * bytesPerSector;
        int rootSectors = (rootEntries * 32 + bytesPerSector - 1) / bytesPerSector;
        dataStart = rootStart + rootSectors * bytesPerSector;
    }

    /**
     * @return file content keyed by the raw 11-character directory name field ("NAME    EXT"), in directory order
     */
    Map<String, byte[]> files() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < rootEntries; i++) {
            int entry = rootStart + i * 32;
            int first = image[entry] & 0xFF;
            if (first == 0x00) {
                break; // end of directory
            }
            int attributes = image[entry + 11] & 0xFF;
            if (first == 0xE5 || (attributes & 0x08) != 0) {
                continue; // deleted entry, volume label or long-name entry
            }
            String name = new String(image, entry, 11, StandardCharsets.ISO_8859_1);
            files.put(name, readChain(u16(entry + 26), (int) u32(entry + 28)));
        }
        return files;
    }

    private byte[] readChain(int cluster, int size) {
        byte[] content = new byte[size];
        int copied = 0;
        while (copied < size) {
            if (cluster < 2 || cluster >= 0xFF0) {
                throw new IllegalStateException("Cluster chain ends after " + copied + " of " + size + " bytes");
            }
            int length = Math.min(clusterBytes, size - copied);
            System.arraycopy(image, dataStart + (cluster - 2) * clusterBytes, content, copied, length);
            copied += length;
            cluster = nextCluster(cluster);
        }
        if (cluster < 0xFF8) {
            throw new IllegalStateException("Cluster chain continues past the file size");
        }
        return content;
    }

    private int nextCluster(int cluster) {
        int pair = u16(fatStart + cluster * 3 / 2);
        return (cluster & 1) == 0 ? pair & 0xFFF : pair >> 4;
    }

    private int u16(int offset) {
        return (image[offset] & 0xFF) | (image[offset + 1] & 0xFF) << 8;
    }

    private long u32(int offset) {
        return u16(offset) | (long) u16(offset + 2) << 16;
    }
}
