package com.example.langchain4j.tools.demo;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 计算器工具类
 *
 * 核心注解说明：
 * - @Tool("描述"): 告诉 AI 这个方法能做什么，AI 根据描述选择是否调用
 * - @P("描述"): 告诉 AI 这个参数是什么，AI 从用户输入中提取对应的值
 *
 * 工作流程：
 * 1. 用户: "计算 15 加 27"
 * 2. AI 分析: 需要加法 → 选择 add 方法
 * 3. AI 提取参数: a=15, b=27
 * 4. LangChain4j 调用: add(15, 27)
 * 5. 返回结果: 42
 * 6. AI 组织回答: "15 + 27 = 42"
 */
public class Calculator {

    /**
     * @Tool - 工具描述注解
     *   作用：告诉 AI 这个方法的功能
     *   AI 根据用户意图匹配最合适的工具
     *
     * @P - 参数描述注解
     *   作用：告诉 AI 每个参数代表什么
     *   AI 会从用户输入中提取对应的值
     *
     * 返回值：
     *   工具的执行结果，AI 会用这个结果来组织回答
     */
    @Tool("计算两个数的和，用于加法运算")
    public double add(
            @P("第一个加数") double a,
            @P("第二个加数") double b) {
        System.out.println("[工具调用] add(" + a + ", " + b + ")");
        return a + b;  // 返回给 AI，AI 用这个结果回答用户
    }

    @Tool("计算两个数的差，用于减法运算")
    public double subtract(
            @P("被减数") double a,
            @P("减数") double b) {
        System.out.println("[工具调用] subtract(" + a + ", " + b + ")");
        return a - b;
    }

    @Tool("计算两个数的积，用于乘法运算")
    public double multiply(
            @P("第一个乘数") double a,
            @P("第二个乘数") double b) {
        System.out.println("[工具调用] multiply(" + a + ", " + b + ")");
        return a * b;
    }

    @Tool("计算两个数的商，用于除法运算")
    public double divide(
            @P("被除数") double a,
            @P("除数，不能为零") double b) {
        System.out.println("[工具调用] divide(" + a + ", " + b + ")");
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为零");
        }
        return a / b;
    }

    @Tool("计算一个数的平方根")
    public double sqrt(
            @P("要计算平方根的数，必须为非负数") double a) {
        System.out.println("[工具调用] sqrt(" + a + ")");
        return Math.sqrt(a);
    }
}
