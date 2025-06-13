# Java代码覆盖率收集系统 v2.1

这是一个基于JaCoCo的非侵入式Java代码覆盖率收集系统。支持**多模块项目**、**应用名和tag组织**、**dump文件合并**、**增量代码覆盖率**等高级功能。通过在被测应用启动时注入JaCoCo agent，无需修改应用代码即可收集覆盖率数据并生成报告。

## 🆕 v2.1 新功能 (在v2.0基础上)

- ✅ **增量覆盖率报告**: 支持比较两个Git引用（分支/标签/提交）之间的增量代码覆盖率，并以JSON格式输出。
- ✅ **Git集成**: 通过`git diff`识别变更的代码行。
- ✅ **相关配置**: 引入`coverage.base-project-path`用于定位项目源码。

## 🆕 v2.0 新功能

- ✅ **多模块支持**: 支持Spring Boot多模块项目的覆盖率收集
- ✅ **应用名+Tag组织**: 支持按应用名和版本标签组织文件结构
- ✅ **Dump文件合并**: 同一tag下多次收集的dump文件可智能合并
- ✅ **目录结构优化**: `~/dump-files/appname/tag` 和 `~/coverage-reports/appname/tag`
- ✅ **多应用配置**: 支持配置多个应用的不同参数
- ✅ **自动清理**: 支持自动清理旧的dump文件

## 功能特性

- 🚀 **非侵入式**: 无需修改被测应用代码
- 📊 **实时收集**: 通过JaCoCo client实时收集覆盖率数据
- 📈 **多格式报告**: 支持HTML和XML格式的覆盖率报告
- 🎛️ **REST API**: 提供简单易用的REST接口
- ⚙️ **灵活配置**: 支持多种配置方式
- 🏗️ **多模块支持**: 完美支持Spring Boot多模块项目
- 🏷️ **标签化管理**: 按应用名和版本标签组织管理文件

## 架构说明

```
多模块应用 (with JaCoCo Agent) ←→ 覆盖率收集器 ←→ 打包机 (多模块源码/class文件)
        ↓                             ↓
   多次dump收集                    智能合并
        ↓                             ↓
~/dump-files/appname/tag  →  ~/coverage-reports/appname/tag
```

## 快速开始

### 1. 启动多模块应用（注入JaCoCo Agent）

对于多模块Spring Boot应用，在启动时添加JaCoCo agent参数：

```bash
java -javaagent:jacoco-agent.jar=destfile=jacoco.exec,includes=com.yourpackage.*,output=tcpserver,port=6300 \
     -jar your-multi-module-application.jar
```

### 2. 配置覆盖率收集器（多模块支持）

修改 `src/main/resources/application.yml` 配置：

```yaml
coverage:
  # 源码根目录, 用于Git Diff操作, 例如 "/home/user/projects" 或 "D:/source/projects"
  # GitDiffService会在此目录下查找名为 appName 的Git仓库 (e.g. /home/user/projects/appName)
  base-project-path: "/path/to/your/source/code/root" 
  
  # 全局默认配置 (如果应用特定配置中未提供)
  agent-host: localhost
  # 多应用配置
  applications:
    - name: user-service
      agent-host: 192.168.1.100
      agent-port: 6300
      source-directories:
        - /build/machine/user-service/src/main/java
        - /build/machine/user-service/user-api/src/main/java
        - /build/machine/user-service/user-core/src/main/java
      class-directories:
        - /build/machine/user-service/target/classes
        - /build/machine/user-service/user-api/target/classes
        - /build/machine/user-service/user-core/target/classes
        
    - name: order-service
      agent-host: 192.168.1.101
      agent-port: 6300
      source-directories:
        - /build/machine/order-service/src/main/java
        - /build/machine/order-service/order-api/src/main/java
        - /build/machine/order-service/order-core/src/main/java
      class-directories:
        - /build/machine/order-service/target/classes
        - /build/machine/order-service/order-api/target/classes
        - /build/machine/order-service/order-core/target/classes
```

