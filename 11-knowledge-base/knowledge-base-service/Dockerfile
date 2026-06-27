# ============================================================
# 企业知识库服务 Dockerfile
#
# 多阶段构建:
# 1. 编译阶段: 使用 Maven 构建
# 2. 运行阶段: 使用精简 JRE 运行
# ============================================================

# ==================== 编译阶段 ====================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml，先下载依赖 (利用 Docker 缓存)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并编译
COPY src ./src
RUN mvn package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 复制编译产物
COPY --from=builder /app/target/*.jar app.jar

# 设置文件权限
RUN chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM 参数
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
