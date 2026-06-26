package com.example.langchain4j.aiservices.demo;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @SystemMessage 系统提示示例
 *
 * 系统提示 (System Message) 是什么？
 * ────────────────────────────────────────────────────
 * 系统提示是发送给 AI 的"幕后指令"，用于：
 * 1. 设定 AI 的角色身份（你是谁）
 * 2. 定义回答风格（如何回答）
 * 3. 设置行为限制（什么不能做）
 * 4. 提供背景知识（需要知道什么）
 *
 * 用户看不到系统提示的内容，但 AI 会始终遵循。
 *
 * 使用场景：
 * ────────────────────────────────────────────────────
 * | 场景         | 系统提示作用                          |
 * |--------------|---------------------------------------|
 * | 客服机器人    | 限定只回答产品问题，保护内部信息        |
 * | 翻译助手      | 指定翻译风格，保持专业术语              |
 * | 代码助手      | 要求使用特定语言，遵循编码规范          |
 * | 儿童教育      | 使用简单语言，避免复杂概念              |
 * | 法律顾问      | 添加免责声明，不提供正式法律意见        |
 * | 角色扮演      | 设定AI的人设和说话方式                  |
 * ────────────────────────────────────────────────────
 */
public class SystemMessageDemo {

    // ==================== 不同角色的 AI 接口定义 ====================

    /**
     * 通用助手 - 无系统提示
     * AI 使用默认行为
     */
    interface GeneralAssistant {
        String chat(String message);
    }

    /**
     * 客服机器人 - 限定回答范围
     *
     * @SystemMessage 注解：
     *   - 放在接口或方法上
     *   - 定义 AI 的角色和行为规则
     *   - 每次对话都会发送给 AI（用户不可见）
     */
    @SystemMessage("""
            你是"智能科技公司"的客服机器人，名字叫"小智"。

            你的职责：
            1. 回答产品咨询（手机、电脑、配件）
            2. 处理售后问题（退换货、维修）
            3. 提供使用帮助

            行为规则：
            - 始终保持礼貌和专业
            - 不要透露公司内部信息（成本、利润等）
            - 不要回答与产品无关的问题
            - 如果不确定，引导用户联系人工客服：400-123-4567

            回答风格：简洁、友好、专业
            """)
    interface CustomerServiceBot {
        String chat(String message);
    }

    /**
     * 代码专家 - 专业技术角色
     */
    @SystemMessage("""
            你是一位资深 Java 开发专家，有 15 年经验。

            回答要求：
            1. 提供可运行的代码示例
            2. 解释代码的关键部分
            3. 指出潜在问题和最佳实践
            4. 使用 Java 17+ 特性

            代码风格：
            - 遵循阿里巴巴 Java 开发规范
            - 添加必要的注释
            - 考虑异常处理
            """)
    interface JavaExpert {
        String ask(String question);
    }

    /**
     * 翻译助手 - 专业翻译
     */
    @SystemMessage("""
            你是专业翻译，精通中英双语。

            翻译原则：
            1. 保持原文的语气和风格
            2. 专业术语保持准确
            3. 只返回翻译结果，不要添加解释
            4. 如有多种翻译方式，选择最自然的表达
            """)
    interface Translator {
        @UserMessage("将以下内容翻译成{{targetLang}}：{{text}}")
        String translate(@V("text") String text, @V("targetLang") String targetLang);
    }

    /**
     * 儿童教育助手 - 适合儿童的回答风格
     */
    @SystemMessage("""
            你是一位友善的老师，正在和一个 8 岁的小朋友对话。

            回答要求：
            1. 使用简单易懂的语言
            2. 多用比喻和例子
            3. 回答要简短，每次不超过 100 字
            4. 可以适当使用表情符号
            5. 避免复杂的专业术语
            """)
    interface KidsTeacher {
        String explain(String topic);
    }

