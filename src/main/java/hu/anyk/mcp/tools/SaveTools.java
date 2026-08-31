package hu.anyk.mcp.tools;

import hu.anyk.mcp.adapter.SimpleXmlSaver;
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
                "outputPath": { "type": "string", "description": "Kimeneti fajl eleresi utja (.xml)" }
              },
              "required": ["sessionId", "outputPath"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_save",
                "Menti a kitoltott nyomtatvanyt XML fajlba.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String outputPath = (String) request.arguments().get("outputPath");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    SimpleXmlSaver.save(bm, outputPath);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("path", outputPath);
                    result.put("fileSize", new File(outputPath).length());
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
