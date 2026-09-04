package hu.anyk.mcp.tools;

import hu.anyk.mcp.adapter.TaxpayerStore;
import hu.anyk.mcp.adapter.TaxpayerStore.TaxpayerProfile;
import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.anyk.mcp.config.AnykConfig;
import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.*;
import hu.piller.enykp.datastore.GUI_Datastore;

@SuppressWarnings("unchecked")
public class TaxpayerTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, TaxpayerStore store, SessionManager sessionManager) {
        server.addTool(saveProfileSpec(store));
        server.addTool(listProfilesSpec(store));
        server.addTool(getProfileSpec(store));
        server.addTool(deleteProfileSpec(store));
        server.addTool(applyToFormSpec(store, sessionManager));
    }

    private static SyncToolSpecification saveProfileSpec(TaxpayerStore store) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Profil azonosito (ha letezo profilt frissitunk). Uj profilnal elhagyhato." },
                "nev": { "type": "string", "description": "Teljes nev (pl. 'Teszt Elek')" },
                "szuletesiNev": { "type": "string", "description": "Szuletesi nev" },
                "adoazonosito": { "type": "string", "description": "Adoazonosito jel (10 szamjegy)" },
                "adoszam": { "type": "string", "description": "Adoszam (########-#-## formatum)" },
                "tajSzam": { "type": "string", "description": "TAJ szam (9 szamjegy)" },
                "szuletesiDatum": { "type": "string", "description": "Szuletesi datum (YYYY.MM.DD)" },
                "szuletesiHely": { "type": "string", "description": "Szuletesi hely" },
                "anyjaNeve": { "type": "string", "description": "Anyja neve" },
                "allampolgarsag": { "type": "string", "description": "Allampolgarsag kod (pl. 'HU')" },
                "iranyitoszam": { "type": "string", "description": "Iranyitoszam" },
                "telepules": { "type": "string", "description": "Telepules" },
                "kozteruletNev": { "type": "string", "description": "Kozterulet neve" },
                "kozteruletTipus": { "type": "string", "description": "Kozterulet tipusa (pl. 'utca', 'ut', 'ter')" },
                "hazszam": { "type": "string", "description": "Hazszam" },
                "emelet": { "type": "string", "description": "Emelet" },
                "ajto": { "type": "string", "description": "Ajto" },
                "telefon": { "type": "string", "description": "Telefonszam" },
                "email": { "type": "string", "description": "E-mail cim" },
                "bankszamlaszam": { "type": "string", "description": "Bankszamlaszam (########-########-########)" },
                "megjegyzes": { "type": "string", "description": "Szabadszoveges megjegyzes" }
              },
              "required": ["nev", "adoazonosito"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("taxpayer_save",
                "Ment vagy frissit egy adozoi profilt. A profil tartalmazhatja a nevet, adoazonositot, cimet, es minden mas alapadatot, ami a bevallasokhoz szukseges.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    Map<String, Object> args = request.arguments();
                    var builder = TaxpayerProfile.builder()
                        .id((String) args.get("id"))
                        .nev((String) args.get("nev"))
                        .szuletesiNev((String) args.get("szuletesiNev"))
                        .adoazonosito((String) args.get("adoazonosito"))
                        .adoszam((String) args.get("adoszam"))
                        .tajSzam((String) args.get("tajSzam"))
                        .szuletesiDatum((String) args.get("szuletesiDatum"))
                        .szuletesiHely((String) args.get("szuletesiHely"))
                        .anyjaNeve((String) args.get("anyjaNeve"))
                        .allampolgarsag((String) args.get("allampolgarsag"))
                        .iranyitoszam((String) args.get("iranyitoszam"))
                        .telepules((String) args.get("telepules"))
                        .kozteruletNev((String) args.get("kozteruletNev"))
                        .kozteruletTipus((String) args.get("kozteruletTipus"))
                        .hazszam((String) args.get("hazszam"))
                        .emelet((String) args.get("emelet"))
                        .ajto((String) args.get("ajto"))
                        .telefon((String) args.get("telefon"))
                        .email((String) args.get("email"))
                        .bankszamlaszam((String) args.get("bankszamlaszam"))
                        .megjegyzes((String) args.get("megjegyzes"));

                    TaxpayerProfile profile = builder.build();
                    store.saveProfile(profile);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("id", profile.id());
                    result.put("nev", profile.nev());
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification listProfilesSpec(TaxpayerStore store) {
        String schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("taxpayer_list",
                "Listazza az osszes mentett adozoi profilt.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    List<TaxpayerProfile> profiles = store.listProfiles();
                    List<Map<String, Object>> results = new ArrayList<>();
                    for (TaxpayerProfile p : profiles) {
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("id", p.id());
                        info.put("nev", p.nev());
                        info.put("adoazonosito", p.adoazonosito());
                        info.put("adoszam", p.adoszam());
                        info.put("telepules", p.telepules());
                        results.add(info);
                    }
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(results))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification getProfileSpec(TaxpayerStore store) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "Profil ID, nev, vagy adoazonosito" }
              },
              "required": ["query"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("taxpayer_get",
                "Lekerdez egy adozoi profilt ID, nev vagy adoazonosito alapjan.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String query = (String) request.arguments().get("query");
                    TaxpayerProfile p = store.getProfile(query);
                    if (p == null) p = store.findByName(query);
                    if (p == null) return errorResult("Profil nem talalhato: " + query);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(p))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification deleteProfileSpec(TaxpayerStore store) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "id": { "type": "string", "description": "Profil ID" }
              },
              "required": ["id"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("taxpayer_delete",
                "Torol egy adozoi profilt.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String id = (String) request.arguments().get("id");
                    store.deleteProfile(id);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("{\"success\": true}")))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification applyToFormSpec(TaxpayerStore store, SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "taxpayerId": { "type": "string", "description": "Adozoi profil ID vagy nev" }
              },
              "required": ["sessionId", "taxpayerId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("taxpayer_apply_to_form",
                "Alkalmazza egy adozoi profil adatait egy megnyitott nyomtatvanyra a template panids mezomapping alapjan (nev, adoazonosito, adoszam, cim, stb.), MINDEN dokumentum-peldanyra. "
                + "FONTOS a helyes sorrend kotegelt nyomtatvanynal: eloszor add hozza az osszes tovabbi dokumentumot (form_add_document), es CSAK UTANA hivd ezt - igy a fedolapokra (pl. 25HIPAKM) is atkerul az azonosito adat. "
                + "A profil-specifikus mezoket (bevallasi idoszak, onkormanyzat, nyilatkozatok, osszegek) ezutan a form_set_field / form_set_fields tool-lal add meg, a documentType parameterrel.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String taxpayerId = (String) request.arguments().get("taxpayerId");
                    hu.anyk.mcp.McpLog.tool("taxpayer_apply_to_form", "taxpayerId=" + taxpayerId);

                    TaxpayerProfile p = store.getProfile(taxpayerId);
                    if (p == null) p = store.findByName(taxpayerId);
                    if (p == null) return errorResult("Profil nem talalhato: " + taxpayerId);

                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                    if (ds == null) return errorResult("Nincs aktiv adattarolo");

                    Map<String, String> profileData = p.toFieldMap();
                    int applied = applyProfileToForm(ds, bm, profileData);
                    hu.anyk.mcp.McpLog.note("taxpayer_apply_to_form appliedFields=" + applied);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("appliedFields", applied);
                    result.put("taxpayer", p.nev());
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    /**
     * A profil adatait a nyomtatvany mezoibe tolti, PONTOSAN ugy mint az ANYK GUI:
     * a mezok META-jaban levo "panids" attributum koti a mezot egy torzsadat-
     * attributumhoz (magyar nevvel). Nincs tippeles - a template mondja meg,
     * hova propagaljon. (Lasd EntityBookModelConnector.applyOnForm.)
     *
     * Vegigmegy minden dokumentum-peldanyon (kotegelt nyomtatvanynal a fedolapon is),
     * es minden panids-szal rendelkezo mezot kitolt, ha van hozza ertek a profilban.
     */
    private static int applyProfileToForm(GUI_Datastore dsIgnored, BookModel bm, Map<String, String> data) {
        Map<String, String> byPanid = buildPanidMap(data);
        int applied = 0;
        if (bm.cc == null) return 0;

        Object savedActive = bm.cc.getActiveObject();
        try {
            for (int i = 0; i < bm.cc.size(); i++) {
                Object o = bm.cc.get(i);
                if (!(o instanceof hu.piller.enykp.datastore.Elem elem)) continue;
                String formId = elem.getType();

                hu.piller.enykp.alogic.metainfo.MetaStore ms =
                    hu.piller.enykp.alogic.metainfo.MetaInfo.getInstance().getMetaStore(formId);
                if (ms == null) continue;

                java.util.Vector<String> filter = new java.util.Vector<>();
                filter.add("panids");
                java.util.Vector<?> metas = ms.getFilteredFieldMetas_And(filter);
                if (metas == null || metas.isEmpty()) continue;

                // Erre a peldanyra allitjuk az aktiv datastore-t
                bm.cc.setActiveObject(elem);
                GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                if (ds == null) continue;

                for (Object mo : metas) {
                    Hashtable meta = (Hashtable) mo;
                    String fid = String.valueOf(meta.get("fid"));
                    String panidsAttr = String.valueOf(meta.get("panids"));
                    if (fid == null || panidsAttr == null) continue;

                    // panids lehet vesszovel elvalasztott lista
                    for (String panid : panidsAttr.split(",")) {
                        panid = panid.trim();
                        String value = byPanid.get(panid);
                        if (value == null || value.isEmpty()) continue;
                        // adoszam/szuletesi idopont: kotojel-mentesites (mint a connector postProcess)
                        if ("Adózó adószáma".equals(panid)
                            || "Bizonylat tulajdonos azonosító".equals(panid)
                            || "Születési időpont".equals(panid)) {
                            value = value.replace("-", "");
                        }
                        try {
                            BookModelAdapter.setFieldWithCalc(bm, ds, 0, fid, value);
                            applied++;
                        } catch (Exception ignored) {}
                        break; // az elso talalt panid ertek eleg
                    }
                }
            }
        } finally {
            if (savedActive != null) bm.cc.setActiveObject(savedActive);
        }
        return applied;
    }

    /**
     * A profil mezoit a torzsadat panid-nevekhez rendeli (a mdm_entitydef.xml /
     * MetaFactory PA_ID_* konstansok nevei alapjan). Osszetett ertekeket is kepez
     * (nev -> vezeteknev/keresztnev), mint az EntityBookModelConnector.postProcess.
     */
    private static Map<String, String> buildPanidMap(Map<String, String> d) {
        Map<String, String> m = new HashMap<>();
        put(m, "Adózó neve", d.get("nev"));
        put(m, "Ügyintéző neve", d.get("nev"));
        put(m, "Bizonylat tulajdonos név", d.get("nev"));
        put(m, "Adózó adószáma", d.get("adoszam"));
        put(m, "Adózó adóazonosító jele", d.get("adoazonosito"));
        put(m, "Bizonylat tulajdonos azonosító", d.get("adoazonosito"));
        put(m, "Adóazonosító jel", d.get("adoazonosito"));
        put(m, "TAJ szám", d.get("tajSzam"));
        put(m, "Adózó neme", d.get("nem"));
        put(m, "Állampolgárság", d.get("allampolgarsag"));
        put(m, "Anyja születési neve", d.get("anyjaNeve"));
        put(m, "Születési hely", d.get("szuletesiHely"));
        put(m, "Születési időpont", d.get("szuletesiDatum"));
        put(m, "Település", d.get("telepules"));
        put(m, "L Település", d.get("telepules"));
        put(m, "Közterület neve", d.get("kozteruletNev"));
        put(m, "L Közterület neve", d.get("kozteruletNev"));
        put(m, "Közterület jellege", d.get("kozteruletTipus"));
        put(m, "L Közterület jellege", d.get("kozteruletTipus"));
        put(m, "Házszám", d.get("hazszam"));
        put(m, "L Házszám", d.get("hazszam"));
        put(m, "Emelet", d.get("emelet"));
        put(m, "Ajtó", d.get("ajto"));
        put(m, "Irányítószám", d.get("iranyitoszam"));
        put(m, "L Irányítószám", d.get("iranyitoszam"));
        put(m, "Ügyintéző telefonszáma", d.get("telefon"));
        put(m, "Ügyintéző e-mail címe", d.get("email"));
        put(m, "Számlaszám", d.get("bankszamlaszam"));

        // Osszetett nev -> vezeteknev/keresztnev (vezeteknev = elso szo, tobbi = keresztnev)
        String nev = d.get("nev");
        if (nev != null && nev.contains(" ")) {
            int sp = nev.indexOf(' ');
            put(m, "Vezetékneve", nev.substring(0, sp));
            put(m, "Keresztneve", nev.substring(sp + 1));
        } else if (nev != null) {
            put(m, "Vezetékneve", nev);
        }
        String sn = d.get("szuletesiNev") != null ? d.get("szuletesiNev") : nev;
        if (sn != null && sn.contains(" ")) {
            int sp = sn.indexOf(' ');
            put(m, "Születési családnév", sn.substring(0, sp));
            put(m, "Születési utónév", sn.substring(sp + 1));
        } else if (sn != null) {
            put(m, "Születési családnév", sn);
        }
        return m;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isEmpty()) m.put(k, v);
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
