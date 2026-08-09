# Mining Tracker — analiza przed implementacją

Dokument roboczy dla nowego HUD-u „Mining Tracker" w Skyblockerze.
**Etap 1: tylko analiza. Kodu jeszcze nie ma.**

---

## 0. Ustalenie wersji i źródeł

### Jar użytkownika

Ścieżka: `C:\Users\danie\AppData\Roaming\.dawn\profiles\feather-default--1-21-4\.minecraft\mods\skyblocker-6.8.2+26.1.2.jar`
(nazwa profilu `feather-default--1-21-4` to tylko etykieta launchera Dawn, nie wersja gry)

Z `fabric.mod.json`:

| Pole | Wartość |
|---|---|
| `id` | `skyblocker` |
| `version` | **6.8.2+26.1.2** |
| `minecraft` | `~26.1` (jar zbudowany przeciwko **26.1.2**) |
| `fabricloader` | `>=0.19.1` |
| `java` | `>=25` |
| `environment` | `client` |
| entrypoint | `de.hysky.skyblocker.SkyblockerMod` |
| mixins | `skyblocker.mixins.json` |
| accessWidener | `skyblocker.classtweaker` |

Zależności twarde (`depends`):
`fabricloader >=0.19.1`, `fabric-api >=0.145.3+26.1.1`, `dandelion >=1.0.0-alpha.21+26.1`,
`yet_another_config_lib_v3 >=3.9.2+26.1`, `fabric-language-kotlin *`, `hm-api >=1.0.3+26.1`,
`minecraft ~26.1`, `java >=25`

Konflikty (`breaks`): `forcecloseworldloadingscreen <=2.2.0`, `skyblockmod <=1.10.7`,
`jei <29.14.0.43` / `<30.8.0.51 ^30`, `sodium <=0.8.7`

JiJ (`jars`): commons-math3 3.6.1, commons-text 1.15.0, dandelion 1.0.0-alpha.21+26.1,
discord-ipc 1.1, hm-api 1.0.3+26.1, legacy-item-dfu 1.0.4+26.1.2, networth-calculator 1.0.5,
neurepoparser 1.12.0, jgit 7.6.0, YACL 3.9.2+26.1-fabric

Uwaga: w folderze modów masz też `fabric-api-0.155.2+26.1.2` i `HypixelModAPI-1.0.2+build.1+mc26.1`,
czyli realnie gra chodzi na linii **26.1.2**, nie 26.1.1. To nie zmienia niczego w implementacji
(`~26.1` łapie obie), ale build robimy pod 26.1.2, żeby był bit-w-bit zgodny z tym co masz zainstalowane.

### Repo źródłowe

Klon: `C:\Users\danie\Skyblocker` (remote `https://github.com/SkyblockerMod/Skyblocker`).
Tag pasujący 1:1: **`v6.8.2+26.1.2`** (istnieje też równoległa linia `+26.2` — nie ta).
Utworzona gałąź robocza: **`mining-tracker`** z tego tagu (`0f53a9cb7`).

`gradle.properties` na tym tagu potwierdza: `minecraft_version=26.1.2`, `loader_version=0.19.1`,
`loom_version=1.16-SNAPSHOT`, `mod_version=6.8.2`, `yacl_version=3.9.2+26.1`.

Dekompilacja **nie była potrzebna** — wersja jara i tag pokrywają się dokładnie, a wszystkie
klasy o które pytasz są w źródłach. Vineflower zostaje w odwodzie na wypadek rozjazdu.

### Toolchain (potwierdzone, nie blokuje)

`build.gradle` wymusza `options.release = 25` i `sourceCompatibility/targetCompatibility = VERSION_25`,
bez bloku `toolchain`. Systemowy `JAVA_HOME` to JDK 21 — **sam z siebie build się nie zbuduje**.
Ale Gradle ma już zaciągnięty JDK 25:
`C:\Users\danie\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2` (jest `provisioned.ok`).
Build będzie odpalany z `JAVA_HOME` ustawionym na tę ścieżkę.

Drugie ryzyko: **na `C:` zostało ~6,5 GB wolnego**, a `~/.gradle` waży już 22 GB.
Build Looma potrafi zjeść kilka GB na remap/decompile. Jeśli zabraknie miejsca — przenosimy
`GRADLE_USER_HOME` na `D:` (58 GB wolnego). Zgłoszę, gdyby do tego doszło.

---

## 1. Rekonesans — co już istnieje w kodzie

### 1a. Farming tracker — jak działa

Dwa pliki, klasyczny podział „logika / render":

- **`skyblock/garden/FarmingHud.java`** — zbiera dane
- **`skyblock/garden/FarmingHudWidget.java`** — rysuje i liczy coins/h

**Skąd bierze zdobyte itemy:** *nie zlicza itemów w ogóle.* To jest kluczowe odkrycie i główny
powód, dla którego Mining Tracker nie może być kopią Farming HUD-u.
Farming HUD czyta **licznik Cultivating z NBT trzymanego narzędzia**
(`ItemUtils.getCustomData(stack)` → klucz `farmed_cultivating`, `FarmingHud:118-130`),
czyli monotoniczny licznik serwerowy. Z różnicy dwóch odczytów wychodzi crops/min.
Żadnego czatu, żadnych sacków, żadnego diffa ekwipunku.

**Skąd ceny:** `ItemUtils.getItemPrice(cropItemId)` + `TooltipInfoType.NPC.getData()`
(`FarmingHudWidget:147-215`). Rozróżnienie źródeł to enum `FarmingConfig.Type {BOTH, NPC, BAZAAR}`:
- `NPC` — tylko cena NPC
- `BAZAAR` — tylko bazaar
- `BOTH` — wyższa z dwóch

Pod spodem `ItemUtils.getItemPrice(id, useBazaarBuyPrice, useAuctionAverage)` (`ItemUtils:415-437`):
bazaar → `BazaarProduct.buyPrice()` / `sellPrice()`, fallback na 3-day average, fallback na lowest BIN.

**Semantyka cen bazaru** (potwierdzone w `BazaarPriceTooltip:30-39`):
- `buyPrice()` = „Bazaar Buy Price" = ile **płacisz** za instant buy = sell offer wall
- `sellPrice()` = „Bazaar Sell Price" = ile **dostajesz** za instant sell = buy order wall

Czyli mapowanie na to co chcesz na HUD:

