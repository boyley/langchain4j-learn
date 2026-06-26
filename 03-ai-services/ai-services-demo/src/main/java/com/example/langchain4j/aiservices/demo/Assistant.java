package com.example.langchain4j.aiservices.demo;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface Assistant {
    String chat(String userMessage);

    @SystemMessage("你是一个友好的助手，回答要简洁明了。")
    String chatWithSystemPrompt(String userMessage);

    @UserMessage("请将以下文本翻译成{{language}}: {{text}}")
    String translate(@V("text") String text, @V("language") String language);
}
