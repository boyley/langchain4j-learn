# 12 - Multi-Agent 多模型协作

本模块演示如何让多个 AI Agent 协作完成复杂任务。

## 核心概念

```
┌─────────────────────────────────────────────────────┐
│  Multi-Agent = 多个 AI 角色协作                      │
│                                                     │
│  [研究员] → [写手] → [编辑] → 最终输出               │
└─────────────────────────────────────────────────────┘
```

## 示例文件

| 文件 | 说明 | 难度 |
|------|------|------|
| Demo01_ChainCall.java | 链式调用 - 两个模型串联 | ⭐ |
| Demo02_RolePlay.java | 角色扮演 - 多专家协作 | ⭐⭐ |
| Demo03_AiServiceChain.java | AI Service 接口方式 | ⭐⭐⭐ |
| Demo04_ParallelAgents.java | 并行调用 + 汇总 | ⭐⭐⭐ |
| Demo05_RouterAgent.java | 路由分发模式 | ⭐⭐⭐⭐ |

## 运行方式

```bash
# 在项目根目录
mvn compile -pl 12-multi-agent

# 运行示例
mvn exec:java -pl 12-multi-agent -Dexec.mainClass="com.example.multiagent.Demo01_ChainCall"
```

## 协作模式

### 1. 串行链式
```
输入 → [Agent A] → [Agent B] → [Agent C] → 输出
```

### 2. 并行汇总
```
        ┌→ [Agent A] ─┐
输入 ───┼→ [Agent B] ─┼→ [汇总] → 输出
        └→ [Agent C] ─┘
```

### 3. 路由分发
```
输入 → [Router] ─┬→ 代码问题 → [Code Agent]
                 ├→ 写作问题 → [Writer Agent]
                 └→ 分析问题 → [Analyst Agent]
```
