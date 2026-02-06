package com.largomodo.floppyconvert.service.fat;

import com.largomodo.floppyconvert.service.FloppyImageWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Native Java implementation for writing files into FAT12 floppy disk images.
 * <p>
 * This class provides native FAT12 image manipulation by directly manipulating
 * the FAT12 filesystem structure. It handles:
 * <ul>
 *   <li>Parsing the BIOS Parameter Block (BPB) to locate filesystem areas</li>
 *   <li>Finding free clusters in the File Allocation Table (FAT)</li>
 *   <li>Packing 12-bit FAT entries</li>
 *   <li>Creating DOS 8.3 directory entries in the Root Directory</li>
 * </ul>
 * <p>
 * Supported Geometry: Standard 720KB (DD), 1.44MB (HD), and 1.6MB (Extra) images.
 */
public class Fat12ImageWriter implements FloppyImageWriter {

    // FAT12 specific constants
    private static final int FAT12_EOF = 0xFFF;
    private static final byte DIR_ENTRY_FREE = 0x00;
    private static final byte DIR_ENTRY_DELETED = (byte) 0xE5;
    private static final byte ATTR_ARCHIVE = 0x20;

    @Override
    public void write(File targetImage, List<File> sources, Map<File, String> dosNameMap) throws IOException {
        try (FileChannel channel = FileChannel.open(targetImage.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            // Use Heap ByteBuffer instead of MappedByteBuffer to avoid Windows file locking issues
            // (MappedByteBuffer keeps file handle open until GC, preventing atomic moves)
            long fileSize = channel.size();
            if (fileSize > Integer.MAX_VALUE) {
                throw new IOException("Image size too large for memory buffer");
            }

            ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // Read entire image into memory
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) break;
            }

            // Parse BPB and calculate offsets (no changes to logic)
            int bytesPerSector = Short.toUnsignedInt(buffer.getShort(11));
            int sectorsPerCluster = Byte.toUnsignedInt(buffer.get(13));
            int reservedSectors = Short.toUnsignedInt(buffer.getShort(14));
            int numberOfFats = Byte.toUnsignedInt(buffer.get(16));
            int rootEntries = Short.toUnsignedInt(buffer.getShort(17));
            int totalSectors = Short.toUnsignedInt(buffer.getShort(19));
            if (totalSectors == 0) {
                totalSectors = buffer.getInt(32);
            }
            int sectorsPerFat = Short.toUnsignedInt(buffer.getShort(22));

            int fatStart = reservedSectors * bytesPerSector;
            int fatSize = sectorsPerFat * bytesPerSector;
            int rootDirStart = fatStart + (numberOfFats * fatSize);
            int rootDirSize = rootEntries * 32;
            int dataStart = rootDirStart + rootDirSize;

            int rootDirSectors = (rootDirSize + bytesPerSector - 1) / bytesPerSector;
            int dataSectors = totalSectors - (reservedSectors + (numberOfFats * sectorsPerFat) + rootDirSectors);
            int totalClusters = dataSectors / sectorsPerCluster;

            for (File source : sources) {
                byte[] fileData = Files.readAllBytes(source.toPath());
                String dosName = dosNameMap.get(source);

                if (dosName == null) {
                    throw new IllegalArgumentException("No DOS name mapping for source: " + source);
                }

                int dirEntryOffset = findFreeDirEntry(buffer, rootDirStart, rootEntries);
                if (dirEntryOffset == -1) {
                    throw new IOException("Root directory full in " + targetImage.getName());
                }

                int startCluster = writeData(buffer, fileData, fatStart, totalClusters, dataStart, bytesPerSector, sectorsPerCluster);
                writeDirEntry(buffer, dirEntryOffset, dosName, startCluster, fileData.length);

                if (numberOfFats > 1) {
                    mirrorFat(buffer, fatStart, fatSize, numberOfFats);
                }
            }

            // Write modified buffer back to disk
            buffer.rewind();
            channel.position(0);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    /**
     * Scans the Root Directory for an empty or deleted entry slot.
     */
    private int findFreeDirEntry(ByteBuffer buffer, int start, int count) {
        for (int i = 0; i < count; i++) {
            int offset = start + (i * 32);
            byte marker = buffer.get(offset);
            if (marker == DIR_ENTRY_FREE || marker == DIR_ENTRY_DELETED) {
                return offset;
            }
        }
        return -1;
    }

    /**
     * Writes file data into the Data Area, allocating clusters in the FAT as needed.
     * Returns the index of the first allocated cluster.
     */
    private int writeData(ByteBuffer buffer, byte[] data, int fatStart, int totalClusters, int dataStart, int bps, int spc) throws IOException {
        if (data.length == 0) {
            return 0; // Empty files have start cluster 0
        }

        int clusterSizeBytes = bps * spc;
        int clustersNeeded = (data.length + clusterSizeBytes - 1) / clusterSizeBytes;

        int firstCluster = 0;
        int prevCluster = -1;
        int bytesWritten = 0;

        for (int i = 0; i < clustersNeeded; i++) {
            // Find free cluster starting at index 2 (0 and 1 are reserved in FAT)
            int currentCluster = findFreeCluster(buffer, fatStart, totalClusters);
            if (currentCluster == -1) {
                throw new IOException("Disk full: not enough free clusters");
            }

            // Mark FAT as used (temporarily EOF) to reserve it for next iteration
            setFatEntry(buffer, fatStart, currentCluster, FAT12_EOF);

            if (prevCluster != -1) {
                // Link previous cluster to this one
                setFatEntry(buffer, fatStart, prevCluster, currentCluster);
            } else {
                firstCluster = currentCluster;
            }

            // Calculate write position
            // Cluster N is at dataStart + (N - 2) * clusterSize
            int writeSize = Math.min(clusterSizeBytes, data.length - bytesWritten);
            int dataOffset = dataStart + ((currentCluster - 2) * clusterSizeBytes);

            // Write data bytes
            for (int b = 0; b < writeSize; b++) {
                buffer.put(dataOffset + b, data[bytesWritten + b]);
            }

            // Zero pad the rest of the cluster (security/cleanliness)
            for (int b = writeSize; b < clusterSizeBytes; b++) {
                buffer.put(dataOffset + b, (byte) 0);
            }

            bytesWritten += writeSize;
            prevCluster = currentCluster;
        }

        return firstCluster;
    }

    /**
     * Finds the first free cluster in the FAT (value 0x000).
     */
    private int findFreeCluster(ByteBuffer buffer, int fatOffset, int totalClusters) {
        // Clusters 0 and 1 are reserved. Start search at 2.
        // FAT12 maximum valid cluster is 4086 (0xFF6), but we check bounds based on image size.
        // Safety cap at 4080 to prevent overrun on 2.88MB images if they were supported.
        int maxCluster = Math.min(totalClusters + 2, 4080);

        for (int c = 2; c < maxCluster; c++) {
            if (getFatEntry(buffer, fatOffset, c) == 0) {
                return c;
            }
        }
        return -1;
    }

    /**
     * Reads a 12-bit entry from the FAT.
     */
    private int getFatEntry(ByteBuffer buffer, int fatOffset, int cluster) {
        // FAT12: 12 bits per entry. Packed into 3 bytes for every 2 entries.
        // Offset = floor(cluster * 1.5)
        int offset = fatOffset + (cluster * 3) / 2;
        int b0 = Byte.toUnsignedInt(buffer.get(offset));
        int b1 = Byte.toUnsignedInt(buffer.get(offset + 1));

        if (cluster % 2 == 0) {
            // Even cluster: low 8 bits of b0 + low 4 bits of b1
            return b0 | ((b1 & 0x0F) << 8);
        } else {
            // Odd cluster: high 4 bits of b0 + all 8 bits of b1
            return (b0 >> 4) | (b1 << 4);
        }
    }

    /**
     * Writes a 12-bit entry to the FAT.
     */
    private void setFatEntry(ByteBuffer buffer, int fatOffset, int cluster, int value) {
        int offset = fatOffset + (cluster * 3) / 2;

        if (cluster % 2 == 0) {
            // Even cluster: occupies byte N and low nibble of N+1
            // Byte N:   Low 8 bits of Value
            // Byte N+1: High 4 bits of Value (in low nibble) | Existing High Nibble

            buffer.put(offset, (byte) (value & 0xFF));

            byte b1 = buffer.get(offset + 1);
            // Preserve high nibble (bits 4-7), set low nibble (bits 0-3)
            b1 = (byte) ((b1 & 0xF0) | ((value >> 8) & 0x0F));
            buffer.put(offset + 1, b1);
        } else {
            // Odd cluster: occupies high nibble of N and byte N+1
            // Byte N:   Existing Low Nibble | Low 4 bits of Value (in high nibble)
            // Byte N+1: High 8 bits of Value

            byte b0 = buffer.get(offset);
            // Preserve low nibble (bits 0-3), set high nibble (bits 4-7)
            b0 = (byte) ((b0 & 0x0F) | ((value & 0x0F) << 4));
            buffer.put(offset, b0);

            buffer.put(offset + 1, (byte) ((value >> 4) & 0xFF));
        }
    }

    /**
     * Copies the primary FAT to all backup FATs.
     */
    private void mirrorFat(ByteBuffer buffer, int fatStart, int fatSize, int numFats) {
        // Simple byte-by-byte copy is efficient enough for floppy-sized FATs (approx 9 sectors)
        for (int i = 0; i < fatSize; i++) {
            byte b = buffer.get(fatStart + i);
            for (int f = 1; f < numFats; f++) {
                buffer.put(fatStart + (f * fatSize) + i, b);
            }
        }
    }

    /**
     * Formats and writes a directory entry.
     * Expects dosName in standard "8.3" format (e.g. "GAME.SFC") or 11-char name.
     */
    private void writeDirEntry(ByteBuffer buffer, int offset, String name, int startCluster, int size) {
        // 1. Clear entry (32 bytes)
        for (int i = 0; i < 32; i++) {
            buffer.put(offset + i, (byte) 0);
        }

        // 2. Format Name to fixed 8+3 field
        String nameBase = name;
        String nameExt = "";

        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            nameBase = name.substring(0, dotIndex);
            nameExt = name.substring(dotIndex + 1);
        }

        // Pad or Truncate Name to 8 chars
        if (nameBase.length() > 8) nameBase = nameBase.substring(0, 8);
        while (nameBase.length() < 8) nameBase += " ";

        // Pad or Truncate Ext to 3 chars
        if (nameExt.length() > 3) nameExt = nameExt.substring(0, 3);
        while (nameExt.length() < 3) nameExt += " ";

        // Write Name (ASCII)
        byte[] baseBytes = nameBase.toUpperCase().getBytes(StandardCharsets.US_ASCII);
        byte[] extBytes = nameExt.toUpperCase().getBytes(StandardCharsets.US_ASCII);

        for (int i = 0; i < 8; i++) buffer.put(offset + i, baseBytes[i]);
        for (int i = 0; i < 3; i++) buffer.put(offset + 8 + i, extBytes[i]);

        // 3. Attributes (Archive)
        buffer.put(offset + 11, ATTR_ARCHIVE);

        // 4. Start Cluster (Low 16 bits)
        buffer.putShort(offset + 26, (short) startCluster);

        // 5. Size (32 bits)
        buffer.putInt(offset + 28, size);
    }
}