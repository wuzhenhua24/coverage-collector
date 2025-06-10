# 真实Nacos集成的多节点覆盖率收集

## 概述

这个版本已经集成了真实的Nacos服务发现，可以动态从Nacos获取应用实例信息，并支持多节点覆盖率数据收集。

## 关键变更

### 1. Nacos集成
- **服务名格式**: `{appName}.app` (例如: `user-service.app`)
- **分组名**: `DEFAULT_GROUP`
- **命名空间**: `public`
- **认证**: `username=nacos, password=nacos123456`

### 2. 集群支持
- **参数变更**: `envName` → `clusterName`
- **集群匹配**: 支持 `p2` → `daily-p2` 的自动匹配
- **健康检查**: 只获取健康且启用的实例

### 3. JaCoCo端口配置
- **优先级**: 配置文件 > Nacos元数据 > 默认端口6300
- **配置路径**: `coverage.jacoco-ports.{appName}`

## 配置示例

### application.yml
```yaml
# Nacos配置
nacos:
  discovery:
    server-addr: your-nacos-server:8848
    namespace-id: public
    group-name: DEFAULT_GROUP
    username: nacos
    password: nacos123456

# 覆盖率配置
coverage:
  jacoco-ports:
    user-service: 6300
    order-service: 6301
    payment-service: 6302
```

## API接口

### 多节点数据收集
```bash
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node" \
  -d "appName=user-service" \
  -d "clusterName=p2" \
  -d "tag=v1.0.0" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

**响应示例:**
```json
{
  "success": true,
  "message": "多节点覆盖率数据收集完成",
  "appName": "user-service",
  "clusterName": "p2",
  "tag": "v1.0.0",
  "totalNodes": 2,
  "successCount": 2,
  "failedCount": 0,
  "successfulDumps": [
    "./dump-files/user-service/p2/v1.0.0/jacoco_192_168_1_100_20231201_143022.exec",
    "./dump-files/user-service/p2/v1.0.0/jacoco_192_168_1_101_20231201_143022.exec"
  ],
  "failedNodes": []
}
```

### 多节点数据重置
```bash
curl -X POST "http://localhost:8080/api/coverage/reset-multi-node" \
  -d "appName=user-service" \
  -d "clusterName=p2" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

## Nacos服务注册

### 注册服务实例
```bash
curl -X POST 'http://localhost:8848/nacos/v1/ns/instance' \
  -d 'serviceName=user-service.app' \
  -d 'groupName=DEFAULT_GROUP' \
  -d 'namespaceId=public' \
  -d 'ip=192.168.1.100' \
  -d 'port=8080' \
  -d 'clusterName=daily-p2' \
  -d 'metadata={"jacoco.port":"6300"}'
```

### 查询服务实例
```bash
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=user-service.app&groupName=DEFAULT_GROUP&namespaceId=public"
```

## 应用启动配置

### JaCoCo Agent配置
```bash
java -javaagent:jacoco-agent.jar=destfile=jacoco.exec,address=*,port=6300,output=tcpserver \
     -jar your-application.jar
```

### Spring Boot应用配置
```yaml
# 应用配置
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: public
        group: DEFAULT_GROUP
        cluster-name: daily-p2
        metadata:
          jacoco.port: 6300
```

## 目录结构

```
dump-files/
├── user-service/
│   ├── p2/
│   │   └── v1.0.0/
│   │       ├── jacoco_192_168_1_100_20231201_143022.exec
│   │       └── jacoco_192_168_1_101_20231201_143022.exec
│   └── daily-p2/
└── order-service/
    └── p2/
```

## 测试验证

### 运行测试脚本
```bash
chmod +x test-real-nacos-api.sh
./test-real-nacos-api.sh
```

### 验证Nacos连接
```bash
curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=user-service.app&groupName=DEFAULT_GROUP&namespaceId=public"
```

## 故障排查

### 1. Nacos连接失败
- 检查Nacos服务器地址和端口
- 验证用户名密码
- 确认网络连通性

### 2. 找不到服务实例
- 确认服务名格式：`{appName}.app`
- 检查groupName和namespaceId
- 验证集群名称匹配

### 3. JaCoCo连接失败
- 检查应用是否启动了JaCoCo agent
- 验证JaCoCo端口配置
- 确认防火墙设置

### 4. 集群匹配问题
- `p2` 会匹配 `daily-p2`
- 检查Nacos中的实际clusterName
- 验证健康状态和启用状态

## 集群名称映射规则

| 请求的clusterName | 匹配的实际clusterName |
|------------------|---------------------|
| p2               | p2, daily-p2        |
| test             | test, daily-test    |
| prod             | prod, daily-prod    |

## 高级配置

### 自定义端口获取策略
1. **配置文件优先**: `coverage.jacoco-ports.{appName}`
2. **Nacos元数据**: `metadata.jacoco.port`
3. **默认端口**: 6300

### 健康检查策略
- 只处理 `healthy=true` 的实例
- 只处理 `enabled=true` 的实例
- 集群名称严格匹配或前缀匹配

### 错误处理策略
- 单节点失败不影响其他节点
- 详细的错误日志和统计
- 优雅的降级处理

## 性能优化建议

1. **连接池**: 考虑使用连接池管理Nacos连接
2. **缓存**: 缓存服务实例信息
3. **并行处理**: 使用线程池处理多节点操作
4. **超时设置**: 设置合理的网络超时时间

---

**注意**: 这个版本已经移除了所有mock数据，完全依赖真实的Nacos服务发现。请确保你的Nacos环境配置正确。 