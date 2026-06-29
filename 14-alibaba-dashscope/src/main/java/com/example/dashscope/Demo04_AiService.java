package com.example.dashscope;

import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Demo04: AI Service 方式
 * 使用接口 + 注解，更优雅地调用 AI
 */
public class Demo04_AiService {

    // ========== 定义 AI 助手接口 ==========

    interface JavaTeacher {
        @SystemMessage("""
                你是一位经验丰富的 Java 老师。
                回答要：
                1. 简洁明了
                2. 给出代码示例
                3. 解释关键概念
                """)
        String ask(String question);
    }

    interface Translator {
        @SystemMessage("你是专业翻译，将中文翻译成英文，只输出翻译结果")
        @UserMessage("翻译：{{text}}")
        String toEnglish(@V("text") String text);

        @SystemMessage("你是专业翻译，将英文翻译成中文，只输出翻译结果")
        @UserMessage("翻译：{{text}}")
        String toChinese(@V("text") String text);
    }

    interface CodeReviewer {
        @SystemMessage("""
                你是代码审查专家。
                审查代码时关注：
                1. 潜在 bug
                2. 性能问题
                3. 代码风格
                给出具体的改进建议。
                """)
        @UserMessage("请审查以下代码：\n```java\n{{code}}\n```")
        String review(@V("code") String code);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo04: 阿里百炼 - AI Service 方式");
        System.out.println("=".repeat(60));

        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            return;
        }

        QwenChatModel model = QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .build();

        // ========== 1. Java 老师 ==========
        System.out.println("\n【Java 老师】");
        System.out.println("-".repeat(40));

        JavaTeacher teacher = AiServices.builder(JavaTeacher.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        System.out.println("问：什么是接口？");
        System.out.println("答：" + teacher.ask("什么是 Java 中的接口？简单解释"));

        // ========== 2. 翻译器 ==========
        System.out.println("\n【翻译器】");
        System.out.println("-".repeat(40));

        Translator translator = AiServices.create(Translator.class, model);

        String chinese = "人工智能正在改变世界";
        String english = translator.toEnglish(chinese);
        System.out.println("中 → 英: " + chinese);
        System.out.println("结果: " + english);

        String back = translator.toChinese(english);
        System.out.println("英 → 中: " + english);
        System.out.println("结果: " + back);

        // ========== 3. 代码审查 ==========
        System.out.println("\n【代码审查】");
        System.out.println("-".repeat(40));

        CodeReviewer reviewer = AiServices.create(CodeReviewer.class, model);

        String code = """
                public String getUser(String id) {
                    String sql = "SELECT * FROM users WHERE id = '" + id + "'";
                    return db.query(sql);
                }
                """;

        System.out.println("待审查代码:");
        System.out.println(code);
        System.out.println("审查结果:");
        System.out.println(reviewer.review(code));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("AI Service 示例完成！");
        System.out.println("=".repeat(60));
    }
}
