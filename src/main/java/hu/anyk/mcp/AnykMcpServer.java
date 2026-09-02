package hu.anyk.mcp;

import hu.anyk.mcp.config.AnykConfig;
import hu.anyk.mcp.session.SessionManager;
import hu.anyk.mcp.adapter.TaxpayerStore;
import hu.anyk.mcp.tools.*;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.json.McpJsonDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnykMcpServer {

    private static final Logger log = LoggerFactory.getLogger(AnykMcpServer.class);

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        // FONTOS: az MCP stdio transport tiszta stdout-ot igenyel a JSON-RPC-hez.
        // Az ANYK osztalyok viszont sokat irnak a System.out-ra (fnBetoltErtek, stb.).
        // Ezert elmentjuk a valodi stdout-ot az MCP-nek, es a System.out-ot stderr-re
        // iranyitjuk, hogy az ANYK zaj ne rontsa el a protokollt.
        java.io.PrintStream realStdout = System.out;
        System.setOut(System.err);

        // ANYK telepitesi konyvtar feloldasa - sorrend:
        // 1. -Danyk.home rendszervaltozo  2. ANYK_HOME env  3. ANYK_ROOT env  4. elso argumentum
        String anykRoot = firstNonEmpty(
            System.getProperty("anyk.home"),
            System.getenv("ANYK_HOME"),
            System.getenv("ANYK_ROOT"),
            args.length > 0 ? args[0] : null
        );
        if (anykRoot == null) {
            System.err.println(
                "Az ANYK telepitesi konyvtar nincs megadva. Add meg az alabbiak egyikevel:\n" +
                "  -Danyk.home=/eleresi/ut/az/abevjava\n" +
                "  ANYK_HOME=/eleresi/ut/az/abevjava\n" +
                "  vagy elso argumentumkent.\n" +
                "  (A konyvtar tartalmazza az abevjava.jar-t es az eroforrasok/-t.)");
            System.exit(1);
        }

        AnykConfig config = new AnykConfig(anykRoot);
        config.initialize();

        SessionManager sessionManager = new SessionManager(config);
        TaxpayerStore taxpayerStore = new TaxpayerStore(anykRoot);

        // A valodi stdout-ot adjuk az MCP transportnak (nem a stderr-re iranyitottat)
        StdioServerTransportProvider transport = new StdioServerTransportProvider(
            McpJsonDefaults.getMapper(), System.in, realStdout);

        McpSyncServer server = McpServer.sync(transport)
            .serverInfo("anyk-mcp-server", "0.1.0")
            .capabilities(ServerCapabilities.builder()
                .tools(true)
                .resources(false, false)
                .build())
            .build();

        TemplateTools.register(server, config);
        FormTools.register(server, sessionManager);
        FieldTools.register(server, sessionManager);
        ValidationTools.register(server, sessionManager);
        SaveTools.register(server, sessionManager);
        HelpTools.register(server, sessionManager, config);
        TaxpayerTools.register(server, taxpayerStore, sessionManager);
        FormAnalyzerTool.register(server, sessionManager, config);

        log.info("ANYK MCP Server started, root: {}", anykRoot);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sessionManager.closeAll();
            server.close();
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