### 3. 启动覆盖率收集器

**重要**: 确保运行覆盖率收集器的主机上已安装 `git` 命令行工具，并且在系统的PATH中可访问。

```bash
mvn spring-boot:run
```

## API接口（v2.3 - Nacos驱动的统一接口）

"一键式"接口 (`/collect-and-report` 和 `/collect-and-report-incremental`) 现在**总是**通过Nacos进行服务发现和多节点操作（即使只发现一个节点）。调用方**必须**提供`clusterName`参数。

### 1. 收集覆盖率数据 (单次收集 - 手动)

```bash
POST http://localhost:8080/api/coverage/collect?appName=user-service&tag=v1.2.0
```
**参数:**
- `appName`: 应用名称（必填）
- `tag`: 版本标签（必填）
- `agentHost`: (可选) JaCoCo agent主机地址。如果未提供，则从应用配置或全局配置中获取。
- `agentPort`: (可选) JaCoCo agent端口。如果未提供，则从应用配置或全局配置中获取。

**说明:** 此接口主要用于单次、单节点的覆盖率数据收集。对于多节点应用，请参考 `/collect-multi-node` 或统一的 "收集并报告" 接口。

### 2. 生成覆盖率报告（手动指定dump来源）

```bash
POST http://localhost:8080/api/coverage/report?appName=user-service&tag=v1.2.0&mergeAllDumps=true
# 可选: &clusterName=prod-cluster
```
**参数:**
- `appName`, `tag` (必填)
- `clusterName`: (可选) 如果提供，将在 `appName/clusterName/tag` 路径下查找/合并dump文件并生成报告。
- `dumpFilePath`, `mergeAllDumps` (可选)

### 3. 生成增量覆盖率报告 (JSON - 手动指定dump来源)

此接口允许手动触发增量报告的生成，可以分别指定用于报告组织/dump文件定位的`tag`和用于Git比较的`newRef`。

```bash
POST http://localhost:8080/api/coverage/report/incremental
  ?appName=my-app
  &tag=report-for-feature-X-vs-develop  # 用于报告和dump文件路径
  &baseRef=develop                      # Git diff 基线
  &newRef=feature/xyz-branch            # Git diff 比较目标 (新状态)
  &mergeAllDumps=true
# 可选: &clusterName=prod-cluster
# 可选: &dumpFilePath=/path/to/specific.exec
```
**参数:**
- `appName`: 应用名称（必需）。
- `tag`: 版本标签（必需）。用于组织报告和dump文件的存储路径。
- `baseRef`: Git基础引用（必需）。
- `newRef`: Git新引用（必需）。用于`git diff`操作，定义比较的"新"代码状态。
- `clusterName`: (可选) 如果提供，将在 `appName/clusterName/tag` 路径下查找/合并dump文件用于增量分析。
- `dumpFilePath`: (可选) 指定单个dump文件的绝对路径。如果提供，将忽略`mergeAllDumps`。
- `mergeAllDumps`: (可选, 默认`false`) 如果`dumpFilePath`未提供，此参数决定是否合并`appName/[clusterName]/tag`下的所有`.exec`文件。

### 4. ✨ [统一] 一键收集并生成全量覆盖率报告 (Nacos驱动)

此接口**必须**提供`clusterName`。它会通过Nacos发现指定`appName`和`clusterName`下的所有节点，进行覆盖率收集，然后合并数据生成报告。IP和端口信息均来自Nacos。

```bash
POST http://localhost:8080/api/coverage/collect-and-report
  ?appName=user-service
  &clusterName=prod-cluster    # 必需: 指定集群名，触发Nacos流程
  &tag=v1.3.0
  &mergeAllDumps=false          # 通常为false
```
**参数:**
- `appName`: 应用名称（必需）。
- `clusterName`: 集群/环境名称（必需）。用于Nacos服务发现，并在`appName/clusterName/tag`下存储dump和报告。
- `tag`: 版本标签（必需）。
- `mergeAllDumps`: (可选, 默认`false`) 是否合并从各节点收集到的dump文件来生成最终报告。多节点时每次合并来自各节点的数据，但是历史数据true时才合并

