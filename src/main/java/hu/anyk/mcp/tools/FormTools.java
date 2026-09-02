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
        server.addTool(listDocTypesSpec(sessionManager));
        server.addTool(addDocumentSpec(sessionManager));
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
                    BookModelAdapter.addEmptyForm(bm);

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

                    result.put("mainDocumentId", bm.main_document_id);

                    List<Map<String, Object>> formsList = new ArrayList<>();
                    boolean hasExtraDocs = false;
                    if (bm.forms != null) {
                        for (int i = 0; i < bm.forms.size(); i++) {
                            FormModel fm = (FormModel) bm.forms.get(i);
                            Map<String, Object> formInfo = new LinkedHashMap<>();
                            formInfo.put("id", fm.id);
                            formInfo.put("name", fm.name);
                            formInfo.put("pageCount", fm.pages != null ? fm.pages.size() : 0);
                            boolean isMain = fm.id.equals(bm.main_document_id);
                            formInfo.put("isMain", isMain);
                            int maxc = i < bm.maxcreation.length ? bm.maxcreation[i] : 1;
                            formInfo.put("maxCreation", maxc);
                            formInfo.put("added", isMain); // csak a fo dokumentum jon letre automatikusan
                            if (!isMain) hasExtraDocs = true;
                            formsList.add(formInfo);
                        }
                    }
                    result.put("forms", formsList);
                    if (hasExtraDocs) {
                        result.put("note", "Kotegelt nyomtatvany: csak a fo dokumentum (isMain=true) jott letre. "
                            + "A tovabbi dokumentumokat (pl. onkormanyzati fedolap) a form_add_document tool-lal add hozza.");
                    }

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

    private static SyncToolSpecification listDocTypesSpec(SessionManager sessionManager) {
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
            .tool(ToolHelper.tool("form_list_document_types",
                "Listazza a nyomtatvanyban elerheto dokumentumtipusokat (fo dokumentum + tovabbi lapok, pl. kotegelt fedolap). Megmutatja melyik a fo (isMain) es melyikbol hozhato letre tobb (maxCreation).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("mainDocumentId", bm.main_document_id);
                    result.put("documentTypes", BookModelAdapter.listDocumentTypes(bm));

                    // aktualis peldanyok a cc-ben
                    List<Map<String, Object>> instances = new ArrayList<>();
                    if (bm.cc != null) {
                        for (int i = 0; i < bm.cc.size(); i++) {
                            Object o = bm.cc.get(i);
                            if (o instanceof hu.piller.enykp.datastore.Elem elem) {
                                Map<String, Object> inst = new LinkedHashMap<>();
                                inst.put("index", i);
                                inst.put("type", elem.getType());
                                instances.add(inst);
                            }
                        }
                    }
                    result.put("instances", instances);
                    return CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(gson.toJson(result))))
                        .build();
                } catch (Exception e) {
                    return errorResult(e.getMessage());
                }
            })
            .build();
    }

    private static SyncToolSpecification addDocumentSpec(SessionManager sessionManager) {
        String schema = """
            {
              "type": "object",
              "properties": {
                "sessionId": { "type": "string", "description": "Session azonosito" },
                "documentType": { "type": "string", "description": "A hozzaadando dokumentumtipus azonositoja (pl. '25HIPAKM' a kotegelt fedolaphoz). A form_list_document_types adja meg az elerheto tipusokat." }
              },
              "required": ["sessionId", "documentType"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_add_document",
                "Hozzaad egy tovabbi dokumentum-peldanyt a nyomtatvanyhoz (pl. kotegelt fedolap 25HIPAKM), ugyanugy mint az ANYK 'uj lap' gombja. Kotegelt nyomtatvanyoknal ez kell a fedolaphoz, es ez inditja be a fo-adatok (adoszam/nev) propagaciojat a fedolap fejleceibe.",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String documentType = (String) request.arguments().get("documentType");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    int newIndex = BookModelAdapter.addDocument(bm, documentType);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("success", true);
                    result.put("documentType", documentType);
                    result.put("instanceIndex", newIndex);
                    result.put("totalInstances", bm.cc.size());
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
