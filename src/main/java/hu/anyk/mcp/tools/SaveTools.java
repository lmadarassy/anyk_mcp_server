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
import hu.piller.enykp.alogic.filesaver.enykinner.EnykInnerSaver;
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
                "outputPath": { "type": "string", "description": "Kimeneti fajl eleresi utja. Alapertelmezetten .frm.enyk (belso ANYK formatum, visszatoltheto ANYK-ba)." },
                "format": { "type": "string", "enum": ["enyk", "xml"], "description": "enyk = belso .frm.enyk mentesi formatum (alapertelmezett, ez toltheto vissza ANYK-ba); xml = .xml export formatum. Alapertelmezes: enyk" }
              },
              "required": ["sessionId", "outputPath"],
              "additionalProperties": false
            }
            """;
        return SyncToolSpecification.builder()
            .tool(ToolHelper.tool("form_save",
                "Menti a kitoltott nyomtatvanyt az eredeti ANYK mentovel. Alapertelmezetten a belso .frm.enyk formatumot hasznalja (EnykInnerSaver), amit az ANYK vissza tud tolteni. A format=xml a .xml export formatumot adja (EnykXmlSaver).",
                schema).build())
            .callHandler((exchange, request) -> {
                try {
                    String sessionId = (String) request.arguments().get("sessionId");
                    String outputPath = (String) request.arguments().get("outputPath");
                    String format = (String) request.arguments().getOrDefault("format", "enyk");
                    FormSession session = sessionManager.getSession(sessionId);
                    BookModel bm = (BookModel) session.getBookModel();

                    Map<String, Object> result = new LinkedHashMap<>();

                    if ("xml".equalsIgnoreCase(format)) {
                        // .xml export - EnykXmlSaver (a mentesi konyvtart a SettingsStore adja)
                        File outFile = new File(outputPath);
                        File dir = outFile.getParentFile();
                        if (dir == null) dir = new File(".");
                        String bareName = outFile.getName();
                        if (bareName.toLowerCase().endsWith(".xml")) {
                            bareName = bareName.substring(0, bareName.length() - 4);
                        }
                        PropertyListInitializer.setSaveDir(dir.getAbsolutePath());
                        EnykXmlSaver saver = new EnykXmlSaver(bm);
                        boolean ok = saver.save(bareName, true);
                        File produced = new File(dir, bareName + saver.getFileNameSuffix());
                        result.put("success", ok);
                        result.put("format", "xml");
                        result.put("path", produced.getAbsolutePath());
                        if (produced.exists()) result.put("fileSize", produced.length());
                        if (!ok) result.put("message", "A mentes sikertelen. Ellenorizze a validacios hibakat form_validate-tel.");
                    } else {
                        // Belso .frm.enyk formatum - EnykInnerSaver
                        // Abszolut path .frm.enyk vegzodessel -> az EnykInnerSaver kozvetlenul ide ir
                        File outFile = new File(outputPath).getAbsoluteFile();
                        String path = outFile.getPath();
                        if (!path.toLowerCase().endsWith(".frm.enyk")) {
                            // ha .xml-t adtak de enyk formatumot kernek, csereljuk a suffixet
                            if (path.toLowerCase().endsWith(".xml")) {
                                path = path.substring(0, path.length() - 4);
                            }
                            path = path + ".frm.enyk";
                        }
                        if (outFile.getParentFile() != null) {
                            outFile.getParentFile().mkdirs();
                        }

                        EnykInnerSaver saver = new EnykInnerSaver(bm, true);
                        // FONTOS: a silent (z=true) overloadot hasznaljuk, ami Result-ot ad.
                        // A save(path,-1) overload z=false-szal hiv, ami headless modban
                        // GUI dialogot (MainFrame.thisinstance=null) probal nyitni es elbukik.
                        hu.piller.enykp.util.base.Result res = saver.save(path, -1, true);

                        result.put("format", "enyk");
                        if (res != null && res.isOk()) {
                            File saved = null;
                            if (res.errorList != null && !res.errorList.isEmpty()
                                && res.errorList.get(0) instanceof File f) {
                                saved = f;
                            }
                            if (saved == null) saved = new File(path);
                            result.put("success", true);
                            result.put("path", saved.getAbsolutePath());
                            result.put("fileSize", saved.length());
                        } else {
                            result.put("success", false);
                            result.put("path", path);
                            List<String> errs = new ArrayList<>();
                            if (res != null && res.errorList != null) {
                                for (Object o : res.errorList) {
                                    if (!(o instanceof File)) errs.add(String.valueOf(o));
                                }
                            }
                            result.put("errors", errs);
                            result.put("message", "A belso mentes (EnykInnerSaver) elutasitotta a nyomtatvanyt.");
                        }
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
