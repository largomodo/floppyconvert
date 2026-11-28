package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.Ucon64Oracle.Converted;
import com.largomodo.floppyconvert.Ucon64Oracle.PartDigest;
import com.largomodo.floppyconvert.Ucon64Oracle.Rejected;
import com.largomodo.floppyconvert.Ucon64Oracle.Result;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory;
import com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.DspChipset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.SRAM_0KB;
import static com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.SRAM_2KB;
import static com.largomodo.floppyconvert.snes.generators.SyntheticRomFactory.SRAM_8KB;

/**
 * Committed oracle for GD3 parity: one row per fixture with the ordered (part name, SHA1) list uCON64 2.2.3
 * produces. Digests only, no ROM bytes, so nothing copyrighted is committed. (ref: DL-004)
 * <p>
 * EXEMPT rows are the exception, because uCON64 does not produce their expected output: their digests are a
 * hardware-verified part set, the linear twin's output, or, for a ROM uCON64 rejects and no hardware set covers
 * ({@link Row#pinsOwnOutput()}), our own output pinned as a regression guard. Regenerating such a row after a
 * splitter change blesses the change, so review its digests by hand.
 * <p>
 * Each row carries known-gap tags from the vocabulary G1..G14. While a row has tags, Gd3ParityTest asserts
 * that our output still differs and reports the tags; the milestone that closes a gap removes its tag. (ref: DL-005)
 * <p>
 * The DIGESTS and REJECTIONS entries are pasted from the output of
 * {@code mvn test -Dtest=Gd3ParityTest#regenerate -Dgd3.parity.regenerate=true}; synthetic rows change whenever
 * SyntheticRomFactory changes. A change to fixture bytes (SyntheticRomFactory or the real-ROM set) is regenerated
 * and committed on unchanged production code, so fixture drift stays separate from a behavior change in the diff.
 * (ref: R-007)
 */
public final class Gd3ParityExpectations {

    /**
     * Known-gap vocabulary of the parity plan; G2, G11 and G12 are never row tags (DL-005).
     */
    public enum Gap {G1, G2, G3, G4, G5, G6, G7, G8, G9, G10, G11, G12, G13, G14}

    public enum LiveOracle {UCON64, EXEMPT}

    @FunctionalInterface
    public interface Source {
        /**
         * @return the input file inside dir, or empty when the fixture is not available on this machine
         */
        Optional<Path> materialize(Path dir) throws IOException;
    }

    @FunctionalInterface
    private interface Generator {
        Path generate(Path dir) throws IOException;
    }

