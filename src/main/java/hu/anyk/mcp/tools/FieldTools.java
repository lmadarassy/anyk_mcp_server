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
            .callHandler(ToolHelper.locked((exchange, request) -> {
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
            }))
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
                "documentType": { "type": "string", "description": "Kotegelt nyomtatvanynal a cel dokumentumtipus (pl. '25HIPAKA' vagy '25HIPAKM'). A fid NEM globalisan egyedi - ugyanaz a fid mas mezot jelenthet kulonbozo dokumentumokban! Alapertelmezes: a fo dokumentum." },
                "pageIndex": { "type": "integer", "description": "Oldal index dinamikus oldalaknal (alapertelmezett: 0)" }
              },
              "required": ["sessionId", "fieldId", "value"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_set_field",
                "Beallitja egy mezo erteket a cel dokumentumban. Kotegelt nyomtatvanynal a fid nem globalisan egyedi, ezert a documentType-pal lehet a helyes dokumentumot valasztani (mint az ANYK-ban a lap-valaszto). Ha a mezo nem letezik a cel dokumentumban, hibat ad.",
                schema).build())
            .callHandler(ToolHelper.locked((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String fieldId = (String) request.arguments().get("fieldId");
                    String value = (String) request.arguments().get("value");
                    String documentType = (String) request.arguments().get("documentType");
                    int pageIndex = getInt(request.arguments().get("pageIndex"), 0);
                    hu.anyk.mcp.McpLog.tool("form_set_field", "doc=" + documentType + " fid=" + fieldId);
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    // Cel dokumentum aktivva tetele (mint a GUI lap-valaszto)
                    if (documentType == null || documentType.isBlank()) {
                        documentType = bm.main_document_id;
                    }
                    int idx = BookModelAdapter.setActiveDocument(bm, documentType);
                    if (idx < 0) {
                        return errorResult("Nincs '" + documentType + "' tipusu dokumentum-peldany. "
                            + "Hasznald a form_list_document_types / form_add_document tool-t.");
                    }

                    boolean ok = BookModelAdapter.setFieldOnActive(bm, pageIndex, fieldId, value);
                    if (!ok) {
                        return errorResult("A(z) '" + fieldId + "' mezo nem letezik a(z) '"
                            + documentType + "' dokumentumban. Ellenorizd a documentType-ot vagy a fid-et "
                            + "(a fid nem globalisan egyedi kotegelt nyomtatvanynal).");
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("fid", fieldId);
                    result.put("value", value);
                    result.put("documentType", documentType);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            }))
            .build();
    }

    private static SyncToolSpecification setFieldsSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "documentType": { "type": "string", "description": "Alapertelmezett cel dokumentumtipus a mezokhoz (pl. '25HIPAKA'). Mezonkent felulirhato. Alapertelmezes: a fo dokumentum." },
                "fields": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "fieldId": { "type": "string" },
                      "value": { "type": "string" },
                      "documentType": { "type": "string", "description": "Ehhez a mezohoz tartozo dokumentumtipus (felulirja a top-level erteket)." },
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
                "Tobb mezo egyszerre torteno beallitasa (batch). Kotegelt nyomtatvanynal a documentType (top-level vagy mezonkent) valasztja a cel dokumentumot - a fid nem globalisan egyedi.",
                schema).build())
            .callHandler(ToolHelper.locked((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String defaultDocType = (String) request.arguments().get("documentType");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) request.arguments().get("fields");
                    hu.anyk.mcp.McpLog.tool("form_set_fields", "doc=" + defaultDocType
                        + " count=" + (fields == null ? 0 : fields.size()));
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    if (bm.cc == null || bm.cc.size() == 0) return errorResult("Nincs aktiv adattarolo");

                    if (defaultDocType == null || defaultDocType.isBlank()) {
                        defaultDocType = bm.main_document_id;
                    }

                    List<Map<String, Object>> setResults = new ArrayList<>();
                    List<Map<String, Object>> errors = new ArrayList<>();

                    for (Map<String, Object> field : fields) {
                        String fieldId = (String) field.get("fieldId");
                        String value = (String) field.get("value");
                        int pageIndex = getInt(field.get("pageIndex"), 0);
                        String docType = (String) field.get("documentType");
                        if (docType == null || docType.isBlank()) docType = defaultDocType;

                        try {
                            int idx = BookModelAdapter.setActiveDocument(bm, docType);
                            if (idx < 0) {
                                Map<String, Object> err = new LinkedHashMap<>();
                                err.put("fid", fieldId);
                                err.put("error", "Nincs '" + docType + "' tipusu dokumentum-peldany");
                                errors.add(err);
                                continue;
                            }
                            boolean ok = BookModelAdapter.setFieldOnActive(bm, pageIndex, fieldId, value);
                            if (ok) {
                                Map<String, Object> r = new LinkedHashMap<>();
                                r.put("fid", fieldId);
                                r.put("value", value);
                                r.put("documentType", docType);
                                setResults.add(r);
                            } else {
                                Map<String, Object> err = new LinkedHashMap<>();
                                err.put("fid", fieldId);
                                err.put("error", "A mezo nem letezik a(z) '" + docType + "' dokumentumban");
                                errors.add(err);
                            }
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
            }))
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
            .callHandler(ToolHelper.locked((exchange, request) -> {
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
            }))
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
