#!/bin/bash

# JaCoCo Agent启动脚本示例
# 该脚本展示如何使用JaCoCo agent启动被测应用

# 配置参数
JACOCO_AGENT_JAR="jacoco-agent.jar"
APPLICATION_JAR="your-application.jar"
JACOCO_PORT=6300
INCLUDE_PACKAGES="com.yourcompany.*"
EXCLUDE_PACKAGES="com.yourcompany.test.*"

# 检查JaCoCo agent文件是否存在
if [ ! -f "$JACOCO_AGENT_JAR" ]; then
    echo "错误: JaCoCo agent文件 $JACOCO_AGENT_JAR 不存在"
    echo "请从 https://www.eclemma.org/jacoco/ 下载JaCoCo并解压"
    exit 1
fi

# 检查应用jar文件是否存在
if [ ! -f "$APPLICATION_JAR" ]; then
    echo "错误: 应用jar文件 $APPLICATION_JAR 不存在"
    echo "请指定正确的应用jar文件路径"
    exit 1
fi

# 构建JaCoCo agent参数
JACOCO_ARGS="-javaagent:$JACOCO_AGENT_JAR="
JACOCO_ARGS="${JACOCO_ARGS}destfile=jacoco.exec,"
JACOCO_ARGS="${JACOCO_ARGS}includes=$INCLUDE_PACKAGES,"
JACOCO_ARGS="${JACOCO_ARGS}excludes=$EXCLUDE_PACKAGES,"
JACOCO_ARGS="${JACOCO_ARGS}output=tcpserver,"
JACOCO_ARGS="${JACOCO_ARGS}port=$JACOCO_PORT,"
JACOCO_ARGS="${JACOCO_ARGS}address=*"

echo "=================================================="
echo "启动应用 with JaCoCo Agent"
echo "=================================================="
echo "JaCoCo Agent: $JACOCO_AGENT_JAR"
echo "应用JAR: $APPLICATION_JAR"
echo "监听端口: $JACOCO_PORT"
echo "包含包: $INCLUDE_PACKAGES"
echo "排除包: $EXCLUDE_PACKAGES"
echo "=================================================="

# 启动应用
echo "正在启动应用..."
java $JACOCO_ARGS -jar $APPLICATION_JAR

echo "应用已停止" 