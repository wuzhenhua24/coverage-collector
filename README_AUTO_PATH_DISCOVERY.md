# 自动路径发现功能说明

## 概述

为了简化多应用的配置管理，系统现在支持自动路径发现功能。你不再需要为每个应用手动配置源码和class文件路径，系统会自动从基础项目路径下搜索。

## 功能特性

### 1. 智能路径发现
- **优先级**: 配置文件 > 自动发现 > 默认路径
- **基础路径**: `~/project/{appName}/`
- **自动搜索**: 递归查找 `src/main/java` 和 `target/classes`
- **多模块支持**: 自动发现所有子模块的源码和class路径

### 2. 配置策略
- **大部分应用**: 无需配置，自动发现
- **特殊应用**: 在配置文件中显式配置
- **灵活配置**: 支持自定义基础项目路径

## 配置示例

### application.yml
```yaml
coverage:
  # 基础项目路径，所有应用都在这个路径下
  base-project-path: ~/project
  
  # 应用特定的JaCoCo端口配置
  jacoco-ports:
    user-service: 6300
    order-service: 6301
    payment-service: 6302
  
  # 只需要配置特殊的应用，其他应用会自动发现路径
  applications:
    special-service:
      agent-host: localhost
      agent-port: 6300
      source-directories:
        - /custom/path/special-service/src/main/java
        - /custom/path/special-service-api/src/main/java
      class-directories:
        - /custom/path/special-service/target/classes
        - /custom/path/special-service-api/target/classes
```

## 目录结构要求

### 标准项目结构
```
~/project/
├── user-service/
│   ├── src/main/java/           # 会被自动发现
│   ├── target/classes/          # 会被自动发现
│   ├── user-service-core/
│   │   ├── src/main/java/       # 会被自动发现
│   │   └── target/classes/      # 会被自动发现
│   └── user-service-api/
│       ├── src/main/java/       # 会被自动发现
│       └── target/classes/      # 会被自动发现
├── order-service/
│   ├── src/main/java/
│   ├── target/classes/
│   └── order-api/
│       ├── src/main/java/
│       └── target/classes/
└── payment-service/
    ├── src/main/java/
    └── target/classes/
```

## 使用演示

### 1. 创建演示目录结构
```bash
chmod +x create-demo-structure.sh
./create-demo-structure.sh
```

### 2. 启动应用
```bash
mvn spring-boot:run
```

### 3. 测试路径发现
```bash
# 查看自动发现的路径
curl "http://localhost:8080/api/coverage/app-config?appName=user-service" | jq '.'
curl "http://localhost:8080/api/coverage/app-config?appName=order-service" | jq '.'
curl "http://localhost:8080/api/coverage/app-config?appName=payment-service" | jq '.'

# 查看特殊配置的应用
curl "http://localhost:8080/api/coverage/app-config?appName=special-service" | jq '.'
```

### 4. 查看服务状态
```bash
curl "http://localhost:8080/api/coverage/status" | jq '.'
```

## API响应示例

### 自动发现的应用
```json
{
  "success": true,
  "appName": "user-service",
  "agentHost": "localhost",
  "agentPort": 6300,
  "sourceDirectories": [
    "/Users/yourname/project/user-service/src/main/java",
    "/Users/yourname/project/user-service/user-service-core/src/main/java",
    "/Users/yourname/project/user-service/user-service-api/src/main/java"
  ],
  "classDirectories": [
    "/Users/yourname/project/user-service/target/classes",
    "/Users/yourname/project/user-service/user-service-core/target/classes",
    "/Users/yourname/project/user-service/user-service-api/target/classes"
  ],
  "sourceDirCount": 3,
  "classDirCount": 3,
  "baseProjectPath": "~/project",
  "isAutoDiscovered": true
}
```

### 配置文件中的应用
```json
{
  "success": true,
  "appName": "special-service",
  "agentHost": "localhost",
  "agentPort": 6300,
  "sourceDirectories": [
    "/custom/path/special-service/src/main/java",
    "/custom/path/special-service-api/src/main/java"
  ],
  "classDirectories": [
    "/custom/path/special-service/target/classes",
    "/custom/path/special-service-api/target/classes"
  ],
  "sourceDirCount": 2,
  "classDirCount": 2,
  "baseProjectPath": "~/project",
  "isAutoDiscovered": false
}
```

## 路径发现逻辑

### 1. 查找策略
1. **优先检查配置文件**: 如果应用在 `coverage.applications` 中配置了，直接使用配置
2. **自动路径发现**: 在 `{baseProjectPath}/{appName}/` 下递归搜索
3. **模式匹配**: 查找所有 `src/main/java` 和 `target/classes` 目录
4. **绝对路径**: 返回完整的绝对路径

### 2. 搜索范围
- **深度限制**: 递归搜索所有子目录
- **模式匹配**: 精确匹配 `src/main/java` 和 `target/classes`
- **多模块支持**: 自动发现Maven多模块项目的所有模块

### 3. 容错处理
- **目录不存在**: 返回默认路径配置
- **权限问题**: 记录警告并跳过
- **空目录**: 正常处理，返回空列表

## 最佳实践

### 1. 标准化项目结构
- 使用标准的Maven/Gradle项目结构
- 保持 `src/main/java` 和 `target/classes` 命名一致
- 多模块项目遵循标准命名规范

### 2. 基础路径配置
- 设置合适的 `base-project-path`
- 确保路径具有读取权限
- 使用绝对路径或HOME目录相对路径

### 3. 特殊应用配置
- 只为非标准结构的应用配置路径
- 使用绝对路径避免歧义
- 定期验证配置的有效性

## 故障排查

### 1. 路径未发现
- 检查基础项目路径是否正确
- 确认目录结构是否符合标准
- 验证目录权限是否可读

### 2. 路径不正确
- 检查是否有特殊字符或空格
- 确认符号链接是否正确
- 验证路径大小写是否匹配

### 3. 性能问题
- 限制搜索深度
- 排除不必要的目录
- 考虑使用缓存机制

---

**注意**: 这个功能大大简化了多应用的配置管理，让你专注于覆盖率收集本身，而不是繁琐的路径配置。 