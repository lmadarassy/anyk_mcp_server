package hu.anyk.mcp.tools;

import hu.anyk.mcp.config.AnykConfig;
import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class HelpTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager, AnykConfig config) {
        server.addTool(getGuideSpec(sessionManager));
        server.addTool(listPagesSpec(sessionManager));
        server.addTool(searchSpec(sessionManager));
    }

    private static SyncToolSpecification getGuideSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "page": { "type": "string", "description": "Segedlet oldal fajlneve (pl. 'index.html'). Ha nincs megadva, a fo oldalt adja vissza." }
              },
              "required": ["sessionId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("help_get_guide",
                "Visszaadja a nyomtatvany kitoltesi utmutatojat (segedletet) szoveges formatumban. Az LLM ebbol erti meg, melyik mezot hogyan kell kitolteni.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String page = (String) request.arguments().get("page");
                    FormSession session = sessionManager.getSession(sessionId);
                    String helpDir = session.getHelpDir();
                    if (helpDir == null) {
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent("Nincs elerheto kitoltesi utmutato ehhez a nyomtatvanyhoz.")))
                            .build();
                    }

                    if (page == null || page.isEmpty()) {
                        page = "index.html";
                        hu.piller.enykp.gui.model.BookModel bm =
                            (hu.piller.enykp.gui.model.BookModel) session.getBookModel();
                        if (bm.help != null && !bm.help.isEmpty()) {
                            page = bm.help;
                        }
                    }

                    File htmlFile = new File(helpDir, page);
                    if (!htmlFile.exists()) {
                        File[] htmlFiles = new File(helpDir).listFiles((d, n) -> n.endsWith(".html") || n.endsWith(".htm"));
                        if (htmlFiles != null && htmlFiles.length > 0) {
                            htmlFile = htmlFiles[0];
                        } else {
                            return CallToolResult.builder()
                                .content(List.of(new McpSchema.TextContent("Segedlet fajl nem talalhato: " + page)))
                                .build();
                        }
                    }

                    String text = htmlToText(htmlFile);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("page", htmlFile.getName());
                    result.put("content", text);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification listPagesSpec(SessionManager sessionManager) {
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
            .tool(ToolHelper.tool("help_list_pages",
                "Listazza a segedlet osszes oldal-hivatkozasat (tartalomjegyzek).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    FormSession session = sessionManager.getSession(sessionId);
                    String helpDir = session.getHelpDir();
                    if (helpDir == null) {
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent("Nincs elerheto segedlet.")))
                            .build();
                    }

                    File dir = new File(helpDir);
                    File[] htmlFiles = dir.listFiles((d, n) -> n.endsWith(".html") || n.endsWith(".htm"));
                    List<Map<String, Object>> pages = new ArrayList<>();
                    if (htmlFiles != null) {
                        Arrays.sort(htmlFiles, Comparator.comparing(File::getName));
                        for (File f : htmlFiles) {
                            Map<String, Object> pageInfo = new LinkedHashMap<>();
                            pageInfo.put("file", f.getName());
                            try {
                                Document doc = Jsoup.parse(f, detectEncoding(f));
                                String title = doc.title();
                                if (title != null && !title.isEmpty()) {
                                    pageInfo.put("title", title);
                                }
                            } catch (Exception ignored) {}
                            pages.add(pageInfo);
                        }
                    }

                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(pages))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification searchSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "query": { "type": "string", "description": "Keresesi kifejezes" }
              },
              "required": ["sessionId", "query"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("help_search",
                "Keres a kitoltesi utmutatoban egy adott kifejezes utan.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String query = (String) request.arguments().get("query");
                    FormSession session = sessionManager.getSession(sessionId);
                    String helpDir = session.getHelpDir();
                    if (helpDir == null) {
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent("Nincs elerheto segedlet.")))
                            .build();
                    }

                    File dir = new File(helpDir);
                    File[] htmlFiles = dir.listFiles((d, n) -> n.endsWith(".html") || n.endsWith(".htm"));
                    List<Map<String, Object>> results = new ArrayList<>();
                    String q = query.toLowerCase();

                    if (htmlFiles != null) {
                        for (File f : htmlFiles) {
                            try {
                                String text = htmlToText(f);
                                String lower = text.toLowerCase();
                                int idx = lower.indexOf(q);
                                if (idx >= 0) {
                                    int start = Math.max(0, idx - 100);
                                    int end = Math.min(text.length(), idx + query.length() + 200);
                                    Map<String, Object> hit = new LinkedHashMap<>();
                                    hit.put("page", f.getName());
                                    hit.put("context", text.substring(start, end).trim());
                                    results.add(hit);
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                    if (results.isEmpty()) {
                        return CallToolResult.builder()
                            .content(List.of(new McpSchema.TextContent("Nincs talalat: " + query)))
                            .build();
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

    private static String htmlToText(File htmlFile) throws IOException {
        String encoding = detectEncoding(htmlFile);
        Document doc = Jsoup.parse(htmlFile, encoding);
        doc.select("script, style, img").remove();
        doc.select("br").append("\\n");
        doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6").append("\\n");
        doc.select("td, th").append(" | ");
        String text = doc.text();
        text = text.replaceAll("\\\\n", "\n");
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    private static String detectEncoding(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String head = new String(bytes, 0, Math.min(bytes.length, 1024), "ISO-8859-1");
            if (head.contains("charset=windows-1250") || head.contains("charset=Windows-1250")) {
                return "windows-1250";
            }
            if (head.contains("charset=iso-8859-2") || head.contains("charset=ISO-8859-2")) {
                return "ISO-8859-2";
            }
            if (head.contains("charset=utf-8") || head.contains("charset=UTF-8")) {
                return "UTF-8";
            }
        } catch (Exception ignored) {}
        return "windows-1250";
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
