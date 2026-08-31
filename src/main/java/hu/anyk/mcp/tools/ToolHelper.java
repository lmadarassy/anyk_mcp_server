package hu.anyk.mcp.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.Tool;

public class ToolHelper {

    public static Tool.Builder tool(String name, String description, String inputSchema) {
        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(McpJsonDefaults.getMapper(), inputSchema);
    }
}
