package com.example.langchain4j.aiservices.bailian;

import dev.langchain4j.service.UserMessage;

public interface SentimentAnalyzer {
    enum Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

    @UserMessage("分析以下文本的情感，只返回 POSITIVE、NEGATIVE 或 NEUTRAL 之一: {{it}}")
    Sentiment analyzeSentiment(String text);

    @UserMessage("以下评论是正面的吗？只回答 true 或 false: {{it}}")
    boolean isPositive(String text);

    @UserMessage("以下文本中有多少个独立的观点？只返回数字: {{it}}")
    int countOpinions(String text);
}
