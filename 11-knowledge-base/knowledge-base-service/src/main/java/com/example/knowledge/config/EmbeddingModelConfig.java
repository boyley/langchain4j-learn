package com.example.knowledge.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入模型配置
 *
 * 支持的模型:
 * - OpenAI: text-embedding-3-small (1536维), text-embedding-3-large (3072维)
 * - Azure OpenAI
 * - 本地模型 (可扩展)
 */
@Slf4j
@Configuration
public class EmbeddingModelConfig {

    @Value("${knowledge.embedding.provider:openai}")
    private String provider;

    @Value("${knowledge.embedding.openai.api-key:}")
    private String openaiApiKey;

    @Value("${knowledge.embedding.openai.base-url:}")
    private String openaiBaseUrl;

    @Value("${knowledge.embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化嵌入模型, provider={}, model={}", provider, openaiModel);

        if ("openai".equalsIgnoreCase(provider)) {
            return createOpenAiEmbeddingModel();
        }

        // 可以扩展支持其他提供商
        // if ("azure".equalsIgnoreCase(provider)) {
        //     return createAzureEmbeddingModel();
        // }
        // if ("local".equalsIgnoreCase(provider)) {
        //     return createLocalEmbeddingModel();
        // }

        throw new IllegalArgumentException("不支持的嵌入模型提供商: " + provider);
    }

    private EmbeddingModel createOpenAiEmbeddingModel() {
        String apiKey = openaiApiKey;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }

        String baseUrl = openaiBaseUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getenv("OPENAI_BASE_URL");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("未配置 OpenAI API Key，请设置 knowledge.embedding.openai.api-key 或环境变量 OPENAI_API_KEY");
        }

        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(openaiModel);

        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        log.info("OpenAI 嵌入模型初始化完成, model={}", openaiModel);
        return builder.build();
    }
}
