package com.example.langchain4j.tools.qwen;

import dev.langchain4j.agent.tool.Tool;

public class Calculator {

    @Tool("计算两个数的和")
    public double add(double a, double b) {
        System.out.println("[工具调用] add(" + a + ", " + b + ")");
        return a + b;
    }

    @Tool("计算两个数的差")
    public double subtract(double a, double b) {
        System.out.println("[工具调用] subtract(" + a + ", " + b + ")");
        return a - b;
    }

    @Tool("计算两个数的积")
    public double multiply(double a, double b) {
        System.out.println("[工具调用] multiply(" + a + ", " + b + ")");
        return a * b;
    }

    @Tool("计算两个数的商")
    public double divide(double a, double b) {
        System.out.println("[工具调用] divide(" + a + ", " + b + ")");
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为零");
        }
        return a / b;
    }

    @Tool("计算一个数的平方根")
    public double sqrt(double a) {
        System.out.println("[工具调用] sqrt(" + a + ")");
        return Math.sqrt(a);
    }
}
