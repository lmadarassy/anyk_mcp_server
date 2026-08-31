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
                "Alkalmazza egy adozoi profil adatait egy megnyitott nyomtatvanyra. Automatikusan kitolti az azonosito, nev, cim, stb. mezoket.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String taxpayerId = (String) request.arguments().get("taxpayerId");

                    TaxpayerProfile p = store.getProfile(taxpayerId);
                    if (p == null) p = store.findByName(taxpayerId);
                    if (p == null) return errorResult("Profil nem talalhato: " + taxpayerId);

                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                    if (ds == null) return errorResult("Nincs aktiv adattarolo");

                    Map<String, String> profileData = p.toFieldMap();
                    int applied = applyProfileToForm(ds, bm, profileData);

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

    private static int applyProfileToForm(GUI_Datastore ds, BookModel bm, Map<String, String> data) {
        int applied = 0;
        if (bm.forms == null) return 0;

        for (int fi = 0; fi < bm.forms.size(); fi++) {
            FormModel fm = (FormModel) bm.forms.get(fi);
            if (fm.fids == null) continue;

            Enumeration<String> keys = fm.fids.keys();
            while (keys.hasMoreElements()) {
                String fid = keys.nextElement();
                DataFieldModel df = (DataFieldModel) fm.fids.get(fid);
                if (df.readonly) continue;

                String mask = df.features != null ? (String) df.features.get("mask") : "";
                if (mask == null) mask = "";
                String fidU = fid.toUpperCase();

                String value = matchField(fidU, mask, df.type, data);
                if (value != null && !value.isEmpty()) {
                    try {
                        ds.set(new Object[]{0, fid}, value);
                        applied++;
                    } catch (Exception ignored) {}
                }
            }
        }
        return applied;
    }

    private static String matchField(String fid, String mask, int type, Map<String, String> data) {
        if (mask.contains("########-#-##") && (fid.contains("B001") || fid.contains("C001")))
            return data.get("adoszam");
        if (mask.contains("##########") && (fid.contains("B004") || fid.contains("E001") || fid.contains("C002")))
            return data.get("adoazonosito");
        if (mask.contains("##########") && fid.contains("E002"))
            return data.get("tajSzam");
        if ((fid.contains("E003") || fid.contains("C008")) && (type == 6 || type == 2) && mask.contains("##"))
            return data.get("allampolgarsag");

        if (fid.contains("E005") || fid.contains("C009"))
            return data.get("iranyitoszam");
        if (fid.contains("E006") || fid.contains("C010"))
            return data.get("telepules");
        if (fid.contains("E007") || fid.contains("C011") || fid.contains("C012"))
            return data.get("kozteruletNev");
        if (fid.contains("E008") || fid.contains("C013"))
            return data.get("kozteruletTipus");
        if (fid.contains("E009") || fid.contains("C014") || fid.contains("C015"))
            return data.get("hazszam");
        if (fid.contains("E011") && type == 4)
            return data.get("szuletesiDatum");

        if (fid.contains("E019") || fid.contains("C006")) {
            String nev = data.get("nev");
            if (nev != null && nev.contains(" ")) return nev.substring(0, nev.indexOf(' '));
            return nev;
        }
        if (fid.contains("E021") || fid.contains("C007")) {
            String nev = data.get("nev");
            if (nev != null && nev.contains(" ")) return nev.substring(nev.indexOf(' ') + 1);
            return null;
        }
        if (fid.contains("E022")) {
            String sn = data.get("szuletesiNev");
            if (sn == null) sn = data.get("nev");
            if (sn != null && sn.contains(" ")) return sn.substring(0, sn.indexOf(' '));
            return sn;
        }
        if (fid.contains("E023")) {
            String sn = data.get("szuletesiNev");
            if (sn == null) sn = data.get("nev");
            if (sn != null && sn.contains(" ")) return sn.substring(sn.indexOf(' ') + 1);
            return null;
        }
        if (fid.contains("E024"))
            return data.get("anyjaNeve");
        if (fid.contains("E025"))
            return data.get("szuletesiHely");

        if ((fid.contains("H003") || fid.contains("E005A")) && mask.contains("###"))
            return data.get("telefon");
        if (fid.contains("G003") && mask.contains("########-"))
            return data.get("bankszamlaszam");

        return null;
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