| Twoja nazwa | Metoda | Znaczenie |
|---|---|---|
| **instasell** (domyślne) | `getItemPrice(id, false)` → `sellPrice()` | ile dostaniesz teraz, natychmiast |
| **sell offer** | `getItemPrice(id, true)` → `buyPrice()` | ile dostaniesz, jak wystawisz i poczekasz |
| **NPC** | `TooltipInfoType.NPC.getData().getDouble(id)` | sprzedaż NPC |

**Jak liczy „na godzinę":** okno kroczące, ale **absurdalnie krótkie — 5 sekund**
(`FarmingHud.STATS_WINDOW = 5_000`). Trzy `Deque`/`Queue` (`counter`, `blockBreaks`, `farmingXp`),
z których każdy tick wypada wszystko starsze niż 5 s (`FarmingHud:56-74`).
`blockBreaks()` = `(size-1) / (last-first) * 1000` → bloki/s.
`farmingXpPerHour()` = *ostatni odczyt XP* × blocks/s × 3600 — czyli ekstrapolacja z 5 sekund.
Dla farmy w Gardenie to ujdzie (dropy są równomierne), **dla miningu to bez sensu**:
jeden Flawless z Pristine w 5-sekundowym oknie dałby coins/h liczone w miliardach.

**Jak zarejestrowany HUD:** adnotacja `@RegisterWidget` na klasie widgetu, processor z `buildSrc`
(`de.hysky.skyblocker.hud.HudProcessor`) generuje rejestrację w czasie kompilacji.
Widget dziedziczy `ElementBasedWidget` (który dziedziczy `HudWidget` → `BasicWidget`) i implementuje:
`updateContent()`, `availableLocations()`, `isEnabledIn(Location)`, `setEnabledIn(Location, boolean)`,
`getDisplayName()`. Pozycjonowanie/skalę ogarnia `WidgetsConfigurationScreen`.
Logika inicjalizująca leci przez `@Init` na statycznej metodzie (processor `InitProcessor`).

**Wpis w configu:** `config/configs/FarmingConfig.java` → `public static class FarmingHud`
z polami `enabled/counter/coins/type/includeSeedsPrice/experience`; opcje YACL budowane w
`config/categories/FarmingCategory.java`; klucze tłumaczeń w
`src/main/resources/assets/skyblocker/lang/en_us.json` (`skyblocker.config.farming.farmingHud.*`
oraz `skyblocker.farming.farmingHud.*` na treść HUD-u).

**Jak wykrywa lokację:** `Utils.getLocation() == Location.GARDEN` (`FarmingHud:133`)
i `isEnabledIn(Location)` w widgecie. `Location` (`utils/Location.java`) to enum wyspy,
mapowany z ID Hypixela (`mining_3` → `DWARVEN_MINES`, `crystal_hollows`, `mineshaft` itd.).

### 1b. Co JUŻ istnieje dla miningu (bardzo dużo!)

To jest najważniejszy wynik rekonesansu. Skyblocker ma już trzy trackery miningowe i grzechem
byłoby dublować ich logikę:

| Klasa | Co robi | Mechanizm |
|---|---|---|
| `skyblock/dwarven/profittrackers/PowderMiningTracker.java` | loot z Powder/Loot Chestów w Crystal Hollows | parsuje blok czatu między `  CHEST LOCKPICKED ` / `  LOOT CHEST COLLECTED ` a linią separatora `▬▬▬…`, wiersze wg `REWARD_PATTERN`; ma gotową mapę `NAME2ID_MAP` (~60 wpisów: gemstony, essence, Goblin Eggi, części Nucleusa, Prehistoric Egg, Treasurite…) |
| `skyblock/dwarven/profittrackers/corpse/CorpseProfitTracker.java` | loot z Frozen Corpses | `CORPSE_PATTERN = "  (LAPIS\|UMBER\|TUNGSTEN\|VANGUARD) CORPSE LOOT! *"`, ten sam schemat blok+separator; **odejmuje cenę klucza** (`CorpseType.getKeyPrice()`); własna `NAME2ID_MAP` (Suspicious Scrap, Frostbitten Dye, plates, Glacite Jewel…) |
| `skyblock/dwarven/profittrackers/AbstractProfitTracker.java` | wspólna baza | `REWARD_PATTERN = " {4}(.*?) ?x?([\\d,]*)"`, `HOTM_XP_PATTERN = " {4}\\+[\\d,]+ HOTM Experience"`, `replaceGemstoneSymbols()`, `getRewardFilePath()` |

Plus infrastruktura ogólna:

| Klasa | Co daje |
|---|---|
| `skyblock/chat/SackMessagePrice.java` | pełny parser wiadomości sackowych: `[Sacks] ` + hover event z `Added items:` / `Removed items:`, trójki (count, nazwa, sack). `ITEM_COUNT_PATTERN = "([-+][\\d,]+)"` |
| `skyblock/ItemPickupWidget.java` | drugi parser sacków (`CHANGE_REGEX = "([+-])([\\d,]+) (.+) \\((.+)\\)"`) **oraz** diff ekwipunku przez `onItemPickup(slot, newStack)` (mixin na pakiet slot-update), z ochroną `changingLobby` na 60 tików po dołączeniu |
| `skyblock/tabhud/widget/PowderWidget.java` | odczyt **stanu powderów z listy TAB**: `"Mithril: ([\\d,]+)"`, `"Gemstone: ([\\d,]+)"`, `"Glacite: ([\\d,]+)"` — i już liczy diff między odczytami |
| `skyblock/tabhud/util/PlayerListManager` | `getPlayerStringList()` — surowe linie TAB-a |
| `utils/Utils.java` | `getLocation()`, `getArea()`, `getIslandArea()` (surowa linia `⏣ …` ze scoreboardu), `STRING_SCOREBOARD`, `getPurse()` |
| `utils/mayor/MayorUtils.java` | `getActivePerks()` → lista aktywnych perków burmistrza/ministra + `SkyblockEvents.MAYOR_CHANGE` |
| `utils/data/ProfiledData<T>` | persystencja per `UUID` + `profileId`, Codec-owa; `SkyblockEvents.PROFILE_CHANGE` przełącza zestaw danych |
| `events/ItemPriceUpdateEvent.ON_PRICE_UPDATE` | hook do przeliczenia wartości po odświeżeniu cen |
| `utils/SkyBlockIcons` | znaki prywatnego obszaru Unicode: `PRISTINE = \uE01C`, `MINING_FORTUNE`, `MINING_SPEED`, `HEALTH`, `DEFENSE`, `INTELLIGENCE`, `STRENGTH`, `CRIT_DAMAGE`, `FISHING_SPEED`, `FORAGING_FORTUNE`, `FARMING_FORTUNE`, `AREA` |

