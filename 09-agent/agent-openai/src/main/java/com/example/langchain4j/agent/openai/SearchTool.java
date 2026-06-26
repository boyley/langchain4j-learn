package com.example.langchain4j.agent.openai;

import dev.langchain4j.agent.tool.Tool;

public class SearchTool {

    @Tool("搜索互联网获取信息。输入搜索关键词，返回搜索结果。")
    public String search(String query) {
        System.out.println("[Agent 调用工具] search(\"" + query + "\")");
        // 模拟搜索结果
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains("langchain4j")) {
            return "LangChain4j 是 Java 版的 LangChain，最新版本 0.35.0，支持多种 LLM。";
        } else if (lowerQuery.contains("java") && lowerQuery.contains("版本")) {
            return "Java 最新 LTS 版本是 Java 21，发布于 2023 年 9 月。Java 17 是上一个 LTS。";
        } else if (lowerQuery.contains("天气")) {
            return "今日北京天气：晴，25-32°C，空气质量良好。";
        } else {
            return "搜索结果：关于 \"" + query + "\" 的信息较少，建议换个关键词。";
        }
    }

    @Tool("获取当前日期时间")
    public String getCurrentDateTime() {
        System.out.println("[Agent 调用工具] getCurrentDateTime()");
        return java.time.LocalDateTime.now().toString();
    }
}
