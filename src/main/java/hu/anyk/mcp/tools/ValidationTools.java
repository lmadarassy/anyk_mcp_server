package hu.anyk.mcp.tools;

import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.alogic.fileutil.DataChecker;
import hu.anyk.mcp.adapter.BookModelAdapter;

@SuppressWarnings("unchecked")
public class ValidationTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager) {
        server.addTool(validateSpec(sessionManager));
        server.addTool(validateFieldSpec(sessionManager));
    }

    private static SyncToolSpecification validateSpec(SessionManager sessionManager) {
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
            .tool(ToolHelper.tool("form_validate",
                "Teljes validaciot futtat a nyomtatvanyon. Visszaadja a hibakat es figyelmeztetesekat.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    DataChecker checker = DataChecker.getInstance();
                    Object checkResult = checker.superCheck(bm, true);

                    List<Map<String, Object>> errorsList = new ArrayList<>();
                    if (bm.errorlist != null) {
                        for (int i = 0; i < bm.errorlist.size(); i++) {
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("severity", "ERROR");
                            err.put("message", bm.errorlist.get(i).toString());
                            errorsList.add(err);
                        }
                    }
                    if (bm.warninglist != null) {
                        for (int i = 0; i < bm.warninglist.size(); i++) {
                            Map<String, Object> warn = new LinkedHashMap<>();
                            warn.put("severity", "WARNING");
                            warn.put("message", bm.warninglist.get(i).toString());
                            errorsList.add(warn);
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("valid", errorsList.isEmpty() || !bm.hasError);
                    result.put("errorCount", bm.errorlist != null ? bm.errorlist.size() : 0);
                    result.put("warningCount", bm.warninglist != null ? bm.warninglist.size() : 0);
                    result.put("errors", errorsList);

                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult("Validacios hiba: " + e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification validateFieldSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "fieldId": { "type": "string", "description": "Mezo azonosito (fid)" },
                "value": { "type": "string", "description": "Ellenorizendo ertek" },
                "formTypeId": { "type": "string", "description": "Urlap azonosito (pl. 'A')" }
              },
              "required": ["sessionId", "fieldId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_validate_field",
                "Egyetlen mezo validacioja.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String fieldId = (String) request.arguments().get("fieldId");
                    String value = (String) request.arguments().get("value");
                    String formTypeId = (String) request.arguments().get("formTypeId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    if (formTypeId == null && bm.forms != null && !bm.forms.isEmpty()) {
                        formTypeId = ((hu.piller.enykp.gui.model.FormModel) bm.forms.get(0)).id;
                    }

                    if (value == null) {
                        hu.piller.enykp.datastore.GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                        if (ds != null) {
                            value = ds.get(new Object[]{Integer.valueOf(0), fieldId});
                        }
                        if (value == null) value = "";
                    }

                    DataChecker checker = DataChecker.getInstance();
                    Object checkResult = checker.checkField(bm, formTypeId, fieldId, value);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("fid", fieldId);
                    result.put("value", value);
                    result.put("valid", checkResult == null || !bm.hasError);

                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
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