**Czego NIE ma i trzeba dopisać:** Mining XP z action baru, licznik proców Pristine,
RARE DROP / PRISTINE!, spawn mineshaftu, Powder Ghast, Sky Mall (jest tylko *filtr* czatu,
`SkyMallFilter`, nie parser), detekcja aktywnego eventu miningowego,
zużycie Suspicious Scrap w ekskawatorze, zużycie paliwa do drilla
(`enableDrillFuel` to wyłącznie pasek durability w tooltipie, `ItemStackMixin:141`).

---

## 2. Mechanizmy detekcji — który do czego i DLACZEGO

Ryzyko podwójnego liczenia jest realne, bo np. Flawed Ruby z Powder Chesta **jednocześnie**:
(a) pojawia się w wiadomości chestowej, (b) wpada do sacka i generuje `[Sacks] +1`,
(c) mógłby wpaść do ekwipunku, gdyby sack był pełny.

### Zasada naczelna: **jeden item ID → dokładnie jeden kanał**

Wprowadzam jawny routing. Każdy trackowany item ma przypisany **jeden** `SourceChannel`.
Zdarzenie z innego kanału dla tego samego ID jest **ignorowane w całości** — nie ma
„okien czasowych", „heurystyk bliskości" ani żadnej innej kruchej magii.
To jest testowalne jednym testem: zbiór ID-ków każdego kanału musi być rozłączny z pozostałymi.

| Kanał | Mechanizm | Co przez niego liczę | Dlaczego akurat ten |
|---|---|---|---|
| `SACK` | wiadomości `[Sacks] ` + hover `Added items:` (parser wzorowany na `SackMessagePrice`) | **wszystkie itemy sackowalne**: rudy, enchanted-rudy, gemstony Rough/Flawed/Fine/Flawless, Hard Stone, Glacite, Suspicious Scrap, Frostbitten Dye, Goblin Eggi | To jedyne źródło, które łapie **wszystko naraz**: kopanie, chesty, korpusy, dropy z mobów, Super Compactor (do sacka wpada już *enchanted* wersja, więc kompaktowanie jest uwzględnione automatycznie i nie trzeba go modelować). Serwer sam agreguje. |
| `PICKUP` | diff ekwipunku (ten sam hook co `ItemPickupWidget.onItemPickup`) | **tylko itemy niesackowalne**, z jawnej allowlisty: pety (Scatha, Yog, Butterfly, Bal, Automaton, Sludge, Thyst), Treasurite, Jungle Heart, Prehistoric Egg, Ascension Rope, Pickonimbus, Wishing Compass, części Nucleusa (Robotron Reflector, Superlite Motor, Control Switch, Synthetic Heart, Electron Transmitter, FTX 3070), Gemstone Crystals, fossile, Refined Mineral, Glossy Gemstone, Raffle Ticket | Te itemy **nie mają sacka**, więc kanał `SACK` ich nie zobaczy. Nie ma tu ryzyka kolizji, bo allowlista jest rozłączna z listą sackowalnych. |
| `CHEST_MSG` | blok czatu Powder Chest (istniejący `PowderMiningTracker`) | **tylko licznik otwartych chestów** + rozbicie „co wypadło" do tabelki top-5 | Wartość pieniężną biorę z `SACK`/`PICKUP`. Wiadomość chestowa służy wyłącznie za **atrybucję** (żeby wiedzieć, że ten Flawed przyszedł z chesta, a nie z Pristine) i za licznik chestów. Zero podwójnego liczenia, bo ten kanał nie dodaje coinów. |
| `CORPSE_MSG` | blok czatu Corpse Loot (istniejący `CorpseProfitTracker`) | licznik korpusów per typ + **koszt klucza** (Umber/Tungsten/Skeleton) + atrybucja lootu | Jak wyżej: przychód z sacków, ale **koszt klucza tylko tutaj** — klucz znika z ekwipunku, więc `PICKUP` mógłby go złapać jako ujemny diff; dlatego klucze wpisuję na blocklistę `PICKUP` i liczę koszt wyłącznie z wiadomości korpusowej. |
| `TAB_DIFF` | dodatnie różnice linii TAB `Mithril: N` / `Gemstone: N` / `Glacite: N` (wzorzec z `PowderWidget`) | **wszystkie trzy powdery, wyłącznie tym kanałem** | Powder nie ma itemu, nie ma sacka i ma kilkanaście źródeł (kopanie, commissiony, Ghast, Goblin, Grubber, Monolith, Fetchur, Puzzler, Nucleus, Emissary, perki Daily Grind/Daily Powder). Licznik z TAB-a jest **sumą wszystkich** z definicji — i **już zawiera mnożnik 2x Powder**. To jest cała odpowiedź na wymaganie „mnożnik nie może być zastosowany podwójnie": **nigdzie nie mnożę przez 2**. Event 2× wykrywam i pokazuję jako informację, ale nie dotykam nim liczb. Ujemne diffy (wydatki w HOTM) idą do osobnego licznika „spent", nie zerują przychodu. |
| `CHAT_PATTERN` | pojedyncze linie czatu (nowe regexy) | zdarzenia **bez wartości pieniężnej**: PRISTINE!, RARE DROP (tylko jako flaga atrybucji), spawn mineshaftu, Powder Ghast, Sky Mall, start/koniec eventu | Te wiadomości są **informacyjne**. Nie dodają coinów — coiny przyszły już sackiem. |
| `BLOCK_BREAK` | `ClientPlayerBlockBreakEvents.AFTER` | Blocks/s, Blocks total, auto-pauza sesji | Jak w Farming HUD. |
| `ACTION_BAR` | overlay message | Mining XP/h | Jak `FARMING_XP` w Farming HUD, tylko `Mining`. |

### Dlaczego NIE wybrałem alternatyw

- **Sacki + pickupy razem** — najczęstszy błąd tego typu trackerów. Kiedy sack się zapełni albo
  gracz ma wyłączone powiadomienia sackowe, item idzie do ekwipunku; policzenie obu daje ×2.
  Rozłączne allowlisty to eliminują z definicji.
- **Diff zawartości sacków (otwieranie GUI sacków co N tików)** — odrzucone: wymaga otwierania
  kontenera, jest wolne, gubi zmiany między odczytami i i tak duplikuje kanał `SACK`.
