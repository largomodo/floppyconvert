package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FigSramEncoderTest {

    private final FigSramEncoder encoder = new FigSramEncoder();

    @Property
    void hiRomWithSramSetsCorrectBits(@ForAll("hiRomWithSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        int emu1 = header[4] & 0xFF;
        int emu2 = header[5] & 0xFF;

        assertEquals(0x02, emu2 & 0x02, "HiROM should set bit 0x02 in emu2");
        assertEquals(0xDD, emu1 & 0xDD, "HiROM with SRAM should set 0xDD in emu1");
    }

    @Property
    void hiRomWithoutSramDoesNotSetSramBits(@ForAll("hiRomWithoutSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        int emu1 = header[4] & 0xFF;
        int emu2 = header[5] & 0xFF;

        assertEquals(0x02, emu2 & 0x02, "HiROM should set bit 0x02 in emu2");
        assertEquals(0x00, emu1 & 0xDD, "HiROM without SRAM should not set 0xDD bits");
    }

    @Property
    void hiRomWithDspSetsCorrectBits(@ForAll("hiRomWithDsp") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        int emu1 = header[4] & 0xFF;
        assertEquals(0xF0, emu1 & 0xF0, "HiROM with DSP should set 0xF0 in emu1");
    }

    @Property
    void loRomWithoutSramSetsDefaults(@ForAll("loRomWithoutSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        assertEquals((byte) 0x77, header[4], "LoROM without SRAM should set emu1 to 0x77");
        assertEquals((byte) 0x83, header[5], "LoROM without SRAM should set emu2 to 0x83");
    }

    @Property
    void loRomWithSmallSramSetsCorrectBits(@ForAll("loRomWithSmallSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        int emu2 = header[5] & 0xFF;
        assertEquals(0x80, emu2, "LoROM with SRAM <= 8KB should set emu2 to 0x80");
    }

    @Property
    void loRomWithDspModifiesEmu1(@ForAll("loRomWithDsp") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        int emu1 = header[4] & 0xFF;
        assertEquals(0x40, emu1 & 0xF0, "LoROM with DSP should clear upper nibble except 0x40");
    }

    @Property
    void encoderOnlyModifiesBytes4And5(@ForAll("anyRom") SnesRom rom) {
        byte[] header = new byte[512];
        Arrays.fill(header, (byte) 0xFF);

        encoder.encodeSram(header, rom);

        for (int i = 0; i < 512; i++) {
            if (i == 4 || i == 5) {
                continue;
            }
            assertEquals((byte) 0xFF, header[i],
                    "Encoder should only modify bytes 4-5, but byte " + i + " was changed");
        }
    }

    @Test
    void encoderCanReadExistingByte4Value() {
        SnesRom rom = createTestRom(RomType.HiROM, 8192, false);
        byte[] header = new byte[512];
        header[4] = 0x01;

        encoder.encodeSram(header, rom);

        int emu1 = header[4] & 0xFF;
        assertTrue((emu1 & 0xDD) != 0, "Encoder should OR with existing value");
    }

    @Provide
    Arbitrary<SnesRom> anyRom() {
        return Combinators.combine(
                RomDataGenerator.loRomData(),
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                Arbitraries.integers().between(0, 32768),
                Arbitraries.of(true, false)
        ).as((data, type, sramSize, hasDsp) ->
                createRomFromData(data, type, sramSize, hasDsp));
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithSram() {
        return RomDataGenerator.hiRomData()
                .map(data -> createRomFromData(data, RomType.HiROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithoutSram() {
        return RomDataGenerator.hiRomData()
                .map(data -> createRomFromData(data, RomType.HiROM, 0, false));
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithDsp() {
        return RomDataGenerator.hiRomData()
                .map(data -> createRomFromData(data, RomType.HiROM, 0, true));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithoutSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0, false));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithSmallSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithDsp() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0, true));
    }

    private SnesRom createRomFromData(byte[] data, RomType type, int sramSize, boolean hasDsp) {
        return new SnesRom(
                data,
                type,
                sramSize,
                "TEST ROM",
                hasDsp,
                0,
                0,
                0,
                0,
                0
        );
    }

    private SnesRom createTestRom(RomType type, int sramSize, boolean hasDsp) {
        byte[] data = new byte[1024 * 1024];
        return createRomFromData(data, type, sramSize, hasDsp);
    }
}
