package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.generators.RomDataGenerator;
import net.jqwik.api.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UfoSramEncoderTest {

    private final UfoSramEncoder encoder = new UfoSramEncoder();

    @Property
    void sramPresentFlagSetCorrectly(@ForAll("romWithSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(1, header[16], "SRAM present flag should be 1 when SRAM > 0");
    }

    @Property
    void sramPresentFlagNotSetWhenNoSram(@ForAll("romWithoutSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(0, header[16], "SRAM present flag should be 0 when SRAM = 0");
    }

    @Property
    void sramCode1For16Kbit(@ForAll("romWith2KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(1, header[19], "SRAM code should be 1 for 16kbit (2KB)");
    }

    @Property
    void sramCode2For64Kbit(@ForAll("romWith8KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(2, header[19], "SRAM code should be 2 for 64kbit (8KB)");
    }

    @Property
    void sramCode3For256Kbit(@ForAll("romWith32KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(3, header[19], "SRAM code should be 3 for 256kbit (32KB)");
    }

    @Property
    void sramCode8ForLargerThan256Kbit(@ForAll("romWith64KB") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(8, header[19], "SRAM code should be 8 for > 256kbit");
    }

    @Property
    void hiRomSramTypeSetsCorrectValue(@ForAll("hiRomWithSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(0, header[23], "HiROM SRAM type should be 0");
        assertEquals(0x0C, header[21], "HiROM with SRAM should set A20/A21 to 0x0C");
        assertEquals(0x02, header[22], "HiROM should set A22/A23 to 0x02");
    }

    @Property
    void loRomSramTypeSetsCorrectValue(@ForAll("loRomWithSram") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(3, header[23], "LoROM SRAM type should be 3");
        assertEquals(2, header[20], "LoROM with SRAM should set A15 to 2");
        assertEquals(0x0F, header[21], "LoROM with SRAM should set A20/A21 to 0x0F");
        assertEquals(3, header[22], "LoROM with SRAM should set A22/A23 to 3");
    }

    @Property
    void loRomWithoutSramAndDspSetsMapping(@ForAll("loRomWithoutSramWithDsp") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(1, header[20], "LoROM without SRAM with DSP should set A15 to 1");
        assertEquals(0x0C, header[21], "LoROM without SRAM with DSP should set A20/A21 to 0x0C");
    }

    @Property
    void loRomWithoutSramWithoutDspSetsDefaults(@ForAll("loRomWithoutSramWithoutDsp") SnesRom rom) {
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);
        assertEquals(2, header[22], "LoROM without SRAM/DSP should set A22 to 2");
        assertEquals(0, header[23], "LoROM without SRAM/DSP should set type to 0");
    }

    @Property
    void encoderOnlyModifiesExpectedBytes(@ForAll("anyRom") SnesRom rom) {
        byte[] header = new byte[512];
        Arrays.fill(header, (byte) 0xFF);

        encoder.encodeSram(header, rom);

        for (int i = 0; i < 512; i++) {
            if (i == 16 || i == 19 || i == 20 || i == 21 || i == 22 || i == 23) {
                continue;
            }
            assertEquals((byte) 0xFF, header[i],
                    "Encoder should only modify bytes 16, 19-23, but byte " + i + " was changed");
        }
    }

    @Test
    void hiRomWithoutSramStillSetsMapping() {
        SnesRom rom = createTestRom(RomType.HiROM, 0, false);
        byte[] header = new byte[512];
        encoder.encodeSram(header, rom);

        assertEquals(0, header[23], "HiROM without SRAM should still set type to 0");
        assertEquals(0x02, header[22], "HiROM without SRAM should still set A22/A23 to 0x02");
        assertEquals(0, header[21], "HiROM without SRAM should not set A20/A21");
    }

    @Provide
    Arbitrary<SnesRom> anyRom() {
        return Combinators.combine(
                RomDataGenerator.loRomData(),
                Arbitraries.of(RomType.LoROM, RomType.HiROM),
                Arbitraries.integers().between(0, 65536),
                Arbitraries.of(true, false)
        ).as((data, type, sramSize, hasDsp) ->
                createRomFromData(data, type, sramSize, hasDsp));
    }

    @Provide
    Arbitrary<SnesRom> romWithSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> romWithoutSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0, false));
    }

    @Provide
    Arbitrary<SnesRom> romWith2KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 2048, false));
    }

    @Provide
    Arbitrary<SnesRom> romWith8KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> romWith32KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 32768, false));
    }

    @Provide
    Arbitrary<SnesRom> romWith64KB() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 65536, false));
    }

    @Provide
    Arbitrary<SnesRom> hiRomWithSram() {
        return RomDataGenerator.hiRomData()
                .map(data -> createRomFromData(data, RomType.HiROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithSram() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 8192, false));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithoutSramWithDsp() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0, true));
    }

    @Provide
    Arbitrary<SnesRom> loRomWithoutSramWithoutDsp() {
        return RomDataGenerator.loRomData()
                .map(data -> createRomFromData(data, RomType.LoROM, 0, false));
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
