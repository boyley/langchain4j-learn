package com.example.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Demo02: 简单的 MCP Server 实现
 *
 * 这个示例展示 MCP 协议的核心数据结构
 * 实际生产中应该使用官方 SDK
 */
public class Demo02_SimpleMcpServer {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo02: MCP Server 核心结构演示");
        System.out.println("=".repeat(60));

        McpServer server = new McpServer();

        // 1. 列出可用工具
        System.out.println("\n【1. 列出可用工具 (tools/list)】");
        System.out.println(gson.toJson(server.listTools()));

        // 2. 调用工具
        System.out.println("\n【2. 调用工具 (tools/call)】");

        // 调用获取时间
        Map<String, Object> timeParams = new HashMap<>();
        timeParams.put("timezone", "Asia/Shanghai");
        System.out.println("调用 get_current_time:");
        System.out.println(gson.toJson(server.callTool("get_current_time", timeParams)));

        // 调用计算器
        Map<String, Object> calcParams = new HashMap<>();
        calcParams.put("operation", "add");
        calcParams.put("a", 10);
        calcParams.put("b", 20);
        System.out.println("\n调用 calculator:");
        System.out.println(gson.toJson(server.callTool("calculator", calcParams)));

        // 3. 列出资源
        System.out.println("\n【3. 列出资源 (resources/list)】");
        System.out.println(gson.toJson(server.listResources()));

        // 4. 读取资源
        System.out.println("\n【4. 读取资源 (resources/read)】");
        System.out.println(gson.toJson(server.readResource("config://app/settings")));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("MCP Server 结构演示完成！");
        System.out.println("=".repeat(60));
    }

    /**
     * 简化的 MCP Server 实现
     */
    static class McpServer {

        /**
         * 列出所有可用工具
         * 对应 MCP 协议的 tools/list
         */
        public Map<String, Object> listTools() {
            List<Map<String, Object>> tools = new ArrayList<>();

            // 工具1: 获取当前时间
            tools.add(Map.of(
                    "name", "get_current_time",
                    "description", "获取当前时间",
                    "inputSchema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "timezone", Map.of(
                                            "type", "string",
                                            "description", "时区，如 Asia/Shanghai"
                                    )
                            )
                    )
            ));

            // 工具2: 计算器
            tools.add(Map.of(
                    "name", "calculator",
                    "description", "简单计算器，支持加减乘除",
                    "inputSchema", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "operation", Map.of(
                                            "type", "string",
                                            "enum", List.of("add", "subtract", "multiply", "divide"),
                                            "description", "运算类型"
                                    ),
                                    "a", Map.of("type", "number", "description", "第一个数"),
                                    "b", Map.of("type", "number", "description", "第二个数")
                            ),
                            "required", List.of("operation", "a", "b")
                    )
            ));

            return Map.of("tools", tools);
        }

        /**
         * 调用工具
         * 对应 MCP 协议的 tools/call
         */
        public Map<String, Object> callTool(String name, Map<String, Object> arguments) {
            try {
                Object result = switch (name) {
                    case "get_current_time" -> {
                        String timezone = (String) arguments.getOrDefault("timezone", "UTC");
                        yield LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                + " (" + timezone + ")";
                    }
                    case "calculator" -> {
                        String op = (String) arguments.get("operation");
                        double a = ((Number) arguments.get("a")).doubleValue();
                        double b = ((Number) arguments.get("b")).doubleValue();
                        yield switch (op) {
                            case "add" -> a + b;
                            case "subtract" -> a - b;
                            case "multiply" -> a * b;
                            case "divide" -> b != 0 ? a / b : "Error: Division by zero";
                            default -> "Unknown operation";
                        };
                    }
                    default -> throw new IllegalArgumentException("Unknown tool: " + name);
                };

                return Map.of(
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", String.valueOf(result)
                        ))
                );
            } catch (Exception e) {
                return Map.of(
                        "isError", true,
                        "content", List.of(Map.of(
                                "type", "text",
                                "text", "Error: " + e.getMessage()
                        ))
                );
            }
        }

        /**
         * 列出资源
         * 对应 MCP 协议的 resources/list
         */
        public Map<String, Object> listResources() {
            List<Map<String, Object>> resources = new ArrayList<>();

            resources.add(Map.of(
                    "uri", "config://app/settings",
                    "name", "应用配置",
                    "description", "应用程序配置信息",
                    "mimeType", "application/json"
            ));

            resources.add(Map.of(
                    "uri", "file://docs/readme.md",
                    "name", "项目文档",
                    "description", "项目说明文档",
                    "mimeType", "text/markdown"
            ));

            return Map.of("resources", resources);
        }

        /**
         * 读取资源
         * 对应 MCP 协议的 resources/read
         */
        public Map<String, Object> readResource(String uri) {
            String content = switch (uri) {
                case "config://app/settings" -> """
                        {
                          "appName": "MCP Demo",
                          "version": "1.0.0",
                          "debug": true
                        }
                        """;
                case "file://docs/readme.md" -> """
                        # MCP Demo Project

                        这是一个 MCP 协议演示项目。

                        ## 功能
                        - 工具调用
                        - 资源读取
                        """;
                default -> "Resource not found: " + uri;
            };

            return Map.of(
                    "contents", List.of(Map.of(
                            "uri", uri,
                            "mimeType", "text/plain",
                            "text", content
                    ))
            );
        }
    }
}
