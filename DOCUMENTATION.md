# ÁNYK MCP Server - Dokumentáció

## 1. Áttekintés

Az ÁNYK MCP Server egy Java alkalmazás, amely MCP (Model Context Protocol) interfészen keresztül teszi lehetővé magyar adónyomtatványok AI-alapú kitöltését. Az eredeti ÁNYK (Általános Nyomtatványkitöltő) alkalmazás `abevjava.jar` osztályait használja fel headless módban.

**Alapelv:** amit lehet, az eredeti `abevjava.jar`-ból használunk. Saját kód csak ott van, ahol a jar GUI-hoz vagy hálózati infrastruktúrához kötött (init property-k, NAV letöltés), illetve teljesen új funkcióknál (profil tárolás, form elemzés).

### Architektúra

```
AI Agent (Claude, stb.)
    |
    | MCP (stdio, JSON-RPC 2.0)
    v
ÁNYK MCP Server (Java 21)
    |
    +-- MCP SDK 1.1.4 (io.modelcontextprotocol)
    +-- abevjava.jar (eredeti ÁNYK osztályok - üzleti logika)
    +-- Vékony adapter réteg (init + új funkciók)
    |
    +-- NAV szerver (template + segédlet letöltés)
    +-- Helyi fájlrendszer (mentés, profil tárolás)
```

### Fontos: valós ÁNYK telepítés szükséges

Az `ANYK_ROOT` egy **valós ÁNYK telepítésre** kell mutasson, amely tartalmazza az `eroforrasok/` könyvtárat a szervezeti erőforrás JAR-okkal (`NAVResources_vX.jar`, `APEHResources_vX.jar`, `VPOPResources_vX.jar`). Ezek nélkül a `Calculator` és a nyomtatvány-betöltés nem működik.

## 2. Az abevjava.jar-ból használt osztályok (a lényegi logika)

| Osztály | Csomag | Mire használjuk |
|---------|--------|-----------------|
| `BookModel` | `gui.model` | **Teljes** nyomtatvány betöltés az eredeti `BookModel(File, silent)` konstruktorral. Ez lefuttatja a `makeempty()`-t: SAX parse + `CachedCollection` + `Calculator` + `MetaInfo` inicializálás. |
| `BookModel.addForm()` | `gui.model` | Új üres nyomtatvány-példány létrehozása (Elem + GUI_Datastore), Calculator eseménnyel és betöltési számításokkal. |
| `BookModel.getHeadData()` | `gui.model` | Gyors, fejléc-only betöltés (`onlyhead=true`) listázáshoz, Calculator nélkül. |
| `FormModel`, `PageModel`, `DataFieldModel`, `VisualFieldModel` | `gui.model` | Form struktúra: űrlapok, oldalak, mezők, címkék. |
| `GUI_Datastore` | `datastore` | Mező értékek írása/olvasása `set(Object[], String)` / `get(Object[])` metódusokkal (`{Integer pageIndex, String fieldId}` kulcs). |
| `CachedCollection`, `Elem` | `datastore` | Nyomtatvány-példányok gyűjteménye. |
| `Calculator` / `CalculatorManager` | `alogic.calculator` | **Számított mezők**: összevont adóalap, adó, fizetendő adó automatikus kiszámítása. A `form_calc()` futtatja. |
| `DataChecker` | `alogic.fileutil` | Validáció: `superCheck(BookModel, true)` teljes ellenőrzés, `checkField(...)` egy mező. |
| `EnykXmlSaver` | `alogic.filesaver.xml` | **Mentés**: az eredeti XML export a helyes `<nyomtatvanyok>` formátumban, validációval, SHA-1 hash-sel, checkbox konverzióval. |
| `OrgInfo` / `OrgResource` | `alogic.orghandler` | Szervezeti erőforrások betöltése az `eroforrasok/` JAR-okból (a `prop.sys.root` alapján). |
| `SettingsStore` | `alogic.settingspanel` | A mentési könyvtár beállítása (`gui/digitális_aláírás`), amit az `EnykXmlSaver.getDsPath()` használ. |
| `PropertyList` | `util.base` | Konfigurációs singleton + fájlformátum konstansok. |
| `MainFrame.role` | `gui.framework` | Adózói szerep (statikus mező, `"0"` = adózó). |

### Teljes workflow — csupa eredeti osztály

```
BookModel(File, silent)          -> template + Calculator + MetaInfo init   [eredeti]
BookModel.addForm(mainForm)      -> üres példány, betöltési számítások       [eredeti]
GUI_Datastore.set(...)           -> mező kitöltés                            [eredeti]
CalculatorManager.form_calc()    -> számított mezők (adó, adóalap)           [eredeti]
DataChecker.superCheck(...)      -> validáció                                [eredeti]
EnykXmlSaver.save(...)           -> mentés XML-be, hash-sel                  [eredeti]
```

