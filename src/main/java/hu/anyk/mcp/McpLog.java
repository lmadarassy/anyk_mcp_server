package hu.anyk.mcp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Strukturalt MCP-akcionaplo - kulon az ANYK zajtol (amit a AnykMcpServer a
 * java.io.tmpdir/anyk-mcp.log-ba iranyit). Ez a naplo azt rogziti, mit csinal
 * MAGA az MCP szerver: tool-hivasok, mezomuveletek, dokumentum-valtasok.
 *
 * Helye: -Danyk.mcp.log=... > java.io.tmpdir/anyk-mcp-actions.log
 * Kikapcsolas: -Danyk.mcp.log=off
 */
public final class McpLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static PrintStream out;
    private static boolean enabled = true;

    private McpLog() {}

    public static synchronized void init() {
        String path = System.getProperty("anyk.mcp.log");
        if ("off".equalsIgnoreCase(path)) { enabled = false; return; }
        if (path == null || path.isBlank()) {
            String tmp = System.getProperty("java.io.tmpdir", ".");
            path = tmp + File.separator + "anyk-mcp-actions.log";
        }
        try {
            OutputStream fos = new FileOutputStream(path, true);
            out = new PrintStream(new BufferedOutputStream(fos, 16 * 1024), true, "UTF-8");
            line("=== MCP action log started, path=" + path + " ===");
        } catch (Exception e) {
            enabled = false;
        }
    }

    public static synchronized void line(String msg) {
        if (!enabled || out == null) return;
        out.println(LocalDateTime.now().format(TS) + " " + msg);
    }

    /** Tool-hivas belepese. */
    public static void tool(String toolName, String args) {
        line("TOOL " + toolName + " " + (args == null ? "" : args));
    }

    /** Mezomuvelet eredmenye. */
    public static void set(String tool, String docType, int idx, String fid, String value, boolean ok) {
        String v = value == null ? "null" : (value.length() > 40 ? value.substring(0, 40) + "..." : value);
        line(String.format("  SET [%s] doc=%s(idx=%d) fid=%s value='%s' -> %s",
            tool, docType, idx, fid, v, ok ? "OK" : "REJECTED(fid not in doc)"));
    }

    public static void note(String msg) {
        line("  " + msg);
    }
}
