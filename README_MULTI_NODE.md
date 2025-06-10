# 多节点覆盖率收集和Nacos集成功能

## 概述

本版本新增了多节点覆盖率收集功能和Nacos服务发现集成，解决了以下问题：

1. **动态IP获取**：通过Nacos服务发现自动获取应用实例IP，无需手动配置
2. **多节点支持**：支持一个应用多个节点的覆盖率收集和合并
3. **环境隔离**：通过envName参数支持不同环境的应用管理

## 新增功能

### 1. Nacos服务发现集成

#### 配置说明
```yaml
nacos:
  discovery:
    server-addr: localhost:8848    # Nacos服务器地址
    namespace: dev                 # 命名空间（可选）
    username: nacos               # 用户名（可选）
    password: nacos               # 密码（可选）
    service-name-pattern: "{appName}-{envName}"  # 服务名构建规则
```

#### 服务名规则
- 默认格式：`{appName}-{envName}`
- 例如：`user-service-dev`、`order-service-prod`

#### JaCoCo端口配置
在Nacos服务实例的元数据中配置JaCoCo端口：
```json
{
  "jacoco.port": "6300"
}
```
如果未配置，默认使用6300端口。

### 2. 多节点覆盖率收集

#### 新增API接口

##### 多节点数据收集
```http
POST /api/coverage/collect-multi-node
```

**参数：**
- `appName`: 应用名称
- `envName`: 环境名称  
- `tag`: 版本标签

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

##### 多节点数据重置
```http
POST /api/coverage/reset-multi-node
```

**参数：**
- `appName`: 应用名称
- `envName`: 环境名称

**响应示例：**
```json
{
  "success": true,
  "message": "多节点覆盖率数据重置完成",
  "appName": "user-service",
  "envName": "dev",
  "totalNodes": 2,
  "successCount": 2,
  "failedCount": 0,
  "failedNodes": []
}
```

### 3. 目录结构优化

#### 新的目录结构
```
dump-files/
├── user-service/
│   ├── dev/
│   │   ├── v1.0.0/
│   │   │   ├── jacoco_192_168_1_100_20231201_143022_123.exec
│   │   │   └── jacoco_192_168_1_101_20231201_143022_456.exec
│   │   └── v1.0.1/
│   └── prod/
└── order-service/
    ├── dev/
    └── prod/

coverage-reports/
├── user-service/
│   ├── dev/
│   │   ├── v1.0.0/
│   │   │   ├── index.html
│   │   │   └── jacoco.xml
│   │   └── v1.0.1/
│   └── prod/
└── order-service/
```

#### 文件命名规则
- Dump文件：`jacoco_{nodeId}_{timestamp}.exec`
- 节点ID：IP地址中的点替换为下划线（如：`192_168_1_100`）
- 时间戳：`yyyyMMdd_HHmmss_SSS`

### 4. 多节点数据合并

#### 自动合并
生成报告时可以自动合并同一tag下的所有dump文件：

```http
POST /api/coverage/report?appName=user-service&tag=v1.0.0&mergeAllDumps=true
```

#### 手动合并
```http
POST /api/coverage/merge-dumps?appName=user-service&tag=v1.0.0
```

## 使用场景

### 场景1：多轮测试覆盖率收集

```bash
# 第一轮测试
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=sprint1"

# 第二轮测试  
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=sprint1"

# 生成合并报告
curl -X POST "http://localhost:8080/api/coverage/report?appName=user-service&tag=sprint1&mergeAllDumps=true"
```

### 场景2：不同环境的覆盖率收集

```bash
# 开发环境
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=dev&tag=v1.0.0"

# 测试环境
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=test&tag=v1.0.0"

# 预发环境
curl -X POST "http://localhost:8080/api/coverage/collect-multi-node?appName=user-service&envName=pre&tag=v1.0.0"
```

## 容错机制

### 1. 节点故障处理
- 单个节点收集失败不影响其他节点
- 返回详细的成功/失败统计信息
- 记录失败节点的详细错误信息

### 2. 服务发现故障
- Nacos连接失败时使用配置文件中的静态IP
- 提供降级机制确保服务可用性

### 3. 网络超时处理
- 可配置的连接超时时间
- 自动重试机制（可选）

## 监控和日志

### 日志级别
```yaml
logging:
  level:
    com.mofari.coveragecollector: DEBUG
    com.mofari.coveragecollector.service.MultiNodeCoverageService: DEBUG
```

### 关键日志
- 节点发现日志
- 连接状态日志
- 收集进度日志
- 错误详情日志

## 性能优化

### 1. 并行收集
- 支持多节点并行数据收集
- 可配置并发线程数

### 2. 连接池
- JaCoCo连接复用
- 减少连接建立开销

### 3. 数据压缩
- Dump文件压缩存储
- 网络传输优化

## 故障排查

### 常见问题

1. **Nacos连接失败**
   - 检查Nacos服务器地址和端口
   - 验证网络连通性
   - 确认认证信息

2. **节点发现失败**
   - 检查服务名格式是否正确
   - 验证应用是否已注册到Nacos
   - 确认命名空间配置

3. **JaCoCo连接失败**
   - 检查应用是否启用了JaCoCo agent
   - 验证JaCoCo端口配置
   - 确认防火墙设置

### 调试命令

```bash
# 检查服务状态
curl "http://localhost:8080/api/coverage/status"

# 查看dump文件列表
curl "http://localhost:8080/api/coverage/dump-files?appName=user-service&tag=v1.0.0"

# 测试单节点连接
curl -X POST "http://localhost:8080/api/coverage/collect?appName=user-service&tag=test&agentHost=192.168.1.100&agentPort=6300"
```

## 后续规划

1. **Nacos真实集成**：替换模拟数据为真实Nacos客户端
2. **负载均衡**：支持多个收集服务实例
3. **实时监控**：提供覆盖率收集的实时监控界面
4. **告警机制**：节点故障和收集失败的告警通知
5. **数据分析**：覆盖率趋势分析和报告 