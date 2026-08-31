package hu.anyk.mcp.tools;

import hu.anyk.mcp.session.FormSession;
import hu.anyk.mcp.session.SessionManager;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.io.File;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import hu.piller.enykp.gui.model.BookModel;
import hu.piller.enykp.gui.model.FormModel;
import hu.piller.enykp.gui.model.PageModel;
import hu.anyk.mcp.adapter.BookModelAdapter;

public class FormTools {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register(McpSyncServer server, SessionManager sessionManager) {
        server.addTool(openSpec(sessionManager));
        server.addTool(closeSpec(sessionManager));
        server.addTool(structureSpec(sessionManager));
    }

    private static SyncToolSpecification openSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "templatePath": {
                  "type": "string",
                  "description": "Teljes eleresi ut a sablon fajlhoz (.tem.enyk)"
                }
              },
              "required": ["templatePath"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_open",
                "Megnyit egy nyomtatvany sablont es letrehoz egy ures peldanyt kitoltesre. Visszaadja a sessionId-t.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String templatePath = (String) request.arguments().get("templatePath");
                    File templateFile = new File(templatePath);
                    if (!templateFile.exists()) {
                        return errorResult("Sablon fajl nem talalhato: " + templatePath);
                    }

                    BookModel bm = BookModelAdapter.loadTemplate(templateFile);
                    BookModelAdapter.addEmptyForm(bm, 0);

                    FormSession session = sessionManager.createSession();
                    session.setBookModel(bm);
                    session.setCachedCollection(bm.cc);
                    session.setTemplateFile(templateFile);
                    session.setTemplateId(bm.id);
                    session.setFormName(bm.name);
                    if (bm.docinfo != null) {
                        session.setOrgId((String) bm.docinfo.get("org"));
                    }

                    String helpsPath = sessionManager.getConfig().getHelpsPath();
                    if (bm.help != null && session.getOrgId() != null) {
                        String helpDir = helpsPath + "/" + session.getOrgId() + "/" + bm.id;
                        if (new File(helpDir).exists()) {
                            session.setHelpDir(helpDir);
                        }
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("sessionId", session.getSessionId());
                    result.put("formId", bm.id);
                    result.put("formName", bm.name);
                    result.put("helpAvailable", session.getHelpDir() != null);

                    List<Map<String, Object>> formsList = new ArrayList<>();
                    if (bm.forms != null) {
                        for (int i = 0; i < bm.forms.size(); i++) {
                            FormModel fm = (FormModel) bm.forms.get(i);
                            Map<String, Object> formInfo = new LinkedHashMap<>();
                            formInfo.put("id", fm.id);
                            formInfo.put("name", fm.name);
                            formInfo.put("pageCount", fm.pages != null ? fm.pages.size() : 0);
                            formsList.add(formInfo);
                        }
                    }
                    result.put("forms", formsList);

                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult("Hiba a nyomtatvany megnyitasakor: " + e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification closeSpec(SessionManager sessionManager) {
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
            .tool(ToolHelper.tool("form_close",
                "Bezar egy megnyitott nyomtatvany session-t.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();
                    if (bm != null) bm.destroy();
                    sessionManager.closeSession(sessionId);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("{\"success\": true}")))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    @SuppressWarnings("unchecked")
    private static SyncToolSpecification structureSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "formTypeId": { "type": "string", "description": "Urlap azonosito (opcionalis, pl. 'A')" },
                "pageId": { "type": "string", "description": "Oldal azonosito (opcionalis)" }
              },
              "required": ["sessionId"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_get_structure",
                "Visszaadja a nyomtatvany strukturajat: urlapok, oldalak, mezok (tipus, cimke, szabalyok, aktualis ertek).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String formTypeId = (String) request.arguments().get("formTypeId");
                    String pageId = (String) request.arguments().get("pageId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    Map<String, Object> result = StructureMapper.mapStructure(bm, formTypeId, pageId);
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
