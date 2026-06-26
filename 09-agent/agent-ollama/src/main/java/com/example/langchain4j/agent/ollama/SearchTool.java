package com.example.langchain4j.agent.ollama;

import dev.langchain4j.agent.tool.Tool;

public class SearchTool {

    @Tool("搜索互联网获取信息。输入搜索关键词，返回搜索结果。")
    public String search(String query) {
        System.out.println("[Agent 调用工具] search(\"" + query + "\")");
        // 模拟搜索结果
        return switch (query.toLowerCase()) {
            case String q when q.contains("langchain4j") ->
                "LangChain4j 是 Java 版的 LangChain，最新版本 0.35.0，支持多种 LLM。";
            case String q when q.contains("java") && q.contains("版本") ->
                "Java 最新 LTS 版本是 Java 21，发布于 2023 年 9 月。Java 17 是上一个 LTS。";
            case String q when q.contains("天气") ->
                "今日北京天气：晴，25-32°C，空气质量良好。";
            default -> "搜索结果：关于 \"" + query + "\" 的信息较少，建议换个关键词。";
        };
    }

    @Tool("获取当前日期时间")
    public String getCurrentDateTime() {
        System.out.println("[Agent 调用工具] getCurrentDateTime()");
        return java.time.LocalDateTime.now().toString();
    }
}
