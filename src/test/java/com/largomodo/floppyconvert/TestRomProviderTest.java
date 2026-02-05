package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.E2ETestRomRegistry.RomSpec;
import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.SnesRom;
import com.largomodo.floppyconvert.snes.SnesRomReader;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestRomProvider.
 * Verifies fallback logic, synthetic ROM generation, and determinism.
 */
class TestRomProviderTest {

    private final SnesRomReader reader = new SnesRomReader();

    @Test
    void getRomOrSynthetic_returnsValidRomPath(@TempDir Path tempDir) throws Exception {
        Path romPath = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.CHRONO_TRIGGER, tempDir);

        assertNotNull(romPath, "Should return a ROM path");
        assertTrue(Files.exists(romPath), "Returned ROM path should exist");
        assertTrue(Files.size(romPath) > 0, "ROM file should not be empty");

        SnesRom rom = reader.load(romPath);
        assertNotNull(rom, "Should be able to load ROM");
        assertEquals(RomType.HiROM, rom.type(), "Should return HiROM (real or synthetic)");
    }

    @Test
    void registrySpecsProduceValidSyntheticRoms(@TempDir Path tempDir) throws Exception {
        testRegistrySpec(E2ETestRomRegistry.CHRONO_TRIGGER, RomType.HiROM, 32, tempDir);
        testRegistrySpec(E2ETestRomRegistry.SUPER_MARIO_WORLD, RomType.LoROM, 4, tempDir);
        testRegistrySpec(E2ETestRomRegistry.SUPER_BOMBERMAN_2, RomType.HiROM, 8, tempDir);
        testRegistrySpec(E2ETestRomRegistry.ART_OF_FIGHTING, RomType.HiROM, 16, tempDir);
        testRegistrySpec(E2ETestRomRegistry.STREET_FIGHTER_II, RomType.HiROM, 20, tempDir);
        testRegistrySpec(E2ETestRomRegistry.BREATH_OF_FIRE, RomType.LoROM, 12, tempDir);
        testRegistrySpec(E2ETestRomRegistry.ACTRAISER_2, RomType.HiROM, 12, tempDir);
        testRegistrySpec(E2ETestRomRegistry.EXHIROM_48MBIT, RomType.ExHiROM, 48, tempDir);
    }

    @Test
    void sameInputProducesSameOutput(@TempDir Path tempDir) throws Exception {
        Path romPath1 = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.CHRONO_TRIGGER, tempDir);
        byte[] rom1Data = Files.readAllBytes(romPath1);

        Path tempDir2 = tempDir.resolve("second");
        Files.createDirectories(tempDir2);
        Path romPath2 = TestRomProvider.getRomOrSynthetic(E2ETestRomRegistry.CHRONO_TRIGGER, tempDir2);
        byte[] rom2Data = Files.readAllBytes(romPath2);

        assertArrayEquals(rom1Data, rom2Data, "Same input should produce identical ROM data");
    }

    @Test
    void unknownRomPathThrowsIllegalArgumentException(@TempDir Path tempDir) {
        String unknownPath = "/snes/NonexistentRom.sfc";

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TestRomProvider.getRomOrSynthetic(unknownPath, tempDir),
                "Should throw IllegalArgumentException for unknown ROM path"
        );

        assertTrue(exception.getMessage().contains("Unknown ROM resource path"),
                "Exception message should indicate unknown ROM path");
    }

    private void testRegistrySpec(String resourcePath, RomType expectedType, int expectedSizeMbit, Path tempDir) throws Exception {
        RomSpec spec = E2ETestRomRegistry.getSpec(resourcePath);

        assertEquals(expectedType, spec.type(), "Spec should have correct type for " + resourcePath);
        assertEquals(expectedSizeMbit, spec.sizeMbit(), "Spec should have correct size for " + resourcePath);

        Path syntheticDir = tempDir.resolve("synthetic_" + resourcePath.hashCode());
        Files.createDirectories(syntheticDir);

        Path romPath = switch (spec.type()) {
            case LoROM -> SyntheticRomFactory.generateLoRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), syntheticDir);
            case HiROM -> SyntheticRomFactory.generateHiRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), syntheticDir);
            case ExHiROM -> SyntheticRomFactory.generateExHiRom(
                    spec.sizeMbit(), spec.sramSizeKb(), spec.hasDsp(), spec.title(), syntheticDir);
        };

        SnesRom rom = reader.load(romPath);
        assertEquals(expectedType, rom.type(), "Generated ROM should have correct type");
        assertEquals(spec.title(), rom.title(), "Generated ROM should have correct title");
    }
}