**响应示例 (成功):**
```json
{
  "success": true,
  "message": "Coverage collection (Nacos) and full report generation successful. Nacos-driven collection for app 'user-service', cluster 'prod-cluster', tag 'v1.3.0'",
  "appName": "user-service",
  "clusterName": "prod-cluster",
  "tag": "v1.3.0",
  "collectionDetails": { /* ... MultiNodeCollectionResult from Nacos collection ... */ },
  "reportPath": "./coverage-reports/user-service/prod-cluster/v1.3.0/report_timestamp",
  "mergedDumpsInReport": true
}
```
**失败响应示例 (例如，Nacos中未找到实例):**
```json
{
  "success": false,
  "message": "Unified collect-and-report (Nacos-driven) failed for Nacos-driven collection for app 'user-service', cluster 'non-existent-cluster', tag 'v1.3.0': No application instances found in Nacos for Nacos-driven collection for app 'user-service', cluster 'non-existent-cluster', tag 'v1.3.0'. Cannot perform collection.",
  "appName": "user-service",
  "clusterName": "non-existent-cluster",
  "tag": "v1.3.0"
}
```

### 5. ✨ [统一] 一键收集并生成增量覆盖率报告 (JSON - Nacos驱动)

此接口**必须**提供`clusterName`。它会通过Nacos发现指定`appName`和`clusterName`下的所有节点，收集覆盖率，然后合并数据生成增量报告。API参数中的`tag`将同时用作报告/dump文件的组织标签和`git diff`中的`newRef`。

```bash
POST http://localhost:8080/api/coverage/collect-and-report-incremental
  ?appName=my-app
  &clusterName=prod-cluster    # 必需: 指定集群名
  &tag=feature-xyz             # 必需: 用于收集、报告存储，并作为增量比较的 "新" 状态 (newRef)
  &baseRef=main                # 必需: Git基础引用 (例如: master, main)
```
**参数:**
- `appName`: 应用名称（必需）。
- `clusterName`: 集群/环境名称（必需）。用于Nacos多节点收集。
- `tag`: （必需）此标签用于：
    1.  组织存储dump文件和生成的报告 (路径: `appName/[clusterName]/tag/...`)
    2.  作为与`baseRef`进行`git diff`比较的"新"代码状态 (`newRef`)。
- `baseRef`: Git基础引用（必需）。

**响应示例 (成功时返回 IncrementalCoverageReport JSON):**
```json
// ... (IncrementalCoverageReport JSON 结构体)
// report.baseRef 将是传入的 baseRef
// report.newRef 将是传入的 API tag (e.g., "feature-xyz")
// report.tag 将是传入的 API tag (e.g., "feature-xyz")
// reportPath 将会是: "./coverage-reports/my-app/prod-cluster/feature-xyz/incremental_timestamp/incremental_coverage.json"
```
**失败响应示例 (例如，Nacos中找到节点但所有节点收集失败):**
```json
{
  "success": false,
  "message": "Unified collect-and-report-incremental (Nacos-driven) failed for Nacos-driven collection for incremental report. App: 'my-app', Cluster: 'prod-cluster', Tag/NewRef: 'feature-xyz', BaseRef: 'main': Coverage collection failed...",
  "appName": "my-app",
  "clusterName": "prod-cluster",
  "tag": "feature-xyz",
  "baseRef": "main"
}
```

**注意 (统一Nacos驱动接口):**
- `clusterName` 参数对于这两个统一接口是**必需的**。
- 系统将始终尝试通过Nacos使用提供的 `appName` 和 `clusterName` 进行多节点操作。确保Nacos服务配置正确且应用实例已注册。
- 静态配置的 `coverage.applications[appName].agentHost/agentPort` **不被**这两个统一接口使用。IP和JaCoCo端口将从Nacos发现的实例中获取。
- `coverage.applications[].clusterName` 字段在 `application.yml` 中对于这两个接口的决策逻辑已无直接作用，因为`clusterName`由API参数提供。

