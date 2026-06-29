package com.example.dashscope;

import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Demo05: 多 Agent 协作
 * 使用通义千问实现多个 AI 角色协作
 */
public class Demo05_MultiAgent {

    // ========== 定义多个 Agent ==========

    interface Researcher {
        @SystemMessage("""
                你是技术研究员。
                职责：分析主题，提取 3-5 个关键要点。
                输出格式：编号列表。
                """)
        @UserMessage("分析主题：{{topic}}")
        String research(@V("topic") String topic);
    }

    interface Writer {
        @SystemMessage("""
                你是技术博主。
                职责：根据要点写一篇通俗易懂的文章。
                要求：300字左右，语言生动。
                """)
        @UserMessage("根据以下要点写文章：\n{{points}}")
        String write(@V("points") String points);
    }

    interface Editor {
        @SystemMessage("""
                你是资深编辑。
                职责：润色文章，优化表达。
                要求：保持原意，提升可读性。
                """)
        @UserMessage("润色以下文章：\n{{article}}")
        String edit(@V("article") String article);
    }

    interface TitleGenerator {
        @SystemMessage("根据文章内容生成一个吸引人的标题，只输出标题")
        String generate(String article);
    }

    // ========== 主程序 ==========

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Demo05: 阿里百炼 - 多 Agent 协作");
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

        // 创建各个 Agent
        Researcher researcher = AiServices.create(Researcher.class, model);
        Writer writer = AiServices.create(Writer.class, model);
        Editor editor = AiServices.create(Editor.class, model);
        TitleGenerator titleGen = AiServices.create(TitleGenerator.class, model);

        String topic = "为什么程序员应该学习 AI";

        System.out.println("\n主题: " + topic);
        System.out.println("协作流程: Researcher → Writer → Editor → TitleGenerator\n");

        // Step 1: 研究
        System.out.println("-".repeat(50));
        System.out.println("【Researcher】分析中...");
        System.out.println("-".repeat(50));
        String points = researcher.research(topic);
        System.out.println(points);

        // Step 2: 写作
        System.out.println("\n" + "-".repeat(50));
        System.out.println("【Writer】撰写中...");
        System.out.println("-".repeat(50));
        String article = writer.write(points);
        System.out.println(article);

        // Step 3: 润色
        System.out.println("\n" + "-".repeat(50));
        System.out.println("【Editor】润色中...");
        System.out.println("-".repeat(50));
        String polished = editor.edit(article);
        System.out.println(polished);

        // Step 4: 生成标题
        System.out.println("\n" + "-".repeat(50));
        System.out.println("【TitleGenerator】生成标题...");
        System.out.println("-".repeat(50));
        String title = titleGen.generate(polished);
        System.out.println("标题: " + title);

        // 最终输出
        System.out.println("\n" + "=".repeat(60));
        System.out.println("最终成果");
        System.out.println("=".repeat(60));
        System.out.println("\n【" + title + "】\n");
        System.out.println(polished);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("多 Agent 协作完成！");
        System.out.println("=".repeat(60));
    }
}