- **Liczenie powderu z czatu / z commissionów** — odrzucone: rozdrobnione na kilkanaście
  wiadomości, część źródeł w ogóle nie pisze na czat, a mnożnik 2× trzeba by aplikować ręcznie
  (czyli dokładnie to, co ma nie wystąpić). TAB wygrywa bezapelacyjnie.
- **Liczenie wartości z wiadomości chestowej/korpusowej** (jak robi to obecny `PowderMiningTracker`)
  — odrzucone jako *źródło coinów*, bo koliduje z sackami. Zostaje jako źródło atrybucji.

### Znane ograniczenia, które trzeba jasno powiedzieć

1. Kanał `SACK` **wymaga włączonych powiadomień sackowych po stronie Hypixela**
   (`/sbmenu → Settings → Personal → Sack Notifications`). Bez tego coins/h będzie ~0.
   Plan: wykrywam brak jakiejkolwiek wiadomości sackowej mimo N zbitych bloków i wypisuję ostrzeżenie na HUD.
2. Wiadomości sackowe są **batchowane przez serwer** (~co kilka sekund). Przy oknie kroczącym
   ≥ 60 s to nie ma znaczenia, przy 5 s (jak w Farming HUD) miałoby.
3. `Blocks/s` nie złapie bloków, które serwer podmienia sam (analogia do Cactus Knife
   z `WorldEvents.BLOCK_STATE_UPDATE` w Farming HUD). Dla drilli AOE / Pickobulusa licznik będzie
   zaniżony — udokumentuję to na tooltipie, nie będę udawał dokładności której nie ma.

### Pristine — jak to naprawdę wykryć

Hypixel **nie wysyła wiadomości o procu Pristine**. Realna metoda:
Pristine zamienia drop `Rough` → `Flawed`. Naturalne kopanie gemstone daje wyłącznie `Rough`.
Zatem: **każdy `Flawed` przyrost z kanału `SACK`, którego nie da się przypisać do otwartego
w tej samej chwili chesta (`CHEST_MSG`) ani korpusa (`CORPSE_MSG`), to proc Pristine.**
Stąd bierze się rola kanałów atrybucji opisanych wyżej — bez nich licznik Pristine byłby zawyżony
o każdy Flawed z chesta. To jedyne miejsce, gdzie stosuję korelację czasową (okno ~2 s od
wiadomości chestowej/korpusowej), i jest ona ograniczona wyłącznie do licznika Pristine —
**nigdy do coinów**.

Jeśli okaże się, że na 26.1.2 Hypixel jednak wysyła linię typu `PRISTINE! …` — regex jest
przygotowany (sekcja 7) i wtedy przełączam się na niego jako źródło twarde, a heurystykę wyłączam.

---

## 3. Pełne pokrycie źródeł przychodu

Legenda kolumny „impl.":
**T** = będzie w pierwszej wersji · **T\*** = będzie, ale opiera się na regexie do weryfikacji z realnym czatem ·
**N** = świadomie poza zakresem (z powodem)

### 3.1 Rudy i metale

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Mithril (+ Enchanted) | `SACK` | przychód | T |
| Titanium (+ Enchanted) | `SACK` | przychód | T |
| Umber (+ Enchanted, Refined, Plate) | `SACK` | przychód | T |
| Tungsten (+ Enchanted, Refined, Plate) | `SACK` | przychód | T |
| Glacite (+ Enchanted) | `SACK` | przychód | T |
| Hard Stone (+ Enchanted) | `SACK` | przychód | T |
| Coal, Iron, Gold, Redstone, Lapis, Emerald, Diamond (+ Enchanted, + Block) | `SACK` | przychód | T |
| Sulphur / Glowstone Dust (+ Enchanted) | `SACK` | przychód | T |
| Netherrack, Quartz, Obsidian, Gravel, Mycelium, Red Sand (+ Enchanted) | `SACK` | przychód | T |
| Wersje `ENCHANTED_*` przy Super Compactorze | `SACK` — serwer wrzuca do sacka już skompaktowany item | przychód | T — **bez osobnej logiki**, wynika z wyboru kanału |
| Refined Mithril / Refined Titanium | `PICKUP` (niesackowalne) | przychód | T |

Mapowanie nazwa→ID: własna tabela `MiningItems`, zasilana z `NEURepoManager.getItemByName()`
jako fallback (tak robi `SackMessagePrice:148-158` i `ItemPickupWidget:68-77`).

### 3.2 Gemstony

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Rough × 12 typów (Ruby, Amethyst, Jade, Sapphire, Amber, Topaz, Jasper, Opal, Onyx, Aquamarine, Citrine, Peridot) | `SACK` | przychód | T |
| Flawed × 12 typów | `SACK` | przychód | T |
| Fine / Flawless (z chestów, korpusów, craftu) | `SACK` | przychód | T |
| **Licznik proców Pristine** (Rough→Flawed) | `SACK` + atrybucja `CHEST_MSG`/`CORPSE_MSG` (sekcja 2) | osobny licznik, **nie coiny** | T\* |
| Gemstone Crystals (Opal/Onyx/Aquamarine/Peridot/Citrine/Ruby/Jasper Crystal z mineshaftów) | `PICKUP` | przychód **bez ceny** — Skyblocker jawnie trzyma je w `PRICELESS_ITEMS` (`CorpseProfitTracker:61`), bo nie mają ceny bazarowej/BIN | T (licznik sztuk), wycena N |

Symbole gemstone’ów w nazwach są ucinane przez `AbstractProfitTracker.replaceGemstoneSymbols()` —
używam tego samego, żeby nie mnożyć wariantów.

### 3.3 Powdery — **traktowane osobno, nie jako coiny**

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Mithril Powder | `TAB_DIFF` (`Mithril: N`) | powder, osobna kategoria | T |
| Gemstone Powder | `TAB_DIFF` (`Gemstone: N`) | powder | T |
| Glacite Powder | `TAB_DIFF` (`Glacite: N`) | powder | T |
| Kopanie, commissiony, Powder Ghast, Golden/Diamond Goblin, Mithril Grubber, Dark Monolith, Fetchur, Puzzler, Crystal Nucleus, Emissary w Gardenie, perki Daily Grind / Daily Powder | **wszystkie przez `TAB_DIFF`** | powder | T — z definicji, bez per-źródłowej logiki |
| Rozbicie „ile z którego źródła" | — | — | **N** — TAB podaje tylko sumę. Rozbicie wymagałoby parsowania każdego źródła osobno, co wraca do problemu podwójnego liczenia. Jeśli tego chcesz, robimy to jako osobny, opcjonalny „powder breakdown" w drugiej iteracji. |
| Event 2× Powder | `CHAT_PATTERN` + `MayorUtils.getActivePerks()` | **tylko wyświetlenie flagi na HUD** | T\* — **mnożnik nigdy nie jest aplikowany do liczb** |
| Wycena powderu w coinach | opcja configu, **domyślnie WYŁĄCZONA** | — | T |