### 6. 合并dump文件

```bash
POST http://localhost:8080/api/coverage/merge-dumps?appName=user-service&tag=v1.2.0
```

### 7. 获取dump文件列表

```bash
GET http://localhost:8080/api/coverage/dump-files?appName=user-service&tag=v1.2.0
```

**响应示例:**
```json
{
  "success": true,
  "appName": "user-service",
  "tag": "v1.2.0",
  "dumpFiles": [
    "./dump-files/user-service/v1.2.0/jacoco_20231120_143052_123.exec",
    "./dump-files/user-service/v1.2.0/jacoco_20231120_144032_456.exec",
    "./dump-files/user-service/v1.2.0/jacoco_20231120_145012_789.exec"
  ],
  "latestFile": "./dump-files/user-service/v1.2.0/jacoco_20231120_145012_789.exec",
  "fileCount": 3
}
```

### 8. 清理旧的dump文件

```bash
POST http://localhost:8080/api/coverage/cleanup-dumps?appName=user-service&tag=v1.2.0&keepCount=5
```

### 9. 多节点：单独收集 (手动)

```bash
POST http://localhost:8080/api/coverage/collect-multi-node?appName=my-app&clusterName=prod-cluster&tag=my-build-123
```
**参数:**
- `appName`, `clusterName`, `tag` (全部必填)

### 10. 多节点：单独重置 (手动)

```bash
POST http://localhost:8080/api/coverage/reset-multi-node?appName=my-app&clusterName=prod-cluster
```

### 11. [已废弃] 从多节点收集并生成增量覆盖率报告

`POST /api/coverage/collect-multi-node-and-report-incremental`

此接口已废弃，请使用统一的 `POST /api/coverage/collect-and-report-incremental` 接口，它会自动处理单节点/多节点情况。

## 目录结构

新的目录结构按应用名、集群名（可选）和tag组织：

```
项目根目录/
├── dump-files/
│   ├── user-service/                  (应用名)
│   │   ├── prod-cluster/              (集群名/环境名 - 可选, 用于多节点)
│   │   │   ├── v1.2.0/                (标签)
│   │   │   │   ├── jacoco_node1_....exec
│   │   │   │   ├── jacoco_node2_....exec
│   │   │   │   └── jacoco_merged_....exec
│   │   ├── staging-cluster/
│   │   │   └── v1.1.0/
│   │   ├── v1.0.0/                    (无集群/环境名时的结构)
│   │   │   ├── jacoco_....exec
│   │   │   └── jacoco_merged_....exec
│   │   └── order-service/
│   └── coverage-reports/
│       ├── user-service/
│       │   ├── prod-cluster/
│       │   │   └── v1.2.0/
│       │   │       └── incremental_timestamp/
│       │   │           └── incremental_coverage.json
│       │   │       └── report_timestamp/ (for full reports)
│       │   │           ├── html/
│       │   │           └── jacoco.xml
│       │   ├── v1.0.0/
│       │   │   └── incremental_timestamp/
│       │   │   └── report_timestamp/
│       │   └── order-service/
```

## 多模块项目配置示例

### Spring Boot多模块项目结构
```
my-microservice/
├── pom.xml (parent)
├── api-module/
│   ├── src/main/java/
│   └── target/classes/
├── core-module/
│   ├── src/main/java/
│   └── target/classes/
├── web-module/
│   ├── src/main/java/
│   └── target/classes/
└── application/
    ├── src/main/java/
    └── target/classes/
```

