package com.largomodo.floppyconvert;

/**
 * Classpath resource paths of the real ROM fixtures. The files are not in the repository: a test that needs one
 * skips when it is absent.
 */
public class E2ETestRomRegistry {

    // Real ROM resource paths
    public static final String CHRONO_TRIGGER = "/snes/Chrono Trigger (USA).sfc";
    public static final String SUPER_MARIO_WORLD = "/snes/Super Mario World (USA).sfc";
    public static final String SUPER_BOMBERMAN_2 = "/snes/Super Bomberman 2 (USA).sfc";
    public static final String ART_OF_FIGHTING = "/snes/Art of Fighting (USA).sfc";
    public static final String BREATH_OF_FIRE = "/snes/Breath of Fire (USA).sfc";
    public static final String ACTRAISER_2 = "/snes/ActRaiser 2 (USA).sfc";

    // Parity fixtures (DL-022): parity rows skip when the file is not on the classpath.
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

    private E2ETestRomRegistry() {
    }
}
