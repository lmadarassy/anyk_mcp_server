# ÁNYK MCP Server - Dokumentáció

## 1. Áttekintés

Az ÁNYK MCP Server egy Java alkalmazás, amely MCP (Model Context Protocol) interfészen keresztül teszi lehetővé magyar adónyomtatványok AI-alapú kitöltését. Az eredeti ÁNYK (Általános Nyomtatványkitöltő) alkalmazás `abevjava.jar` osztályait használja fel headless módban, kiegészítve saját adapter réteggel a GUI-függőségek kikerülésére.

### Architektúra

```
AI Agent (Claude, stb.)
    |
    | MCP (stdio, JSON-RPC 2.0)
    v
ÁNYK MCP Server (Java 21)
    |
    +-- MCP SDK 1.1.4 (io.modelcontextprotocol)
    +-- abevjava.jar (eredeti ÁNYK osztályok)
    +-- Adapter réteg (saját implementáció)
    |
    +-- NAV szerver (template + segédlet letöltés)
    +-- Helyi fájlrendszer (mentés, profil tárolás)
```

## 2. Az abevjava.jar-ból használt osztályok

### 2.1 Template betöltés és form modell

| Osztály | Csomag | Mire használjuk |
|---------|--------|-----------------|
| `BookModel` | `hu.piller.enykp.gui.model` | Nyomtatvány sablon betöltése SAX parserrel. A `load(File)` metódust hívjuk, ami beolvassa a `.tem.enyk` fájlt és felépíti a form/page/field hierarchiát. |
| `FormModel` | `hu.piller.enykp.gui.model` | Egy űrlaptípus modellje. A `fids` (Hashtable) tartalmazza az összes mező definíciót, a `pages` (Vector) az oldalakat. |
| `PageModel` | `hu.piller.enykp.gui.model` | Egy oldal modellje. A `y_sorted_df` tartalmazza a mezőket Y-koordináta szerint, a `z_sorted_vf` a vizuális elemeket (címkéket). |
| `DataFieldModel` | `hu.piller.enykp.gui.model` | Egy mező definíciója: `key` (fid), `type`, `readonly`, `features` (mask, len, values, stb.), `x/y/w/h` koordináták. |
| `VisualFieldModel` | `hu.piller.enykp.gui.model` | Vizuális címke elem. A `text` és `getOriginalBounds()` alapján rendeljük össze a mezőkkel (geometriai közelség). |

### 2.2 Adattárolás

| Osztály | Mire használjuk |
|---------|-----------------|
| `GUI_Datastore` | Mező értékek tárolása. A `set(Object[], String)` és `get(Object[])` metódusokkal írjuk/olvassuk a mezőket `{Integer pageIndex, String fieldId}` kulccsal. |
| `CachedCollection` | Nyomtatvány-példányok gyűjteménye. Minden példány egy `Elem` objektum, ami egy `GUI_Datastore`-t tartalmaz. |
| `Elem` | Egy nyomtatvány-példány wrapere. A `getRef()` adja vissza a `GUI_Datastore`-t. |

### 2.3 Validáció

| Osztály | Mire használjuk |
|---------|-----------------|
| `DataChecker` | Singleton (`getInstance()`). A `superCheck(BookModel, boolean)` teljes validációt futtat, a `checkField(...)` egyetlen mezőt ellenőriz. |

### 2.4 PropertyList

| Osztály | Mire használjuk |
|---------|-----------------|
| `PropertyList` | Singleton konfiguráció tár. Az `getInstance()` hívással inicializáljuk, majd `set()`/`get()` metódusokkal töltjük fel a szükséges property-ket (debug, path-ok, GUI paraméterek). |

### 2.5 Fájlformátum konstansok

| Osztály | Mire használjuk |
|---------|-----------------|
| `PropertyList` (konstansok) | `TEMPLATE_SUFFIX` (`.tem.enyk`), `INNER_DATA_SUFFIX` (`.frm.enyk`), `XML_DATA_SUFFIX` (`.xml`), `PROGRAM_VERSION`, `UTF_ENCODING` stb. |

