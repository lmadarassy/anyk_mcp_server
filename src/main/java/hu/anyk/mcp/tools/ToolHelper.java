package hu.anyk.mcp.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.server.McpSyncServerExchange;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

public class ToolHelper {

    /**
     * Globalis szerializalo zar. Az ANYK osztalyok (Calculator, MetaInfo,
     * CalculatorManager, PropertyList - mind JVM-szintu statikus singleton kozos
     * mutalhato allapottal) egyszalu (Swing) hasznalatra keszultek. Az MCP SDK
     * viszont parhuzamos reaktor-szalakon futtathatja a tool-hivasokat, ami az
     * egyidejuleg futo hivasoknal allapotromlast/befagyast okoz.
     *
     * Ezert MINDEN tool-hivast ezzel a zarral szerializalunk: egyszerre csak egy
     * fut, tukrozve az ANYK egyszalu modelljet. Fair zar, hogy ne legyen kieheztetes.
     */
    public static final ReentrantLock ANYK_LOCK = new ReentrantLock(true);

    public static Tool.Builder tool(String name, String description, String inputSchema) {
        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(McpJsonDefaults.getMapper(), inputSchema);
    }

    /**
     * Becsomagol egy tool-handlert a globalis ANYK zarral, igy egyszerre csak
     * egy tool-hivas fut. Minden callHandler-t ezen kell atvezetni.
     */
    public static BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> locked(
            BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        return (exchange, request) -> {
            ANYK_LOCK.lock();
            try {
                return handler.apply(exchange, request);
            } finally {
                ANYK_LOCK.unlock();
            }
        };
    }
}
