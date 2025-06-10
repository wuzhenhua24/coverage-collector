# 多节点覆盖率收集使用示例

## 快速开始

### 1. 启动应用
```bash
# 如果有Maven
mvn spring-boot:run

# 或者如果有jar包
java -jar coverage-collector-0.0.1-SNAPSHOT.jar
```

### 2. 基本API测试

#### 检查服务状态
```bash
curl -X GET "http://localhost:8080/api/coverage/status"
```

#### 多节点数据收集
```bash
# 收集用户服务开发环境的覆盖率数据
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node" \
  -d "appName=user-service" \
  -d "envName=dev" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

**响应示例：**
```json
{
  "success": true,
  "message": "多节点覆盖率数据收集完成",
  "appName": "user-service",
  "envName": "dev",
  "tag": "v1.0.0",
  "totalNodes": 2,
  "successCount": 2,
  "failedCount": 0,
  "successfulDumps": [
    "./dump-files/user-service/dev/v1.0.0/jacoco_192_168_1_100_20231201_143022_123.exec",
    "./dump-files/user-service/dev/v1.0.0/jacoco_192_168_1_101_20231201_143022_456.exec"
  ],
  "failedNodes": []
}
```

#### 多节点数据重置
```bash
curl -X POST "http://localhost:8080/api/coverage/reset-multi-node" \
  -d "appName=user-service" \
  -d "envName=dev" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

#### 生成报告（合并多节点数据）
```bash
curl -X POST "http://localhost:8080/api/coverage/report" \
  -d "appName=user-service" \
  -d "tag=v1.0.0" \
  -d "mergeAllDumps=true" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

### 3. 模拟数据说明

当前系统包含以下模拟服务实例：

**用户服务-开发环境 (user-service-dev):**
- 节点1: 192.168.1.100:6300
- 节点2: 192.168.1.101:6300

**订单服务-开发环境 (order-service-dev):**
- 节点1: 192.168.1.102:6300
- 节点2: 192.168.1.103:6300

**订单服务-测试环境 (order-service-test):**
- 节点1: 192.168.2.102:6300
- 节点2: 192.168.2.103:6300

### 4. 测试不同环境

```bash
# 测试开发环境
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=sprint1"

# 测试测试环境
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=order-service&envName=test&tag=sprint1"

# 测试不存在的服务（会返回空结果）
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=payment-service&envName=dev&tag=sprint1"
```

### 5. 目录结构验证

成功收集后，你会看到如下目录结构：

```
dump-files/
├── user-service/
│   └── dev/
│       └── v1.0.0/
│           ├── jacoco_192_168_1_100_20231201_143022_123.exec
│           └── jacoco_192_168_1_101_20231201_143022_456.exec
└── order-service/
    ├── dev/
    │   └── v1.0.0/
    └── test/
        └── sprint1/
```

### 6. 真实环境配置

要在真实环境中使用，需要：

1. **配置Nacos连接**（application.yml）：
```yaml
nacos:
  discovery:
    server-addr: your-nacos-server:8848
    namespace: your-namespace
    username: your-username
    password: your-password
```

2. **应用启动时启用JaCoCo agent**：
```bash
java -javaagent:jacoco-agent.jar=destfile=jacoco.exec,address=*,port=6300,output=tcpserver \
     -jar your-application.jar
```

3. **在Nacos中注册服务时添加JaCoCo端口元数据**：
```json
{
  "jacoco.port": "6300"
}
```

### 7. 故障排查

如果遇到问题：

1. **检查服务状态**：
```bash
curl "http://localhost:8080/api/coverage/status"
```

2. **查看日志**：
```bash
tail -f logs/spring.log
```

3. **测试单节点连接**：
```bash
curl -X POST "http://localhost:8080/api/coverage/collect?appName=user-service&tag=test&agentHost=192.168.1.100&agentPort=6300"
```

### 8. 完整测试流程

```bash
#!/bin/bash

echo "=== 多节点覆盖率收集完整测试 ==="

# 1. 检查服务状态
echo "1. 检查服务状态"
curl -s "http://localhost:8080/api/coverage/status" | jq '.'

# 2. 收集用户服务数据
echo "2. 收集用户服务数据"
curl -s -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=test-run1" | jq '.'

# 3. 再次收集（模拟多轮测试）
echo "3. 第二轮收集"
curl -s -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=test-run1" | jq '.'

# 4. 生成合并报告
echo "4. 生成合并报告"
curl -s -X POST "http://localhost:8080/api/coverage/report?appName=user-service&tag=test-run1&mergeAllDumps=true" | jq '.'

# 5. 重置数据
echo "5. 重置数据"
curl -s -X POST "http://localhost:8080/api/coverage/reset-multi-node?appName=user-service&envName=dev" | jq '.'

echo "=== 测试完成 ==="
``` 