package com.example.multiagent;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Demo05: 路由 Agent
 * 根据用户问题类型，分发给不同的专家处理
 *
 * 这是智能客服、知识问答的常见模式
 */
public class Demo05_RouterAgent {

    // ========== 路由器 ==========

    enum QuestionType {
        CODE,       // 代码问题
        CONCEPT,    // 概念解释
        PRACTICE,   // 最佳实践
        UNKNOWN     // 未知类型
    }

    interface Router {
        @SystemMessage("""
                你是一个问题分类器。根据用户问题判断类型：
                - CODE: 代码实现、语法、bug 相关
                - CONCEPT: 概念解释、原理说明
                - PRACTICE: 最佳实践、设计模式、架构建议
                - UNKNOWN: 无法分类

                只输出类型名称，不要其他内容。
                """)
        @UserMessage("{{question}}")
        QuestionType classify(@V("question") String question);
    }

    // ========== 专家 Agent ==========

    interface CodeExpert {
        @SystemMessage("""
                你是编程专家，擅长：
                - 代码实现
                - Bug 调试
                - 代码优化
                给出具体的代码示例。
                """)
        @UserMessage("{{question}}")
        String answer(@V("question") String question);
    }

    interface ConceptExpert {
        @SystemMessage("""
                你是技术讲师，擅长：
                - 解释技术概念
                - 类比说明
                - 原理剖析
                用通俗易懂的语言解释。
                """)
        @UserMessage("{{question}}")
        String answer(@V("question") String question);
    }

    interface PracticeExpert {
        @SystemMessage("""
                你是架构师，擅长：
                - 最佳实践
                - 设计模式
                - 架构决策
                给出实用的建议和案例。
                """)
        @UserMessage("{{question}}")
        String answer(@V("question") String question);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo05: 路由 Agent（智能分发）");
        System.out.println("=".repeat(60));

        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-3.5-turbo")
                .build();

        // 创建 Agent
        Router router = AiServices.create(Router.class, model);
        CodeExpert codeExpert = AiServices.create(CodeExpert.class, model);
        ConceptExpert conceptExpert = AiServices.create(ConceptExpert.class, model);
        PracticeExpert practiceExpert = AiServices.create(PracticeExpert.class, model);

        // 测试问题
        String[] questions = {
                "Java 中如何实现单例模式？",
                "什么是微服务架构？",
                "数据库连接池应该如何配置？"
        };

        for (String question : questions) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("问题: " + question);
            System.out.println("-".repeat(60));

            // Step 1: 路由分类
            QuestionType type = router.classify(question);
            System.out.println("分类结果: " + type);
            System.out.println("-".repeat(60));

            // Step 2: 分发给对应专家
            String answer = switch (type) {
                case CODE -> {
                    System.out.println("→ 转给【编程专家】");
                    yield codeExpert.answer(question);
                }
                case CONCEPT -> {
                    System.out.println("→ 转给【概念专家】");
                    yield conceptExpert.answer(question);
                }
                case PRACTICE -> {
                    System.out.println("→ 转给【架构专家】");
                    yield practiceExpert.answer(question);
                }
                default -> "抱歉，无法处理这个问题。";
            };

            System.out.println("\n回答:");
            System.out.println(answer);
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("路由分发演示完成！");
        System.out.println("=".repeat(60));
    }
}
