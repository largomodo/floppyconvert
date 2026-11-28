package com.largomodo.floppyconvert;

import com.largomodo.floppyconvert.Ucon64Oracle.Converted;
import com.largomodo.floppyconvert.Ucon64Oracle.PartDigest;
import com.largomodo.floppyconvert.Ucon64Oracle.Rejected;
import com.largomodo.floppyconvert.Ucon64Oracle.Result;
import com.largomodo.floppyconvert.format.CopierFormat;

import java.util.List;
import java.util.Map;

/**
 * Pins FIG, SWC and UFO split output (part name + SHA1) for every Gd3ParityExpectations fixture, so edits to
 * shared code (SnesRom, SnesRomReader, NativeRomSplitter, SplitStrategyFactory, SyntheticRomFactory) cannot
 * change those formats unnoticed. (ref: DL-006)
 * <p>
 * Keys are {@code <row id>|<FORMAT>}. A format that rejects a fixture is recorded in REJECTIONS with its message.
 * Entries are pasted from {@code CrossFormatStabilityTest#regenerate}, generated from the code under test after
 * SyntheticRomFactory's fill is final; they are not derived from ucon64.
 */
public final class CrossFormatExpectations {

    // GENERATED-BEGIN: pasted from CrossFormatStabilityTest#regenerate
    private static final Map<String, List<PartDigest>> DIGESTS = Map.ofEntries(
            Map.entry("hirom-2|FIG", List.of(
                    new PartDigest("synthetic_hirom_2mbit_0kb.1", "5a6e1c5cf6b18f1285c56b18224331142a467377")
            )),
            Map.entry("hirom-2|SWC", List.of(
                    new PartDigest("synthetic_hirom_2mbit_0kb.1", "9cb598389356dc7696475a45c6905fc662941c1e")
            )),
            Map.entry("hirom-2|UFO", List.of(
                    new PartDigest("synthetic_hirom_2mbit_0kb.1gm", "9189a26dba0fb443351ce9ccc5c780f0c66d83f0")
            )),
            Map.entry("hirom-4|FIG", List.of(
                    new PartDigest("synthetic_hirom_4mbit_0kb.1", "585a9bc1d8384fb630512615ceed33c661100a93")
            )),
            Map.entry("hirom-4|SWC", List.of(
                    new PartDigest("synthetic_hirom_4mbit_0kb.1", "f1e6b66a704cd343e1f51ac75d6ff39d1a44a35f")
            )),
            Map.entry("hirom-4|UFO", List.of(
                    new PartDigest("synthetic_hirom_4mbit_0kb.1gm", "5428a59393c6a0589ce402fbff3d45cd0f3cddcd"),
                    new PartDigest("synthetic_hirom_4mbit_0kb.2gm", "4bbd659dbfb2a4035458a7193cbbca6c8dfeefa3")
            )),
            Map.entry("hirom-8|FIG", List.of(
                    new PartDigest("synthetic_hirom_8mbit_0kb.1", "74e45a6f8097390a293c36eee1c35c83d59d4982"),
                    new PartDigest("synthetic_hirom_8mbit_0kb.2", "3c32f4005863fbaa4710190e0724f3134bafdcf4")
            )),
            Map.entry("hirom-8|SWC", List.of(
                    new PartDigest("synthetic_hirom_8mbit_0kb.1", "06e5ea780b6cb532234a05f8eef89ad41ca7c2ff"),
                    new PartDigest("synthetic_hirom_8mbit_0kb.2", "c3016e416cf129f037c1af5b606aa52fcacfb3cc")
            )),
            Map.entry("hirom-8|UFO", List.of(
                    new PartDigest("synthetic_hirom_8mbit_0kb.1gm", "800efada398d40cc50445149923a24e19c75a550"),
                    new PartDigest("synthetic_hirom_8mbit_0kb.2gm", "9790a44b1631f49102b8ec1169fa309800f21887"),
                    new PartDigest("synthetic_hirom_8mbit_0kb.3gm", "eee10e0456d80f99ac7ecbaabde0a450a6d932c8"),
                    new PartDigest("synthetic_hirom_8mbit_0kb.4gm", "3e2a5effedfe8f88f78a314cd3cfd4e9805e3bcd")
            )),
            Map.entry("hirom-10|FIG", List.of(
                    new PartDigest("synthetic_hirom_10mbit_0kb.1", "5bf3f6adc305db157301b89f8c4c425d79209e6f"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.2", "9c0f17eaff2f60ac41a6b93ee9759f0184e1559d"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.3", "a197b405639d3376c1fe69dc0b4bb835e25c2fe1"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.4", "ea804753387046e6acc981778503980cd6804649")
            )),
            Map.entry("hirom-10|SWC", List.of(
                    new PartDigest("synthetic_hirom_10mbit_0kb.1", "3bae6f6e086b4d53046b2605b68734f30f9ba55b"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.2", "9412b6f229ff17e0c5d3c4f1db329ff2d8fcc104"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.3", "11c4b454e61dbb3734ed8afbdd6e008764bedfcf"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.4", "9a409d46aa2bb4fa4dba75b15e83278ba2f3a153")
            )),
            Map.entry("hirom-10|UFO", List.of(
                    new PartDigest("synthetic_hirom_10mbit_0kb.1gm", "7d6612de17691c5d5f447d07e3eedc45643ee869"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.2gm", "5ccc3229e1fe3e25308ea34e39250dfc5fe4ce67"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.3gm", "5aea350bff33c604248c97d5682035abac6b98ec"),
                    new PartDigest("synthetic_hirom_10mbit_0kb.4gm", "e5f85a2d2636e5c0f41f9a6fab444de978ccbfe2")
            )),
            Map.entry("hirom-12|FIG", List.of(
                    new PartDigest("synthetic_hirom_12mbit_0kb.1", "006be10364d8a35422f45cc68b38d45b3fe21e60"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.2", "a84f5ee386eb17aba7b4d5271493cc68de8c1045"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.3", "56b84fc7ad30863589d9cc09901c09ad503ef590"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.4", "1a52645064b7b008029ec5591a48575fe77325b4")
            )),
            Map.entry("hirom-12|SWC", List.of(
                    new PartDigest("synthetic_hirom_12mbit_0kb.1", "92d2e2a0f76d178ec7928f4b910647398fbefe42"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.2", "4a9e2de808218a0dcc6974d3eb1177e10e105aae"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.3", "7eb017b632975e5b6ba573705ebbdc21528e13f4"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.4", "e8c9e742f1f25e9c7a2c43f0b28d0f66d40e352c")
            )),
            Map.entry("hirom-12|UFO", List.of(
                    new PartDigest("synthetic_hirom_12mbit_0kb.1gm", "2ed91c76ad53b6a5894f649a5ac680c935c7b59c"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.2gm", "ff6c0e7d29953c2d6cccc15c6677928301fd8180"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.3gm", "5d1d8fe506a762a5aa834492ea7ee7b32641ad26"),
                    new PartDigest("synthetic_hirom_12mbit_0kb.4gm", "4b6d4f7b9d40aba4f4813b4ae7835161878fc4dd")
            )),
            Map.entry("hirom-16|FIG", List.of(
                    new PartDigest("synthetic_hirom_16mbit_0kb.1", "a888615a71d0cf365f3286cd29aa9a6e4a593a5b"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.2", "f0b183a97ad3229e074aff18e3cc7e5722230b74"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.3", "124f2dbfe440f037fe3cba55eb9d8b5c0cd3fb8d"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.4", "5baccf5432f721ef29f6365ad6a5a82ec9915ce0")
            )),
            Map.entry("hirom-16|SWC", List.of(
                    new PartDigest("synthetic_hirom_16mbit_0kb.1", "2a84d4acbb9276e3298a13057ee93fd869ad19c8"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.2", "e0755667bad7a09cdf58c9aa90e77e1974a0e0fc"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.3", "49f3c06ea627f72dbf94a091959ce64b20ce1fa9"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.4", "1797e038e85ba93e2258c1cc16bf00341584e89e")
            )),
            Map.entry("hirom-16|UFO", List.of(
                    new PartDigest("synthetic_hirom_16mbit_0kb.1gm", "d8a436063a32acf0aa2fd1f7f2778d53ac977351"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.2gm", "df97fefea92efb6d3f49565c83f61a5c4c3b1b29"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.3gm", "92854f59f957d721a8885599367554d216dc7878"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.4gm", "376169e7953072a5fb99b0c3686c10052b72deaa"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.5gm", "6a51a4b1f43ddc0a9582539fde7cc090e75ad1de"),
                    new PartDigest("synthetic_hirom_16mbit_0kb.6gm", "a95d48d68b4bb9bd1289baed83e4d1aa03c1e0b2")
            )),
            Map.entry("hirom-16-sram8|FIG", List.of(
                    new PartDigest("synthetic_hirom_16mbit_8kb.1", "9ad4b0333d9f37b645c03edaef4f908f5d6a4d39"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.2", "3dcbb366d0f73e9078354574c3a4ef19ebefa836"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.3", "de948fc83b6c260a2e0af0c1b3c5d374f1045308"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.4", "2b868400867197594002d73c0767dd9f055aaeb8")
            )),
            Map.entry("hirom-16-sram8|SWC", List.of(
                    new PartDigest("synthetic_hirom_16mbit_8kb.1", "9d38794215d52c0b69a2a72c56e10221aa2b3fae"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.2", "149cda3664506463d748dcfa8bbea6c6bd4a7080"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.3", "5ecef85edb06a27c57b587aedf01614435d0e7b1"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.4", "64df7068d53e13d908e40cb658cae65d289cd533")
            )),
            Map.entry("hirom-16-sram8|UFO", List.of(
                    new PartDigest("synthetic_hirom_16mbit_8kb.1gm", "5137617f3cfcd2459a780bb8ea2083b152ef3c55"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.2gm", "de054d4889a3d4dc715febfdcd1c8144a8a382da"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.3gm", "417d0519dfa19bb046129dbe140f22bafe1a3c7a"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.4gm", "d477e8b78f2b721730889cc2f1849e5a0e365438"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.5gm", "63da52ee152af1c30365ae515399a4068772d7f3"),
                    new PartDigest("synthetic_hirom_16mbit_8kb.6gm", "42bdb4c05f3708a7b67a971f0129a9096c0e51e9")
            )),
            Map.entry("hirom-20|FIG", List.of(
                    new PartDigest("synthetic_hirom_20mbit_0kb.1", "bff8580f819e8a9cc0995f448c9ba6f387fec53e"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.2", "f2fe5a011f10dd4bd05b0f35eaac96d045c2c50b"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.3", "1da5203d12da8c38f0bea6a6c23582ace32cab85"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.4", "8c43ab89f667251ef560a2d1496bf9b701181f43"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.5", "ea34645bea18292e58992489d4ded50eeea55bdb"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.6", "bff8580f819e8a9cc0995f448c9ba6f387fec53e"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.7", "f2fe5a011f10dd4bd05b0f35eaac96d045c2c50b"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.8", "4402de431ef8ff78fd9fb86694e5b8a86de28f0b")
            )),
            Map.entry("hirom-20|SWC", List.of(
                    new PartDigest("synthetic_hirom_20mbit_0kb.1", "dfeb877fee44e3ac891b931e85ba0ea20810e5ab"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.2", "7bd377fa7aeac1e6fd217aadec609725f358d8a3"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.3", "31a715084347f107f6e26a4c240228aaafcfd17d"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.4", "c14032e7ab6880a89032d6372b8c6104c690addb"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.5", "b491c384f70672c15a2418c779ce6a00db8a9ce3"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.6", "dfeb877fee44e3ac891b931e85ba0ea20810e5ab"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.7", "7bd377fa7aeac1e6fd217aadec609725f358d8a3"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.8", "51ef9ae88db1a5b27886796e0cb5e168935b9238")
            )),
            Map.entry("hirom-20|UFO", List.of(
                    new PartDigest("synthetic_hirom_20mbit_0kb.1gm", "339e0db1453e6653ca4f7da5fac968e25d978148"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.2gm", "f64eb8e61f05d4f099697abe51e75d56d887bc29"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.3gm", "d1c7ccd9176f66ee279ded53acc35222acd449a5"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.4gm", "8be6815543fa4cd44ccb51d80fc5dd3f1235553c"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.5gm", "cc1c2b75732ba86f0c3622dbe8446f502c5307ff"),
                    new PartDigest("synthetic_hirom_20mbit_0kb.6gm", "d9a66327e829a50f5d1623a9f35307b92b572842")
            )),
            Map.entry("hirom-24|FIG", List.of(
                    new PartDigest("synthetic_hirom_24mbit_0kb.1", "92428bdf217c68b6bd3f7d4f7f438138ce0b2559"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.2", "69a2fbb5a2af12e3c9ade5beed3949c64fcdfaaf"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.3", "8ed46e90c9437b23353efe8099c31a7b1faf57f0"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.4", "e94ff0b0c3bd70f1e8ccfb22ea0df2dc423b0989"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.5", "819d023147f82056d0d0e770dc396fcc9c4fad90"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.6", "36b447155f5a286827c2c429c5b20ac9e4913c61"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.7", "92428bdf217c68b6bd3f7d4f7f438138ce0b2559"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.8", "39a252fd6b1879c1bafcdb62bf53312112fbe212")
            )),
            Map.entry("hirom-24|SWC", List.of(
                    new PartDigest("synthetic_hirom_24mbit_0kb.1", "d159a173b09a89f3ca185969a36f2d12c90483a1"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.2", "d4876798ee119af8630cf02eb0ec6c82220ead99"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.3", "a1708caae55cdc1e61d99cbc231cb411ce53fe00"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.4", "b46bafa8206a3b0bd8390fd1a03bc39032280fb8"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.5", "14a1eb0328ec0209f657ecf4ed2644e570676799"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.6", "72c4bd7a134adeca35df1d89d91f95faadcafefb"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.7", "d159a173b09a89f3ca185969a36f2d12c90483a1"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.8", "87d1014d544c3134300011cf08e86f9dd8fe893b")
            )),
            Map.entry("hirom-24|UFO", List.of(
                    new PartDigest("synthetic_hirom_24mbit_0kb.1gm", "5ce3fb7b4936a7af7604874ce533d31325c4e47d"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.2gm", "7e3230a53d843a64627f0dec896e1ba0562b5214"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.3gm", "04f5e6ead63ced56320c6bcb16bc08c3fd31151c"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.4gm", "105949e33561347bd188a070f471633058907be8"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.5gm", "23aa7df7c37dc621eea0dd3b63a694bb9f9fe93d"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.6gm", "2a847d96df46fbd6fddd78077330057fdbc04228"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.7gm", "20923ca3b7893c05125155aef6604367be5d254e"),
                    new PartDigest("synthetic_hirom_24mbit_0kb.8gm", "de508f22f47d03241ab854dda0fc18558aad18ad")
            )),
            Map.entry("hirom-28|FIG", List.of(
                    new PartDigest("synthetic_hirom_28mbit_0kb.1", "be95cade6062b18e6e780105047878937761d6d7"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.2", "216e3b36567eb3be61ab17dc125af5a8602536e3"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.3", "ac44eaad95596efa8ee4ab4b44a1b4cb239785b2"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.4", "379bf38f3b2efaa4e664a4be03d6e7391d828bf5"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.5", "99cc88003e73e8975dcc6f132b749fbc3d6302e1"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.6", "7174bb910c0f33fc6b9486a2cc692744eeab6d78"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.7", "e96dbed91ca157d9b2ae4ad7cea0914089c2a3f3"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.8", "5779be7b4c4129c0eefca3a437f8d6d9a445c60f")
            )),
            Map.entry("hirom-28|SWC", List.of(
                    new PartDigest("synthetic_hirom_28mbit_0kb.1", "d6ed6f18e3b8e25cbebaa87047b89a1a53fe498e"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.2", "7f16c29a2b2072935db868b0ecfc3c7ce79a4d43"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.3", "30f2f6927d198e655712399132d15c52214b8e76"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.4", "1c79044e5abdbdc4fb250ba2f43c3b01bb434634"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.5", "7e877e86074059ca10bcd8e8dac8f037bd0b0c60"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.6", "4cd290912cbb1e7d96add83cda68d1a18d07a83f"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.7", "a54b732ebceb1a08310108f04caf11564a958a89"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.8", "7439d8bb613ab45ae8c930c844e351d2391d1b57")
            )),
            Map.entry("hirom-28|UFO", List.of(
                    new PartDigest("synthetic_hirom_28mbit_0kb.1gm", "d75171f93f348160837c9abb8b628b4e93612afe"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.2gm", "fe87148e1514d4a74d0d1fcdef3b1fb704399d88"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.3gm", "cf3a7f46415ce7e4037870ce67d99310f5ddbd8e"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.4gm", "395399a3273ec653f8783d985cc91841f0f09462"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.5gm", "a7bfdfdb4a4cc6fd659cfe484eb9049d96da5d3b"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.6gm", "b053c3df46df08fef1a75f7ba4c214d0c7c1d3bd"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.7gm", "b38cde67c95a0aa83c3174dc0ae41df55988d3ad"),
                    new PartDigest("synthetic_hirom_28mbit_0kb.8gm", "b39586920befd67959f066ef4147e15a67edf98c")
            )),
            Map.entry("hirom-32|FIG", List.of(
                    new PartDigest("synthetic_hirom_32mbit_0kb.1", "206841055f7ad1d46f678787d8217c0a8a6364b4"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.2", "6a2559f80be7076566347674ea8cbb865aa489e3"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.3", "4a254dac569e61115a1393f7fd61770727e02dff"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.4", "dc96885e3ad84be5c1359fb36885aa9e72799445"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.5", "adfdc00b10afd56a9378a5c6824bd8d445c30967"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.6", "51b84497b6b608b64b85edc33cdb9c1bb88e19d7"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.7", "37d236f9e78bad36a8d5db1edea16cc9e435941b"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.8", "1c46594a6f29a7f5764314ec55a360d9e4b07faf")
            )),
            Map.entry("hirom-32|SWC", List.of(
                    new PartDigest("synthetic_hirom_32mbit_0kb.1", "36cd84ae257d7abe904c04bee4871291f6378f5a"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.2", "5de56fa127cee5bdb076ea754fe2e38ec8bfee29"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.3", "2a8f96042609f47808595310a0104b237abb3901"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.4", "3d0455831dbec6680a4c35616bdf75f35bbeb327"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.5", "0cd18cf0a7024bb81deca0a5071cbf0498c0dc4a"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.6", "6f0a961fe64f3aef6bdee60937e69a533edf3f7a"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.7", "72b143c542e34498626dc67689dc373f3e7e52de"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.8", "21820aa3f3fa71a8b01c7371a3fd7dcfb4cfcbcb")
            )),
            Map.entry("hirom-32|UFO", List.of(
                    new PartDigest("synthetic_hirom_32mbit_0kb.1gm", "9d880a7f71699c1db2c58753170f44a1a9d5264f"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.2gm", "7541db34fd4420fc21bc8899dd43f7bb3adf18ca"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.3gm", "efeb95be252122c5954b8ec3b220f72a2cc0de86"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.4gm", "211086bf4672328e265210849e01c06bc6f22fb9"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.5gm", "54a77c2b1d582dcce439c6617569b5cd345b8640"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.6gm", "2ec630478f2c3a88d0529d9222c96dc63354cb85"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.7gm", "12cfaa95ebd39dd6de95cfc927184baee869dcac"),
                    new PartDigest("synthetic_hirom_32mbit_0kb.8gm", "4a7d64bea4c1b20aac4952eec7c8c9d834d48867")
            )),
            Map.entry("hirom-32-sram8|FIG", List.of(
                    new PartDigest("synthetic_hirom_32mbit_8kb.1", "9e467da88e2548b6bf6dc3bff742ef76610c78ca"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.2", "14df3dffe5dd922fbb269d56a3a0db807a2db389"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.3", "128685c0daa297b488a186be0699faeaacb89b99"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.4", "9139fe9f0c07f2b300843fa5af0a0795e375ddbb"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.5", "ef240bcfd51cde036679acdaf5cb86cb409bdc2e"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.6", "9ecb4f2bfbe304d619c530e96dec9799e89fbf62"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.7", "9639380f4dd1c3259bbb5454c2b4ee0c2837c004"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.8", "6010bd0ce291477affcd9abd4297dbfbaea3bdba")
            )),
            Map.entry("hirom-32-sram8|SWC", List.of(
                    new PartDigest("synthetic_hirom_32mbit_8kb.1", "dddc591520dbb63afd6cb0f2070d24510d3dabad"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.2", "cc64cf13b38d0a41e8ebab699b123f10faa7d257"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.3", "5474184da41807bf3bed92abd7c47a2074b43299"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.4", "669331d446a4ace17da0f586a133e63afcf6b53c"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.5", "ebd48092adbfe6fa54bd5d37980a37cd4c2fc8fc"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.6", "c87c75721037e631595b861e9d111e1cb7096270"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.7", "134d8522824e89f45cbbf30c031c025195a245bd"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.8", "ea8590f9bccab30ea737d87482d21faa3140143f")
            )),
            Map.entry("hirom-32-sram8|UFO", List.of(
                    new PartDigest("synthetic_hirom_32mbit_8kb.1gm", "9779a58fe5b92859a911bc756db26f05c437c29c"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.2gm", "dce0f25e8a919d79f5b5451b451df71558b49819"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.3gm", "234622c1efcdbb3822618633f0e6bc2c87d3646d"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.4gm", "c83ef795a919aab285ba95c6af3e920fbe51976e"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.5gm", "3f0b13d911a77d88c3b9298448fdf99908b922db"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.6gm", "06dd01d1d9cf3ab61096f8dd850b72290b66faf3"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.7gm", "31676a7a1132a3a3389a7bfc78dffbc4a6d4af71"),
                    new PartDigest("synthetic_hirom_32mbit_8kb.8gm", "91813741913ba8749d08dfa8a93829981acd1c96")
            )),
            Map.entry("lorom-4|FIG", List.of(
                    new PartDigest("synthetic_lorom_4mbit_0kb.1", "85f9fc04fd875b6c25bcada3f16f78520b08eb99")
            )),
            Map.entry("lorom-4|SWC", List.of(
                    new PartDigest("synthetic_lorom_4mbit_0kb.1", "4ecd8225aef67970a895e554db0659dd0b12eb86")
            )),
            Map.entry("lorom-4|UFO", List.of(
                    new PartDigest("synthetic_lorom_4mbit_0kb.1gm", "5b7a8da7126ca3d50cc86567092f22e2a758511d")
            )),
            Map.entry("lorom-8|FIG", List.of(
                    new PartDigest("synthetic_lorom_8mbit_0kb.1", "553de633fb4dd2bb965f6873f6591f7ef4129d5b"),
                    new PartDigest("synthetic_lorom_8mbit_0kb.2", "3442b96988b37079e2333e651e67fe6d12d1bf53")
            )),
            Map.entry("lorom-8|SWC", List.of(
                    new PartDigest("synthetic_lorom_8mbit_0kb.1", "a77c0de2af6e32979a6fde467132dd6bf334624c"),
                    new PartDigest("synthetic_lorom_8mbit_0kb.2", "2ded538d167382a9b2b8a5781935f43b36a7e744")
            )),
            Map.entry("lorom-8|UFO", List.of(
                    new PartDigest("synthetic_lorom_8mbit_0kb.1gm", "9c91186902c556105f128ac5d09efacc95d42bf0"),
                    new PartDigest("synthetic_lorom_8mbit_0kb.2gm", "e3b43d07a5f9a088e7ba08b303e080e95d8fe018")
            )),
            Map.entry("lorom-10|FIG", List.of(
                    new PartDigest("synthetic_lorom_10mbit_0kb.1", "b3f3c11e2e1d66948b7e85ab23f2d3a1e9af3d11"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.2", "4b86f6dbaccc2ae0bc829c6dd6f4a9608d08befa"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.3", "8d581ae513cf40034fe7a27b94f35cdaaa44eccd"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.4", "b16fa1b463b11b35c777941c97d1bfe29b758338")
            )),
            Map.entry("lorom-10|SWC", List.of(
                    new PartDigest("synthetic_lorom_10mbit_0kb.1", "ce508b9689162e74c523502712baff2c3db37d15"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.2", "646a112128114b17c549c5683ab99612beaa9f59"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.3", "0d43818dc9f0bd0412085a1cbc97ba93a57cf5fa"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.4", "1b1b20e4da1d72f772cc666d6e14768bd44ebcac")
            )),
            Map.entry("lorom-10|UFO", List.of(
                    new PartDigest("synthetic_lorom_10mbit_0kb.1gm", "b5cc274b2e3ca52311e92ff86252ca377d8560f4"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.2gm", "064405ba9edbb225fe2387b7830002557632f808"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.3gm", "29ef6b56d90137b090b64ee263065e0eb5240920"),
                    new PartDigest("synthetic_lorom_10mbit_0kb.4gm", "6d8726b9fa9fc3d7cf1ec892aafef4dc6cce1af6")
            )),
            Map.entry("lorom-12-sram2-dsp|FIG", List.of(
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.1", "c1d619fe4c6c064821f72bfdf71dee97f8d0801d"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.2", "3a4af6b91aa67f664c4cf1acd0692292e9c23e64"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.3", "2ba1709bf0c9b622aa0f646c98de9b44bee068e2"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.4", "812c699bd79bb46115d826e5b685e175bb8d92cd")
            )),
            Map.entry("lorom-12-sram2-dsp|SWC", List.of(
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.1", "1c237098fbada05eccb2b22a6c2156d0172883d0"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.2", "688a32e1a2d3fb0ba4e1f38fa3b857a411d74329"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.3", "fdc44e5cdc70fb899f525ba302c3644daf91466e"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.4", "d3193b7a3041af0e53593f974e19d9fd0b6878f7")
            )),
            Map.entry("lorom-12-sram2-dsp|UFO", List.of(
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.1gm", "8780282e321149db1c5c764d0061917609874135"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.2gm", "ce396c1f22647f90145a98c32da0c1a7dd5e6dc9"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.3gm", "e9ff6c7de5bf75907d76536fb4932aefd94baeab"),
                    new PartDigest("synthetic_lorom_12mbit_2kb_dsp.4gm", "536b616a5d910412d805a805b3f35fb52e1f3de0")
            )),
            Map.entry("lorom-16|FIG", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb.1", "e7da7e9b46ea2e98274d8dc41182296861e42598"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.2", "6976881a79aff3c13147ab1454a0a1552ed7188b"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.3", "e0ab6da1dc30e79dcbc25e46a4b78ae2d4d1906d"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.4", "ea0193e9b54598b38c035653fd71ee591458e974")
            )),
            Map.entry("lorom-16|SWC", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb.1", "5b4fd9b9ec4c012e28262cd7c51c43cd0e998a92"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.2", "a2c7f85de39a07e58669eecf467257bcc06a7334"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.3", "f43a3c00fcab89622d4360398928b89c99ed87d7"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.4", "e190d7da37f26c81544ca0eaebadef98421b1bdf")
            )),
            Map.entry("lorom-16|UFO", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb.1gm", "8074d0406f7c1c9b19d5c877833ea46319e666b0"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.2gm", "b08f629d846c472aaf835f68d0637edca3d9286a"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.3gm", "6a2bd03a8e64998530c80dae9cd35dc5887ae8d7"),
                    new PartDigest("synthetic_lorom_16mbit_0kb.4gm", "f8404bf8e67be5ef2a9e2f4c144843b6ecfeccfa")
            )),
            Map.entry("lorom-16-chipset-f6|FIG", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.1", "929a9bfddda4e3877d539cd75fd22f7822302533"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.2", "6976881a79aff3c13147ab1454a0a1552ed7188b"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.3", "e0ab6da1dc30e79dcbc25e46a4b78ae2d4d1906d"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.4", "ea0193e9b54598b38c035653fd71ee591458e974")
            )),
            Map.entry("lorom-16-chipset-f6|SWC", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.1", "2b056b4ac62093598ab4737f7d44815f4f6348ca"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.2", "a2c7f85de39a07e58669eecf467257bcc06a7334"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.3", "f43a3c00fcab89622d4360398928b89c99ed87d7"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.4", "e190d7da37f26c81544ca0eaebadef98421b1bdf")
            )),
            Map.entry("lorom-16-chipset-f6|UFO", List.of(
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.1gm", "592fd7a09bf99012a9847362e4389092a05efe88"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.2gm", "b08f629d846c472aaf835f68d0637edca3d9286a"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.3gm", "6a2bd03a8e64998530c80dae9cd35dc5887ae8d7"),
                    new PartDigest("synthetic_lorom_16mbit_0kb_chipf6.4gm", "f8404bf8e67be5ef2a9e2f4c144843b6ecfeccfa")
            )),
            Map.entry("lorom-20|FIG", List.of(
                    new PartDigest("synthetic_lorom_20mbit_0kb.1", "a02291addf344889127ad70bab8d35ae7fef88c8"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.2", "68ab19e9de8ab17cd8b5a446e27f1f2752f323d6"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.3", "2ec564f142baefcf66e246b8fae7f6b09b86e86c"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.4", "869aa722d83b56ad5b455c4c4c938c4f66eb6e08"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.5", "d3b52014af67f9c09acb23d6d551ede77870e46e"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.6", "a02291addf344889127ad70bab8d35ae7fef88c8"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.7", "68ab19e9de8ab17cd8b5a446e27f1f2752f323d6"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.8", "e481436abc2905fe8c42e3b5309252ee5f08c828")
            )),
            Map.entry("lorom-20|SWC", List.of(
                    new PartDigest("synthetic_lorom_20mbit_0kb.1", "5e913ed6b129501398c760d6b9d1c83633c41275"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.2", "4a742ba7ead76be8e2ade1a2d3a65b31fe0c99de"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.3", "71b84e709ccf5ee71aacd15ac20ee0fc873aee23"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.4", "9f6d972ec2464d9d63707526f86ab80bb59397e3"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.5", "e4f53a15c86f496b2ff1150ab3f0c10f087f3724"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.6", "5e913ed6b129501398c760d6b9d1c83633c41275"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.7", "4a742ba7ead76be8e2ade1a2d3a65b31fe0c99de"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.8", "eb5609706bfb8ac4afc561a9d9b4170db26b0086")
            )),
            Map.entry("lorom-20|UFO", List.of(
                    new PartDigest("synthetic_lorom_20mbit_0kb.1gm", "31c265b07134e1935ac6c93d1b172eb1466c847d"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.2gm", "74dfebcfbbb732be2efa6486d4c7d3fcd298c9bc"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.3gm", "6ffcc679743d4cad4945eb0dab88c5b91f140bdb"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.4gm", "55331e74d434a386cf00b43b3fc57f7c08c40e13"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.5gm", "5d018f27a9d5a5725c5c0d251de5bd4ce4120b62"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.6gm", "31c265b07134e1935ac6c93d1b172eb1466c847d"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.7gm", "74dfebcfbbb732be2efa6486d4c7d3fcd298c9bc"),
                    new PartDigest("synthetic_lorom_20mbit_0kb.8gm", "d3bf31ff390ce3f5c91c80dcdad180d99b05c6aa")
            )),
            Map.entry("lorom-24|FIG", List.of(
                    new PartDigest("synthetic_lorom_24mbit_0kb.1", "fecf84662327d14f16a3dc35609640d36dfc0cbb"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.2", "b88264375d93cc1f27d74648d653d2e6e63d476b"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.3", "5ae0d9ab9d766bbc78edbe7f4f758a7a7cac8884"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.4", "309a26e275014d5d9366495ced5dedc3cd044098"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.5", "3b8537ec63338bf2a8d4c26a55eee741f6a03b00"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.6", "f885ea4d30a4e42939b156e37f356c6bc239cb59"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.7", "fecf84662327d14f16a3dc35609640d36dfc0cbb"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.8", "269642f2f7342e78cad3f80e66716e020f4d8826")
            )),
            Map.entry("lorom-24|SWC", List.of(
                    new PartDigest("synthetic_lorom_24mbit_0kb.1", "c1b398f6a75785c6644772fb4a57f5343fb4920c"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.2", "a4f3a3e3e1492f8c9975e2d255df5465980c75b8"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.3", "d0765eeebd1bdfd0e6a8d1a9436d91783d71eb34"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.4", "eab092ec63916cc3ddb8b40c7f4cf1ce21e3586f"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.5", "8795795be6ea55c5ad78ac6d379f6d2c22f785e4"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.6", "0b1fd43b1d79c996a1934ae50c60486508a124f1"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.7", "c1b398f6a75785c6644772fb4a57f5343fb4920c"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.8", "ffc64e6f835197a48159b436122eef2b49451163")
            )),
            Map.entry("lorom-24|UFO", List.of(
                    new PartDigest("synthetic_lorom_24mbit_0kb.1gm", "15139d74311685fda39b6d826c06e82edc1cbfc4"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.2gm", "da630c27e41e399b82700ac2309334733bd6356e"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.3gm", "55c1647396bd5706ee210500b20169d81b528fbd"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.4gm", "4e74fa40aea447fcb31eb1911ae6d4c3c890b50f"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.5gm", "240aee3e176b912d912d9280044bb660abc74eb2"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.6gm", "4aebde226e906dc2c918acf929adf22c8c73e6f2"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.7gm", "15139d74311685fda39b6d826c06e82edc1cbfc4"),
                    new PartDigest("synthetic_lorom_24mbit_0kb.8gm", "b117d40ca8b28acde688d053f8d2b6f9e87ccd8e")
            )),
            Map.entry("lorom-32|FIG", List.of(
                    new PartDigest("synthetic_lorom_32mbit_0kb.1", "90688760aaba0bd99abbe68a03a138cac6cdc052"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.2", "5d79c2db0c2b7f47d6901bfd066011ae5337b65c"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.3", "eba990f3ac29cbc1f0f21eb706dde4057032f60b"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.4", "20ec76de6adca3acbaba7751fe19ac6b59f9a2a7"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.5", "36a4245aa3cdd5a1b1bfef24e40297b5d5794995"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.6", "6b7cc0fecc202166c785bdff2ded00e63d1993ce"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.7", "70efc8b7aeadd0adc38f503a2275c4d948ec21eb"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.8", "aed1d6539b67679ec88d27657712d1fcb2b220d0")
            )),
            Map.entry("lorom-32|SWC", List.of(
                    new PartDigest("synthetic_lorom_32mbit_0kb.1", "09871060df9ce4a6dd577cc97da10a868d74f91f"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.2", "db103f0c838b07e7122d9dabb0f466f9e993a2c2"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.3", "9324d2103bf5fc427974ed0ff107ad331ca4e034"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.4", "3646390baa35e5208ea667b038b2d87495e76e7a"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.5", "16786ae6629c122b7aad263175c4a229aa425c21"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.6", "922a42c71aa9598a3bdac387cdfdc2bedb66a41f"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.7", "76a004e10ae94f8730077992ad5215b6c32f8fe2"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.8", "ee0a09e335a380f9a4574d81158de183aefe7e24")
            )),
            Map.entry("lorom-32|UFO", List.of(
                    new PartDigest("synthetic_lorom_32mbit_0kb.1gm", "1f1cffffca29d92304a85fdcad966a82931c075c"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.2gm", "d0e68a6814206601dfb269def0f62cdb7cccb656"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.3gm", "60d4eff0cb8463874214adf67abc05df629a07b0"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.4gm", "184b01b094fecbe96b357ace721f2dde7a97c311"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.5gm", "716a546e78172d837d245242144ffc6b1e9878f8"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.6gm", "bcfe923c48ce405b5e4c94d0e75546199e779e67"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.7gm", "83a9882c2e57516a0603b90336da7ea6f375ed71"),
                    new PartDigest("synthetic_lorom_32mbit_0kb.8gm", "3adb6718ff9ffc191b8f428fa325491bf024d15d")
            )),
            Map.entry("exhirom-40|FIG", List.of(
                    new PartDigest("synthetic_exhirom_40mbit_0kb.1", "03619611794148a5e82e457b92011c8819735708"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.2", "7c7e174fbd24b9503e3c4dda48bbaedd1dd03049"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.3", "b232439084b92c918c978bff243c07a9388da7c0"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.4", "1258cd69fe3852d0eeef165f03f0ba00ccf1b7e8"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.5", "1abdada231116ad0cf9b571ef6cd32fd00834aef"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.6", "807671fc96d9a8104256fbbd30e48c613b0822cd"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.7", "08a7987ce21a0ed0928992202761b3e1f500f96f"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.8", "98948e94d6a58ca93b64f0aea4735beb6e499576")
            )),
            Map.entry("exhirom-40|SWC", List.of(
                    new PartDigest("synthetic_exhirom_40mbit_0kb.1", "a1f69ed66328ba6b06f831d87de1ebc944934580"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.2", "c1f3e443534706b1343bb6fbecebfc4eb99e2bc1"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.3", "9dfff2232bdaff38a28f1f8cd5b3f4ce16f4bacd"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.4", "5a094e716bbaef6e46d272b1649b9aab96d13b0a"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.5", "c63f4431212a871d7e746a99617901704b653058"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.6", "d0d8b3e94996c20b59928cf7a4aa334c71ffa2af"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.7", "cba635a743d5bc1a0d3789d634ef28553b0359c2"),
                    new PartDigest("synthetic_exhirom_40mbit_0kb.8", "366a5166e2bb83c41f57e41aade8827d20be582d")
            )),
            Map.entry("exhirom-48|FIG", List.of(
                    new PartDigest("synthetic_exhirom_48mbit_0kb.1", "5830d81b37614d07678f3c7b84435e89fb684ee3"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.2", "084f8f617ea5bfd52d40e32f03fde6edfeffe0e9"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.3", "65ccee0ef6022b3f9555036f6ab635cb69101fe6"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.4", "204d5de4745cf63e3a663b43e0675dea22755ed8"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.5", "9d3b4367ba2c8afd4834a3eafb4874b70862d86a"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.6", "2c3cf6b8483e2c0d4519d12d032d4ee3c0de1e6e"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.7", "41628628b6bd4a0ad6f7820d6149d361700f8c0f"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.8", "5cc5cd0b94fd7ae87789498162141ac2b079e44a")
            )),
            Map.entry("exhirom-48|SWC", List.of(
                    new PartDigest("synthetic_exhirom_48mbit_0kb.1", "1443ffa03dd3bd9572a49b9103ccb058a77c3d11"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.2", "52f7c9e760a2e24689621168646169888d7af797"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.3", "82d498ea4348a91983b4dc5e3d763a3ca555aad6"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.4", "205940c2ed3e2de30b1fb92073026e9cc0e004c1"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.5", "49ec7992ff24695ea97199de882e0501c5553f9e"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.6", "5ba6a496bb1032d6018c976e61918818fa3bdf08"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.7", "530a0bd8c5fed6ab36d6b0e39372174491783c5a"),
                    new PartDigest("synthetic_exhirom_48mbit_0kb.8", "a534682d024808905470fb6052650eb98997ec6a")
            )),
            Map.entry("exhirom-64|FIG", List.of(
                    new PartDigest("synthetic_exhirom_64mbit_0kb.1", "3629ce1bc8261d584a8797da28868d1f394876ee"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.2", "06cfd097b921c88d80755f9e4a048ebc65b46366"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.3", "d76c8db12c0d1eba942bd66baa250a4f4f630846"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.4", "1d06ce5d481385c6beaac3f4a1dc96a591c993b0"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.5", "2faada9ef69e97b5d504cd13f8134fac797b5b67"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.6", "d61159a07e137f29bb4f952483fccc3ba2a63313"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.7", "e8acc72130cbb0fdac1907048f0a27c16dc17365"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.8", "c978df62e838f39387fa6625df54582ecf664a82")
            )),
            Map.entry("exhirom-64|SWC", List.of(
                    new PartDigest("synthetic_exhirom_64mbit_0kb.1", "884621b291d36dbd2486ab365ba8319d4d81aec0"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.2", "d0b5614bd1f2761012aa52bfb7f90e2937f8a9f7"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.3", "5e6a1f8de899029d6e3d02e3b1abb8d7a2494747"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.4", "0eac4fe5a9af3fae55b499c28afb5750637aa0c8"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.5", "43aa5f7129f5ce7b890a6a7b2b0886f31d1aae83"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.6", "8d5619d62b793344a484d4152f57ff816dc130d0"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.7", "b537ba073f9605a67ef9bd44378ecc592a01c096"),
                    new PartDigest("synthetic_exhirom_64mbit_0kb.8", "f1e98a29be46cf360ad242f388551f0006d80409")
            )),
            Map.entry("lorom-96-reject|FIG", List.of(
                    new PartDigest("synthetic_lorom_96mbit_0kb.1", "93b6dd4d753dd71d39f8a639bc418b5952502e52"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.2", "a17443451e226d353c3fa5d180cc232063c6cd2d"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.3", "7aaa9629ef72fdd9c5b0f3b3504e72b9f611a1ff"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.4", "e0cc500b019ef2ae137547e19018d2b13c304638"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.5", "a98d2fca377232033c9ef7cb0a2dd45d078818fd"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.6", "bb6257f49d6f11603915b99dac69a1efb48aa69c"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.7", "dab8bd17d907a646eabcf96d5b8e3fb1d7f5f871"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.8", "ba42e8abd92c1e8dc9163b0b2cdbb5ca31d45262")
            )),
            Map.entry("lorom-96-reject|SWC", List.of(
                    new PartDigest("synthetic_lorom_96mbit_0kb.1", "e49e74b4db0cc38f8d9ff9b13d2d867e7d1ee023"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.2", "de679267eb44bddcaf269080bcd83c7d76d85f6e"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.3", "144e9cb91b0d7d0b145ec1a040723e90d807fad5"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.4", "4d91f80136b7c69754c89de9edf14a556eeb6a7b"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.5", "1a2af7c243fd51dbafbbe74a2bb7448c6e2df230"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.6", "9056ce576457ff965f3267d6f729bf1e9ba21bb3"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.7", "49a73c77c1e8ba8647a90fe47e043a9cdebcd2c4"),
                    new PartDigest("synthetic_lorom_96mbit_0kb.8", "b917ceb603c568ba1b91dbadc50b94f3d8f49eb2")
            )),
            Map.entry("real-chrono-trigger-32|FIG", List.of(
                    new PartDigest("Chrono Trigger (USA).1", "f9bf27e362777e50cb42ea2fc2c24e450b4b3c4b"),
                    new PartDigest("Chrono Trigger (USA).2", "ab66d6088bac23caeb16cefce347b0bdf84f6d68"),
                    new PartDigest("Chrono Trigger (USA).3", "44f66a73ff1d6ade604511f9981c9544c0674447"),
                    new PartDigest("Chrono Trigger (USA).4", "47eac7aac4e5b70ea22f119ba82080ce181e8b54"),
                    new PartDigest("Chrono Trigger (USA).5", "c827b8db0df74b2f6453a63cc18fab5b83f1195f"),
                    new PartDigest("Chrono Trigger (USA).6", "2a97f9fad8a89cbe74d96dcb54d2bbbfaac1d803"),
                    new PartDigest("Chrono Trigger (USA).7", "d6a08abc766ac7c800578427dc4ce385c211964b"),
                    new PartDigest("Chrono Trigger (USA).8", "2deedfb642562eab5b1bbbbbc2f1442e0dd319ab")
            )),
            Map.entry("real-chrono-trigger-32|SWC", List.of(
                    new PartDigest("Chrono Trigger (USA).1", "295df1ed70e712b432aa49d331cf1845f012f146"),
                    new PartDigest("Chrono Trigger (USA).2", "453571a9a0125291b375e0ca8fc3c0c1b95870a9"),
                    new PartDigest("Chrono Trigger (USA).3", "453fb84691ba7bca301a458be32e17aabb90a2b2"),
                    new PartDigest("Chrono Trigger (USA).4", "312d41148bffd2ccaaded1ca7428b54d1964485d"),
                    new PartDigest("Chrono Trigger (USA).5", "efffcfffd0afc57fdee214dc81dfa65d5639fbe6"),
                    new PartDigest("Chrono Trigger (USA).6", "f60b9733659837974a28668d195d98c75d8fadb8"),
                    new PartDigest("Chrono Trigger (USA).7", "e9b4f7179bf7ff68d74dbcb337db0407d73b2c7d"),
                    new PartDigest("Chrono Trigger (USA).8", "541d24d62ff4af2fbde3822022fa4dd87d19161b")
            )),
            Map.entry("real-chrono-trigger-32|UFO", List.of(
                    new PartDigest("Chrono Trigger (USA).1gm", "2b3f2ed5039fcd93ec7b13a27f696ae4807323a9"),
                    new PartDigest("Chrono Trigger (USA).2gm", "392426fe06912cc481dea40b29a934d594a5fbe2"),
                    new PartDigest("Chrono Trigger (USA).3gm", "49c7d5cb0aa31b5e6d608aedc78990d119c9ee0c"),
                    new PartDigest("Chrono Trigger (USA).4gm", "bed43f78a488dbf92022bbb25745c3e002199b25"),
                    new PartDigest("Chrono Trigger (USA).5gm", "d049f57021145fcfd4e21051b49842cdc89a6161"),
                    new PartDigest("Chrono Trigger (USA).6gm", "6634dcb594c8c7777e8c01d35b4d58287a4f9471"),
                    new PartDigest("Chrono Trigger (USA).7gm", "305e7d86612ab204d3f25bd216f6d4e01a1f976e"),
                    new PartDigest("Chrono Trigger (USA).8gm", "163bd3365de28183ecd76574881f9238196f6c3e")
            )),
            Map.entry("real-super-mario-world-4|FIG", List.of(
                    new PartDigest("Super Mario World (USA).1", "98adce3f4c4754d531fc4d527f5197739c735d9a")
            )),
            Map.entry("real-super-mario-world-4|SWC", List.of(
                    new PartDigest("Super Mario World (USA).1", "ec81dc52ec209a14fb4fe7ed287cbcad47633809")
            )),
            Map.entry("real-super-mario-world-4|UFO", List.of(
                    new PartDigest("Super Mario World (USA).1gm", "f2c1721c1005acf89daad86d048f30105a11342c")
            )),
            Map.entry("real-super-bomberman-2-8|FIG", List.of(
                    new PartDigest("Super Bomberman 2 (USA).1", "4fb24a4ae1a2e5b6567a931359743965dd557e6e"),
                    new PartDigest("Super Bomberman 2 (USA).2", "f21b59bcdf4092398956691ded46a697f30e8c61")
            )),
            Map.entry("real-super-bomberman-2-8|SWC", List.of(
                    new PartDigest("Super Bomberman 2 (USA).1", "baf8553f6c87c9fb24d15af8b892b58c36211c17"),
                    new PartDigest("Super Bomberman 2 (USA).2", "fcd2541cdea9671c616725ccef8ac40d26be72ac")
            )),
            Map.entry("real-super-bomberman-2-8|UFO", List.of(
                    new PartDigest("Super Bomberman 2 (USA).1gm", "ef6abf7e7f4c49b16e08ec8dba478328c628eaa4"),
                    new PartDigest("Super Bomberman 2 (USA).2gm", "f959f2ea0a2b1d349aee39aca1783ffad11aedc6"),
                    new PartDigest("Super Bomberman 2 (USA).3gm", "fc309d732e082a15fb325dcb93bd3f70b984f4a3"),
                    new PartDigest("Super Bomberman 2 (USA).4gm", "e7d6d176598cec6322b60c6967c31fc92a668047")
            )),
            Map.entry("real-art-of-fighting-16|FIG", List.of(
                    new PartDigest("Art of Fighting (USA).1", "336c396e581424c1457bdb144d8e3e539d94b9cc"),
                    new PartDigest("Art of Fighting (USA).2", "93b78a95b2b1f2520c67f18923112fc239aef0c0"),
                    new PartDigest("Art of Fighting (USA).3", "55ce0fefed3462ebc55f422480b5d7c775456013"),
                    new PartDigest("Art of Fighting (USA).4", "7523e16ee3d039448eb5ec8e0ae711e3aa55af07")
            )),
            Map.entry("real-art-of-fighting-16|SWC", List.of(
                    new PartDigest("Art of Fighting (USA).1", "c5879c35b07ee9f10468b22b14be299feaa9390a"),
                    new PartDigest("Art of Fighting (USA).2", "b1ab2dd77c72d1d64120fbc37ab653201f91f763"),
                    new PartDigest("Art of Fighting (USA).3", "1325d55c950372195fae256946d9d24f3f3ab9db"),
                    new PartDigest("Art of Fighting (USA).4", "560162d84bb4d40d61c11c012657b68dc077b80a")
            )),
            Map.entry("real-art-of-fighting-16|UFO", List.of(
                    new PartDigest("Art of Fighting (USA).1gm", "e37a25b0e3d49b944af2ec1d6266b43d15263d05"),
                    new PartDigest("Art of Fighting (USA).2gm", "c647348ebf34a08baf1a6b6cd116af7fcb22c5ca"),
                    new PartDigest("Art of Fighting (USA).3gm", "29126927d406efa95e1989126cdc708c4a744b25"),
                    new PartDigest("Art of Fighting (USA).4gm", "8e96b0ee31a9772f1e58003601b18282057f8ab0"),
                    new PartDigest("Art of Fighting (USA).5gm", "4a22df8d730b7ffd89e99976e1594ef1c853de12"),
                    new PartDigest("Art of Fighting (USA).6gm", "dddee2d5fe9e4842233c336b56c673c24b8f5e87")
            )),
            Map.entry("real-street-fighter-ii-turbo-20|FIG", List.of(
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).1", "57cb024abc4440161c9100030279c428fbaf2719"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).2", "e8060756232ef14da21a05beb8eb00f9f699e30d"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).3", "fc33b08ea44e65b8afd67935342551c8af409ee7"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).4", "c39a91d4ca11b37117f99fc28526812721ba8774"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).5", "fb266455ffe7ef4295b58bc3ee5354a87f35cf0f"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).6", "57cb024abc4440161c9100030279c428fbaf2719"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).7", "e8060756232ef14da21a05beb8eb00f9f699e30d"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).8", "3232596906e92b74a7ec5dc864e1b9352600c6e0")
            )),
            Map.entry("real-street-fighter-ii-turbo-20|SWC", List.of(
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).1", "3ea73be3cd94c288dab0ad6d78f2d4423751f5af"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).2", "f0a33b4840f124a0466a9257b5a5919404a79d24"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).3", "28e0d0bd9d1919474bdc4f13f90a3aa04c2d9bd9"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).4", "36ed506fc0c1a252cb179477989148913c563776"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).5", "44e74c063822816f27fd2e8b211516f51ab9ce8c"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).6", "3ea73be3cd94c288dab0ad6d78f2d4423751f5af"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).7", "f0a33b4840f124a0466a9257b5a5919404a79d24"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).8", "10008e1ff7b97c109e8b132e3053048b57139ded")
            )),
            Map.entry("real-street-fighter-ii-turbo-20|UFO", List.of(
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).1gm", "dac332faf6a5327a8c11d174a0726635434581bd"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).2gm", "e48b20b8770f071d344870f0319a30af72759fb9"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).3gm", "534e19409889670f226848c536fb736d5051b480"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).4gm", "43230d46bd505b233cd70df28e0fac3420da9bb9"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).5gm", "279a332268c15e2a2d1f6b5c6c52cd2273a69007"),
                    new PartDigest("Street Fighter II Turbo (USA) (Rev 1).6gm", "7ba2696b35ada7922dd0d1c2b53ae39504f0d31c")
            )),
            Map.entry("real-breath-of-fire-12|FIG", List.of(
                    new PartDigest("Breath of Fire (USA).1", "bb84db37ec340f65eb0388fee7c3799c2983d481"),
                    new PartDigest("Breath of Fire (USA).2", "746a3fae240725249c88737e8758e841271dcb6e"),
                    new PartDigest("Breath of Fire (USA).3", "77c4b5f28499e02d4725e1e3b090a157500a5ddd"),
                    new PartDigest("Breath of Fire (USA).4", "1e603ae3d80f0817a67f7b254750d8095e48a308")
            )),
            Map.entry("real-breath-of-fire-12|SWC", List.of(
                    new PartDigest("Breath of Fire (USA).1", "061e53b0004824d989201e3cbef8e152238ffa93"),
                    new PartDigest("Breath of Fire (USA).2", "5c9c29a87c14eee02ccb7ad62f8d17e72e02cc26"),
                    new PartDigest("Breath of Fire (USA).3", "de8371c0259afef519b7ffeda13e59d5cbf00c15"),
                    new PartDigest("Breath of Fire (USA).4", "5cdc032cc755fbf48586f278e9cccedd51ccc138")
            )),
            Map.entry("real-breath-of-fire-12|UFO", List.of(
                    new PartDigest("Breath of Fire (USA).1gm", "f803c5e7d2a40c8f0ee7901df31ef8a346bed332"),
                    new PartDigest("Breath of Fire (USA).2gm", "477914deb7bc0bbe9b3001705db4e7f760c2cf7a"),
                    new PartDigest("Breath of Fire (USA).3gm", "05b88f3a227eef186b4e1a4553c3cda7ac7f801c"),
                    new PartDigest("Breath of Fire (USA).4gm", "bf6ba02b0096971b887c0987e40f7baca70f56f7")
            )),
            Map.entry("real-actraiser-2-12|FIG", List.of(
                    new PartDigest("ActRaiser 2 (USA).1", "d336e10431dcb1da4682db255dc1898b016ec99b"),
                    new PartDigest("ActRaiser 2 (USA).2", "88bd8647cb1d8e2b7765d4fe7780a609727e6da2"),
                    new PartDigest("ActRaiser 2 (USA).3", "b26b61631dbc4af560ce6fb6823c0e0166c53345"),
                    new PartDigest("ActRaiser 2 (USA).4", "4d15ca2b5836df1148a136573ef658319f506b46")
            )),
            Map.entry("real-actraiser-2-12|SWC", List.of(
                    new PartDigest("ActRaiser 2 (USA).1", "5cfc920a11a50010446930408417de113de82219"),
                    new PartDigest("ActRaiser 2 (USA).2", "530346aac8c7089cc25f73e20d7fb0450bca6c34"),
                    new PartDigest("ActRaiser 2 (USA).3", "2dade6ea8512b2a5ca2cd477da982053a68aa38e"),
                    new PartDigest("ActRaiser 2 (USA).4", "bea22d9ffec3e5a41516a45062b24bbf8b2844c5")
            )),
            Map.entry("real-actraiser-2-12|UFO", List.of(
                    new PartDigest("ActRaiser 2 (USA).1gm", "dcd3f3e963bde9e20f3c5ee0a3f14611da251b0c"),
                    new PartDigest("ActRaiser 2 (USA).2gm", "d7fb51696558ce16204904fa6c031dcc56f339f0"),
                    new PartDigest("ActRaiser 2 (USA).3gm", "9b76fe7f5524f6787cb4eae33cd85badc859dbb4"),
                    new PartDigest("ActRaiser 2 (USA).4gm", "49f8ceca08f4eac359bed891b0bf421f9cb349cd")
            )),
            Map.entry("real-tales-of-phantasia-lcase-48|FIG", List.of(
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].1", "d5f9bfb7f25dac7dd4c31d0df642fb288c7f38da"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].2", "88bf2045633abd0d8d380ec3856048f67560d475"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].3", "256aafa86dbc49f6b798c271c5d68d2f33520f66"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].4", "d09af5ddb4e14fc1be27e2899c382f1a2637cbac"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].5", "e63167100aaeedda6cd4b20185acbb383d0dd57d"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].6", "92790e13044a9d8878736b11911890f53589b871"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].7", "6a82c9184872dd33f15970739b6e995167114b06"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].8", "fafbbb298b914205497eaa0cbfa1010e991db640")
            )),
            Map.entry("real-tales-of-phantasia-lcase-48|SWC", List.of(
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].1", "1e6a3384f684862e1f87e68658f3e98774d76725"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].2", "85f847b28ac7bc8281a59c6cc1041ac606cc7309"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].3", "3fe6d6132e7e9cd3ca2cfb54fca984b54d16bd9a"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].4", "ee0edbd03e54b1c0377e02d25f7b89db41fa9ada"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].5", "42e8eabd21e4d6612719f16cf6dc59d2c2a33f5f"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].6", "2d6131163f2863e31f3ec664f12825653663797e"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].7", "58991360265585a4b269659d9245786a804ef417"),
                    new PartDigest("Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].8", "d4b543b7e1155c075e4fb7fac14e2b4ecb8f8967")
            )),
            Map.entry("real-bahamut-lagoon-near-64|FIG", List.of(
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.1", "0db97303a25cff89be2f911b7b40c472c447b5b7"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.2", "f5afef32570bb0837590d4ff18147144429262c8"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.3", "240f296e31d4bb59632af1f1de886c494a8b6717"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.4", "a58d1719e03f388e77d99812072e1c9c81e3e6f9"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.5", "d965506bae7fd709d18dec49ee2be4958745a8bb"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.6", "63ad4e5d8045e96abf8b5f32747de48189637b35"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.7", "3d45f91e72226c990678f55b43d77ddb95082f02"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.8", "18d89dbf48e5db03616fa895d8ad2b5f97b1f892")
            )),
            Map.entry("real-bahamut-lagoon-near-64|SWC", List.of(
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.1", "5eeb973e5917ecc508fad5b43562ab25a28c4ed9"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.2", "ee8738f905f21fda7b58a771d850106d4173d231"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.3", "9a21051721029fa01e93c7071e81b243d7630d92"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.4", "2efbac4e287322e23bd0be6501398666e0b8022c"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.5", "65e4ed904ce10d424c12148f4361ec09a0f9d4d8"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.6", "3ebfdae832c5141832c3a3d6141d69d92bb2949b"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.7", "1761e51e145b96a6d97576797b0ee1eb35a429f3"),
                    new PartDigest("Bahamut Lagoon (J) T+Eng v1.2 Near.8", "1525926048b3cae743e7e648481c23e330e4ea18")
            )),
            Map.entry("real-trials-of-mana-48|FIG", List.of(
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).1", "a2b7c0ec825d7e207173fbdfdce0942166a1e8f0"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).2", "526c073dc3f33ab4423ab1095e4b57640e25ef3e"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).3", "e95bb44fa9c9ac3c83041594e9e7eee7e7a8621f"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).4", "7aec365a529f11a16479406ffec4b56996a7c45a"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).5", "40272ebe7d9e96241c7bfc1fe10b9c893113aade"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).6", "89d6dbbbfacfd5646c896eedbfc655514137bf90"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).7", "abf12c9f8082c53cde5935ed9789db783282c85f"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).8", "bf123649c03f79c4194488c10361800c3d004981")
            )),
            Map.entry("real-trials-of-mana-48|SWC", List.of(
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).1", "0cbdef3f7252889b8f690404bd3193d965ea257d"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).2", "246170a168e148e6c81de231fbb00c76fcdc9dc4"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).3", "980dff1711c94fd19e031dbcad8719ce079569b1"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).4", "f3cbc5eed27a8e3403c0bcf9eeee5647fdadd0f2"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).5", "03c24e4e42105b3daa144904df12e8c2f194b3a3"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).6", "d9daa2c753c628f07151e3806e1f22b0fbb95fe1"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).7", "d89b5d05862d980b4ff78f5f51d4c6a6f104f7ea"),
                    new PartDigest("Trials of Mana (World) (Rev 1) (Collection of Mana).8", "b0e3d9a6e9bad9588d57cf95f5b52a682e0e4d63")
            )),
            Map.entry("real-dq3-rod-merida|FIG", List.of(
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.1", "b89d50c814b8d883bc2e9c85a1d312507bebf89d"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.2", "eb75ed573cd73397d15cccde6ae2051b32df3387"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.3", "a1dd337096598c47fdf91fd60f0c682b29dc6a92"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.4", "45168508294d94d6644549792f2cf061acd3a954"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.5", "44fc70826543c89b24e99fa2a5d681e98e142855"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.6", "8b6142a9616d124f6e661c02fe976aa50c73b360"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.7", "53ccbc49a0c2dd8986747361fc391e6004be55e8"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.8", "63f4618c8b5e156c907cfcb1e4b6b21fed5b7a72")
            )),
            Map.entry("real-dq3-rod-merida|SWC", List.of(
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.1", "51532de5f77d0e30a8a45387f8f5e3fdddbc39f7"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.2", "dd6cb391c3e80412571a95b2728511bee6ed4c91"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.3", "c8ad29ef4371c9526af15c01c88a938c78c7a55b"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.4", "355c40a36e2f33dd5468851ac13247f393f2d8b7"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.5", "6f417be4363590586720cf56f79abeca42d36c91"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.6", "dbb664945c9a76fdf4c713ef522e8765d2ee03e6"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.7", "bd94a2ea02ccd472a2a2b6587ba61050eeb9693a"),
                    new PartDigest("Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.8", "62b3df4780c6ddbe26f9ffc3a53c63398143b197")
            )),
            Map.entry("real-tokimeki-memorial|FIG", List.of(
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.1", "ec48593c8c2b4482aafe7c7700e6b2ceaa3f7903"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.2", "5c41d4912344ebb1b993d79e2f096f90aac93b0e"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.3", "e7c6ed7e90fb74aadb504be406a2ff2f6d99bfab"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.4", "a18edff47b1f036e7111de6217d7a7bd4db22e59"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.5", "09c08e02bed58933a86bc31fd1f3829569b890d1"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.6", "c6069b8302ccbfe00d0474f44ac25fe9de107d45"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.7", "f0effe34c39f6a084e2a687044c363243cc4f081"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.8", "e73fb06de021892fe2a82101530be51c39353bd2")
            )),
            Map.entry("real-tokimeki-memorial|SWC", List.of(
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.1", "376fd7d6b6c872317d2edbbc5905181ca4466162"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.2", "119f1a7698a2110cdaefbaf70c318fb6da50a149"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.3", "afeefc893acd2371cb56ea57067c0c28ba969959"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.4", "9d964fd262582a822cc08e6cfd12e7ce74d508fa"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.5", "b7e080f15cdb9864a820055a16f4611ca804b906"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.6", "75fcadf9dde977a10483b6a171c20c3953f48afb"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.7", "bbc7dc830defb35d5e673eecf56a948fb3ba1107"),
                    new PartDigest("Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.8", "73a0ad5e5f815dc712b1de6f0f23b2c533a9a61a")
            )),
            Map.entry("real-wizardry-vi|FIG", List.of(
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.1", "668db9cfcb4e77d5095711cd4ba8ba858e90cd7c"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.2", "3e259c7119484f7abc56a69716f9b524f17433aa"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.3", "2261cff9003aa9379a9d2b69a79f6b26358efa4c"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.4", "b4ae042400528ad47381818b1f9af6afcebc6ac9"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.5", "25dc8c5d516e9850116e06690f5280e8c8b22b4f"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.6", "d99087f0e0a456360fb10830009c5ca68ee22dbf"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.7", "0de297068901fef74639a2a2b1f5eb5956a10f37"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.8", "5103a6693ce1d36457e427573915b49bcc324dc1")
            )),
            Map.entry("real-wizardry-vi|SWC", List.of(
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.1", "696e533e10360bc39c83b792ba9384233e67580d"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.2", "3bfc471a67e37a77f3920112df03b877626e0071"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.3", "36135480dde8fc15ec9703a3dc981985631e15d7"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.4", "9fc62029fbf3b719aade0ae9c964328b721f4212"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.5", "bcffc4d371866dca8d8649c25fc72bfb7c42a11c"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.6", "148b4b2587c87647e41f2fab6b2de261f9fe96bd"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.7", "bff509ecb22325610dc61f6e2d2c4ecaec777be9"),
                    new PartDigest("Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.8", "ebef5959c7a4315890a2468149d5b3fad70f054b")
            )),
            Map.entry("real-daikaijuu-monogatari-2|FIG", List.of(
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.1", "efd7cc4cf9ea80fb7822ccf9af0263fb4a64834b"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.2", "75d636a7586ef5e5ee8b4fe0cb3b13d7fda95723"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.3", "539fc4ae8c391860118d478ff3bfa4e5ffe2be45"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.4", "721b7a7e2b44725d32e40b724f07058b66360b43"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.5", "b5b2ed179cd45c511a87540ae3401fa3bccb0835"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.6", "d8d7e2ee1813fd1b2eb922095476aaf58aef99f3"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.7", "03a8f8929ac4a6f448fadef3809b6ff0e7d39171"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.8", "2ae5a714184a9719cee622dedd3684e1a806c826")
            )),
            Map.entry("real-daikaijuu-monogatari-2|SWC", List.of(
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.1", "5333f1e60866d17e9bf2d1a847c2195a203bb155"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.2", "22fef3641ffa7fde79b475728a6cc366b3d4ab83"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.3", "b75c3abc37012e7b81d1b9e7fd7c826b82c69ab3"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.4", "638785ea3dc77a034dc1dd9bfe4ea704a31e8e3f"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.5", "0c88a850db4298c15c9afe39039fc2e06f2b17d1"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.6", "a1787cb3a2452e7b2ac166e84a79018553d4a4b6"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.7", "af08a9f5a01d6c90a432ce0527780c817ba7f3ed"),
                    new PartDigest("Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.8", "3fef15c8fbea0f6f6352789851dd0e980226ea0f")
            )),
            Map.entry("real-star-ocean-neviksti-96|FIG", List.of(
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.1", "845c989e5a32eb82ffaf1c0786eca99427000581"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.2", "e03d190166937afbb9ccf54bf01317c8c65fe884"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.3", "9a563bc71a7cdba4354392766d2c3cf18753cb9c"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.4", "d08061e758c88a14aef3bf2d5c6993155a8fda8f"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.5", "826da16272b83acac0253c8a072e193864c2749d"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.6", "170b4437cd93108cf18514c0ab9c5427256f41fe"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.7", "1adec4f5a800b2b1ef2dfa47316e8f71c51a9201"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.8", "4e4783ebbb50514680b38575d149b1add2c84404")
            )),
            Map.entry("real-star-ocean-neviksti-96|SWC", List.of(
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.1", "f697075e3efd0cb0efd84abf2ce289594627a5d4"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.2", "341d05a520e085f158ac24883d8f10bafe7c9caa"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.3", "0d7087131cace3b84ab3c806d32f952f95ae993d"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.4", "f45de90d6436f270a1265d6f4ba1c6ce3564a3b8"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.5", "413607571265a5b598c880463016d7dade6ad134"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.6", "1899f33cfada59dca1f7424e3c606bbb3052692a"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.7", "4fd4de367d103a16f09112008fa9dc2a80838b78"),
                    new PartDigest("Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.8", "e047ecc0fbb20025c1ad48e31e561bcc419a6ed0")
            )),
            Map.entry("real-sf96sfa2-body-as-sfc|FIG", List.of(
                    new PartDigest("SF96SFA2-body.1", "8988bc1a2a956cebc5d167be960153d1a03fb773"),
                    new PartDigest("SF96SFA2-body.2", "c70dee8b2c288b93bd159b20291e86b345b1acb0"),
                    new PartDigest("SF96SFA2-body.3", "d185fd76dbee592fb63aa38fdbaa5a382cc6159c"),
                    new PartDigest("SF96SFA2-body.4", "12e225166c1d6bd6b50ff43be4a6b13ad7d9bf75"),
                    new PartDigest("SF96SFA2-body.5", "16f7d8eebd4b77a845ff15c1aba2a2e08e384ce0"),
                    new PartDigest("SF96SFA2-body.6", "036200e2e76ffb9e1a25252db933924d3defd891"),
                    new PartDigest("SF96SFA2-body.7", "dabc716ed8182d6a7b160078fd4d16fca54446f2"),
                    new PartDigest("SF96SFA2-body.8", "c4ee8c094442481ee32d0e013cd40bec2fe46477")
            )),
            Map.entry("real-sf96sfa2-body-as-sfc|SWC", List.of(
                    new PartDigest("SF96SFA2-body.1", "cc4f4820116fee6eb28b54b4cadeb896ea9e1e51"),
                    new PartDigest("SF96SFA2-body.2", "73a1fe751725e7efba87af774cafc31c9235d46a"),
                    new PartDigest("SF96SFA2-body.3", "aaab3250fc255df49df2066c445279f8563a38cf"),
                    new PartDigest("SF96SFA2-body.4", "6d22d06789bc90fac2a4fbe6eee1b52f81f2cba3"),
                    new PartDigest("SF96SFA2-body.5", "b284d039caad9fa1cfab3054b2a46e3be63357d7"),
                    new PartDigest("SF96SFA2-body.6", "7305ebaf117c5d054e784b14335f38799e8abd55"),
                    new PartDigest("SF96SFA2-body.7", "0bc4e6b3878735fc23989373e195bc35aba31e10"),
                    new PartDigest("SF96SFA2-body.8", "0ef8ac49b9338605caecf2b7b904b290ac4777b2")
            ))
    );

    private static final Map<String, String> REJECTIONS = Map.ofEntries(
            Map.entry("exhirom-40|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 40 Mbit"),
            Map.entry("exhirom-48|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 48 Mbit"),
            Map.entry("exhirom-64|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 64 Mbit"),
            Map.entry("lorom-96-reject|UFO", "UFO hardware supports max 9 parts (36Mbit) for LoROM: ROM is 96 Mbit"),
            Map.entry("real-tales-of-phantasia-lcase-48|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 48 Mbit"),
            Map.entry("real-bahamut-lagoon-near-64|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 64 Mbit"),
            Map.entry("real-trials-of-mana-48|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 48 Mbit"),
            Map.entry("real-dq3-rod-merida|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 48 Mbit"),
            Map.entry("real-tokimeki-memorial|UFO", "UFO hardware supports max 9 parts (36Mbit) for LoROM: ROM is 64 Mbit"),
            Map.entry("real-wizardry-vi|UFO", "UFO hardware supports max 9 parts (36Mbit) for LoROM: ROM is 48 Mbit"),
            Map.entry("real-daikaijuu-monogatari-2|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 44 Mbit"),
            Map.entry("real-star-ocean-neviksti-96|UFO", "UFO hardware supports max 9 parts (36Mbit) for LoROM: ROM is 96 Mbit"),
            Map.entry("real-sf96sfa2-body-as-sfc|UFO", "UFO hardware does not support HiROM > 32Mbit: ROM is 96 Mbit")
    );
    // GENERATED-END

    private CrossFormatExpectations() {
    }

    public static String key(String rowId, CopierFormat format) {
        return rowId + "|" + format.name();
    }

    public static Result expected(String rowId, CopierFormat format) {
        String key = key(rowId, format);
        String rejection = REJECTIONS.get(key);
        if (rejection != null) {
            return new Rejected(rejection);
        }
        List<PartDigest> parts = DIGESTS.get(key);
        if (parts == null) {
            throw new IllegalStateException("No committed expectation for " + key + "; run CrossFormatStabilityTest#regenerate");
        }
        return new Converted(parts);
    }
}