### Összefoglalás: abevjava.jar-ból használt funkciók

```
Template betöltés:  BookModel.load(File)          ✅ Működik headless módban
Form struktúra:     FormModel/PageModel/DFM       ✅ Működik
Adattárolás:        GUI_Datastore.set()/get()     ✅ Működik (Object[] formátummal)
Validáció:          DataChecker.superCheck()       ✅ Működik (alapvető)
PropertyList:       PropertyList.getInstance()     ✅ Működik (manuális init után)
```

## 3. Saját implementáció (ami az abevjava.jar-ból NEM használható)

### 3.1 BookModelAdapter - Template betöltés headless módban

**Probléma:** A `BookModel(File)` konstruktor a `makeempty()` metódusban `OrgResource`-t próbál betölteni (szervezeti erőforrás JAR), ami nincs jelen. Emiatt `hasError=true` lesz és a `CachedCollection` nem jön létre.

**Megoldás:** `BookModelAdapter.loadTemplate(File)`:
1. `new BookModel()` (üres konstruktor)
2. `bm.load(templateFile)` - SAX parse (ez működik)
3. Manuálisan létrehozza a `CachedCollection`-t, `maxcreation[]`-t és `created[]`-t
4. `addEmptyForm()` - létrehoz egy `Elem`-et üres `GUI_Datastore`-ral

**Miért nem az eredeti:** A `BookModel.addForm()` meghívja a `CachedCollection.setActiveObject()`-et, ami a `Calculator.eventFired()`-ot hívja - de a Calculator null, mert a `makeempty()` nem futott le. A `Calculator` inicializálásához az `OrgResource` és `MetaInfo` singletonok kellenének.

### 3.2 SimpleXmlSaver - Mentés ÁNYK formátumban

**Probléma:** Az `EnykXmlSaver` konstruktora a `HeadChecker.getHeadData()`-t hívja, ami a `MetaInfo.getMetaStore()`-t, ami null, mert a `MetaInfo.init()` nem futott (ez is a `makeempty()`-ben történne).

**Megoldás:** `SimpleXmlSaver.save(BookModel, String)`:
- Az ÁNYK `.frm.enyk` formátumot állítja elő:
  ```xml
  <file>
    <head filetype="zn1810">
      <type>single</type>
      <saved>YYYYMMDDHHmmssSSS</saved>
      <docinfo name="..." id="..." ver="..." org="..." ... />
    </head>
    <nyomtatvanyok xmlns="http://www.apeh.hu/abev/nyomtatvanyok/2005/01" template="...">
      <abev>...</abev>
      <nyomtatvany sn="0">
        <nyomtatvanyinformacio>...</nyomtatvanyinformacio>
        <mezok>
          <mezo eazon="0_FID">érték</mezo>
          ...
        </mezok>
      </nyomtatvany>
    </nyomtatvanyok>
  </file>
  ```
- A fájlnév generálás: `{formId}_{adóazonosító}_{Név}_{timestamp}.frm.enyk`

**Miért nem az eredeti:** Az `EnykXmlSaver` -> `HeadChecker` -> `MetaInfo` -> `Calculator` lánc túl sok singleton inicializálást igényelne. A `SimpleXmlSaver` közvetlenül a `GUI_Datastore`-ból olvassa ki a mezőértékeket.

### 3.3 DownloadAdapter - NAV szerver letöltés

**Probléma:** Az ÁNYK letöltési rendszere (`DownloadableComponents`, `FileDownloader`, `ExtractStage`) az `OrgInfo` singletont és az `UpgradeFormController` GUI dialógust használja.

**Megoldás:** `DownloadAdapter`:
1. HTTP GET a NAV frissítési URL-re (`https://nav.gov.hu/abev/abev_new`)
2. SAX parse az `enyk.xml` válaszból (`<adat>` -> `<nyomtatvany>/<utmutato>/<keretprogram>`)
3. JAR letöltés az `<url>` + `<file>` alapján
4. JAR kicsomagolás: `application/nyomtatvanyok/` -> `{root}/nyomtatvanyok/`, `application/segitseg/` -> `{root}/segitseg/`

