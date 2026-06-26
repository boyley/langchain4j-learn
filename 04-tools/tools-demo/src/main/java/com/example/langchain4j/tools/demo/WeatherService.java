package com.example.langchain4j.tools.demo;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 天气服务工具类
 *
 * 展示 @Tool 和 @P 注解在复杂参数场景下的使用
 */
public class WeatherService {

    /**
     * @Tool - 描述工具功能，AI 根据此描述决定是否调用
     * @P - 描述参数含义，AI 从用户输入中提取参数值
     *
     * 返回值 String：
     *   工具执行结果，AI 会将此结果整合到回答中
     *   例如用户问"北京天气"，返回"北京: 晴天, 25°C"
     *   AI 会回答："北京今天晴天，温度25度"
     */
    @Tool("查询指定城市的当前天气，返回温度、天气状况和湿度")
    public String getCurrentWeather(
            @P("要查询天气的城市名称，如：北京、上海、广州") String city) {
        System.out.println("[工具调用] getCurrentWeather(" + city + ")");
        // 模拟天气数据（实际应用中调用真实天气 API）
        return switch (city) {
            case "北京" -> "北京: 晴天, 温度 25°C, 湿度 40%";
            case "上海" -> "上海: 多云, 温度 28°C, 湿度 65%";
            case "广州" -> "广州: 小雨, 温度 30°C, 湿度 80%";
            case "深圳" -> "深圳: 晴天, 温度 32°C, 湿度 70%";
            default -> city + ": 未知天气数据";
        };
    }

    /**
     * 多参数工具示例
     * AI 会从用户输入中同时提取多个参数
     *
     * 例如用户说："查一下北京未来3天的天气"
     * AI 提取: city="北京", days=3
     */
    @Tool("查询指定城市未来几天的天气预报")
    public String getWeatherForecast(
            @P("要查询的城市名称") String city,
            @P("预报天数，1-7天") int days) {
        System.out.println("[工具调用] getWeatherForecast(" + city + ", " + days + ")");
        return city + " 未来 " + days + " 天天气预报: 晴转多云, 温度 25-32°C";
    }
}