### 3.4 Crystal Hollows

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Zawartość Powder Chestów (Treasure Chest) | wartość: `SACK`/`PICKUP`; licznik i atrybucja: `CHEST_MSG` (istniejący parser) | przychód | T |
| Natural Loot Chest (osobno) | `CHEST_MSG` — jest już flaga `countNaturalChestsInTracker` | przychód | T |
| Dropy z Worms / Scatha | `SACK` (rudy) + `PICKUP` (pet Scatha) | przychód | T |
| Pet Scatha | `PICKUP` + `CHAT_PATTERN` (`PET DROP!`) na potwierdzenie | przychód | T\* |
| Automaton, Sludge, Butterfly, Bal, Yog, Thyst — dropy | `SACK` | przychód | T |
| …ich pety | `PICKUP` + `CHAT_PATTERN` | przychód | T\* |
| Sludge Juice, Yoggie, Jungle Heart, Treasurite | `PICKUP` (są już w `NAME2ID_MAP` istniejącego trackera) | przychód | T |
| Loot z Crystal Nucleus runs: Robotron Reflector, Superlite Motor, Control Switch, Synthetic Heart, Electron Transmitter, FTX 3070, Prehistoric Egg | `PICKUP` | przychód | T |
| Wishing Compass, Pickonimbus 2000 | `PICKUP` | przychód | T |

### 3.5 Glacite Mineshafts

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Frozen Corpse: Lapis / Umber / Tungsten / Vanguard — **osobne tabele per typ** | `CORPSE_MSG` (istniejący `CorpseProfitTracker` już rozbija per `CorpseType`) + wartość z `SACK`/`PICKUP` | przychód | T |
| Koszt kluczy: Umber Key, Tungsten Key, Skeleton Key | `CORPSE_MSG` → `CorpseType.getKeyPrice()` | **koszt** | T |
| Suspicious Scrap (zdobyty) | `SACK` | przychód | T |
| Frostbitten Dye | `SACK` (`DYE_FROSTBITTEN`) | przychód | T |
| Glacite Jewel, Glacite Amalgamation, Bejeweled Handle, Frozen Scute, Shattered Locket, Caged Wisp, Enchanted Book (Ice Cold I), Dwarven O’s Metallic Minis | `SACK`/`PICKUP` wg sackowalności | przychód | T |
| Ascension Rope jako źródło bonusowego powderu | efekt widoczny w `TAB_DIFF`; sam item przez `PICKUP` | przychód (item) | T |
| Spawn mineshaftu (licznik wejść) | `CHAT_PATTERN` | licznik | T\* |

### 3.6 Fossil Excavator

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Wykopane fossile (Helix, Ugly, Tusk, Claw, Spine, Webbed, Footprint, Claw…) | `PICKUP` | przychód | T |
| Fossil Dust | `SACK` jeśli sackowalny, inaczej `PICKUP` — do potwierdzenia w grze | przychód | T |
| **Zużyte Suspicious Scrap** | `PICKUP` (ujemny diff) — Scrap znika z ekwipunku przy włożeniu do ekskawatora | **koszt** | T |
| Postęp wykopu | istnieje `FossilSolver.PERCENTAGE_PATTERN` / `CHARGES_PATTERN` — użyję do wykrycia „excavator w toku" | — | T |

Uwaga: Scrap jest jedynym itemem, dla którego kanał `PICKUP` obsługuje **oba kierunki**
(dodatni = przychód z korpusa? nie — przychód idzie sackiem; ujemny = koszt).
Żeby nie było kolizji: **Scrap ma kanał `SACK` dla przychodu i `PICKUP` tylko dla ujemnych diffów.**
To jedyny wyjątek od zasady „jeden item → jeden kanał" i jest bezpieczny, bo kierunki są rozłączne.
Test jednostkowy to zweryfikuje.

### 3.7 Eventy i mayorowie

| Źródło | Mechanizm | Przychód/koszt | impl. |
|---|---|---|---|
| Wykrycie aktywnego eventu (co jest aktywne → na HUD) | `MayorUtils.getActivePerks()` + `SkyblockEvents.MAYOR_CHANGE` + `CHAT_PATTERN` na start/koniec | informacja | T\* |
| Mining Fiesta: Refined Mineral | `PICKUP`, **liczone tylko gdy event aktywny** | przychód | T |
| Mining Fiesta: Glossy Gemstone | `PICKUP`, tylko przy aktywnym evencie | przychód | T |
| Raffle: Raffle Ticket | `PICKUP` | przychód | T |
| Raffle: nagrody | `CHAT_PATTERN` + `PICKUP` | przychód | T\* |
| Goblin Raid: Golden Goblin Egg, Goblin Egg (+ Green/Blue/Red/Yellow) | `SACK` (są w `NAME2ID_MAP`) | przychód | T |
| Better Together, Fortunate Freezing, Mithril Gourmand, Gone with the Wind | `MayorUtils.getActivePerks()` — **wyświetlane jako aktywny modyfikator** | informacja, **bez mnożenia liczb** | T |
| „2× Powder" | j.w. | informacja | T\* |

Świadoma decyzja: **żaden perk ani event nie modyfikuje wyliczeń**. Wszystkie ich efekty są już
zawarte w tym, co realnie wpadło do sacka / do TAB-a. Perki służą wyłącznie do etykiety na HUD,
żeby patrząc na coins/h wiedzieć, w jakich warunkach ta liczba powstała.

### 3.8 Koszty

| Koszt | Mechanizm | impl. |
|---|---|---|
| Umber Key, Tungsten Key, Skeleton Key | `CORPSE_MSG` → `CorpseType.getKeyPrice()` | T |
| Jungle Key | `CHAT_PATTERN` (otwarcie Jungle Temple) + `PICKUP` (ujemny) | T\* |
| Paliwo do drilla: Volta, Oil Barrel, Goblin Egg | `PICKUP` — ujemny diff przy tankowaniu drilla | T\* |
| Zużyty Suspicious Scrap w ekskawatorze | `PICKUP` ujemny (sekcja 3.6) | T |
| Ascension Rope, jeśli konsumowalny | `PICKUP` ujemny | T\* |
| Pickonimbus (zużycie ładunków) | — | **N** — to durability, nie item; doliczenie wymagałoby modelowania amortyzacji. Do drugiej iteracji, jeśli będziesz chciał. |

