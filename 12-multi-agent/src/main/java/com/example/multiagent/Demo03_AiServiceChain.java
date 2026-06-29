package com.example.multiagent;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Demo03: 使用 AI Service 接口实现 Multi-Agent
 *
 * 优点：
 * - 接口定义清晰，类型安全
 * - 注解配置，简洁优雅
 * - 易于测试和维护
 * - 支持 Spring 依赖注入
 */
public class Demo03_AiServiceChain {

    // ========== 定义 Agent 接口 ==========

    interface Researcher {
        @SystemMessage("""
                你是一位技术研究员。
                分析技术话题，列出 3-5 个关键要点。
                输出格式：编号列表，每点 1-2 句话。
                """)
        @UserMessage("分析主题：{{topic}}")
        String research(@V("topic") String topic);
    }

    interface Writer {
        @SystemMessage("""
                你是一位技术博主。
                根据要点写一篇通俗易懂的技术科普文章。
                字数：200-300字。
                """)
        @UserMessage("根据以下要点写文章：\n{{points}}")
        String write(@V("points") String points);
    }

    interface Translator {
        @SystemMessage("""
                你是一位专业翻译。
                将中文内容翻译成英文。
                保持专业术语的准确性。
                """)
        @UserMessage("翻译以下内容：\n{{content}}")
        String translate(@V("content") String content);
    }

    interface TitleGenerator {
        @SystemMessage("根据文章内容生成一个吸引人的标题，只输出标题，不要其他内容")
        @UserMessage("{{article}}")
        String generateTitle(@V("article") String article);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo03: AI Service 接口链式调用");
        System.out.println("=".repeat(60));

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 创建 Agent 实例
        Researcher researcher = AiServices.create(Researcher.class, model);
        Writer writer = AiServices.create(Writer.class, model);
        Translator translator = AiServices.create(Translator.class, model);
        TitleGenerator titleGen = AiServices.create(TitleGenerator.class, model);

        String topic = "Kubernetes 容器编排";
        System.out.println("\n主题: " + topic);
        System.out.println("流程: Researcher → Writer → TitleGenerator → Translator\n");

        // Step 1: 研究
        System.out.println("-".repeat(40));
        System.out.println("【Researcher】分析中...");
        String points = researcher.research(topic);
        System.out.println(points);

        // Step 2: 写作
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【Writer】撰写中...");
        String article = writer.write(points);
        System.out.println(article);

        // Step 3: 生成标题
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【TitleGenerator】生成标题...");
        String title = titleGen.generateTitle(article);
        System.out.println("标题: " + title);

        // Step 4: 翻译
        System.out.println("\n" + "-".repeat(40));
        System.out.println("【Translator】翻译中...");
        String englishArticle = translator.translate(title + "\n\n" + article);
        System.out.println(englishArticle);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("AI Service 链式调用完成！");
        System.out.println("=".repeat(60));
    }
}