**Miért nem az eredeti:** Az `OrgInfo.getUpgradeURLAllOrganizations()` az `OrgResource` JAR-okból olvassa ki a frissítési URL-t, de az `OrgResource` betöltéséhez a teljes ÁNYK installációra lenne szükség. A `DownloadAdapter` közvetlenül a NAV publikus URL-jét használja.

### 3.4 PropertyListInitializer - ÁNYK singleton inicializálás

**Probléma:** Sok ÁNYK osztály a `PropertyList.getInstance().get("prop.xxx")` hívást használja. Az `InitApplication` osztály tölti fel ezeket GUI-módban.

**Megoldás:** `PropertyListInitializer.ensureInitialized(anykRoot)`:
- Beállítja a minimálisan szükséges property-ket: `prop.dynamic.debug`, `prop.usr.root`, `prop.sys.root`, GUI méretek stb.

### 3.5 TaxpayerStore - Adózói profil tárolás

**Teljesen új funkció** (nincs ÁNYK megfelelője):
- JSON fájlban (`taxpayers.json`) tárolja az adózói profilokat
- Több személy adatait kezeli (név, adóazonosító, cím, bankszámla, stb.)
- A `taxpayer_apply_to_form` tool automatikusan kitölti a nyomtatvány azonosító/cím/bankszámla mezőit a profilból

### 3.6 FormAnalyzerTool - Nyomtatvány elemzés

**Teljesen új funkció**:
- Kategorizálja a mezőket: azonosítás, személyi adatok, lakcím, bevallási időszak, bankszámla, összeg mezők, nyilatkozatok
- A segédlet HTML-t plain textre konvertálja (Jsoup)
- Megmondja, mit kell kérdezni a felhasználótól és mit lehet profilból kitölteni

### 3.7 StructureMapper - Mező-címke összerendelés

**Teljesen új funkció**:
- A `VisualFieldModel` (címkék) és `DataFieldModel` (mezők) geometriai pozíciója alapján rendeli össze a címkéket a mezőkkel
- Balra lévő és felette lévő címkéket is figyelembe veszi távolságszámítással

## 4. Összefoglaló táblázat

| Funkció | abevjava.jar | Saját implementáció | Megjegyzés |
|---------|:---:|:---:|------------|
| Template SAX parse | ✅ | | `BookModel.load(File)` |
| Form/Page/Field modell | ✅ | | `FormModel`, `PageModel`, `DataFieldModel` |
| Mező értékek tárolása | ✅ | | `GUI_Datastore` (Object[] kulccsal) |
| CachedCollection + Elem | ✅ | | Létrehozás manuális, de az osztályok működnek |
| Template inicializálás | | ✅ | `BookModelAdapter` (makeempty kikerülése) |
| Mentés (.frm.enyk) | | ✅ | `SimpleXmlSaver` (EnykXmlSaver nem használható) |
| NAV letöltés | | ✅ | `DownloadAdapter` (OrgInfo kikerülése) |
| Validáció | ✅ | | `DataChecker.superCheck()` |
| PropertyList init | ✅ (tároló) | ✅ (feltöltés) | `PropertyListInitializer` |
| Címke-mező összerendelés | | ✅ | `StructureMapper` (geometriai) |
| Adózói profilok | | ✅ | `TaxpayerStore` (JSON) |
| Form elemzés | | ✅ | `FormAnalyzerTool` |
| HTML segédlet olvasás | | ✅ | Jsoup + encoding detektálás |
| Calculator (számított mezők) | ❌ | ❌ | Nem inicializálható OrgResource nélkül |
| Beküldés (KAU/SOAP) | ❌ | ❌ | Nem implementált (interaktív auth szükséges) |