---

## 4. Zawartość HUD

Widget: `MiningTrackerWidget extends ElementBasedWidget`, internal ID `mining_tracker`,
`@RegisterWidget`. Dostępny w: `DWARVEN_MINES`, `CRYSTAL_HOLLOWS`, `GLACITE_MINESHAFTS`,
`DEEP_CAVERNS`, `GOLD_MINE`. Każda linia = osobny `boolean` w configu.

| Linia | Domyślnie | Uwagi |
|---|---|---|
| Coins/h | ✅ | z przełącznikiem `PriceSource {INSTASELL, SELL_OFFER, NPC}`, domyślnie `INSTASELL` |
| Coins total (sesja) | ✅ | |
| Blocks/s | ✅ | |
| Blocks total | ✅ | |
| Mining XP/h | ✅ | |
| Powder/h — Mithril | ✅ | osobno |
| Powder/h — Gemstone | ✅ | osobno |
| Powder/h — Glacite | ✅ | osobno |
| Powder/h — total | ⬜ | domyślnie off, bo sumowanie trzech różnych walut jest mylące |
| Pristine proców/h | ✅ | |
| Top 5 najcenniejszych dropów sesji | ✅ | sortowane po `wartość × ilość` malejąco |
| Czas sesji | ✅ | z auto-pauzą |
| Aktywny event miningowy | ✅ | linia znika, gdy nic nie trwa |

**Auto-pauza:** brak `BLOCK_BREAK` przez `pauseAfterSeconds` (config, domyślnie **60 s**) →
zegar sesji staje, okno kroczące przestaje się przesuwać. Wznowienie przy pierwszym zbitym bloku.
Ważne: pauza **nie czyści** okna, tylko zamraża jego czas trwania — inaczej po każdej przerwie
coins/h skakałoby do zera.

**Okno kroczące (nie od startu sesji):**
`RollingWindow` = `ArrayDeque<Sample(long timestampMs, double value)>`, przycinana co tick do
`windowSeconds` (config, domyślnie **900 s = 15 min**; dopuszczalne 60–3600).
Stawka = `suma wartości w oknie / aktywny czas w oknie × 3600`, gdzie „aktywny czas" wyklucza
odcinki pauzy. Osobne okna dla: coinów, bloków, XP, każdego powderu, proców Pristine.

Uzasadnienie 15 minut zamiast 5 sekund z Farming HUD: w miningu wartość jest skrajnie bursty
(jeden Flawless ≈ tysiące zwykłych rud). Przy oknie 5 s liczba byłaby losowym szumem;
przy 15 min stabilizuje się i nadal reaguje na zmianę tempa w rozsądnym czasie.

---

## 5. Ceny

- **To samo repozytorium cen co farming tracker** — `ItemUtils.getItemPrice(...)` +
  `TooltipInfoType.{BAZAAR, NPC, LOWEST_BINS, THREE_DAY_AVERAGE}`.
  **Żadnego własnego pobierania z API.** Odświeżenie przez `ItemPriceUpdateEvent.ON_PRICE_UPDATE`
  → pełne przeliczenie sesji (tak jak robi `PowderMiningTracker.recalculatePrices()`).
- **Domyślnie instasell** = `getItemPrice(id, /*useBazaarBuyPrice=*/false)` = `BazaarProduct.sellPrice()`.
  Dla itemów spoza bazaru fallback łańcuchowy: 3-day average (jeśli włączone) → lowest BIN.
- `SELL_OFFER` = `getItemPrice(id, true)` = `buyPrice()`.
- `NPC` = `TooltipInfoType.NPC.getData().getDouble(id)`, z `hasOrNullWarning()` jak w `FarmingHudWidget:149`.
- Item bez ceny (np. Gemstone Crystals — `PRICELESS_ITEMS`) → liczony w sztukach, wykluczony
  z coins/h, oznaczony na liście top-5.
- **Osobna opcja `valuePowder`, domyślnie `false`.** Powder nie jest zbywalny — wliczenie go
  zawyżałoby coins/h. Gdy włączona, wycena po umownym kursie z configu (`coinsPerPowder`,
  osobno per typ), a nie po żadnej „cenie rynkowej", bo takiej nie ma.

---

## 6. Persystencja i komendy

**Persystencja:** `ProfiledData<MiningSession>` w `~/.minecraft/config/skyblocker/reward-trackers/mining-tracker.json`
(ta sama konwencja co `powder-mining.json` i `corpse-profits.json`, przez `AbstractProfitTracker.getRewardFilePath()`).
Klucz: `UUID` gracza + `profileId`.

- Relog / zmiana lobby → sesja **przeżywa** (dane trzymane per profil, nie per połączenie).
  Dodatkowo `ClientPlayConnectionEvents.JOIN` ustawia flagę `changingLobby` na 60 tików,
  żeby diff ekwipunku nie policzył całego inventory jako „zdobyte" (dokładnie jak `ItemPickupWidget:56-58`).
- Zmiana profilu → `SkyblockEvents.PROFILE_CHANGE` przełącza na inny zestaw. Sesja **nie przenosi się**
  między profilami. ✅ zgodne z wymaganiem.

**Komendy** (rejestracja przez `ClientCommandRegistrationCallback`, prefiks `/skyblocker`):

```
/skyblocker miningTracker reset      — zeruje sesję dla bieżącego profilu
/skyblocker miningTracker pause      — pauzuje ręcznie
/skyblocker miningTracker resume     — wznawia
/skyblocker miningTracker list       — pełne rozbicie w czacie/ekranie
/skyblocker hud mining               — otwiera WidgetsConfigurationScreen na tym widgecie
```

**Config YACL:** `MiningConfig.MiningTracker miningTracker` (nowa klasa wewnętrzna w
`config/configs/MiningConfig.java`), grupa `OptionGroup` w `config/categories/MiningCategory.java`
— **ta sama kategoria „Mining", obok Crystal Hollows / Glacite**, czyli tam gdzie siedzą pozostałe
HUD-y miningowe. Klucze `skyblocker.config.mining.miningTracker.*` w `en_us.json`
(+ `pl_pl.json`, jeśli chcesz — sprawdzę czy jest w repo).

