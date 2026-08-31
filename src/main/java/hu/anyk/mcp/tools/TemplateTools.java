package hu.anyk.mcp.tools;

import hu.anyk.mcp.config.AnykConfig;
import hu.anyk.mcp.adapter.BookModelAdapter;
import hu.anyk.mcp.adapter.DownloadAdapter;
import hu.anyk.mcp.adapter.DownloadAdapter.ComponentInfo;
import hu.anyk.mcp.adapter.DownloadAdapter.DownloadResult;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.io.File;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.BookModel;

public class TemplateTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, AnykConfig config) {
        server.addTool(listInstalledSpec(config));
        server.addTool(searchSpec(config));
        server.addTool(listAvailableSpec());
        server.addTool(downloadSpec(config));
    }

    private static SyncToolSpecification listInstalledSpec(AnykConfig config) {
        String schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("template_list_installed",
                "Listazza a telepitett nyomtatvany sablonokat.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    List<Map<String, Object>> results = listTemplates(config);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(results))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification searchSpec(AnykConfig config) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Keresesi kifejezes (nyomtatvany szam vagy nev resze)"
                }
              },
              "required": ["query"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("template_search",
                "Keres egy nyomtatvanyt nev vagy azonosito alapjan a telepitett sablonok kozott.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String query = (String) request.arguments().get("query");
                    List<Map<String, Object>> all = listTemplates(config);
                    String q = query.toLowerCase();
                    List<Map<String, Object>> filtered = all.stream()
                        .filter(t -> {
                            String id = String.valueOf(t.get("id")).toLowerCase();
                            String name = String.valueOf(t.get("name")).toLowerCase();
                            return id.contains(q) || name.contains(q);
                        })
                        .toList();
                    if (filtered.isEmpty()) {
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent("Nem talalhato telepitett nyomtatvany: " + query + ". Hasznald a template_list_available tool-t a letoltheto nyomtatvanyokhoz.")))
                            .build();
                    }
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(filtered))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification listAvailableSpec() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Opcionalis szuro: nyomtatvany szam vagy nev resze (pl. '2558' vagy 'forgalmi')"
                }
              },
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("template_list_available",
                "Lekerdezi a NAV szerverrol az osszes letoltheto nyomtatvanyt es segedletet. Opcionalis szurovel szukitheto.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String query = (String) request.arguments().get("query");
                    List<ComponentInfo> all = DownloadAdapter.fetchAvailableComponents();

                    List<Map<String, Object>> results = new ArrayList<>();
                    for (ComponentInfo c : all) {
                        if (query != null && !query.isEmpty()) {
                            String q = query.toLowerCase();
                            if (!c.shortName().toLowerCase().contains(q) &&
                                (c.description() == null || !c.description().toLowerCase().contains(q))) {
                                continue;
                            }
                        }
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("shortName", c.shortName());
                        info.put("category", c.category());
                        info.put("org", c.org());
                        info.put("version", c.version());
                        info.put("description", c.description());
                        info.put("jarUrl", c.getJarUrl());
                        results.add(info);
                    }

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("totalCount", results.size());
                    response.put("components", results);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(response))))
                        .build();
                } catch (Exception e) {
                    return errorResult("NAV szerver lekerdezesi hiba: " + e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification downloadSpec(AnykConfig config) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "formId": {
                  "type": "string",
                  "description": "Nyomtatvany rovid neve / azonositoja (pl. '2558')"
                },
                "includeHelp": {
                  "type": "boolean",
                  "description": "Segedlet (utmutato) letoltese is (alapertelmezett: true)"
                }
              },
              "required": ["formId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("template_download",
                "Letolt egy nyomtatvany sablont (es opcionálisan a segedletet) a NAV szerverrol es telepiti.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String formId = (String) request.arguments().get("formId");
                    Boolean includeHelp = (Boolean) request.arguments().get("includeHelp");
                    if (includeHelp == null) includeHelp = true;

                    List<ComponentInfo> all = DownloadAdapter.fetchAvailableComponents();

                    ComponentInfo template = null;
                    ComponentInfo help = null;
                    String fid = formId.toLowerCase();

                    for (ComponentInfo c : all) {
                        String sn = c.shortName().toLowerCase();
                        if (c.isTemplate() && (sn.equals(fid) || sn.endsWith("_" + fid) || sn.equals("nav_" + fid))) {
                            if (template == null || c.version().compareTo(template.version()) > 0) {
                                template = c;
                            }
                        }
                        if (c.isHelp() && (sn.equals(fid) || sn.endsWith("_" + fid) || sn.equals("nav_" + fid))) {
                            if (help == null || c.version().compareTo(help.version()) > 0) {
                                help = c;
                            }
                        }
                    }

                    if (template == null) {
                        return errorResult("Nem talalhato letoltheto nyomtatvany: " + formId);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();

                    DownloadResult templateResult = DownloadAdapter.downloadAndInstall(template, config.getAnykRoot());
                    Map<String, Object> tInfo = new LinkedHashMap<>();
                    tInfo.put("name", templateResult.name());
                    tInfo.put("version", templateResult.version());
                    tInfo.put("installedFiles", templateResult.installedFiles().size());
                    result.put("template", tInfo);

                    if (includeHelp && help != null) {
                        DownloadResult helpResult = DownloadAdapter.downloadAndInstall(help, config.getAnykRoot());
                        Map<String, Object> hInfo = new LinkedHashMap<>();
                        hInfo.put("name", helpResult.name());
                        hInfo.put("version", helpResult.version());
                        hInfo.put("installedFiles", helpResult.installedFiles().size());
                        result.put("help", hInfo);
                    } else if (includeHelp) {
                        result.put("help", "Nincs elerheto segedlet ehhez a nyomtatvanyhoz");
                    }

                    result.put("success", true);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult("Letoltesi hiba: " + e.getMessage());
                }
            })
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listTemplates(AnykConfig config) {
        List<Map<String, Object>> results = new ArrayList<>();
        File templatesDir = new File(config.getTemplatesPath());
        if (!templatesDir.exists()) return results;

        File[] files = templatesDir.listFiles((dir, name) -> name.endsWith(".tem.enyk"));
        if (files == null) return results;

        for (File f : files) {
            try {
                BookModel bm = BookModelAdapter.loadTemplate(f);
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", bm.id != null ? bm.id : "");
                info.put("name", bm.name != null ? bm.name : "");
                info.put("templatePath", f.getAbsolutePath());
                if (bm.docinfo != null) {
                    info.put("version", bm.docinfo.get("ver"));
                    info.put("org", bm.docinfo.get("org"));
                }
                info.put("formCount", bm.forms != null ? bm.forms.size() : 0);
                info.put("helpAvailable", bm.help != null && !bm.help.isEmpty());
                bm.destroy();
                results.add(info);
            } catch (Exception e) {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("file", f.getName());
                info.put("error", e.getMessage());
                results.add(info);
            }
        }
        return results;
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