## 5. MCP Tool-ok (22 db)

### Template kezelés
| Tool | Leírás |
|------|--------|
| `template_list_installed` | Telepített sablonok listázása |
| `template_search` | Keresés telepített sablonok között |
| `template_list_available` | NAV szerveren elérhető sablonok (538 template, 527 segédlet) |
| `template_download` | Letöltés + telepítés a NAV szerverről |

### Form műveletek
| Tool | Leírás |
|------|--------|
| `form_open` | Sablon megnyitása, session létrehozása |
| `form_close` | Session bezárása |
| `form_get_structure` | Mezők struktúrája (típus, címke, szabályok, aktuális érték) |
| `form_analyze_requirements` | Nyomtatvány elemzés: mit kell kérdezni a felhasználótól |

### Mező műveletek
| Tool | Leírás |
|------|--------|
| `form_get_field` | Egy mező értékének lekérdezése |
| `form_set_field` | Egy mező értékének beállítása |
| `form_set_fields` | Több mező egyszerre (batch) |
| `form_get_all_fields` | Összes mező értéke |

### Validáció és mentés
| Tool | Leírás |
|------|--------|
| `form_validate` | Teljes validáció |
| `form_validate_field` | Egyetlen mező validációja |
| `form_save` | Mentés ÁNYK-kompatibilis `.frm.enyk` formátumban |

### Segédlet
| Tool | Leírás |
|------|--------|
| `help_get_guide` | Kitöltési útmutató olvasása (HTML -> text) |
| `help_list_pages` | Segédlet tartalomjegyzék |
| `help_search` | Keresés a segédletben |

### Adózói profilok
| Tool | Leírás |
|------|--------|
| `taxpayer_save` | Profil mentése (név, adóazonosító, cím, bankszámla, stb.) |
| `taxpayer_list` | Összes profil listázása |
| `taxpayer_get` | Profil lekérdezése |
| `taxpayer_delete` | Profil törlése |
| `taxpayer_apply_to_form` | Profil alkalmazása nyomtatványra (auto-fill) |

## 6. Ismert korlátozások

1. **Calculator nem működik**: A számított mezők (összegek, adóalapok) nem számítódnak ki automatikusan, mert a `Calculator` inicializálásához `OrgResource` és `MetaInfo` szükséges. Az AI-nak magának kell kiszámolnia ezeket.

2. **EnykXmlSaver nem használható**: A mentés saját implementációval történik. Az XML struktúra megegyezik az ÁNYK formátummal, de a hash számítás és egyes metaadatok eltérhetnek.

3. **Beküldés nem támogatott**: A KAU (Központi Azonosítási Ügynök) autentikáció interaktív böngészőt igényel.

4. **Dinamikus oldalak**: Csak az első példány (pageIndex=0) kezelt teljes mértékben.

## 7. Indítás

```bash
# Környezeti változóval
ANYK_ROOT=/path/to/anyk_data ./gradlew run

# Vagy argumentummal
./gradlew run --args="/path/to/anyk_data"
```

Az `ANYK_ROOT` könyvtárban a szerver létrehozza a szükséges alkönyvtárakat:
- `nyomtatvanyok/` - telepített sablonok
- `segitseg/` - kitöltési útmutatók
- `upgrade/` - staging könyvtár

## 8. Függőségek

| Függőség | Verzió | Cél |
|----------|--------|-----|
| `abevjava.jar` | 3.49.0 | ÁNYK osztályok (template, datastore, validáció) |
| `io.modelcontextprotocol.sdk:mcp` | 1.1.4 | MCP szerver (stdio transport, tool regisztráció) |
| `com.google.code.gson:gson` | 2.11.0 | JSON szerializáció (profil tárolás, tool válaszok) |
| `org.jsoup:jsoup` | 1.18.1 | HTML -> text konverzió (segédlet olvasás) |
| `org.slf4j:slf4j-simple` | 2.0.16 | Naplózás |
| Java | 21 | Futtatási környezet |