---

## 7. Testy i lista regexów do weryfikacji

### Planowane testy jednostkowe (`src/test/java/.../MiningTrackerTest.java` i sąsiednie)

1. **Parsowanie wiadomości sackowych** — hover `Added items:` / `Removed items:`, wartości
   dodatnie i ujemne, liczby z przecinkami (`+1,024`), wiele itemów w jednej wiadomości,
   item nieznany (ma nie wywalić parsera, ma zalogować warn).
2. **Rozłączność kanałów** — test, który dla każdego znanego item ID sprawdza, że należy do
   dokładnie jednego `SourceChannel` (jedyny dozwolony wyjątek: Suspicious Scrap = `SACK` dla `+`,
   `PICKUP` dla `−`). To jest formalny dowód, że sack diff i pickup nie liczą tego samego dwa razy.
3. **Brak podwójnego liczenia sack + pickup** — symulacja: wiadomość sackowa `+64 Mithril`
   ORAZ pickup 64× Mithril w tym samym ticku → oczekiwane `count == 64`, nie 128.
4. **2× Powder nie mnoży dwa razy** — symulacja: `TAB_DIFF` +2000 Gemstone przy aktywnej fladze
   eventu 2× → oczekiwane `+2000` (nie 4000). Test wprost sprawdza, że flaga eventu nie wchodzi
   do żadnego wyrażenia arytmetycznego.
5. **Okno kroczące** — wstrzykiwany zegar; próbki starsze niż okno wypadają; pauza zamraża
   mianownik; stawka po wznowieniu nie skacze do 0.
6. **Parsowanie bloków czatu chest/corpse** — reużycie istniejących wzorców, testy na atrybucję
   Pristine (Flawed z chesta ≠ proc).
7. **Action bar Mining XP** — warianty `%`, `current/max`, `max` z sufiksem `k`.

### Regexy do sprawdzenia z realnym czatem

Poniższe **muszę zweryfikować z Tobą**, bo część opieram na wzorcach z 1.8-owych trackerów i
nie mam pewności co do brzmienia na 26.1.2. Oznaczone ⚠️ to te, których nie ma w istniejącym
kodzie Skyblockera i które wymyśliłem — te są najbardziej ryzykowne.

**Reużywane, sprawdzone (są w repo, działają):**

| # | Regex | Źródło |
|---|---|---|
| R1 | `([-+][\d,]+)` — licznik w hoverze sacka | `SackMessagePrice:40` |
| R2 | `([+-])([\d,]+) (.+) \((.+)\)` — linia hovera sacka | `ItemPickupWidget:39` |
| R3 | `^\[Sacks\] ` — prefiks wiadomości sackowej | `SackMessagePrice:53` |
| R4 | ` {4}(.*?) ?x?([\d,]*)` — wiersz nagrody w bloku chest/corpse | `AbstractProfitTracker:16` |
| R5 | ` {4}\+[\d,]+ HOTM Experience` | `AbstractProfitTracker:17` |
| R6 | `  (LAPIS\|UMBER\|TUNGSTEN\|VANGUARD) CORPSE LOOT! *` | `CorpseProfitTracker:68` |
| R7 | `  CHEST LOCKPICKED ` / `  LOOT CHEST COLLECTED ` (literały) | `PowderMiningTracker:149` |
| R8 | `▬{64}` — separator kończący blok nagród | `PowderMiningTracker:144` |
| R9 | `Mithril: ([\d,]+)` / `Gemstone: ([\d,]+)` / `Glacite: ([\d,]+)` — linie TAB | `PowderWidget:26-28` |
| R10 | `Cold: -(\d+)❄` | `GlaciteColdOverlay:21` |
| R11 | `Fossil Excavation Progress: (\d{1,2}.\d)%` | `FossilSolver:35` |
| R12 | `Chisel Charges Remaining: (\d{1,2})` | `FossilSolver:36` |
| R13 | `New day! Your Sky Mall buff changed!` | `SkyMallFilter:8` |
| R14 | `You can disable this messaging by toggling Sky Mall in your /hotm!` | `SkyMallFilter:11` |

**Nowe — ⚠️ DO WERYFIKACJI Z REALNYM CZATEM:**

| # | Regex | Co ma łapać |
|---|---|---|
| ⚠️ N1 | `\+(?<xp>\d+(?:\.\d+)?) Mining \((?:(?<percent>[\d,]+(?:\.\d+)?%)\|(?<current>[\d,]+)/(?<max>[\d,]+k?))\)` | Mining XP z action baru (kalka z `FarmingHud.FARMING_XP:43`, zmienione tylko słowo) |
| ⚠️ N2 | `^PRISTINE! You found (?<item>.+)!$` | proc Pristine — **nie jestem pewien, czy taka wiadomość w ogóle istnieje** |
| ⚠️ N3 | `^RARE DROP! (?<item>.+?)(?: \((?<bonus>.+)\))?$` | rare drop (atrybucja) |
| ⚠️ N4 | `^PET DROP! (?<pet>.+?)(?: \((?<bonus>.+)\))?$` | drop peta (Scatha, Yog…) |
| ⚠️ N5 | `^You have entered a Glacite Mineshaft!` | wejście do mineshaftu |
| ⚠️ N6 | `mineshaft` w linii typu `A Glacite Mineshaft has spawned nearby!` | spawn mineshaftu |
| ⚠️ N7 | `^A Powder Ghast has spawned` / `^You killed a Powder Ghast` | Powder Ghast |
| ⚠️ N8 | `^(?<goblin>Golden Goblin\|Diamond Goblin) has spawned` | goblin |
| ⚠️ N9 | `2x Powder` / `Double Powder` w scoreboardzie lub czacie | event 2× |
| ⚠️ N10 | `^MINING FIESTA` / `Mining Fiesta has (begun\|ended)` | Mining Fiesta |
| ⚠️ N11 | `^GOBLIN RAID` / `The Goblin Raid has (begun\|ended)` | Goblin Raid |
| ⚠️ N12 | `^You used your .*(Umber\|Tungsten\|Skeleton\|Jungle) Key` | zużycie klucza |
| ⚠️ N13 | `^Your (Drill\|.*Drill) is out of fuel!` / tankowanie Voltą | paliwo drilla |
| ⚠️ N14 | `^You uncovered a (?<fossil>.+) Fossil!` | fossil z ekskawatora |
| ⚠️ N15 | `^SKY MALL: ` + linia buffa dnia | Sky Mall buff |

