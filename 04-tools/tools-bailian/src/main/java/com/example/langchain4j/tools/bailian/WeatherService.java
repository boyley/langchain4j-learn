package com.example.langchain4j.tools.bailian;

import dev.langchain4j.agent.tool.Tool;

public class WeatherService {

    @Tool("查询指定城市的当前天气")
    public String getCurrentWeather(String city) {
        System.out.println("[工具调用] getCurrentWeather(" + city + ")");
        // 模拟天气数据
        return switch (city) {
            case "北京" -> "北京: 晴天, 温度 25°C, 湿度 40%";
            case "上海" -> "上海: 多云, 温度 28°C, 湿度 65%";
            case "广州" -> "广州: 小雨, 温度 30°C, 湿度 80%";
            case "深圳" -> "深圳: 晴天, 温度 32°C, 湿度 70%";
            default -> city + ": 未知天气数据";
        };
    }

    @Tool("查询指定城市未来几天的天气预报")
    public String getWeatherForecast(String city, int days) {
        System.out.println("[工具调用] getWeatherForecast(" + city + ", " + days + ")");
        return city + " 未来 " + days + " 天天气预报: 晴转多云, 温度 25-32°C";
    }
}