**Bizonyított működés (2553 SZJA teszt):** 20M Ft bérjövedelemre a Calculator kiszámolta a 3M Ft adót, az `EnykXmlSaver` érvényes 4606 byte-os XML-t mentett, a `DataChecker` 0 hibát jelzett.

## 3. Saját implementáció (csak a szükséges minimum)

### 3.1 PropertyListInitializer — init property-k beállítása

**Miért kell:** GUI-módban az `InitApplication` tölti fel a `PropertyList` singletont és állítja be a `MainFrame.role`-t. Headless módban ezt nekünk kell megtenni.

**Mit csinál:**
- Beállítja a `prop.sys.root`-ot a valós ÁNYK telepítésre (így az `OrgInfo` megtalálja az `eroforrasok/`-t)
- `prop.usr.*` path-ok (settings, saves, kr, tmp, log)
- GUI méret property-k (a fejléc-számításokhoz)
- `MainFrame.role = "0"` (adózó)
- `setSaveDir()`: a `SettingsStore` mentési könyvtár beállítása az `EnykXmlSaver`-hez

Ez **nem** párhuzamos implementáció — csak azt az init lépést pótolja, amit normál esetben a GUI indítás végez el. Utána minden az eredeti jar logikán fut.

### 3.2 BookModelAdapter — vékony wrapper

**Mit csinál:** az eredeti `BookModel(File)` konstruktort és `addForm()` metódust hívja. Nincs benne párhuzamos betöltési logika — csak kényelmi metódusok:
- `loadTemplate(File)` → `new BookModel(file, true)` + hibaellenőrzés
- `addEmptyForm(bm)` → a `DefaultMultiFormViewer.buid()` logikáját követi (fő űrlap hozzáadása)
- `loadHead(File)` → gyors fejléc-only betöltés listázáshoz
- `getActiveDataStore(bm)` → az aktív `Elem` `GUI_Datastore`-ja

### 3.3 DownloadAdapter — NAV szerver letöltés

**Miért kell:** az ÁNYK letöltő rendszere (`DownloadableComponents`, `UpgradeFormController`) GUI dialógusra épül. Az alacsony szintű `FileDownloader` használható lenne, de a `DownloadAdapter` egyszerűbb: közvetlen HTTP-t használ.

**Mit csinál:**
1. HTTP GET a NAV frissítési URL-re (`https://nav.gov.hu/abev/abev_new` — ez az `orginfo.xml` `updateurl` attribútumából származik)
2. SAX parse az `enyk.xml`-ből (`<nyomtatvany>/<utmutato>/<keretprogram>`)
3. JAR letöltés + kicsomagolás a `nyomtatvanyok/` és `segitseg/` könyvtárakba

### 3.4 TaxpayerStore — adózói profil tárolás

**Teljesen új funkció** (nincs ÁNYK megfelelője): JSON fájlban több személy alapadatait tárolja, és automatikusan kitölti a nyomtatvány azonosító/cím/bankszámla mezőit.

### 3.5 FormAnalyzerTool + StructureMapper — új segédfunkciók

**Teljesen új funkciók:**
- `FormAnalyzerTool`: kategorizálja a mezőket és megmondja, mit kell kérdezni a felhasználótól
- `StructureMapper`: a `VisualFieldModel` címkéket geometriai pozíció alapján rendeli a mezőkhöz
- HTML segédlet → plain text konverzió (Jsoup)

## 4. Összefoglaló táblázat

| Funkció | Forrás | Megjegyzés |
|---------|--------|------------|
| Template betöltés | **abevjava.jar** | `BookModel(File, silent)` — teljes init |
| Form/Page/Field modell | **abevjava.jar** | `FormModel`, `PageModel`, `DataFieldModel` |
| Példány létrehozás | **abevjava.jar** | `BookModel.addForm()` |
| Mező értékek | **abevjava.jar** | `GUI_Datastore` (Object[] kulcs) |
| Számított mezők | **abevjava.jar** | `CalculatorManager.form_calc()` — MŰKÖDIK |
| Validáció | **abevjava.jar** | `DataChecker.superCheck()` |
| Mentés | **abevjava.jar** | `EnykXmlSaver.save()` — helyes formátum + hash |
| Szervezeti erőforrások | **abevjava.jar** | `OrgInfo` / `OrgResource` |
| Init property-k | saját (vékony) | `PropertyListInitializer` — a GUI init pótlása |
| Betöltés wrapper | saját (vékony) | `BookModelAdapter` — csak kényelmi metódusok |
| NAV letöltés | saját | `DownloadAdapter` — HTTP + JAR kicsomagolás |
| Adózói profilok | saját (új) | `TaxpayerStore` (JSON) |
| Form elemzés | saját (új) | `FormAnalyzerTool` |
| Címke-mező összerendelés | saját (új) | `StructureMapper` (geometriai) |
| Beküldés (KAU/SOAP) | ❌ | Nem implementált (interaktív auth) |