Po pierwszym buildzie wygeneruję z kodu **kompletną, aktualną listę wszystkich regexów**
(automatycznie, nie z pamięci), żebyś mógł je przeklikać z realnym czatem. Do tego czasu
te oznaczone ⚠️ traktuj jako propozycje — kod będzie zbudowany tak, żeby **brak dopasowania
któregokolwiek z nich nie psuł reszty trackera**, tylko wyłączał tę jedną funkcję.

---

## 8. Plan plików (po akceptacji tej analizy)

```
src/main/java/de/hysky/skyblocker/skyblock/dwarven/profittrackers/mining/
├── MiningTracker.java          @Init, event wiring, sesja, komendy
├── MiningTrackerWidget.java    @RegisterWidget, render linii HUD
├── MiningSession.java          record + Codec, persystencja przez ProfiledData
├── RollingWindow.java          okno kroczące z pauzą (zegar wstrzykiwalny → testowalne)
├── SourceChannel.java          enum SACK / PICKUP / CHEST_MSG / CORPSE_MSG / TAB_DIFF / CHAT_PATTERN
├── MiningItems.java            routing item ID → kanał + tabele nazw
├── MiningEvents.java           detekcja aktywnego eventu/perków
└── MiningPatterns.java         wszystkie regexy w jednym miejscu (żeby dało się je wypisać)

src/main/java/de/hysky/skyblocker/config/configs/MiningConfig.java   (+ class MiningTracker)
src/main/java/de/hysky/skyblocker/config/categories/MiningCategory.java (+ OptionGroup)
src/main/resources/assets/skyblocker/lang/en_us.json                 (+ klucze)

src/test/java/de/hysky/skyblocker/skyblock/dwarven/profittrackers/mining/
├── MiningSackParsingTest.java
├── SourceChannelDisjointTest.java
├── NoDoubleCountingTest.java
├── PowderMultiplierTest.java
└── RollingWindowTest.java
```

Build: `JAVA_HOME=<gradle jdk 25> ./gradlew build`, jar z `build/libs/` → folder modów,
stary jar przenoszony na `skyblocker-6.8.2+26.1.2.jar.bak` (**nie kasowany**).

---

## 8b. Co się zmieniło względem analizy po przejrzeniu Twoich logów

Po akceptacji przejrzałem `latest.log` (8538 linii czatu z sesji w Dwarven Mines i Glacite Mineshafts,
SkyBlock v0.27). Sześć rzeczy wyszło inaczej niż zakładałem:

1. **`PRISTINE! You found ⸰ Flawed Amethyst Gemstone x15!` NAPRAWDĘ ISTNIEJE** (185 wystąpień w logu).
   To wywala całą heurystykę „Flawed z sacka minus atrybucja" z sekcji 2 — mam twarde źródło.
   Konsekwencja: kanały `CHEST_MSG`/`CORPSE_MSG` **nie są już potrzebne do atrybucji Pristine**,
   zostają tylko jako licznik chestów/korpusów i jako miejsce naliczenia kosztu klucza.
   Zniknęła jedyna korelacja czasowa w całym projekcie. Kod jest przez to prostszy i dokładniejszy.
2. **`Your Pickobulus destroyed 105 blocks!`** — klient nie widzi tych łamań, więc bez tego licznik
   bloków byłby zaniżony o cały AOE. Dodane do `BLOCK_BREAK`.
3. **Sack notifications MASZ włączone.** Format: `[Sacks] +1,005 items, -442 items. (Last 25s.)`,
   szczegóły w hoverze. Fallback na diff ekwipunku nieimplementowany, zgodnie z Twoją decyzją.
4. **Ujemne diffy sackowe są ignorowane.** W logu regularnie widać `-442 items` obok `+1,005`.
   To itemy wychodzące z sacka (sprzedaż, craft), już policzone jako przychód przy wejściu —
   odejmowanie ich drugi raz karałoby sesję za sprzedanie tego, co się wykopało.
5. **Nowy event, którego nie było na mojej liście:**
   `✴ A Fallen Star has crashed at Cliffside Veins! Nearby ore and Powder drops are amplified!`
   Dodany jako etykieta na HUD.
6. **Goblin Egg jako paliwo do drilla NIE jest liczony jako koszt.** Goblin Egg jest sackowalny
   (jest w tabeli sacków), więc dopuszczenie go do kanału `PICKUP` złamałoby rozłączność kanałów
   i groziło policzeniem go raz jako przychód, raz jako koszt. Volta, Oil Barrel i Biofuel są liczone.

### Okna kroczące — Twoja decyzja

Dwa okna pokazywane jednocześnie: **5 min / 1 h**, oba w *aktywnym* czasie sesji.
Mianownik to `min(długość okna, całkowity aktywny czas)` — czyli na starcie sesji dzieli przez to,
ile faktycznie kopiesz, a nie przez sztywne 5 minut. Po dziesięciu minutach kopania okno 5-minutowe
dzieli już przez 5 minut. Auto-pauza (domyślnie 60 s bez złamanego bloku) zatrzymuje aktywny zegar,
więc okno **zamarza** zamiast po cichu spływać do zera — po powrocie z przerwy widzisz tę samą
liczbę, na której skończyłeś.

### Nieimplementowane, zgodnie z Twoimi odpowiedziami

- rozbicie powderu per źródło (pkt 2) — TAB podaje tylko sumę
- amortyzacja Pickonimbusa (pkt 3)
- wycena Gemstone Crystals (pkt 4) — licznik sztuk
- fallback na diff ekwipunku dla itemów sackowalnych (pkt 5)

## 9. Pytania / decyzje do potwierdzenia

1. **Okno kroczące 15 min** — pasuje, czy wolisz krócej (np. 5 min, szybsza reakcja, większy szum)?
2. **Powder breakdown per źródło** — świadomie odpuszczam w v1 (sekcja 3.3). Potwierdzasz?
3. **Amortyzacja Pickonimbusa** — odpuszczam w v1 (sekcja 3.8). Potwierdzasz?
4. **Wycena Gemstone Crystals** — nie mają ceny w źródłach Skyblockera; zostają jako licznik sztuk. OK?
5. **Czy masz włączone Sack Notifications** na Hypixelu? Bez tego cały kanał `SACK` (czyli
   większość przychodu) nie zadziała — muszę wiedzieć, czy planować fallback na diff ekwipunku.
6. Regexy ⚠️ z sekcji 7 — jeśli masz gdzieś logi czatu z miningu, będą bezcenne.
