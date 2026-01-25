package com.largomodo.floppyconvert.snes.generators;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static net.jqwik.api.Arbitraries.*;

public class RomDataGenerator {

    private static final int SNES_HEADER_START = 0x7FB0;
    private static final int HIROM_OFFSET = 0x8000;
    private static final int COPIER_HEADER_SIZE = 512;

    public static Arbitrary<byte[]> loRomData() {
        return romDataWithHeader(0, 1024 * 1024);
    }

    public static Arbitrary<byte[]> hiRomData() {
        return romDataWithHeader(HIROM_OFFSET, 1024 * 1024);
    }

    public static Arbitrary<byte[]> withCopierHeader(Arbitrary<byte[]> romData) {
        return romData.map(data -> {
            byte[] withHeader = new byte[COPIER_HEADER_SIZE + data.length];
            System.arraycopy(data, 0, withHeader, COPIER_HEADER_SIZE, data.length);
            return withHeader;
        });
    }

    private static Arbitrary<byte[]> romDataWithHeader(int headerOffset, int minSize) {
        int headerBase = SNES_HEADER_START + headerOffset;
        int requiredSize = headerBase + 0x50;
        int size = Math.max(minSize, requiredSize);

        return Combinators.combine(
                printableTitle(),
                mapType(),
                romType(),
                romSizeByte(),
                sramSizeByte(),
                country(),
                maker(),
                version()
        ).as((title, mapType, romType, romSize, sramSize, country, maker, version) -> {
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) 0xFF);

            writeString(data, headerBase + 0x10, title, 21);
            data[headerBase + 0x25] = mapType;
            data[headerBase + 0x26] = romType;
            data[headerBase + 0x27] = romSize;
            data[headerBase + 0x28] = sramSize;
            data[headerBase + 0x29] = country;
            data[headerBase + 0x2A] = maker;
            data[headerBase + 0x2B] = version;

            int checksum = calculateChecksum(data);
            int complement = checksum ^ 0xFFFF;
            writeWord(data, headerBase + 0x2C, complement);
            writeWord(data, headerBase + 0x2E, checksum);

            data[headerBase + 0x3C] = (byte) 0x00;
            data[headerBase + 0x3D] = (byte) 0x80;

            return data;
        });
    }

    private static Arbitrary<String> printableTitle() {
        return strings()
                .ascii()
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(21)
                .map(s -> String.format("%-21s", s));
    }

    private static Arbitrary<Byte> mapType() {
        return bytes().between((byte) 0x20, (byte) 0x23);
    }

    private static Arbitrary<Byte> romType() {
        return bytes().between((byte) 0x00, (byte) 0x02);
    }

    private static Arbitrary<Byte> romSizeByte() {
        return bytes().between((byte) 0x07, (byte) 0x0D);
    }

    private static Arbitrary<Byte> sramSizeByte() {
        return bytes().between((byte) 0x00, (byte) 0x05);
    }

    private static Arbitrary<Byte> country() {
        return bytes().between((byte) 0x00, (byte) 0x0D);
    }

    private static Arbitrary<Byte> maker() {
        return bytes().between((byte) 0x00, (byte) 0x33);
    }

    private static Arbitrary<Byte> version() {
        return bytes().between((byte) 0x00, (byte) 0x02);
    }

    private static void writeString(byte[] data, int offset, String s, int maxLen) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        int len = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, data, offset, len);
    }

    private static void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static int calculateChecksum(byte[] data) {
        int sum = 0;
        for (byte b : data) {
            sum += (b & 0xFF);
        }
        return sum & 0xFFFF;
    }
}
