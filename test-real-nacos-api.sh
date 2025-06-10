#!/bin/bash

echo "=== 真实Nacos集成的多节点覆盖率收集测试 ==="

# 设置API base URL
BASE_URL="http://localhost:8080/api/coverage"

# 检查服务状态
echo "1. 检查服务状态"
curl -s "$BASE_URL/status" | jq '.' || echo "服务未启动或jq未安装"
echo ""

# 测试不同的集群
echo "2. 测试p2集群（可能对应daily-p2）"
curl -s -X POST "$BASE_URL/collect-multi-node" \
  -d "appName=user-service" \
  -d "clusterName=p2" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq '.' || echo "请求失败"
echo ""

echo "3. 测试daily-p2集群"
curl -s -X POST "$BASE_URL/collect-multi-node" \
  -d "appName=user-service" \
  -d "clusterName=daily-p2" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq '.' || echo "请求失败"
echo ""

echo "4. 测试不存在的应用"
curl -s -X POST "$BASE_URL/collect-multi-node" \
  -d "appName=nonexistent-service" \
  -d "clusterName=p2" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq '.' || echo "请求失败"
echo ""

echo "5. 测试多节点重置"
curl -s -X POST "$BASE_URL/reset-multi-node" \
  -d "appName=user-service" \
  -d "clusterName=p2" \
  -H "Content-Type: application/x-www-form-urlencoded" | jq '.' || echo "请求失败"
echo ""

echo "=== 测试完成 ==="
echo ""
echo "注意事项："
echo "1. 确保你的Nacos服务器运行在 localhost:8848"
echo "2. 确保有名为 'user-service.app' 的服务注册在Nacos中"
echo "3. 确保服务实例在指定的集群中（p2或daily-p2）"
echo "4. 如果看到连接失败，请检查目标应用是否启动了JaCoCo agent"
echo ""
echo "示例Nacos服务注册："
echo "curl -X POST 'http://localhost:8848/nacos/v1/ns/instance' \\"
echo "  -d 'serviceName=user-service.app' \\"
echo "  -d 'groupName=DEFAULT_GROUP' \\"
echo "  -d 'ip=192.168.1.100' \\"
echo "  -d 'port=8080' \\"
echo "  -d 'clusterName=daily-p2' \\"
echo "  -d 'metadata={\"jacoco.port\":\"6300\"}'" 