    /**
     * @param exemptReason  why the live ucon64 layer skips the row; non-null only for EXEMPT rows
     * @param hardwareParts repo-relative directory of the hardware-verified part set that defines an EXEMPT row's digests;
     *                      null when the row pins our own output instead
     * @param preHeadered   input already carries a GD3 header, so ucon64 runs only {@code -s}
     * @param twinOf        id of the linear row whose output this row must reproduce from an interleaved copy of
     *                      the same ROM; null for ordinary rows
     */
    public record Row(String id, Source source, Set<Gap> gaps, LiveOracle liveOracle,
                      String exemptReason, String hardwareParts, boolean preHeadered, String twinOf) {

        Row(String id, Source source) {
            this(id, source, Set.of(), LiveOracle.UCON64, null, null, false, null);
        }

        Row tagged(Gap... tags) {
            return new Row(id, source, Set.copyOf(Arrays.asList(tags)), liveOracle, exemptReason, hardwareParts, preHeadered, twinOf);
        }

        Row exempt(String reason, String partsDir) {
            return new Row(id, source, gaps, LiveOracle.EXEMPT, reason, partsDir, preHeadered, twinOf);
        }

        Row asPreHeadered() {
            return new Row(id, source, gaps, liveOracle, exemptReason, hardwareParts, true, twinOf);
        }

        /**
         * Interleaved copy of this row's ROM. Its expected output is the linear row's output, not ucon64
         * {@code --gd3} output, so the live layer skips it. (ref: DL-028)
         */
        Row interleavedTwin() {
            return new Row(id + "-interleaved-twin", source, Set.of(), LiveOracle.EXEMPT,
                    "expected output is the linear twin's, not ucon64 --gd3 output", null, false, id);
        }

        /**
         * @return true for an EXEMPT row that uCON64 rejects and no hardware set covers: its digests are our own
         *         output, and the live layer checks only that uCON64 still rejects the ROM
         */
        boolean pinsOwnOutput() {
            return liveOracle == LiveOracle.EXEMPT && hardwareParts == null && twinOf == null;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    // Rows with an interleaved twin: the twin must reproduce the linear row output.
    private static final Row HIROM_16 = hiRom("hirom-16", 16, SRAM_0KB);
    private static final Row HIROM_20 = hiRom("hirom-20", 20, SRAM_0KB);

    public static final List<Row> ROWS = List.of(
            // One row per HiROM size class ucon64 converts.
            // HiROM rows
            hiRom("hirom-2", 2, SRAM_0KB),
            hiRom("hirom-4", 4, SRAM_0KB),
            hiRom("hirom-8", 8, SRAM_0KB),
            hiRom("hirom-10", 10, SRAM_0KB),
            hiRom("hirom-12", 12, SRAM_0KB),
            HIROM_16,
            HIROM_16.interleavedTwin(),
            hiRom("hirom-16-sram8", 16, SRAM_8KB),
            HIROM_20,
            HIROM_20.interleavedTwin(),
            hiRom("hirom-24", 24, SRAM_0KB),
            hiRom("hirom-28", 28, SRAM_0KB),
            hiRom("hirom-32", 32, SRAM_0KB),
            hiRom("hirom-32-sram8", 32, SRAM_8KB),
            // LoROM rows
            loRom("lorom-4", 4, SRAM_0KB, DspChipset.ABSENT),
            loRom("lorom-8", 8, SRAM_0KB, DspChipset.ABSENT),
            loRom("lorom-10", 10, SRAM_0KB, DspChipset.ABSENT),
            loRom("lorom-12-sram2-dsp", 12, SRAM_2KB, DspChipset.PRESENT),
            loRom("lorom-16", 16, SRAM_0KB, DspChipset.ABSENT),
            synthetic("lorom-16-chipset-f6", dir -> SyntheticRomFactory.generateLoRom(16, SRAM_0KB, 0xF6, "SYNLO16F6", dir))
                    ,
            loRom("lorom-20", 20, SRAM_0KB, DspChipset.ABSENT),
            loRom("lorom-24", 24, SRAM_0KB, DspChipset.ABSENT),
            loRom("lorom-32", 32, SRAM_0KB, DspChipset.ABSENT),
            // Extended HiROM
            exHiRom("exhirom-40", 40),
            exHiRom("exhirom-48", 48),
            exHiRom("exhirom-64", 64),
            // ucon64 rejects 96 Mbit LoROM ("LoROM > 32 Mbit -- cannot convert"); we convert it, so the row pins
            // our own output and the live layer skips it.
            synthetic("lorom-96", dir -> SyntheticRomFactory.generateLoRom(96, SRAM_0KB, DspChipset.ABSENT, dir))
                    .exempt("ucon64 rejects 96 Mbit LoROM; digests are our own output", null),

            real("real-chrono-trigger-32", E2ETestRomRegistry.CHRONO_TRIGGER),
            real("real-super-mario-world-4", E2ETestRomRegistry.SUPER_MARIO_WORLD),
            real("real-super-bomberman-2-8", E2ETestRomRegistry.SUPER_BOMBERMAN_2),
            real("real-art-of-fighting-16", E2ETestRomRegistry.ART_OF_FIGHTING),
            real("real-street-fighter-ii-turbo-20", E2ETestRomRegistry.STREET_FIGHTER_II_TURBO),
            real("real-breath-of-fire-12", E2ETestRomRegistry.BREATH_OF_FIRE),
            real("real-actraiser-2-12", E2ETestRomRegistry.ACTRAISER_2),
            // The DeJap LCase build has a valid checksum pair and converts; the UCase build is rejected as CHECKSUM.
            real("real-tales-of-phantasia-lcase-48", E2ETestRomRegistry.TALES_OF_PHANTASIA_LCASE),
            real("real-bahamut-lagoon-near-64", E2ETestRomRegistry.BAHAMUT_LAGOON_NEAR),
            real("real-thracia-776-lil-manster-48", E2ETestRomRegistry.THRACIA_776_LIL_MANSTER),
            real("real-trials-of-mana-48", E2ETestRomRegistry.TRIALS_OF_MANA),
            real("real-dq3-rod-merida", E2ETestRomRegistry.DQ3_ROD_MERIDA),
            real("real-tokimeki-memorial", E2ETestRomRegistry.TOKIMEKI_MEMORIAL),
            real("real-wizardry-vi", E2ETestRomRegistry.WIZARDRY_VI),
            real("real-daikaijuu-monogatari-2", E2ETestRomRegistry.DAIKAIJUU_MONOGATARI_2),
            // 96 Mbit pre-decompressed builds: ucon64 rejects the .sfc, so the expected digests are the
            // hardware-verified sets, and the live layer skips these rows.
            real("real-star-ocean-neviksti-96", E2ETestRomRegistry.STAR_OCEAN_NEVIKSTI)
                    .exempt("ucon64 rejects 96 Mbit LoROM; digests are the hardware-verified snes/SF96soe_sf7/parts", "snes/SF96soe_sf7/parts"),
            realBody("real-sf96sfa2-body-as-sfc", E2ETestRomRegistry.GD3_SF96SFA2, "SF96SFA2-body.sfc")
                    .exempt("ucon64 rejects 96 Mbit LoROM; digests are the hardware-verified snes/SF96SFA2_sf7/parts", "snes/SF96SFA2_sf7/parts"),
            // Pre-headered GD3 files: ucon64 runs only -s
            real("real-sf48tal-pre-headered", E2ETestRomRegistry.GD3_SF48TAL).asPreHeadered(),
            real("real-sf96soe-pre-headered", E2ETestRomRegistry.GD3_SF96SOE).asPreHeadered(),
            real("real-sf96sfa2-pre-headered", E2ETestRomRegistry.GD3_SF96SFA2).asPreHeadered()
    );

    // GENERATED-BEGIN: pasted from Gd3ParityTest#regenerate (uCON64 2.2.3)
    private static final Map<String, List<PartDigest>> DIGESTS = Map.ofEntries(
            Map.entry("hirom-2", List.of(
                    new PartDigest("SF2SYNXA.078", "272d7112bad33d2ba43d536c01ca2a252cfa5d14"),
                    new PartDigest("SF2SYNXB.078", "d0c287d3f99f9f440f9a86d6d4ba3e60cc6bf05f")
            )),
            Map.entry("hirom-4", List.of(
                    new PartDigest("SF4SYNXA.078", "02108d3c24024fa774191faad52392b58bcf7ccf"),
                    new PartDigest("SF4SYNXB.078", "51651092a7b7b4c0d1546e99333cc180b9fc086e")
            )),
            Map.entry("hirom-8", List.of(
                    new PartDigest("SF8SYNXA.078", "b88f88102dc783d3b162ce457dcaeb4744877f85"),
                    new PartDigest("SF8SYNXB.078", "611dee5d51f8ffd953120bbcdeba47911b7c1158")
            )),
            Map.entry("hirom-10", List.of(
                    new PartDigest("SF12SYNA.078", "1fc2e46424380992cf78de7c382ad8f477caf7f1"),
                    new PartDigest("SF12SYNB.078", "b7fd4102f02254fc56b899282b615590651a40df")
            )),
            Map.entry("hirom-12", List.of(
                    new PartDigest("SF12SYNA.078", "27dc7841ab1a418f47218cfc38e276797b19c01e"),
                    new PartDigest("SF12SYNB.078", "5839f81c15d340b8fe575b9ee4cfb68fc6d82f1e")
            )),
            Map.entry("hirom-16", List.of(
                    new PartDigest("SF16SYNA.078", "cd2f15e34c5408a95ec15a6d75dd4eab9d7b9f12"),
                    new PartDigest("SF16SYNB.078", "1724a063a6707df5e914c259b54c2a2065e92395")
            )),
            Map.entry("hirom-16-sram8", List.of(
                    new PartDigest("SF16SYNA.078", "095fef152aeab34fd692616269ac72b553fa331b"),
                    new PartDigest("SF16SYNB.078", "1724a063a6707df5e914c259b54c2a2065e92395")
            )),
            Map.entry("hirom-20", List.of(
                    new PartDigest("SF24SYNA.078", "16568fe122648b96d6f2e00f0e8bb5d8ed3614e9"),
                    new PartDigest("SF24SYNB.078", "7e862ee77f97938b059ef00360f9a1298e485729"),
                    new PartDigest("SF24SYNC.078", "e7b3dd0268bfb74132336fd8bb10e7a67ebe9613")
            )),
            Map.entry("hirom-24", List.of(
                    new PartDigest("SF24SYNA.078", "d6f3738aa0534d3013387bfbfdbca730341960b7"),
                    new PartDigest("SF24SYNB.078", "045d01d20e5267245b0bb3be2c40f96d8e5ad59d"),
                    new PartDigest("SF24SYNC.078", "434564cbedf8a1575c5e908089275dd2c5429ab7")
            )),
            Map.entry("hirom-28", List.of(
                    new PartDigest("SF32SYNA.078", "7ce5c295da9cf5389e25af186ad60c3a2d3f239c"),
                    new PartDigest("SF32SYNB.078", "fa0fdac712b400a839046136bf89d75b3cc6bf02"),
                    new PartDigest("SF32SYNC.078", "687d08aac414a3afc3f5a8577cd1aecf381b28e0"),
                    new PartDigest("SF32SYND.078", "f551359dbbc7a90bb3609f2a93579d674e937d52")
            )),
            Map.entry("hirom-32", List.of(
                    new PartDigest("SF32SYNA.078", "3a4693133744c4430aa505dfb79cfd4612167de9"),
                    new PartDigest("SF32SYNB.078", "2fd8f0545acf4f9694fe6ece749d679b427bcf51"),
                    new PartDigest("SF32SYNC.078", "d5ecae43ed39eb55a8b917f4e4b688fe0930ff00"),
                    new PartDigest("SF32SYND.078", "7fc25f950c10afb3f5b0d46962b197ef26faa89d")
            )),
            Map.entry("hirom-32-sram8", List.of(
                    new PartDigest("SF32SYNA.078", "34e97aa0a5539f2fefff5640be68febb64f0edd2"),
                    new PartDigest("SF32SYNB.078", "2fd8f0545acf4f9694fe6ece749d679b427bcf51"),
                    new PartDigest("SF32SYNC.078", "d5ecae43ed39eb55a8b917f4e4b688fe0930ff00"),
                    new PartDigest("SF32SYND.078", "7fc25f950c10afb3f5b0d46962b197ef26faa89d")
            )),
            Map.entry("lorom-4", List.of(
                    new PartDigest("SF4SYN.078", "a7e5c1ccdda08493ce063ba713fb02a8171022f9")
            )),
            Map.entry("lorom-8", List.of(
                    new PartDigest("SF8SYN.078", "44dcc130ef7c714e923050600ec434cb2caeecdb")
            )),
            Map.entry("lorom-10", List.of(
                    new PartDigest("SF10SYNA.078", "2c672f84443420110cfa1d61dc669769c289b466"),
                    new PartDigest("SF10SYNB.078", "acd47d0da3081555a0b1bc30153b03e1e1d8be41")
            )),
            Map.entry("lorom-12-sram2-dsp", List.of(
                    new PartDigest("SF12SYNA.078", "ac9d22c88a29dc6abca7b7a615ad8b7d156b402b"),
                    new PartDigest("SF12SYNB.078", "608eba28e0e5768e30ce79f108b09210e16fc3a8")
            )),
            Map.entry("lorom-16", List.of(
                    new PartDigest("SF16SYNA.078", "061f655153b5b00dd34f77c14a00507e179b4533"),
                    new PartDigest("SF16SYNB.078", "0b826e6523e5c22e3f82e653b24f17340dca6351")
            )),
            Map.entry("lorom-16-chipset-f6", List.of(
                    new PartDigest("SF16SYNA.078", "884dd2d890a1260b54725c2cadfc2312b77ac79f"),
                    new PartDigest("SF16SYNB.078", "0b826e6523e5c22e3f82e653b24f17340dca6351")
            )),
            Map.entry("lorom-20", List.of(
                    new PartDigest("SF20SYNA.078", "5c2559a738b53a7657f2ba7639f2139aee7ca182"),
                    new PartDigest("SF20SYNB.078", "b9ba384646afbe3364320b3ae90ae54f2b0913dd"),
                    new PartDigest("SF20SYNC.078", "edfd4884eef5533fe33248461fc6bb5442e8cab8")
            )),
            Map.entry("lorom-24", List.of(
                    new PartDigest("SF24SYNA.078", "5a8e75abd6497a14827417a29ac6a42f716e2b87"),
                    new PartDigest("SF24SYNB.078", "ef43800d22644ba698e40eacc7510d9240888e48"),
                    new PartDigest("SF24SYNC.078", "e42c031dd5d60d3194a565d1b36b7107ad43a8d5")
            )),
            Map.entry("lorom-32", List.of(
                    new PartDigest("SF32SYNA.078", "9d8fa43a5b9cfbed9d4632817c978b9bad699626"),
                    new PartDigest("SF32SYNB.078", "10e4169dc06ae290ff6f313fab698e08c6f43845"),
                    new PartDigest("SF32SYNC.078", "d226719f8b54cffd4fc33ef3a1d20ccf46422feb"),
                    new PartDigest("SF32SYND.078", "d39778ad175133ff05a423915db200f7feb039a0")
            )),
            Map.entry("exhirom-40", List.of(
                    new PartDigest("SF40SYNA.078", "7dc546968de420784077292193a88180473a339a"),
                    new PartDigest("SF40SYNB.078", "634b34ab6f74f1ccda2b50a484b7d5b8ae5e8fa3"),
                    new PartDigest("SF40SYNC.078", "bb8debe7c40789f93116591501b0a5d720d40f39"),
                    new PartDigest("SF40SYND.078", "32bb039fc08c861e92ffe573136d65ab5403f576"),
                    new PartDigest("SF40SYNE.078", "4056875539624c06f438a9c1f7c2e945ea35041e")
            )),
            Map.entry("exhirom-48", List.of(
                    new PartDigest("SF48SYNA.078", "83d17958740ef30c6b5dd5dbf77df40dda8454f1"),
                    new PartDigest("SF48SYNB.078", "b679beed91443ed669c2aaffb33fdaa1ff14d851"),
                    new PartDigest("SF48SYNC.078", "7a6911c467de34cec52e77bfcf2f5553c0f64b51"),
                    new PartDigest("SF48SYND.078", "77b2d7933fa2dde8c828e7a760dd6342f3f56cb9"),
                    new PartDigest("SF48SYNE.078", "8d0f1453730bc1fea53d93e211931fc8d53b824e"),
                    new PartDigest("SF48SYNF.078", "1921084d85c48c1dc52928650c4a0b77475a78c0")
            )),
            Map.entry("exhirom-64", List.of(
                    new PartDigest("SF64SYNA.078", "f830491708af9f025ad178115119d6fd94322d9d"),
                    new PartDigest("SF64SYNB.078", "1fd61f4431f25a1c9b322c342f9389fce5a259e9"),
                    new PartDigest("SF64SYNC.078", "1964e4ec5a54b9a20a993fb9b1faec46882c43e0"),
                    new PartDigest("SF64SYND.078", "d5bde725509e5c7d84c39e67237fd70d944a4719"),
                    new PartDigest("SF64SYNE.078", "4143c71916007b826a9249ab3016ee3fb7416b1a"),
                    new PartDigest("SF64SYNF.078", "0d8154d583bdf8c2a7b7a67055879bed93e0b0c0"),
                    new PartDigest("SF64SYNG.078", "d4412f25bcbcf9c44aa2922447cc8aca515139a5"),
                    new PartDigest("SF64SYNH.078", "73be226119c1c993d2852bd7109e53d5fd34c476")
            )),
            Map.entry("real-chrono-trigger-32", List.of(
                    new PartDigest("SF32CHRA.078", "f0a9c05c987fd85de5db8e995f1ef6319235b77c"),
                    new PartDigest("SF32CHRB.078", "ea34f598bf5acb31a021c7cd57866ddfdfa4721d"),
                    new PartDigest("SF32CHRC.078", "103c7087da8dae4889845cbc3b6f5f0849e52e53"),
                    new PartDigest("SF32CHRD.078", "bd4da80adcc753fa7d413234628fdd811b3e91cc")
            )),
            Map.entry("real-super-mario-world-4", List.of(
                    new PartDigest("SF4SUP.078", "16bb58db84a607fffc053a23ae6c3b38295762b1")
            )),
            Map.entry("real-super-bomberman-2-8", List.of(
                    new PartDigest("SF8SUPXA.078", "d2bf596521c4e0253803256ab23bcb4d8b85273d"),
                    new PartDigest("SF8SUPXB.078", "ed5818f44b6f4d1b46ae257ca68e3da40cba6ee1")
            )),
            Map.entry("real-art-of-fighting-16", List.of(
                    new PartDigest("SF16ARTA.078", "1bdafbef58c6204031317ece330b2ee8fe647c00"),
                    new PartDigest("SF16ARTB.078", "f6a69636a183e99b7c94a79a06d3588449f3feaa")
            )),
            Map.entry("real-street-fighter-ii-turbo-20", List.of(
                    new PartDigest("SF24STRA.078", "2543ce6f47c06f1d4b0c5ee8bd493acba80ce518"),
                    new PartDigest("SF24STRB.078", "bb47c11d271b4708d11f90c245c2569fcfab9ad5"),
                    new PartDigest("SF24STRC.078", "3106e0c6b76775d36fd3b0c076544e1b46851fcb")
            )),
            Map.entry("real-breath-of-fire-12", List.of(
                    new PartDigest("SF12BREA.078", "c52c57ec7435046edfad7ca9f3756af8ee8eebb8"),
                    new PartDigest("SF12BREB.078", "95b8e0a1edd99d46728d42c03761eb23eb2dcc2c")
            )),
            Map.entry("real-actraiser-2-12", List.of(
                    new PartDigest("SF12ACTA.078", "08ce9d99307719d97ddad8aed0122bdb2245a43b"),
                    new PartDigest("SF12ACTB.078", "f9dc8560ef45519de81ac8565cd863d825410ef9")
            )),
            Map.entry("real-tales-of-phantasia-lcase-48", List.of(
                    new PartDigest("SF48TALA.078", "3b14cbe9bb82169dbdec91fd287a3d5ccdaddb29"),
                    new PartDigest("SF48TALB.078", "81d16595f4033b8849d0a3125c5620d193157126"),
                    new PartDigest("SF48TALC.078", "57b299bd40f110fe0372b128af39ef1047d30d46"),
                    new PartDigest("SF48TALD.078", "41d89b0e3bb7efd906b5822ea9de617066fd0c19"),
                    new PartDigest("SF48TALE.078", "234b308d6c7c719d48328a6431bc456dea814c67"),
                    new PartDigest("SF48TALF.078", "d745a1d8aa2ad61127a5e330f329b452a83c22d8")
            )),
            Map.entry("real-bahamut-lagoon-near-64", List.of(
                    new PartDigest("SF64BAHA.078", "9d81c5c9aa9759bdffb5cab2e064918b17181bb0"),
                    new PartDigest("SF64BAHB.078", "5619ffa5e2255dabd4caf81f5314869b7ff96bd0"),
                    new PartDigest("SF64BAHC.078", "437fdf85bdef5197667bdc609ecb2d2219e3cff2"),
                    new PartDigest("SF64BAHD.078", "3b71f43ff30f4b15b5cd85dd9e95ebc7e84eb5a3"),
                    new PartDigest("SF64BAHE.078", "586783f04a8cff52387e64756b4be9edc9ec04d3"),
                    new PartDigest("SF64BAHF.078", "f8a6715adc34ba91d60caee8b3b40f3632823c02"),
                    new PartDigest("SF64BAHG.078", "7a5b011391842ed95aff90edaf52224d109f3e14"),
                    new PartDigest("SF64BAHH.078", "b849cb8c55c17ab8bf72460659fc55f29584198c")
            )),
            Map.entry("real-trials-of-mana-48", List.of(
                    new PartDigest("SF48TRIA.078", "573b7d8af8172c1cadb3b021c6b151c1f197e791"),
                    new PartDigest("SF48TRIB.078", "390a7eec6d6957546141bc89b5b44722495212c6"),
                    new PartDigest("SF48TRIC.078", "49b0fee9080df57744db92a18cddc7163ea1e417"),
                    new PartDigest("SF48TRID.078", "0ecec9314233d503dc0d88e768678e69ee703843"),
                    new PartDigest("SF48TRIE.078", "082901c299f935466c2caa13b4212492038d3c98"),
                    new PartDigest("SF48TRIF.078", "d64fe164417a4dd2c9c38227e6731a4e78b728e3")
            )),
            Map.entry("real-dq3-rod-merida", List.of(
                    new PartDigest("SF48DRAA.078", "5a0c81fb23df24da4982248c13db75deca16fd25"),
                    new PartDigest("SF48DRAB.078", "d08c6d67bed8b9d78cd848068312213cd14dc39f"),
                    new PartDigest("SF48DRAC.078", "261cc9ef1abde32688e79549d05245e3586799bd"),
                    new PartDigest("SF48DRAD.078", "4ee363a32d4a914fd5a93bec0447db3a133c1a10"),
                    new PartDigest("SF48DRAE.078", "c05a580987829130015481936aa4883e1062ebb0"),
                    new PartDigest("SF48DRAF.078", "fc35fce7dd1e84f25fd8d5c8fdb5af364ab887a6")
            )),
            Map.entry("real-tokimeki-memorial", List.of(
                    new PartDigest("SF64TOKA.078", "21b1619f09a05a98a3c43dabd6aae775a40baaec"),
                    new PartDigest("SF64TOKB.078", "a6ccd8ff86793f10947410e943d7c460772376aa"),
                    new PartDigest("SF64TOKC.078", "0169b00c0813d57d3d62e9e233104df2573bace0"),
                    new PartDigest("SF64TOKD.078", "b250f85cc437b41a6ed762645c1392cb7edb7efd"),
                    new PartDigest("SF64TOKE.078", "65c4dac70a46f72134a4031c7c69a643b194aeae"),
                    new PartDigest("SF64TOKF.078", "392e377bddad573082a997e6c7e313e36dafabfb"),
                    new PartDigest("SF64TOKG.078", "cf6e446ab69e120c10ffc4cdccd08f38a959a477"),
                    new PartDigest("SF64TOKH.078", "0afefa4d23eb5e4cfb2adb39b7e9593febf90652")
            )),
            Map.entry("real-wizardry-vi", List.of(
                    new PartDigest("SF48WIZA.078", "94d40df1e1aff3a1f15fb38db6d7b31edd81a004"),
                    new PartDigest("SF48WIZB.078", "dd1a0b7241d7a02297d4ed1001bac3f000b2a886"),
                    new PartDigest("SF48WIZC.078", "9ee910686cef18a2cb4bd97a8aea6bfa05f32595"),
                    new PartDigest("SF48WIZD.078", "259b50d5155f5b572e9b2a77306ffd072572d534"),
                    new PartDigest("SF48WIZE.078", "274e768784178737ff15074e8958715835852897"),
                    new PartDigest("SF48WIZF.078", "3b71f43ff30f4b15b5cd85dd9e95ebc7e84eb5a3")
            )),
            Map.entry("real-star-ocean-neviksti-96", List.of(
                    new PartDigest("SF96SOEA.078", "f27efd244bbf1e6956c145fe4414ddef15d0f450"),
                    new PartDigest("SF96SOEB.078", "442eebe3872c2e3b8ae43a0c07132e7aca61260a"),
                    new PartDigest("SF96SOEC.078", "d168cea4fa3ccc2530f82d7c16fdfc9f8c9c4c64"),
                    new PartDigest("SF96SOED.078", "ba80fcd8d31752a09987f90bd5364827a18da8e3"),
                    new PartDigest("SF96SOEE.078", "e95d0d4cffff0a9a5ffdcfa20c369762c122f1a6"),
                    new PartDigest("SF96SOEF.078", "18abed58994c284f45b62cff93c4011d376c768b"),
                    new PartDigest("SF96SOEG.078", "e6f186481ce3d4f79eea8d34af42d1973025cc77"),
                    new PartDigest("SF96SOEH.078", "9feaaf7d02fcb8e5409df9c1c05e31d05f0a4d4b"),
                    new PartDigest("SF96SOEI.078", "07b661ef21d32439690b03bd57db16d65adbd462"),
                    new PartDigest("SF96SOEJ.078", "4142d7cc16fd9d1716b2ddadb56c0c6b089b33a1"),
                    new PartDigest("SF96SOEK.078", "585b51699cd787e8ab1e241d7babcc8209bda00e"),
                    new PartDigest("SF96SOEL.078", "e09fa50726c8fde34e84e69f2ae4174a9b6b7e3f")
            )),
            Map.entry("real-sf96sfa2-body-as-sfc", List.of(
                    new PartDigest("SF96SFAA.078", "b24d5573cc3234ef6216a559401d4c454367a97f"),
                    new PartDigest("SF96SFAB.078", "260bd0cd05176332788ba678ac6b6cab475ce8fb"),
                    new PartDigest("SF96SFAC.078", "22407ebe10d812e88ea102a2824ccb75637df28a"),
                    new PartDigest("SF96SFAD.078", "51233f8c116480e5e5cc54434efb415e41da3457"),
                    new PartDigest("SF96SFAE.078", "4f52071e18a7fb7f663874fc7fbada9e35eaed8c"),
                    new PartDigest("SF96SFAF.078", "260bd0cd05176332788ba678ac6b6cab475ce8fb"),
                    new PartDigest("SF96SFAG.078", "e94dbb3a516c5aa48ea13fe33b9055d84b8157aa"),
                    new PartDigest("SF96SFAH.078", "5fcf0fd161d5dffd9266b24ffb25a5b8e6b0c0fa"),
                    new PartDigest("SF96SFAI.078", "09e7708d41f9e5bac771998bd94a96764f9c8634"),
                    new PartDigest("SF96SFAJ.078", "f49d8ba0aee74cf6fabe4c6aeb12b52c7ced4dcc"),
                    new PartDigest("SF96SFAK.078", "d74438f55b2668a6c9182ba13824fcf125ed4fda"),
                    new PartDigest("SF96SFAL.078", "0c031a703b04c154d5411fc3a7e53cefa62accbc")
            )),
            Map.entry("real-sf48tal-pre-headered", List.of(
                    new PartDigest("SF48TALA.078", "3b14cbe9bb82169dbdec91fd287a3d5ccdaddb29"),
                    new PartDigest("SF48TALB.078", "81d16595f4033b8849d0a3125c5620d193157126"),
                    new PartDigest("SF48TALC.078", "57b299bd40f110fe0372b128af39ef1047d30d46"),
                    new PartDigest("SF48TALD.078", "41d89b0e3bb7efd906b5822ea9de617066fd0c19"),
                    new PartDigest("SF48TALE.078", "234b308d6c7c719d48328a6431bc456dea814c67"),
                    new PartDigest("SF48TALF.078", "d745a1d8aa2ad61127a5e330f329b452a83c22d8")
            )),
            Map.entry("real-sf96soe-pre-headered", List.of(
                    new PartDigest("SF96SOEA.078", "f27efd244bbf1e6956c145fe4414ddef15d0f450"),
                    new PartDigest("SF96SOEB.078", "442eebe3872c2e3b8ae43a0c07132e7aca61260a"),
                    new PartDigest("SF96SOEC.078", "d168cea4fa3ccc2530f82d7c16fdfc9f8c9c4c64"),
                    new PartDigest("SF96SOED.078", "ba80fcd8d31752a09987f90bd5364827a18da8e3"),
                    new PartDigest("SF96SOEE.078", "e95d0d4cffff0a9a5ffdcfa20c369762c122f1a6"),
                    new PartDigest("SF96SOEF.078", "18abed58994c284f45b62cff93c4011d376c768b"),
                    new PartDigest("SF96SOEG.078", "e6f186481ce3d4f79eea8d34af42d1973025cc77"),
                    new PartDigest("SF96SOEH.078", "9feaaf7d02fcb8e5409df9c1c05e31d05f0a4d4b"),
                    new PartDigest("SF96SOEI.078", "07b661ef21d32439690b03bd57db16d65adbd462"),
                    new PartDigest("SF96SOEJ.078", "4142d7cc16fd9d1716b2ddadb56c0c6b089b33a1"),
                    new PartDigest("SF96SOEK.078", "585b51699cd787e8ab1e241d7babcc8209bda00e"),
                    new PartDigest("SF96SOEL.078", "e09fa50726c8fde34e84e69f2ae4174a9b6b7e3f")
            )),
            Map.entry("lorom-96", List.of(
                    new PartDigest("SF96SYNA.078", "5ddc89a35722e9b138d40584ae22f6eacb2fd480"),
                    new PartDigest("SF96SYNB.078", "4134000555757f039726dbbbafb03b41a3dea5e6"),
                    new PartDigest("SF96SYNC.078", "773198902c5bef097693088c62afeee016f22f22"),
                    new PartDigest("SF96SYND.078", "0077dae950fe04cbdcef3106be70ebe2a44c683c"),
                    new PartDigest("SF96SYNE.078", "15317e833535d4242b11ae0d8ca6b4a31179ed77"),
                    new PartDigest("SF96SYNF.078", "197303c3ffc6bfced47477674ad0e7b0b3c01cdf"),
                    new PartDigest("SF96SYNG.078", "c311abc1111e7ed61658d83686c5408972868f8f"),
                    new PartDigest("SF96SYNH.078", "193ccb234f72144473a9ca20c9d9ccee896f7a39"),
                    new PartDigest("SF96SYNI.078", "4e90cbc4847ad1e1077c67a27bc0e2de29a2f05f"),
                    new PartDigest("SF96SYNJ.078", "a47ae4e4369e67deb4e8a1d953e53e1f3a03ffeb"),
                    new PartDigest("SF96SYNK.078", "459540f58f6c974c12e00515bda3ea801c7e4fd4"),
                    new PartDigest("SF96SYNL.078", "4953bcd265fc728da71dd932cf5ee26f02df33d4")
            )),
            Map.entry("real-sf96sfa2-pre-headered", List.of(
                    new PartDigest("SF96SFAA.078", "b24d5573cc3234ef6216a559401d4c454367a97f"),
                    new PartDigest("SF96SFAB.078", "260bd0cd05176332788ba678ac6b6cab475ce8fb"),
                    new PartDigest("SF96SFAC.078", "22407ebe10d812e88ea102a2824ccb75637df28a"),
                    new PartDigest("SF96SFAD.078", "51233f8c116480e5e5cc54434efb415e41da3457"),
                    new PartDigest("SF96SFAE.078", "4f52071e18a7fb7f663874fc7fbada9e35eaed8c"),
                    new PartDigest("SF96SFAF.078", "260bd0cd05176332788ba678ac6b6cab475ce8fb"),
                    new PartDigest("SF96SFAG.078", "e94dbb3a516c5aa48ea13fe33b9055d84b8157aa"),
                    new PartDigest("SF96SFAH.078", "5fcf0fd161d5dffd9266b24ffb25a5b8e6b0c0fa"),
                    new PartDigest("SF96SFAI.078", "09e7708d41f9e5bac771998bd94a96764f9c8634"),
                    new PartDigest("SF96SFAJ.078", "f49d8ba0aee74cf6fabe4c6aeb12b52c7ced4dcc"),
                    new PartDigest("SF96SFAK.078", "d74438f55b2668a6c9182ba13824fcf125ed4fda"),
                    new PartDigest("SF96SFAL.078", "0c031a703b04c154d5411fc3a7e53cefa62accbc")
            ))
    );

    private static final Map<String, String> REJECTIONS = Map.ofEntries(
            Map.entry("real-daikaijuu-monogatari-2", "ERROR: ROM size is 44.00000 Mbit -- conversion not yet implemented/verified")
    );
    // GENERATED-END

    private Gd3ParityExpectations() {
    }

    /**
     * @return what uCON64 produces for the row; for EXEMPT rows the verified hardware set or, when the row
     *         {@linkplain Row#pinsOwnOutput() pins our own output}, that output
     * @throws IllegalStateException when the row has no committed expectation yet
     */
    public static Result expected(String rowId) {
        String rejection = REJECTIONS.get(rowId);
        if (rejection != null) {
            return new Rejected(rejection);
        }
        List<PartDigest> parts = DIGESTS.get(rowId);
        if (parts == null) {
            throw new IllegalStateException("No committed expectation for row " + rowId + "; run Gd3ParityTest#regenerate");
        }
        return new Converted(parts);
    }

    private static Row synthetic(String id, Generator generator) {
        return new Row(id, dir -> {
            Files.createDirectories(dir);
            return Optional.of(generator.generate(dir));
        });
    }

    private static Row hiRom(String id, int sizeMbit, int sramKb) {
        return synthetic(id, dir -> SyntheticRomFactory.generateHiRom(sizeMbit, sramKb, DspChipset.ABSENT, dir));
    }

    private static Row loRom(String id, int sizeMbit, int sramKb, DspChipset dsp) {
        return synthetic(id, dir -> SyntheticRomFactory.generateLoRom(sizeMbit, sramKb, dsp, dir));
    }

    private static Row exHiRom(String id, int sizeMbit) {
        return synthetic(id, dir -> SyntheticRomFactory.generateExHiRom(
                sizeMbit, SRAM_0KB, DspChipset.ABSENT, "SYNEX" + sizeMbit + "T0", dir));
    }

    // Real fixtures use the same classpath lookup as TestRomProvider and never fall back to a synthetic ROM:
    // the expected digests describe ucon64 output of the real bytes. (ref: DL-022)
    private static Row real(String id, String resource) {
        return new Row(id, dir -> copyResource(resource, dir, 0, fileName(resource)));
    }

    private static Row realBody(String id, String resource, String asFileName) {
        return new Row(id, dir -> copyResource(resource, dir, 512, asFileName));
    }

    private static Optional<Path> copyResource(String resource, Path dir, int skipBytes, String fileName) throws IOException {
        try (InputStream in = Gd3ParityExpectations.class.getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);
            in.skipNBytes(skipBytes);
            Files.copy(in, target);
            return Optional.of(target);
        }
    }

    private static String fileName(String resource) {
        return resource.substring(resource.lastIndexOf('/') + 1);
    }
}