**Változás a korábbi verzióhoz képest:** a korábbi `SimpleXmlSaver` (saját XML mentés) és a `BookModelAdapter` manuális `makeempty`-kikerülése **megszűnt**. A hiba nem architekturális volt, hanem hiányzó inicializáció: a `prop.sys.root`, `MainFrame.role` és a `SettingsStore` beállítása után az eredeti `BookModel` + `Calculator` + `EnykXmlSaver` teljes egészében működik.

## 5. MCP Tool-ok (22 db)

### Template kezelés
| Tool | Leírás |
|------|--------|
| `template_list_installed` | Telepített sablonok (gyors fejléc-only betöltés) |
| `template_search` | Keresés telepített sablonok között |
| `template_list_available` | NAV szerveren elérhető sablonok |
| `template_download` | Letöltés + telepítés a NAV szerverről |

### Form műveletek
| Tool | Leírás |
|------|--------|
| `form_open` | Sablon megnyitása (Calculator init), session létrehozása |
| `form_close` | Session bezárása |
| `form_get_structure` | Mezők struktúrája (típus, címke, szabályok, érték) |
| `form_analyze_requirements` | Nyomtatvány elemzés: mit kell kérdezni |

### Mező műveletek
| Tool | Leírás |
|------|--------|
| `form_get_field` | Egy mező értéke |
| `form_set_field` | Egy mező beállítása |
| `form_set_fields` | Több mező (batch) |
| `form_get_all_fields` | Összes mező értéke (számított mezőkkel együtt) |

### Validáció és mentés
| Tool | Leírás |
|------|--------|
| `form_validate` | Teljes validáció (`DataChecker`) |
| `form_validate_field` | Egy mező validációja |
| `form_save` | Mentés az eredeti `EnykXmlSaver`-rel |

### Segédlet
| Tool | Leírás |
|------|--------|
| `help_get_guide` | Kitöltési útmutató (HTML → text) |
| `help_list_pages` | Segédlet tartalomjegyzék |
| `help_search` | Keresés a segédletben |

### Adózói profilok
| Tool | Leírás |
|------|--------|
| `taxpayer_save` | Profil mentése |
| `taxpayer_list` | Profilok listázása |
| `taxpayer_get` | Profil lekérdezése |
| `taxpayer_delete` | Profil törlése |
| `taxpayer_apply_to_form` | Profil alkalmazása nyomtatványra |

## 6. Ismert korlátozások

1. **Valós telepítés kell**: az `eroforrasok/` JAR-ok nélkül a Calculator és a betöltés nem működik. Az `AnykConfig.hasResources()` figyelmeztet, ha hiányzik.

2. **Betöltési sebesség**: a teljes `BookModel` betöltés (Calculator build-del) egy nagy nyomtatványnál (pl. 2553, 4085 mező) néhány másodperc. A listázás ezért gyors fejléc-only betöltést használ.

3. **Beküldés nem támogatott**: a KAU (Központi Azonosítási Ügynök) autentikáció interaktív böngészőt igényel.

4. **Dinamikus oldalak**: alapvetően az első példány (pageIndex=0) kezelt.

## 7. Indítás

```bash
# ANYK_ROOT = valós ÁNYK telepítés (eroforrasok/-kal!)
ANYK_ROOT=/path/to/abevjava ./gradlew run

# vagy
./gradlew run --args="/path/to/abevjava"
```

## 8. Függőségek

| Függőség | Verzió | Cél |
|----------|--------|-----|
| `abevjava.jar` | 3.49.0 | ÁNYK osztályok (betöltés, Calculator, validáció, mentés) |
| `io.modelcontextprotocol.sdk:mcp` | 1.1.4 | MCP szerver (stdio) |
| `com.google.code.gson:gson` | 2.11.0 | JSON (profil, tool válaszok) |
| `org.jsoup:jsoup` | 1.18.1 | HTML → text (segédlet) |
| `org.slf4j:slf4j-simple` | 2.0.16 | Naplózás |
| Java | 21 | Futtatási környezet |
