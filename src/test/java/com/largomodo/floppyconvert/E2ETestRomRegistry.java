package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.snes.RomType;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;

import java.util.Map;
import java.util.Objects;

/**
 * Registry mapping real ROM resource paths to synthetic ROM specifications.
 * Used by TestRomProvider for E2E test fallback when real ROMs unavailable.
 */
public class E2ETestRomRegistry {

    // Real ROM resource paths
    public static final String CHRONO_TRIGGER = "/snes/Chrono Trigger (USA).sfc";
    public static final String SUPER_MARIO_WORLD = "/snes/Super Mario World (USA).sfc";
    public static final String SUPER_BOMBERMAN_2 = "/snes/Super Bomberman 2 (USA).sfc";
    public static final String ART_OF_FIGHTING = "/snes/Art of Fighting (USA).sfc";
    public static final String STREET_FIGHTER_II = "/snes/Street Fighter II - The World Warrior (USA).sfc";
    public static final String BREATH_OF_FIRE = "/snes/Breath of Fire (USA).sfc";
    public static final String ACTRAISER_2 = "/snes/ActRaiser 2 (USA).sfc";
    public static final String EXHIROM_48MBIT = "/snes/synthetic_exhirom_48mbit_0kb.sfc";

    // Real-only parity fixtures (DL-022): no RomSpec entry, so TestRomProvider never synthesises them
    // and parity rows skip when the file is not on the classpath.
    public static final String STREET_FIGHTER_II_TURBO = "/snes/Street Fighter II Turbo (USA) (Rev 1).sfc";
    public static final String STAR_OCEAN_NEVIKSTI = "/snes/Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.sfc";
    public static final String TALES_OF_PHANTASIA_LCASE = "/snes/Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].sfc";
    public static final String BAHAMUT_LAGOON_NEAR = "/snes/Bahamut Lagoon (J) T+Eng v1.2 Near.sfc";
    public static final String THRACIA_776_LIL_MANSTER = "/snes/Fire Emblem - Thracia 776 (J) T+Eng v1.07 Lil' Manster.sfc";
    public static final String TRIALS_OF_MANA = "/snes/Trials of Mana (World) (Rev 1) (Collection of Mana).sfc";
    public static final String DQ3_ROD_MERIDA = "/snes/Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.sfc";
    public static final String TOKIMEKI_MEMORIAL = "/snes/Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.sfc";
    public static final String WIZARDRY_VI = "/snes/Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.sfc";
    public static final String DAIKAIJUU_MONOGATARI_2 = "/snes/Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.sfc";
    public static final String GD3_SF48TAL = "/snes/sf48Tal";
    public static final String GD3_SF96SOE = "/snes/SF96soe";
    public static final String GD3_SF96SFA2 = "/snes/SF96SFA2";
    // Mapping from real ROM paths to synthetic specs
    private static final Map<String, RomSpec> REGISTRY = Map.of(
            CHRONO_TRIGGER, new RomSpec(32, RomType.HiROM, 8, DspChipset.ABSENT, "SYNCHRN32H"),
            SUPER_MARIO_WORLD, new RomSpec(4, RomType.LoROM, 0, DspChipset.ABSENT, "SYNMARIO4L"),
            SUPER_BOMBERMAN_2, new RomSpec(8, RomType.HiROM, 0, DspChipset.ABSENT, "SYNBOMB8H"),
            ART_OF_FIGHTING, new RomSpec(16, RomType.HiROM, 0, DspChipset.ABSENT, "SYNFIGHT16H"),
            STREET_FIGHTER_II, new RomSpec(20, RomType.HiROM, 0, DspChipset.ABSENT, "SYNSTREET20H"),
            BREATH_OF_FIRE, new RomSpec(12, RomType.LoROM, 8, DspChipset.ABSENT, "SYNBREATH12L"),
            ACTRAISER_2, new RomSpec(12, RomType.HiROM, 0, DspChipset.ABSENT, "SYNACT12H"),
            EXHIROM_48MBIT, new RomSpec(48, RomType.HiROM, 0, DspChipset.ABSENT, "SYNEX48T0", true)
    );

    /**
     * Returns the synthetic ROM specification for the given resource path.
     *
     * @param resourcePath Real ROM resource path
     * @return RomSpec for generating synthetic fallback
     * @throws IllegalArgumentException if resourcePath not in registry
     */
    public static RomSpec getSpec(String resourcePath) {
        RomSpec spec = REGISTRY.get(resourcePath);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown ROM resource path: " + resourcePath);
        }
        return spec;
    }

    // ROM specification record for synthetic fallback; extended selects the two-header 40-64 Mbit generators
    public record RomSpec(int sizeMbit, RomType type, int sramSizeKb, DspChipset hasDsp, String title, boolean extended) {
        public RomSpec(int sizeMbit, RomType type, int sramSizeKb, DspChipset hasDsp, String title) {
            this(sizeMbit, type, sramSizeKb, hasDsp, title, false);
        }

        public RomSpec {
            Objects.requireNonNull(type, "ROM type cannot be null");
            Objects.requireNonNull(hasDsp, "DSP status cannot be null");
            Objects.requireNonNull(title, "Title cannot be null");
            if (sizeMbit <= 0) {
                throw new IllegalArgumentException("ROM size must be positive");
            }
            if (sramSizeKb < 0) {
                throw new IllegalArgumentException("SRAM size cannot be negative");
            }
            if (title.trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be empty");
            }
            // SNES ROM internal header title field is 21 bytes (space-padded per SNES dev spec)
            if (title.length() > 21) {
                throw new IllegalArgumentException("Title must be max 21 characters");
            }
        }
    }
}
