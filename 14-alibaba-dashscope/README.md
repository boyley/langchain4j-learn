# 14 - 阿里百炼 (DashScope)

阿里云 DashScope 平台，通义千问系列模型调用示例。

## 前置准备

### 1. 获取 API Key

1. 访问 [阿里云百炼控制台](https://dashscope.console.aliyun.com/)
2. 登录/注册阿里云账号
3. 开通 DashScope 服务
4. 在「API-KEY 管理」创建 Key

### 2. 配置环境变量

```bash
# Mac/Linux
export DASHSCOPE_API_KEY=sk-xxxxxxxx

# Windows PowerShell
$env:DASHSCOPE_API_KEY="sk-xxxxxxxx"

# Windows CMD
set DASHSCOPE_API_KEY=sk-xxxxxxxx
```

或者在项目根目录的 `.env` 文件中添加：
```
DASHSCOPE_API_KEY=sk-xxxxxxxx
```

## 可用模型

| 模型 | 说明 | 适用场景 |
|------|------|----------|
| `qwen-turbo` | 快速版 | 简单任务，成本低 |
| `qwen-plus` | 增强版 | 平衡性能和成本（推荐） |
| `qwen-max` | 旗舰版 | 复杂推理任务 |
| `qwen-max-longcontext` | 长文本版 | 超长上下文处理 |

## 示例文件

| 文件 | 说明 |
|------|------|
| Demo01_BasicChat.java | 基础对话 |
| Demo02_Streaming.java | 流式输出 |
| Demo03_MultiRound.java | 多轮对话 |
| Demo04_AiService.java | AI Service 方式 |
| Demo05_MultiAgent.java | 多 Agent 协作 |

## 运行方式

```bash
# 在项目根目录执行
mvn compile -pl 14-alibaba-dashscope

# 运行示例
mvn exec:java -pl 14-alibaba-dashscope \
    -Dexec.mainClass="com.example.dashscope.Demo01_BasicChat"
```

## 价格参考（2024）

| 模型 | 输入 (每千Token) | 输出 (每千Token) |
|------|-----------------|-----------------|
| qwen-turbo | ¥0.002 | ¥0.006 |
| qwen-plus | ¥0.004 | ¥0.012 |
| qwen-max | ¥0.02 | ¥0.06 |

新用户有免费额度，具体见官网。

## 参考链接

- [DashScope 官网](https://dashscope.aliyun.com/)
- [通义千问 API 文档](https://help.aliyun.com/zh/dashscope/developer-reference/tongyi-qianwen-vl-api)
- [langchain4j DashScope 文档](https://docs.langchain4j.dev/integrations/language-models/dashscope)
