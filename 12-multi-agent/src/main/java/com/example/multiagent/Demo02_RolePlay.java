package com.example.multiagent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;

/**
 * Demo02: 角色扮演
 * 定义多个专家角色，模拟团队协作
 *
 * 团队：研究员 → 写手 → 编辑
 */
public class Demo02_RolePlay {

    private static OpenAiChatModel model;

    // ========== 定义角色 ==========

    static final String RESEARCHER_PROMPT = """
            你是一位资深研究分析师。

            职责：
            1. 深入分析给定主题
            2. 提取 3-5 个核心要点
            3. 为每个要点提供简短说明

            输出格式：编号列表
            风格：客观、严谨、有数据支撑（可以是合理推测）
            """;

    static final String WRITER_PROMPT = """
            你是一位专业内容创作者。

            职责：
            1. 根据研究要点撰写完整文章
            2. 开头要吸引人
            3. 结构清晰：引言 → 主体 → 结论

            字数：300-400字
            风格：专业但易读，适合大众阅读
            """;

    static final String EDITOR_PROMPT = """
            你是一位资深编辑。

            职责：
            1. 检查文章逻辑和表达
            2. 优化句子结构
            3. 确保观点连贯
            4. 简要说明你做了哪些修改

            要求：保持原意，提升可读性
            """;

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo02: 角色扮演（多专家协作）");
        System.out.println("=".repeat(60));

        model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        String topic = "人工智能对程序员职业的影响";
        System.out.println("\n任务主题: " + topic);
        System.out.println("协作流程: 研究员 → 写手 → 编辑\n");

        // ========== 研究员 ==========
        System.out.println("-".repeat(50));
        System.out.println("👨‍🔬【研究员】正在分析主题...");
        System.out.println("-".repeat(50));

        String research = callAgent(RESEARCHER_PROMPT,
                "请分析以下主题：" + topic);
        System.out.println(research);

        // ========== 写手 ==========
        System.out.println("\n" + "-".repeat(50));
        System.out.println("✍️【写手】正在撰写文章...");
        System.out.println("-".repeat(50));

        String article = callAgent(WRITER_PROMPT,
                "根据以下研究要点撰写文章：\n\n" + research);
        System.out.println(article);

        // ========== 编辑 ==========
        System.out.println("\n" + "-".repeat(50));
        System.out.println("📝【编辑】正在润色审核...");
        System.out.println("-".repeat(50));

        String finalArticle = callAgent(EDITOR_PROMPT,
                "请编辑以下文章：\n\n" + article);
        System.out.println(finalArticle);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("多专家协作完成！");
        System.out.println("=".repeat(60));
    }

    /**
     * 调用指定角色的 Agent
     */
    private static String callAgent(String systemPrompt, String userMessage) {
        Response<AiMessage> response = model.generate(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMessage)
        );
        return response.content().text();
    }
}