### 对应的配置
```yaml
coverage:
  applications:
    - name: my-microservice
      agent-host: localhost
      agent-port: 6300
      source-directories:
        - /build/machine/my-microservice/api-module/src/main/java
        - /build/machine/my-microservice/core-module/src/main/java
        - /build/machine/my-microservice/web-module/src/main/java
        - /build/machine/my-microservice/application/src/main/java
      class-directories:
        - /build/machine/my-microservice/api-module/target/classes
        - /build/machine/my-microservice/core-module/target/classes
        - /build/machine/my-microservice/web-module/target/classes
        - /build/machine/my-microservice/application/target/classes
```

## 使用流程（多模块项目）

### 典型的多模块项目测试流程：

1. **测试前准备**
   ```bash
   # 重置指定应用的覆盖率数据
   curl -X POST "http://localhost:8080/api/coverage/reset?appName=user-service"
   ```

2. **执行测试（多轮测试）**
   ```bash
   # 第一轮测试后收集
   curl -X POST "http://localhost:8080/api/coverage/collect?appName=user-service&tag=v1.2.0"
   
   # 执行更多测试...
   
   # 第二轮测试后收集
   curl -X POST "http://localhost:8080/api/coverage/collect?appName=user-service&tag=v1.2.0"
   
   # 第三轮测试后收集
   curl -X POST "http://localhost:8080/api/coverage/collect?appName=user-service&tag=v1.2.0"
   ```

3. **生成合并报告**
   ```bash
   # 合并同一tag下的所有dump文件并生成报告
   curl -X POST "http://localhost:8080/api/coverage/report?appName=user-service&tag=v1.2.0&mergeAllDumps=true"
   ```

4. **查看报告**
   - 打开 `./coverage-reports/user-service/v1.2.0/coverage_report_xxx/html/index.html`

## Dump文件合并说明

### 为什么需要合并？
在多轮测试中，每次调用 `/collect` 接口都会生成一个新的dump文件。这些文件包含不同测试阶段的覆盖率数据。合并这些文件可以得到完整的覆盖率信息。

### 合并策略
- **自动合并**: 设置 `mergeAllDumps=true` 时，自动合并同一tag下的所有dump文件
- **手动合并**: 调用 `/merge-dumps` 接口手动合并
- **智能去重**: JaCoCo会自动处理重复的执行数据

### 示例
```bash
# 查看当前dump文件
curl "http://localhost:8080/api/coverage/dump-files?appName=user-service&tag=v1.2.0"

# 手动合并dump文件
curl -X POST "http://localhost:8080/api/coverage/merge-dumps?appName=user-service&tag=v1.2.0"

# 使用合并后的文件生成报告
curl -X POST "http://localhost:8080/api/coverage/report?appName=user-service&tag=v1.2.0&mergeAllDumps=true"
```

## 配置说明

### 环境变量支持
```bash
export COVERAGE_AGENT_HOST_USER_SERVICE=192.168.1.100
export COVERAGE_AGENT_PORT_USER_SERVICE=6300
```

### 启动参数支持
```bash
java -jar coverage-collector.jar \
  --coverage.applications[0].name=user-service \
  --coverage.applications[0].agent-host=192.168.1.100 \
  --coverage.applications[0].source-directories[0]=/path/to/src1 \
  --coverage.applications[0].source-directories[1]=/path/to/src2
```

## 故障排查

### 多模块相关问题

1. **部分模块覆盖率为0**
   - 检查JaCoCo agent的 `includes` 配置是否包含所有模块的包名
   - 确认所有模块的class目录都在配置中

2. **合并失败**
   - 检查dump文件是否损坏
   - 确认所有dump文件来自同一应用的同一版本

3. **报告生成失败**
   - 验证所有源码目录和class目录是否存在
   - 检查是否有足够的磁盘空间

## 技术栈

- **Spring Boot 2.3.12**: Web框架
- **JaCoCo 0.8.7**: 代码覆盖率工具
- **Maven**: 构建工具  
- **Java 8**: 运行环境

## 版本兼容

- **v1.x**: 兼容旧版API（使用 `/collect-legacy`, `/reset-legacy` 等接口）
- **v2.x**: 新版API，支持多模块和应用参数

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request！特别欢迎对多模块项目支持的改进建议。 