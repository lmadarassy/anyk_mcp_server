package hu.anyk.mcp.tools;

import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import hu.anyk.mcp.config.AnykConfig;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.*;
import org.jsoup.Jsoup;

@SuppressWarnings("unchecked")
public class FormAnalyzerTool {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager, AnykConfig config) {
        server.addTool(analyzeSpec(sessionManager, config));
    }

    private static SyncToolSpecification analyzeSpec(SessionManager sessionManager, AnykConfig config) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" }
              },
              "required": ["sessionId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_analyze_requirements",
                "Elemzi a nyomtatvanyt es a segedletet, es osszefoglalja milyen informaciokra lesz szukseg a kitolteshez. Kategorizalja a mezoket (azonositas, cim, jovedelem, stb.) es megmondja mit kell kerdezni a felhasznalotol.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    Map<String, Object> analysis = analyzeForm(bm, session, config);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(analysis))))
                        .build();
                } catch (Exception e) {
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Error: " + e.getMessage())))
                        .isError(true)
                        .build();
                }
            })
            .build();
    }

    private static Map<String, Object> analyzeForm(BookModel bm, FormSession session, AnykConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formId", bm.id);
        result.put("formName", bm.name);
        if (bm.docinfo != null) {
            result.put("description", bm.docinfo.get("info"));
            result.put("type", bm.docinfo.get("tipus"));
        }

        // Categorize writable fields
        Map<String, List<Map<String, Object>>> categories = new LinkedHashMap<>();
        categories.put("azonositas", new ArrayList<>());
        categories.put("szemelyi_adatok", new ArrayList<>());
        categories.put("lakcim", new ArrayList<>());
        categories.put("bevallasi_idoszak", new ArrayList<>());
        categories.put("bankszamla", new ArrayList<>());
        categories.put("kapcsolattarto", new ArrayList<>());
        categories.put("nyilatkozatok", new ArrayList<>());
        categories.put("osszeg_mezok", new ArrayList<>());
        categories.put("egyeb", new ArrayList<>());

        int totalWritable = 0;
        int autoFillable = 0;

        if (bm.forms != null) {
            for (int fi = 0; fi < bm.forms.size(); fi++) {
                FormModel fm = (FormModel) bm.forms.get(fi);
                if (fm.pages == null) continue;

                hu.piller.enykp.alogic.metainfo.MetaStore ms =
                    hu.piller.enykp.alogic.metainfo.MetaInfo.getInstance().getMetaStore(fm.id);

                for (int pi = 0; pi < fm.pages.size(); pi++) {
                    PageModel pm = (PageModel) fm.pages.get(pi);
                    if (pm.y_sorted_df == null) continue;

                    for (int di = 0; di < pm.y_sorted_df.size(); di++) {
                        DataFieldModel df = (DataFieldModel) pm.y_sorted_df.get(di);
                        if (df.readonly) continue;
                        totalWritable++;

                        String mask = df.features != null ? (String) df.features.get("mask") : "";
                        if (mask == null) mask = "";
                        // A template META-jabol: vid (mezonev) es panids (torzsadat-jelentes)
                        String label = "";
                        String panids = null;
                        if (ms != null) {
                            try {
                                Map<?, ?> metas = ms.getFieldMetas(df.key);
                                if (metas != null) {
                                    Object v = metas.get("vid");
                                    if (v != null) label = v.toString();
                                    Object p = metas.get("panids");
                                    if (p != null) panids = p.toString();
                                }
                            } catch (Exception ignored) {}
                        }
                        String category = categorizeField(df.key, mask, df.type, label, panids);

                        if ("azonositas".equals(category) || "szemelyi_adatok".equals(category)
                            || "lakcim".equals(category) || "bankszamla".equals(category)) {
                            autoFillable++;
                        }

                        Map<String, Object> fieldInfo = new LinkedHashMap<>();
                        fieldInfo.put("fid", df.key);
                        fieldInfo.put("page", pm.name);
                        if (!label.isEmpty()) fieldInfo.put("label", label);
                        fieldInfo.put("type", getTypeName(df.type));
                        if (!mask.isEmpty() && !"%".equals(mask)) fieldInfo.put("mask", mask);

                        categories.computeIfAbsent(category, k -> new ArrayList<>()).add(fieldInfo);
                    }
                }
            }
        }

        // Remove empty categories
        categories.entrySet().removeIf(e -> e.getValue().isEmpty());

        result.put("totalWritableFields", totalWritable);
        result.put("autoFillableFromProfile", autoFillable);
        result.put("fieldCategories", categories);

        // Summary of what to ask
        List<String> questionsForUser = new ArrayList<>();
        if (!categories.getOrDefault("azonositas", List.of()).isEmpty())
            questionsForUser.add("Adoazonosito jel, adoszam (ha van), TAJ szam");
        if (!categories.getOrDefault("szemelyi_adatok", List.of()).isEmpty())
            questionsForUser.add("Nev, szuletesi nev, szuletesi datum es hely, anyja neve");
        if (!categories.getOrDefault("lakcim", List.of()).isEmpty())
            questionsForUser.add("Lakcim (iranyitoszam, telepules, kozterulet, hazszam)");
        if (!categories.getOrDefault("bevallasi_idoszak", List.of()).isEmpty())
            questionsForUser.add("Bevallasi idoszak (mikortol meddig)");
        if (!categories.getOrDefault("bankszamla", List.of()).isEmpty())
            questionsForUser.add("Bankszamlaszam (visszautalaashoz)");
        if (!categories.getOrDefault("osszeg_mezok", List.of()).isEmpty())
            questionsForUser.add("Jovedelem adatok, osszegek (a nyomtatvany specikus reszeletezo lapjainak megfelelo adatok)");
        if (!categories.getOrDefault("nyilatkozatok", List.of()).isEmpty())
            questionsForUser.add("Nyilatkozatok (pl. 1% felajanlas, kedvezmenyek)");

        result.put("questionsForUser", questionsForUser);

        // Read help summary if available
        String helpSummary = getHelpSummary(session, config);
        if (helpSummary != null) {
            result.put("helpSummary", helpSummary);
        }

        result.put("recommendation",
            "Hasznalj taxpayer_apply_to_form-ot az azonositas/cim/bankszamla adatok automatikus kitoltesehez. " +
            "Utana csak a nyomtatvany-specifikus mezoket (osszegek, nyilatkozatok) kell kulon kerdezni.");

        return result;
    }

    private static String categorizeField(String fid, String mask, int type, String label, String panids) {
        // 1. Elsodlegesen a template META panids-e (torzsadat-jelentes) alapjan -
        //    ez pontos, nem heurisztika. A panids nevek a mdm_entitydef.xml-bol jonnek.
        if (panids != null && !panids.isEmpty()) {
            String p = panids.toLowerCase();
            if (p.contains("adószám") || p.contains("adóazonosító") || p.contains("taj")
                || p.contains("bizonylat tulajdonos azonosító"))
                return "azonositas";
            if (p.contains("neve") || p.contains("vezetéknev") || p.contains("keresztnev")
                || p.contains("anyja") || p.contains("születési") || p.contains("neme")
                || p.contains("állampolgár") || p.contains("titulus"))
                return "szemelyi_adatok";
            if (p.contains("település") || p.contains("közterület") || p.contains("házszám")
                || p.contains("irányítószám") || p.contains("emelet") || p.contains("ajtó")
                || p.contains("lépcsőház") || p.contains("épület"))
                return "lakcim";
            if (p.contains("bevallási időszak"))
                return "bevallasi_idoszak";
            if (p.contains("számla"))
                return "bankszamla";
            if (p.contains("telefon") || p.contains("e-mail") || p.contains("ügyintéző"))
                return "kapcsolattarto";
        }

        // 2. Fallback: a korabbi mask/fid + label heurisztika (panids nelkuli mezokre)
        String fidU = fid.toUpperCase();
        String labelL = label == null ? "" : label.toLowerCase();

        if (mask.contains("########-#-##") || (mask.contains("##########") && (fidU.contains("B001") || fidU.contains("B004") || fidU.contains("C001") || fidU.contains("C002"))))
            return "azonositas";
        if (mask.contains("NEBIH") || mask.contains("OCSG")) return "azonositas";

        if (type == 1) return "nyilatkozatok";

        if (mask.contains("%\\") || labelL.contains("összeg") || labelL.contains("forint") ||
            labelL.contains("adó") || labelL.contains("járulék"))
            return "osszeg_mezok";

        return "egyeb";
    }

    private static String getHelpSummary(FormSession session, AnykConfig config) {
        String helpDir = session.getHelpDir();
        if (helpDir == null) {
            BookModel bm = (BookModel) session.getBookModel();
            if (bm.help != null && session.getOrgId() != null) {
                helpDir = config.getHelpsPath() + "/" + session.getOrgId() + "/" + bm.id;
            }
        }
        if (helpDir == null || !new File(helpDir).exists()) return null;

        BookModel bm = (BookModel) session.getBookModel();
        String helpFile = bm.help;
        if (helpFile == null) helpFile = "index.html";
        File mainHelp = new File(helpDir, helpFile);
        if (!mainHelp.exists()) {
            File[] htmls = new File(helpDir).listFiles((d, n) -> n.endsWith(".htm") || n.endsWith(".html"));
            if (htmls != null && htmls.length > 0) {
                Arrays.sort(htmls, Comparator.comparing(File::getName));
                mainHelp = htmls[0];
            }
        }
        if (!mainHelp.exists()) return null;

        try {
            var doc = Jsoup.parse(mainHelp, "UTF-8");
            doc.select("script, style, img").remove();
            String text = doc.text();
            if (text.length() > 2000) text = text.substring(0, 2000) + "...";
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getTypeName(int type) {
        return switch (type) {
            case 0 -> "text"; case 1 -> "check"; case 2 -> "combo";
            case 3 -> "tatext"; case 4 -> "date"; case 5 -> "ttext";
            case 6 -> "tcombo"; case 7 -> "ftext"; default -> "t" + type;
        };
    }
}
