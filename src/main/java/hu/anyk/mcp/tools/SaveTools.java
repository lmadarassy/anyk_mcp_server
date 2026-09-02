package hu.anyk.mcp.tools;

import hu.anyk.mcp.adapter.PropertyListInitializer;
import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.io.File;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.alogic.filesaver.xml.EnykXmlSaver;

public class SaveTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager) {
        server.addTool(saveSpec(sessionManager));
    }

    private static SyncToolSpecification saveSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "outputPath": { "type": "string", "description": "Kimeneti fajl eleresi utja (.xml). A konyvtar es a fajlnev innen szarmazik." }
              },
              "required": ["sessionId", "outputPath"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_save",
                "Menti a kitoltott nyomtatvanyt XML export formatumban az eredeti ANYK EnykXmlSaver-rel (validacioval es SHA-1 hash-sel).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String outputPath = (String) request.arguments().get("outputPath");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    File outFile = new File(outputPath);
                    File dir = outFile.getParentFile();
                    if (dir == null) dir = new File(".");
                    String bareName = outFile.getName();
                    // A .xml suffixet az EnykXmlSaver adja hozza
                    if (bareName.toLowerCase().endsWith(".xml")) {
                        bareName = bareName.substring(0, bareName.length() - 4);
                    }

                    // Mentesi konyvtar beallitasa (getDsPath ezt hasznalja)
                    PropertyListInitializer.setSaveDir(dir.getAbsolutePath());

                    EnykXmlSaver saver = new EnykXmlSaver(bm);
                    boolean ok = saver.save(bareName, true);

                    File produced = new File(dir, bareName + saver.getFileNameSuffix());

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", ok);
                    result.put("path", produced.getAbsolutePath());
                    if (produced.exists()) {
                        result.put("fileSize", produced.length());
                    }
                    if (!ok) {
                        result.put("message", "A mentes sikertelen. Ellenorizze a validacios hibakat form_validate-tel.");
                    }
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult("Mentesi hiba: " + e.getMessage());
                }
            })
            .build();
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
