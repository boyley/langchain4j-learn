# 13 - MCP 协议 (Model Context Protocol)

MCP 是 Anthropic 提出的开放协议，让 AI 模型能够安全地与外部工具和数据源交互。

## 什么是 MCP？

```
┌─────────────────────────────────────────────────────────────┐
│                        MCP 架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────┐     MCP 协议      ┌──────────────────┐       │
│   │  AI 模型  │ ←──────────────→ │   MCP Server     │       │
│   │ (Client)  │                  │  (工具提供方)      │       │
│   └──────────┘                   └──────────────────┘       │
│        │                                  │                 │
│        │                                  │                 │
│   用户的问题                          连接各种工具：           │
│   "帮我查天气"                        - 文件系统              │
│                                       - 数据库               │
│                                       - API                 │
│                                       - 浏览器               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## MCP vs 普通 Function Calling

| 特性 | Function Calling | MCP |
|------|-----------------|-----|
| 工具定义 | 硬编码在应用中 | 由 Server 动态提供 |
| 可扩展性 | 需要修改代码 | 添加 Server 即可 |
| 标准化 | 各厂商不同 | 统一协议 |
| 安全性 | 自己实现 | 协议内置 |

## MCP 的三大核心能力

### 1. Tools（工具）
Server 提供可调用的功能

```json
{
  "name": "get_weather",
  "description": "获取天气信息",
  "parameters": {
    "city": "城市名称"
  }
}
```

### 2. Resources（资源）
Server 提供可读取的数据

```json
{
  "uri": "file:///path/to/document.txt",
  "name": "项目文档",
  "mimeType": "text/plain"
}
```

### 3. Prompts（提示模板）
Server 提供预定义的提示

```json
{
  "name": "code_review",
  "description": "代码审查提示模板",
  "arguments": ["language", "code"]
}
```

## 示例文件

| 文件 | 说明 |
|------|------|
| Demo01_WhatIsMcp.java | MCP 概念介绍 |
| Demo02_SimpleMcpServer.java | 简单的 MCP Server 实现 |
| Demo03_McpClient.java | MCP Client 使用 |
| Demo04_McpWithLangchain4j.java | langchain4j 集成 MCP |

## 常用 MCP Server

| Server | 功能 |
|--------|------|
| @modelcontextprotocol/server-filesystem | 文件系统操作 |
| @modelcontextprotocol/server-github | GitHub 操作 |
| @modelcontextprotocol/server-postgres | PostgreSQL 查询 |
| @modelcontextprotocol/server-puppeteer | 浏览器自动化 |

## 参考资源

- [MCP 官方文档](https://modelcontextprotocol.io/)
- [MCP GitHub](https://github.com/modelcontextprotocol)
- [langchain4j MCP 支持](https://docs.langchain4j.dev/integrations/mcp)
