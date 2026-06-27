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
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【什么是嵌入模型 (Embedding Model)?】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * 嵌入模型将文本转换为向量 (浮点数数组)，使计算机能够理解语义相似性。
 *
 *   "如何请假"  ──→ EmbeddingModel ──→ [0.12, -0.34, 0.56, ..., 0.78]  (1536维)
 *   "怎么休假"  ──→ EmbeddingModel ──→ [0.11, -0.35, 0.55, ..., 0.77]  (语义相似，向量接近)
 *   "今天天气"  ──→ EmbeddingModel ──→ [0.89, 0.23, -0.45, ..., -0.12] (语义不同，向量远离)
 *
 * 向量相似度计算:
 * - 余弦相似度 (Cosine Similarity): 最常用，计算两向量夹角的余弦值
 * - 欧几里得距离 (Euclidean Distance): 计算两点间直线距离
 * - 内积 (Dot Product): 计算两向量的点积
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【支持的模型】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * | 提供商      | 模型名称                    | 维度   | 特点                    |
 * |------------|----------------------------|--------|------------------------|
 * | OpenAI     | text-embedding-3-small     | 1536   | 性价比高，推荐首选        |
 * | OpenAI     | text-embedding-3-large     | 3072   | 精度更高，成本也更高      |
 * | OpenAI     | text-embedding-ada-002     | 1536   | 旧版模型，已不推荐        |
 * | Azure      | text-embedding-3-small     | 1536   | 企业合规，数据不出境      |
 * | 本地       | bge-small-zh               | 512    | 中文优化，可离线使用      |
 * | 本地       | all-MiniLM-L6-v2           | 384    | 多语言，轻量级            |
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【配置示例】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * application.yml:
 * ```yaml
 * knowledge:
 *   embedding:
 *     provider: openai                        # 提供商: openai, azure, local
 *     openai:
 *       api-key: ${OPENAI_API_KEY}           # API 密钥
 *       base-url: https://api.openai.com/v1  # API 地址 (可选，用于代理)
 *       model: text-embedding-3-small        # 模型名称
 * ```
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【向量维度的重要性】
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ⚠️ 向量维度必须全局一致!
 *
 * 涉及维度的配置点:
 * 1. EmbeddingModel 输出维度 (由模型决定)
 * 2. VectorStore 索引维度 (必须与模型匹配)
 * 3. 查询向量维度 (必须与存储一致)
 *
 * 如果维度不匹配，会出现:
 * - 存储失败: "dimension mismatch"
 * - 搜索无结果: 向量无法比较
 *
 * @see <a href="https://platform.openai.com/docs/guides/embeddings">OpenAI Embeddings Guide</a>
 */
@Slf4j
/*
 * @Configuration - Spring 配置类注解
 *
 * 作用:
 * 1. 标记此类为配置类，等价于 XML 配置文件
 * 2. 类中的 @Bean 方法返回的对象会被注册到 Spring 容器
 * 3. 配置类本身也是一个 Bean，会被 CGLIB 代理
 *
 * 与 @Component 的区别:
 * - @Component: 普通组件，@Bean 方法每次调用都创建新实例
 * - @Configuration: 配置类，@Bean 方法被代理，保证单例
 *
 * 示例:
 * ```java
 * @Configuration
 * public class AppConfig {
 *     @Bean
 *     public ServiceA serviceA() {
 *         return new ServiceA(serviceB()); // 调用 serviceB()
 *     }
 *
 *     @Bean
 *     public ServiceB serviceB() {
 *         return new ServiceB(); // 只会创建一个实例
 *     }
 * }
 * ```
 */
@Configuration
public class EmbeddingModelConfig {

    // ═══════════════════════════════════════════════════════════════════════════
    // 配置属性
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 嵌入模型提供商
     *
     * 可选值:
     * - openai: 使用 OpenAI API
     * - azure: 使用 Azure OpenAI (需要额外配置)
     * - local: 使用本地模型 (需要额外实现)
     *
     * @Value 注解说明:
     * - ${property:default}: 从配置文件读取，冒号后是默认值
     * - 如果配置文件没有此属性，使用默认值 "openai"
     * - 支持 SpEL 表达式: #{systemProperties['user.name']}
     */
    @Value("${knowledge.embedding.provider:openai}")
    private String provider;

    /**
     * OpenAI API 密钥
     *
     * 获取方式:
     * 1. 访问 https://platform.openai.com/api-keys
     * 2. 创建新的 API Key
     * 3. 复制并安全保存 (只显示一次)
     *
     * 配置方式 (按优先级):
     * 1. 配置文件: knowledge.embedding.openai.api-key=sk-xxx
     * 2. 环境变量: OPENAI_API_KEY=sk-xxx
     *
     * 安全提示:
     * - 不要将 API Key 提交到代码仓库
     * - 生产环境使用环境变量或密钥管理服务
     * - 定期轮换 API Key
     */
    @Value("${knowledge.embedding.openai.api-key:}")
    private String openaiApiKey;

    /**
     * OpenAI API 基础 URL
     *
     * 默认: https://api.openai.com/v1
     *
     * 使用场景:
     * 1. 使用代理服务 (如国内无法直连 OpenAI)
     * 2. 使用兼容 OpenAI 接口的第三方服务
     * 3. 企业私有部署
     *
     * 示例:
     * - 官方: (空，使用默认)
     * - 代理: https://api.openai-proxy.com/v1
     * - 私有: https://your-company.com/openai/v1
     */
    @Value("${knowledge.embedding.openai.base-url:}")
    private String openaiBaseUrl;

