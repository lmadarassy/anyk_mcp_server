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
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.datastore.GUI_Datastore;
import hu.anyk.mcp.adapter.BookModelAdapter;

@SuppressWarnings("unchecked")
public class FieldTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager) {
        server.addTool(getFieldSpec(sessionManager));
        server.addTool(setFieldSpec(sessionManager));
        server.addTool(setFieldsSpec(sessionManager));
        server.addTool(getAllFieldsSpec(sessionManager));
    }

    private static SyncToolSpecification getFieldSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "fieldId": { "type": "string", "description": "Mezo azonosito (fid)" },
                "pageIndex": { "type": "integer", "description": "Oldal index dinamikus oldalaknal (alapertelmezett: 0)" }
              },
              "required": ["sessionId", "fieldId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_get_field",
                "Lekerdezi egy mezo aktualis erteket.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String fieldId = (String) request.arguments().get("fieldId");
                    int pageIndex = getInt(request.arguments().get("pageIndex"), 0);
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                    if (ds == null) return errorResult("Nincs aktiv adattarolo");

                    String value = ds.get(new Object[]{Integer.valueOf(pageIndex), fieldId});
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("fid", fieldId);
                    result.put("value", value != null ? value : "");
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification setFieldSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "fieldId": { "type": "string", "description": "Mezo azonosito (fid)" },
                "value": { "type": "string", "description": "Beallitando ertek" },
                "pageIndex": { "type": "integer", "description": "Oldal index dinamikus oldalaknal (alapertelmezett: 0)" }
              },
              "required": ["sessionId", "fieldId", "value"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_set_field",
                "Beallitja egy mezo erteket. Visszaadja a szamitott mezok valtozasait is.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String fieldId = (String) request.arguments().get("fieldId");
                    String value = (String) request.arguments().get("value");
                    int pageIndex = getInt(request.arguments().get("pageIndex"), 0);
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                    if (ds == null) return errorResult("Nincs aktiv adattarolo");

                    BookModelAdapter.setFieldWithCalc(bm, ds, pageIndex, fieldId, value);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("fid", fieldId);
                    result.put("value", value);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification setFieldsSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "fields": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "fieldId": { "type": "string" },
                      "value": { "type": "string" },
                      "pageIndex": { "type": "integer" }
                    },
                    "required": ["fieldId", "value"]
                  },
                  "description": "Beallitando mezok listaja"
                }
              },
              "required": ["sessionId", "fields"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_set_fields",
                "Tobb mezo egyszerre torteno beallitasa (batch). Hatekonya sok mezo kitoltesenel.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) request.arguments().get("fields");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);
                    if (ds == null) return errorResult("Nincs aktiv adattarolo");

                    List<Map<String, Object>> setResults = new ArrayList<>();
                    List<Map<String, Object>> errors = new ArrayList<>();

                    for (Map<String, Object> field : fields) {
                        String fieldId = (String) field.get("fieldId");
                        String value = (String) field.get("value");
                        int pageIndex = getInt(field.get("pageIndex"), 0);
                        try {
                            BookModelAdapter.setFieldWithCalc(bm, ds, pageIndex, fieldId, value);
                            Map<String, Object> ok = new LinkedHashMap<>();
                            ok.put("fid", fieldId);
                            ok.put("value", value);
                            setResults.add(ok);
                        } catch (Exception e) {
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("fid", fieldId);
                            err.put("error", e.getMessage());
                            errors.add(err);
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", errors.isEmpty());
                    result.put("setCount", setResults.size());
                    if (!errors.isEmpty()) result.put("errors", errors);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification getAllFieldsSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "formTypeId": { "type": "string", "description": "Urlap azonosito (opcionalis, pl. '25HIPAKA')" },
                "onlyFilled": { "type": "boolean", "description": "Csak a kitoltott mezoket adja vissza (alapertelmezett: false)" }
              },
              "required": ["sessionId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_get_all_fields",
                "Az osszes mezo aktualis erteket adja vissza (opcionalisan szurheto urlap/oldal szerint).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String formTypeId = (String) request.arguments().get("formTypeId");
                    String pageId = (String) request.arguments().get("pageId");
                    Boolean onlyFilled = (Boolean) request.arguments().get("onlyFilled");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    GUI_Datastore ds = BookModelAdapter.getActiveDataStore(bm);

                    List<Map<String, Object>> fieldsList = new ArrayList<>();
                    if (bm.forms != null) {
                        for (int fi = 0; fi < bm.forms.size(); fi++) {
                            FormModel fm = (FormModel) bm.forms.get(fi);
                            if (formTypeId != null && !formTypeId.equals(fm.id)) continue;
                            if (fm.fids != null) {
                                Enumeration<String> keys = fm.fids.keys();
                                while (keys.hasMoreElements()) {
                                    String fid = keys.nextElement();
                                    String val = ds != null ? ds.get(new Object[]{Integer.valueOf(0), fid}) : null;
                                    if (Boolean.TRUE.equals(onlyFilled) && (val == null || val.isEmpty())) continue;

                                    Map<String, Object> fieldMap = new LinkedHashMap<>();
                                    fieldMap.put("fid", fid);
                                    fieldMap.put("formId", fm.id);
                                    fieldMap.put("value", val != null ? val : "");
                                    fieldsList.add(fieldMap);
                                }
                            }
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("fieldCount", fieldsList.size());
                    result.put("fields", fieldsList);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static int getInt(Object obj, int defaultVal) {
        if (obj == null) return defaultVal;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return defaultVal; }
    }

    private static CallToolResult errorResult(String msg) {
        return CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Error: " + msg)))
            .isError(true)
            .build();
    }
}
