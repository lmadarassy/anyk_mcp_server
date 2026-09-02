# ÁNYK MCP Server

MCP (Model Context Protocol) szerver magyar adónyomtatványok (ÁNYK/ABEV) AI-alapú kitöltéséhez. Az eredeti `abevjava.jar` osztályait használja headless módban.

Részletes architektúra és a felhasznált osztályok: [DOCUMENTATION.md](DOCUMENTATION.md).

## Előfeltételek

- Java 21
- Egy **valós ÁNYK telepítés** (tartalmazza az `abevjava.jar`-t és az `eroforrasok/`-t)

## Build

Az `abevjava.jar` `compileOnly` függőség, tehát az ÁNYK telepítésből jön, nem a repóból. Add meg a telepítés útját:

```bash
./gradlew installDist -PanykHome=/eleresi/ut/az/abevjava
```

Ez a `build/install/anyk-mcp-server/` alá készít egy futtatható disztribúciót.

Az `anykHome` több módon is megadható (sorrend: `-PanykHome` > `ANYK_HOME` env > `gradle.properties`). Lásd `gradle.properties.example`.

## Beállítás opencode-hoz

Másold az `opencode.json.example`-t a projekted `opencode.json`-jába (vagy `~/.config/opencode/opencode.json`-ba), és cseréld ki a path-okat:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "anyk": {
      "type": "local",
      "enabled": true,
      "command": [
        "java",
        "-Danyk.home=/eleresi/ut/az/abevjava",
        "-cp",
        "/eleresi/ut/anyk-mcp-server/build/install/anyk-mcp-server/lib/*:/eleresi/ut/az/abevjava/abevjava.jar",
        "hu.anyk.mcp.AnykMcpServer"
      ]
    }
  }
}
```

Két path-ot kell kitölteni:
- `-Danyk.home=...` → az ÁNYK telepítés könyvtára
- `-cp .../lib/*:.../abevjava.jar` → a build dist `lib/`-je **és** az `abevjava.jar` (mert az nincs becsomagolva)

opencode config változás után **indítsd újra az opencode-ot** (a config nem tölődik újra futás közben).

## Miért van szükség a classpath-ban az abevjava.jar-ra külön?

Az `abevjava.jar` `compileOnly`, ezért nincs a dist `lib/`-jében. Így a repó nem tartalmaz jogvédett ÁNYK kódot, de futtatáskor a jar-nak a classpath-on kell lennie.

## Elérhető MCP tool-ok

22 tool: template letöltés/listázás, form megnyitás/struktúra/elemzés, mező írás/olvasás, validáció, mentés, segédlet olvasás, adózói profilok. Részletek: [DOCUMENTATION.md](DOCUMENTATION.md) 5. szakasz.

## Megjegyzés a stdout-ról

Az ÁNYK osztályok sokat írnak a `System.out`-ra, ami elrontaná az MCP stdio JSON-RPC protokollt. A szerver ezért indításkor a `System.out`-ot a stderr-re irányítja, és a valódi stdout-ot tartja fenn az MCP kommunikációnak.