    /**
     * OpenAI 嵌入模型名称
     *
     * 可选模型:
     *
     * | 模型名称                  | 维度   | 价格 (每 1M tokens) | 说明           |
     * |--------------------------|--------|-------------------|----------------|
     * | text-embedding-3-small   | 1536   | $0.02             | 推荐，性价比高   |
     * | text-embedding-3-large   | 3072   | $0.13             | 精度更高        |
     * | text-embedding-ada-002   | 1536   | $0.10             | 旧版，已不推荐   |
     *
     * 注意:
     * - 选择模型后，vector-store.dimension 必须匹配
     * - 更换模型需要重建向量索引
     */
    @Value("${knowledge.embedding.openai.model:text-embedding-3-small}")
    private String openaiModel;

    // ═══════════════════════════════════════════════════════════════════════════
    // Bean 定义
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * 创建嵌入模型 Bean
     *
     * @return EmbeddingModel 实例
     *
     * @Bean 注解说明:
     * - 方法返回值会被注册到 Spring 容器
     * - 方法名 "embeddingModel" 就是 Bean 的名称
     * - 默认作用域是 singleton (单例)
     * - 可以通过 @Autowired 或构造器注入使用
     *
     * 使用示例:
     * ```java
     * @Service
     * public class MyService {
     *     private final EmbeddingModel embeddingModel;
     *
     *     public MyService(EmbeddingModel embeddingModel) {
     *         this.embeddingModel = embeddingModel;
     *     }
     *
     *     public void process(String text) {
     *         Embedding embedding = embeddingModel.embed(text).content();
     *         // 使用 embedding...
     *     }
     * }
     * ```
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化嵌入模型, provider={}, model={}", provider, openaiModel);

        if ("openai".equalsIgnoreCase(provider)) {
            return createOpenAiEmbeddingModel();
        }

        // ═══════════════════════════════════════════════════════════════════════
        // 扩展点: 其他提供商
        // ═══════════════════════════════════════════════════════════════════════

        // Azure OpenAI 示例:
        // if ("azure".equalsIgnoreCase(provider)) {
        //     return AzureOpenAiEmbeddingModel.builder()
        //             .apiKey(azureApiKey)
        //             .deploymentName(azureDeploymentName)
        //             .endpoint(azureEndpoint)
        //             .build();
        // }

        // 本地模型示例 (使用 Ollama):
        // if ("local".equalsIgnoreCase(provider)) {
        //     return OllamaEmbeddingModel.builder()
        //             .baseUrl("http://localhost:11434")
        //             .modelName("bge-small-zh")
        //             .build();
        // }

        throw new IllegalArgumentException("不支持的嵌入模型提供商: " + provider);
    }

    /**
     * 创建 OpenAI 嵌入模型
     *
     * @return OpenAiEmbeddingModel 实例
     */
    private EmbeddingModel createOpenAiEmbeddingModel() {
        // ═══════════════════════════════════════════════════════════════════════
        // API Key 获取策略 (优先级从高到低)
        // ═══════════════════════════════════════════════════════════════════════
        // 1. 配置文件: knowledge.embedding.openai.api-key
        // 2. 环境变量: OPENAI_API_KEY
        //
        // 为什么支持环境变量:
        // - 配置文件可能被提交到代码仓库
        // - 环境变量更安全，不会泄露
        // - 容器化部署常用环境变量注入
        String apiKey = openaiApiKey;
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }

        // Base URL 同样支持环境变量
        String baseUrl = openaiBaseUrl;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = System.getenv("OPENAI_BASE_URL");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "未配置 OpenAI API Key，请设置 knowledge.embedding.openai.api-key 或环境变量 OPENAI_API_KEY");
        }

        /*
         * OpenAiEmbeddingModel.builder() 参数说明:
         *
         * 必需参数:
         * ─────────────────────────────────────────────────────────────────────
         * apiKey(String)
         *   - OpenAI API 密钥
         *   - 格式: sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
         *
         * modelName(String)
         *   - 模型名称
         *   - 可选: text-embedding-3-small, text-embedding-3-large, text-embedding-ada-002
         *
         * 可选参数:
         * ─────────────────────────────────────────────────────────────────────
         * baseUrl(String)
         *   - API 基础 URL
         *   - 默认: https://api.openai.com/v1
         *   - 用于代理或私有部署
         *
         * organizationId(String)
         *   - OpenAI 组织 ID
         *   - 用于多组织账户区分计费
         *
         * timeout(Duration)
         *   - HTTP 请求超时时间
         *   - 默认: 60秒
         *   - 建议: 网络不稳定时适当增大
         *
         * maxRetries(int)
         *   - 失败重试次数
         *   - 默认: 3
         *   - 遇到网络错误或 429 (限流) 时自动重试
         *
         * logRequests(boolean)
         *   - 是否记录请求日志
         *   - 调试时可开启，生产环境建议关闭
         *
         * logResponses(boolean)
         *   - 是否记录响应日志
         *   - 调试时可开启，生产环境建议关闭
         *
         * dimensions(int)
         *   - 输出向量维度 (仅 text-embedding-3-* 支持)
         *   - 可以设置比默认维度小的值以节省存储
         *   - 例如: text-embedding-3-small 默认 1536，可设为 512
         */
        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(openaiModel);

        // 如果配置了自定义 Base URL，则使用
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        log.info("OpenAI 嵌入模型初始化完成, model={}", openaiModel);
        return builder.build();
    }
}
