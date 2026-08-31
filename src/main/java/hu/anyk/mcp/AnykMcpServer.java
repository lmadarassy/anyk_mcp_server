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

        String anykRoot = System.getenv("ANYK_ROOT");
        if (anykRoot == null || anykRoot.isEmpty()) {
            if (args.length > 0) {
                anykRoot = args[0];
            } else {
                System.err.println("ANYK_ROOT environment variable or first argument must be set to ANYK installation directory");
                System.exit(1);
            }
        }

        AnykConfig config = new AnykConfig(anykRoot);
        config.initialize();

        SessionManager sessionManager = new SessionManager(config);
        TaxpayerStore taxpayerStore = new TaxpayerStore(anykRoot);

        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

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
}
