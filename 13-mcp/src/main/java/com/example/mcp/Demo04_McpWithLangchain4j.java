package com.example.mcp;

/**
 * Demo04: langchain4j 集成 MCP
 *
 * langchain4j 0.35+ 支持 MCP 协议
 * 这个 Demo 展示如何连接 MCP Server
 */
public class Demo04_McpWithLangchain4j {

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo04: langchain4j 集成 MCP");
        System.out.println("=".repeat(60));

        System.out.println("""

                【langchain4j MCP 集成】

                langchain4j 从 0.35 版本开始支持 MCP 协议。

                ═══════════════════════════════════════════════════════════════

                【前置准备】

                1. 安装 Node.js（MCP Server 通常用 Node.js 运行）

                2. 安装 MCP Server，例如文件系统 Server：
                   npm install -g @modelcontextprotocol/server-filesystem

                3. 启动 MCP Server：
                   npx @modelcontextprotocol/server-filesystem /path/to/directory

                ═══════════════════════════════════════════════════════════════

                【代码示例】

                ```java
                import dev.langchain4j.mcp.McpClient;
                import dev.langchain4j.mcp.McpToolProvider;
                import dev.langchain4j.mcp.transport.stdio.StdioMcpTransport;

                // 1. 创建 MCP 传输层（连接到 MCP Server）
                StdioMcpTransport transport = StdioMcpTransport.builder()
                    .command("npx")
                    .arguments("@modelcontextprotocol/server-filesystem", "/tmp")
                    .build();

                // 2. 创建 MCP 客户端
                McpClient mcpClient = McpClient.builder()
                    .transport(transport)
                    .build();

                // 3. 初始化连接
                mcpClient.initialize();

                // 4. 获取工具提供者
                McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClient(mcpClient)
                    .build();

                // 5. 在 AI Service 中使用
                Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(model)
                    .toolProvider(toolProvider)
                    .build();

                // 6. 现在 AI 可以使用 MCP Server 提供的工具了
                String response = assistant.chat("列出 /tmp 目录下的文件");
                ```

                ═══════════════════════════════════════════════════════════════

                【常用 MCP Server】

                1. 文件系统
                   npm install -g @modelcontextprotocol/server-filesystem
                   功能：读写文件、列出目录、搜索文件

                2. GitHub
                   npm install -g @modelcontextprotocol/server-github
                   功能：查看仓库、Issue、PR 等

                3. PostgreSQL
                   npm install -g @modelcontextprotocol/server-postgres
                   功能：执行 SQL 查询

                4. Puppeteer（浏览器）
                   npm install -g @modelcontextprotocol/server-puppeteer
                   功能：网页截图、页面操作

                ═══════════════════════════════════════════════════════════════

                【配置文件方式】

                可以在 Claude Desktop 的配置文件中配置多个 MCP Server：

                macOS: ~/Library/Application Support/Claude/claude_desktop_config.json
                Windows: %APPDATA%\\Claude\\claude_desktop_config.json

                ```json
                {
                  "mcpServers": {
                    "filesystem": {
                      "command": "npx",
                      "args": [
                        "@modelcontextprotocol/server-filesystem",
                        "/Users/yourname/Documents"
                      ]
                    },
                    "github": {
                      "command": "npx",
                      "args": ["@modelcontextprotocol/server-github"],
                      "env": {
                        "GITHUB_TOKEN": "your-github-token"
                      }
                    }
                  }
                }
                ```

                ═══════════════════════════════════════════════════════════════

                【Java 实现 MCP Server】

                如果你想用 Java 实现自己的 MCP Server：

                ```java
                // 使用 Spring WebFlux 实现 MCP Server
                @RestController
                @RequestMapping("/mcp")
                public class MyMcpServer {

                    @PostMapping("/tools/list")
                    public Mono<ToolsListResponse> listTools() {
                        // 返回可用工具列表
                    }

                    @PostMapping("/tools/call")
                    public Mono<ToolCallResponse> callTool(@RequestBody ToolCallRequest request) {
                        // 执行工具调用
                    }
                }
                ```

                ═══════════════════════════════════════════════════════════════

                【学习资源】

                - MCP 官方文档: https://modelcontextprotocol.io/
                - MCP 规范: https://spec.modelcontextprotocol.io/
                - langchain4j MCP: https://docs.langchain4j.dev/integrations/mcp
                - MCP Servers: https://github.com/modelcontextprotocol/servers

                ═══════════════════════════════════════════════════════════════
                """);
    }
}
