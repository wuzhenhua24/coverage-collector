#!/bin/bash

# 多节点覆盖率收集API测试脚本

BASE_URL="http://localhost:8080/api/coverage"

echo "=== 多节点覆盖率收集API测试 ==="
echo

# 测试1: 多节点数据收集
echo "1. 测试多节点数据收集"
echo "请求: POST ${BASE_URL}/collect-multi-node"
echo "参数: appName=user-service, envName=dev, tag=v1.0.0"
echo

curl -X POST "${BASE_URL}/collect-multi-node" \
  -d "appName=user-service" \
  -d "envName=dev" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  | jq '.' 2>/dev/null || echo "响应数据"

echo -e "\n"

# 测试2: 多节点数据重置
echo "2. 测试多节点数据重置"
echo "请求: POST ${BASE_URL}/reset-multi-node"
echo "参数: appName=user-service, envName=dev"
echo

curl -X POST "${BASE_URL}/reset-multi-node" \
  -d "appName=user-service" \
  -d "envName=dev" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  | jq '.' 2>/dev/null || echo "响应数据"

echo -e "\n"

# 测试3: 服务状态检查
echo "3. 测试服务状态检查"
echo "请求: GET ${BASE_URL}/status"
echo

curl -X GET "${BASE_URL}/status" \
  | jq '.' 2>/dev/null || echo "响应数据"

echo -e "\n"

# 测试4: 不同环境的收集
echo "4. 测试不同环境的收集"
echo "请求: POST ${BASE_URL}/collect-multi-node"
echo "参数: appName=order-service, envName=test, tag=v2.0.0"
echo

curl -X POST "${BASE_URL}/collect-multi-node" \
  -d "appName=order-service" \
  -d "envName=test" \
  -d "tag=v2.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  | jq '.' 2>/dev/null || echo "响应数据"

echo -e "\n"

echo "=== 测试完成 ===" 