    /**
     * 严格模式助手 - 限制回答范围
     */
    @SystemMessage("""
            你只能回答关于天气的问题。

            对于其他任何问题，请回复：
            "抱歉，我只能回答天气相关的问题。"

            不要尝试回答任何非天气问题，即使用户坚持。
            """)
    interface WeatherOnlyBot {
        String ask(String question);
    }

    // ==================== 演示 ====================

    public static void main(String[] args) {
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .modelName("gpt-4o-mini")
                .build();

        System.out.println("=== @SystemMessage 系统提示示例 ===\n");

        // ========== 示例 1: 客服机器人 ==========
        System.out.println("【示例 1: 客服机器人】");
        System.out.println("系统提示: 你是智能科技公司客服小智，只回答产品问题");
        System.out.println("-".repeat(50));

        CustomerServiceBot customerService = AiServices.create(CustomerServiceBot.class, model);

        System.out.println("用户: 你们的手机有什么颜色？");
        System.out.println("小智: " + customerService.chat("你们的手机有什么颜色？"));

        System.out.println("\n用户: 你们公司一年赚多少钱？");
        System.out.println("小智: " + customerService.chat("你们公司一年赚多少钱？"));

        // ========== 示例 2: 代码专家 vs 通用助手 ==========
        System.out.println("\n【示例 2: 代码专家 vs 通用助手】");
        System.out.println("-".repeat(50));

        JavaExpert javaExpert = AiServices.create(JavaExpert.class, model);
        GeneralAssistant generalAssistant = AiServices.create(GeneralAssistant.class, model);

        String codeQuestion = "如何在 Java 中实现单例模式？";

        System.out.println("问题: " + codeQuestion);
        System.out.println("\n>>> Java专家回答 (有系统提示):");
        System.out.println(javaExpert.ask(codeQuestion));

        // ========== 示例 3: 儿童教育 ==========
        System.out.println("\n【示例 3: 儿童教育助手】");
        System.out.println("系统提示: 用简单语言解释，适合8岁儿童");
        System.out.println("-".repeat(50));

        KidsTeacher kidsTeacher = AiServices.create(KidsTeacher.class, model);

        System.out.println("小朋友: 为什么天空是蓝色的？");
        System.out.println("老师: " + kidsTeacher.explain("为什么天空是蓝色的？"));

        // ========== 示例 4: 翻译助手 ==========
        System.out.println("\n【示例 4: 专业翻译】");
        System.out.println("系统提示: 只返回翻译结果，不添加解释");
        System.out.println("-".repeat(50));

        Translator translator = AiServices.create(Translator.class, model);

        String text = "人工智能正在改变世界";
        System.out.println("原文: " + text);
        System.out.println("英文: " + translator.translate(text, "英文"));
        System.out.println("日文: " + translator.translate(text, "日文"));

        // ========== 示例 5: 严格限制模式 ==========
        System.out.println("\n【示例 5: 严格限制模式】");
        System.out.println("系统提示: 只能回答天气问题");
        System.out.println("-".repeat(50));

        WeatherOnlyBot weatherBot = AiServices.create(WeatherOnlyBot.class, model);

        System.out.println("用户: 北京今天天气怎么样？");
        System.out.println("AI: " + weatherBot.ask("北京今天天气怎么样？"));

        System.out.println("\n用户: 帮我写一首诗");
        System.out.println("AI: " + weatherBot.ask("帮我写一首诗"));

        // ========== 总结 ==========
        System.out.println("\n" + "=".repeat(50));
        System.out.println("【总结】@SystemMessage 的作用：");
        System.out.println("1. 设定角色: 客服、专家、老师等");
        System.out.println("2. 定义风格: 专业、友好、简洁等");
        System.out.println("3. 限制范围: 只回答特定主题");
        System.out.println("4. 添加规则: 不透露信息、必须包含XX等");
        System.out.println("5. 用户不可见，但 AI 始终遵循");
    }
}
