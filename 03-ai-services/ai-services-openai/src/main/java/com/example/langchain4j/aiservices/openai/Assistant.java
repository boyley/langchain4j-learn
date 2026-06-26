package com.example.langchain4j.aiservices.openai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface Assistant {
    String chat(String userMessage);

    @SystemMessage("你是一个友好的助手，回答要简洁明了。")
    String chatWithSystemPrompt(String userMessage);

    @UserMessage("请将以下文本翻译成{{language}}: {{text}}")
    String translate(String text, String language);
}
