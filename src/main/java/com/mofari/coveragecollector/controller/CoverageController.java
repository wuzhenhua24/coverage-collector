package com.mofari.coveragecollector.controller;

import com.mofari.coveragecollector.service.DumpMergeService;
import com.mofari.coveragecollector.service.JaCoCoClientService;
import com.mofari.coveragecollector.service.ReportGeneratorService;
import com.mofari.coveragecollector.service.MultiNodeCoverageService;
import com.mofari.coveragecollector.config.CoverageConfig;
import com.mofari.coveragecollector.model.incremental.IncrementalCoverageReport;
import com.mofari.coveragecollector.model.incremental.FileCoverage;
import com.mofari.coveragecollector.model.FullCoverageReport;
import com.mofari.coveragecollector.service.SonarQubeIntegrationService.SonarAnalysisResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coverage")
public class CoverageController {
    
    private static final Logger logger = LoggerFactory.getLogger(CoverageController.class);
    
    @Autowired
    private JaCoCoClientService jaCoCoClientService;
    
    @Autowired
    private ReportGeneratorService reportGeneratorService;
    
    @Autowired
    private DumpMergeService dumpMergeService;
    
    @Autowired
    private MultiNodeCoverageService multiNodeCoverageService;
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    /**
     * 收集覆盖率数据
     * @param appName 应用名称
     * @param tag 版本标签
     * @param agentHost JaCoCo agent主机地址（可选）
     * @param agentPort JaCoCo agent端口（可选）
     * @return 响应结果
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collectCoverage(
            @RequestParam String appName,
            @RequestParam String tag,
            @RequestParam(required = false) String agentHost,
            @RequestParam(required = false) Integer agentPort) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到覆盖率收集请求，应用: {}, 标签: {}", appName, tag);
            String dumpFilePath = jaCoCoClientService.collectCoverageData(appName, tag, agentHost, agentPort);
            
            response.put("success", true);
            response.put("message", "覆盖率数据收集成功");
            response.put("appName", appName);
            response.put("tag", tag);
            response.put("dumpFilePath", dumpFilePath);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("收集覆盖率数据失败", e);
            
            response.put("success", false);
            response.put("message", "收集覆盖率数据失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 重置覆盖率数据
     * @param appName 应用名称
     * @param agentHost JaCoCo agent主机地址（可选）
     * @param agentPort JaCoCo agent端口（可选）
     * @return 响应结果
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetCoverage(
            @RequestParam String appName,
            @RequestParam(required = false) String agentHost,
            @RequestParam(required = false) Integer agentPort) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到覆盖率重置请求，应用: {}", appName);
            jaCoCoClientService.resetCoverageData(appName, agentHost, agentPort);
            
            response.put("success", true);
            response.put("message", "覆盖率数据重置成功");
            response.put("appName", appName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("重置覆盖率数据失败", e);
            
            response.put("success", false);
            response.put("message", "重置覆盖率数据失败: " + e.getMessage());
            response.put("appName", appName);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 生成覆盖率报告
     * @param appName 应用名称
     * @param clusterName 集群名
     * @param tag 版本标签
     * @param dumpFilePath dump文件路径（可选）
     * @param mergeAllDumps 是否合并同tag下的所有dump文件
     * @return 响应结果
     */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> generateReport(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag,
            @RequestParam(value = "dumpFilePath", required = false) String dumpFilePath,
            @RequestParam(value = "mergeAllDumps", defaultValue = "false") boolean mergeAllDumps) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到生成报告请求，应用: {}, 集群: {},标签: {}, 合并dump: {}", appName, clusterName, tag, mergeAllDumps);
            
            FullCoverageReport coverageReport = reportGeneratorService.generateReport(appName, clusterName, tag, dumpFilePath, mergeAllDumps);
            
            response.put("success", true);
            response.put("message", "覆盖率报告生成成功");
            response.put("appName", appName);
            response.put("tag", tag);
            response.put("reportPath", coverageReport.getReportPath());
            response.put("mergedDumps", mergeAllDumps);
            response.put("coverageStats", new HashMap<String, Object>() {{
                put("totalLineCount", coverageReport.getTotalLineCount());
                put("coveredLineCount", coverageReport.getCoveredLineCount());
                put("lineCoveragePercentage", coverageReport.getLineCoveragePercentage());
            }});
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("生成覆盖率报告失败", e);
            
            response.put("success", false);
            response.put("message", "生成覆盖率报告失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 生成增量覆盖率报告 (JSON)
     * @param appName 应用名称
     * @param clusterName 集群名
     * @param tag 用于报告存储和标识的标签
     * @param baseRef Git基础引用 (例如: "master", "main", commit SHA)
     * @param newRef Git新引用 (例如: "develop", "feature/xyz", commit SHA, tag)
     * @param dumpFilePath dump文件路径（可选）
     * @param mergeAllDumps 是否合并同tag下的所有dump文件 (如果dumpFilePath未提供)
     * @return 增量覆盖率报告对象
     */
    @PostMapping("/report/incremental")
    public ResponseEntity<?> generateIncrementalReport(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag,
            @RequestParam String baseRef,
            @RequestParam String newRef,
            @RequestParam(required = false) String dumpFilePath,
            @RequestParam(defaultValue = "false") boolean mergeAllDumps) {
        
        try {
            logger.info("Received request to generate incremental coverage report. App: {}, cluster: {}, Tag: {}, BaseRef: {}, NewRef: {}, MergeDumps: {}",
                        appName, clusterName, tag, baseRef, newRef, mergeAllDumps);
            
            IncrementalCoverageReport report = reportGeneratorService.generateIncrementalReport(
                    appName, clusterName, tag, baseRef, newRef, dumpFilePath, mergeAllDumps);
            
            return ResponseEntity.ok(report);
            
        } catch (Exception e) {
            logger.error("Failed to generate incremental coverage report for app: {}, tag: {}, base: {}, new: {}. Error: {}", 
                         appName, tag, baseRef, newRef, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to generate incremental report: " + e.getMessage());
            errorResponse.put("appName", appName);
            errorResponse.put("tag", tag);
            errorResponse.put("baseRef", baseRef);
            errorResponse.put("newRef", newRef);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * Collects coverage data via Nacos from specified cluster and generates a full report.
     *
     * @param appName       Application name (required).
     * @param clusterName   Cluster/environment name for Nacos-based multi-node operation (required).
     * @param tag           Version tag (required).
     * @param mergeAllDumps Whether to merge dumps for the report (typically false).
     * @return Response entity.
     */
    @PostMapping("/collect-and-report")
    public ResponseEntity<Map<String, Object>> collectAndGenerateReport(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag,
            @RequestParam(value = "mergeAllDumps", defaultValue = "false") boolean mergeAllDumps) {
        
        Map<String, Object> response = new HashMap<>();
        String operationSummary = String.format("Nacos-driven collection for app '%s', cluster '%s', tag '%s'", appName, clusterName, tag);

        try {
            logger.info("Unified collect-and-report request (Nacos-driven): {}", operationSummary);
            
            MultiNodeCoverageService.MultiNodeCollectionResult collectionResult =
                    multiNodeCoverageService.collectFromAllNodes(appName, clusterName, tag);
            
            if (collectionResult.getTotalNodes() == 0) {
                throw new RuntimeException("No application instances found in Nacos for " + operationSummary + ". Cannot perform collection.");
            }
            if (collectionResult.getSuccessCount() == 0) {
                 throw new RuntimeException("Coverage collection failed for all " + collectionResult.getTotalNodes() + " discovered Nacos instance(s) for " + operationSummary + ".");
            }
            if (collectionResult.getFailedCount() > 0) {
                logger.warn("Multi-node collection partially succeeded for {}. Succeeded: {}, Failed: {}. Proceeding with report.", 
                            operationSummary, collectionResult.getSuccessCount(), collectionResult.getFailedCount());
            }
            response.put("collectionDetails", collectionResult);
            
            logger.info("Generating full report for {}. mergeDumps: {}", operationSummary, mergeAllDumps);
            FullCoverageReport coverageReport = reportGeneratorService.generateReport(appName, clusterName, tag, null, mergeAllDumps);
            logger.info("Full report generated: {}", coverageReport.getReportPath());
            
            response.put("success", true);
            response.put("message", "Coverage collection (Nacos) and full report generation successful. " + operationSummary);
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            response.put("tag", tag);
            response.put("reportPath", coverageReport.getReportPath());
            response.put("mergedDumpsInReport", mergeAllDumps);
            response.put("coverageStats", new HashMap<String, Object>() {{
                put("totalLineCount", coverageReport.getTotalLineCount());
                put("coveredLineCount", coverageReport.getCoveredLineCount());
                put("lineCoveragePercentage", coverageReport.getLineCoveragePercentage());
            }});
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Unified collect-and-report (Nacos-driven) failed for {}. Error: {}", operationSummary, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Unified collect-and-report (Nacos-driven) failed for " + operationSummary + ": " + e.getMessage());
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            response.put("tag", tag);
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 合并dump文件
     * @param appName 应用名称
     * @param tag 版本标签
     * @return 响应结果
     */
    @PostMapping("/merge-dumps")
    public ResponseEntity<Map<String, Object>> mergeDumpFiles(
            @RequestParam String appName,
            @RequestParam String tag) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到合并dump文件请求，应用: {}, 标签: {}", appName, tag);
            
            List<String> dumpFiles = dumpMergeService.getDumpFiles(appName, tag);
            String mergedFilePath = dumpMergeService.mergeDumpFiles(appName, tag);
            
            response.put("success", true);
            response.put("message", "dump文件合并成功");
            response.put("appName", appName);
            response.put("tag", tag);
            response.put("originalFiles", dumpFiles);
            response.put("mergedFilePath", mergedFilePath);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("合并dump文件失败", e);
            
            response.put("success", false);
            response.put("message", "合并dump文件失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取dump文件列表
     * @param appName 应用名称
     * @param tag 版本标签
     * @return 响应结果
     */
    @GetMapping("/dump-files")
    public ResponseEntity<Map<String, Object>> getDumpFiles(
            @RequestParam String appName,
            @RequestParam String tag) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<String> dumpFiles = dumpMergeService.getDumpFiles(appName, tag);
            String latestFile = dumpMergeService.getLatestDumpFile(appName, tag);
            
            response.put("success", true);
            response.put("appName", appName);
            response.put("tag", tag);
            response.put("dumpFiles", dumpFiles);
            response.put("latestFile", latestFile);
            response.put("fileCount", dumpFiles.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取dump文件列表失败", e);
            
            response.put("success", false);
            response.put("message", "获取dump文件列表失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 清理旧的dump文件
     * @param appName 应用名称
     * @param tag 版本标签
     * @param keepCount 保留的文件数量
     * @return 响应结果
     */
    @PostMapping("/cleanup-dumps")
    public ResponseEntity<Map<String, Object>> cleanupDumpFiles(
            @RequestParam String appName,
            @RequestParam String tag,
            @RequestParam(defaultValue = "5") int keepCount) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到清理dump文件请求，应用: {}, 标签: {}, 保留数量: {}", appName, tag, keepCount);
            
            List<String> beforeFiles = dumpMergeService.getDumpFiles(appName, tag);
            dumpMergeService.cleanupOldDumpFiles(appName, tag, keepCount);
            List<String> afterFiles = dumpMergeService.getDumpFiles(appName, tag);
            
            response.put("success", true);
            response.put("message", "dump文件清理完成");
            response.put("appName", appName);
            response.put("tag", tag);
            response.put("beforeCount", beforeFiles.size());
            response.put("afterCount", afterFiles.size());
            response.put("deletedCount", beforeFiles.size() - afterFiles.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("清理dump文件失败", e);
            
            response.put("success", false);
            response.put("message", "清理dump文件失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取服务状态
     * @return 响应结果
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("success", true);
            response.put("message", "覆盖率收集服务运行正常");
            response.put("service", "coverage-collector");
            response.put("version", "2.0.0");
            response.put("features", new String[]{
                "multi-module-support", 
                "app-tag-organization", 
                "dump-file-merging", 
                "automatic-cleanup",
                "multi-node-support",
                "nacos-integration",
                "auto-path-discovery"
            });
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取服务状态失败", e);
            
            response.put("success", false);
            response.put("message", "获取服务状态失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取应用路径配置信息
     * @param appName 应用名称
     * @return 响应结果
     */
    @GetMapping("/app-config")
    public ResponseEntity<Map<String, Object>> getApplicationConfig(
            @RequestParam String appName) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("获取应用配置信息，应用: {}", appName);
            
            CoverageConfig.ApplicationConfig config = coverageConfig.getApplicationConfig(appName);
            
            response.put("success", true);
            response.put("appName", config.getName());
            response.put("agentHost", config.getAgentHost());
            response.put("agentPort", config.getAgentPort());
            response.put("sourceDirectories", config.getSourceDirectories());
            response.put("classDirectories", config.getClassDirectories());
            response.put("sourceDirCount", config.getSourceDirectories().size());
            response.put("classDirCount", config.getClassDirectories().size());
            response.put("baseProjectPath", coverageConfig.getBaseProjectPath());
            response.put("isAutoDiscovered", !coverageConfig.getApplications().containsKey(appName));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取应用配置信息失败", e);
            
            response.put("success", false);
            response.put("message", "获取应用配置信息失败: " + e.getMessage());
            response.put("appName", appName);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // 保持向后兼容的旧版API
    @Deprecated
    @PostMapping("/collect-legacy")
    public ResponseEntity<Map<String, Object>> collectCoverageLegacy() {
        return collectCoverage("default", "latest", null, null);
    }
    
    @Deprecated
    @PostMapping("/reset-legacy")
    public ResponseEntity<Map<String, Object>> resetCoverageLegacy() {
        return resetCoverage("default", null, null);
    }
    
    /**
     * 从所有节点收集覆盖率数据（多节点支持）
     * @param appName 应用名称
     * @param clusterName 集群名称
     * @param tag 版本标签
     * @return 响应结果
     */
    @PostMapping("/collect-multi-node")
    public ResponseEntity<Map<String, Object>> collectCoverageFromAllNodes(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到多节点覆盖率收集请求，应用: {}, 集群: {}, 标签: {}", appName, clusterName, tag);
            
            // 使用真实的多节点收集服务
            MultiNodeCoverageService.MultiNodeCollectionResult result = 
                    multiNodeCoverageService.collectFromAllNodes(appName, clusterName, tag);
            
            response.put("success", true);
            response.put("message", "多节点覆盖率数据收集完成");
            response.put("appName", result.getAppName());
            response.put("clusterName", result.getClusterName());
            response.put("tag", result.getTag());
            response.put("totalNodes", result.getTotalNodes());
            response.put("successCount", result.getSuccessCount());
            response.put("failedCount", result.getFailedCount());
            response.put("successfulDumps", result.getSuccessfulDumps());
            response.put("failedNodes", result.getFailedNodes());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("多节点覆盖率收集失败", e);
            
            response.put("success", false);
            response.put("message", "多节点覆盖率收集失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            response.put("tag", tag);
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 重置所有节点的覆盖率数据
     * @param appName 应用名称
     * @param clusterName 集群名称
     * @return 响应结果
     */
    @PostMapping("/reset-multi-node")
    public ResponseEntity<Map<String, Object>> resetCoverageFromAllNodes(
            @RequestParam String appName,
            @RequestParam String clusterName) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("收到多节点覆盖率重置请求，应用: {}, 集群: {}", appName, clusterName);
            
            // 使用真实的多节点重置服务
            MultiNodeCoverageService.MultiNodeResetResult result = 
                    multiNodeCoverageService.resetAllNodes(appName, clusterName);
            
            response.put("success", true);
            response.put("message", "多节点覆盖率数据重置完成");
            response.put("appName", result.getAppName());
            response.put("clusterName", result.getClusterName());
            response.put("totalNodes", result.getTotalNodes());
            response.put("successCount", result.getSuccessCount());
            response.put("failedCount", result.getFailedCount());
            response.put("failedNodes", result.getFailedNodes());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("多节点覆盖率重置失败", e);
            
            response.put("success", false);
            response.put("message", "多节点覆盖率重置失败: " + e.getMessage());
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Collects coverage data via Nacos from specified cluster and generates an incremental report.
     *
     * @param appName      Application name (required).
     * @param clusterName  Cluster/environment name for Nacos-based multi-node operation (required).
     * @param tag          Tag for collection and report (required).
     * @param baseRef      Base Git reference (required).
     * @return Response entity with IncrementalCoverageReport.
     */
    @PostMapping("/collect-and-report-incremental")
    public ResponseEntity<Map<String, Object>> collectAndGenerateIncrementalReport(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag,
            @RequestParam String baseRef) {

        Map<String, Object> response = new HashMap<>();
        String operationSummary = String.format("Nacos-driven collection for incremental report. App: '%s', Cluster: '%s', Tag (as newRef): '%s', BaseRef: '%s'", appName, clusterName, tag, baseRef);

        try {
            logger.info("Unified collect-and-report-incremental (Nacos-driven): {}", operationSummary);
            
            MultiNodeCoverageService.MultiNodeCollectionResult collectionResult =
                    multiNodeCoverageService.collectFromAllNodes(appName, clusterName, tag);

            if (collectionResult.getTotalNodes() == 0) {
                throw new RuntimeException("No application instances found in Nacos for " + operationSummary + ". Cannot perform collection.");
            }
            if (collectionResult.getSuccessCount() == 0) {
                throw new RuntimeException("Coverage collection failed for all " + collectionResult.getTotalNodes() + " discovered Nacos instance(s) for " + operationSummary + ".");
            }
            if (collectionResult.getFailedCount() > 0) {
                logger.warn("Multi-node collection for incremental report partially succeeded for {}. Succeeded: {}, Failed: {}. Proceeding.", 
                            operationSummary, collectionResult.getSuccessCount(), collectionResult.getFailedCount());
            }
            
            logger.info("Generating incremental report for {}. BaseRef: {}, NewRef (from tag): {}, MergeDumps: true", 
                        operationSummary, baseRef, tag);
            IncrementalCoverageReport report = reportGeneratorService.generateIncrementalReport(
                    appName,
                    clusterName, 
                    tag,
                    baseRef,
                    tag,       // Use tag as newRef for the service call
                    null,       
                    true        
            );

            // 构建简化的响应
            response.put("success", true);
            response.put("message", "Coverage collection and incremental report generation successful");
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            response.put("tag", tag);
            response.put("baseRef", baseRef);
            response.put("reportPath", report.getReportPath());
            
            // 添加文件级别的覆盖率统计
            List<Map<String, Object>> fileStats = new ArrayList<>();
            for (FileCoverage file : report.getFiles()) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("filePath", file.getFilePath());
                fileInfo.put("summary", file.getSummary());
                fileStats.add(fileInfo);
            }
            response.put("files", fileStats);
            
            // 添加总体覆盖率统计
            response.put("overallStats", report.getOverallStats());
            
            // 添加收集结果统计
            response.put("collectionStats", new HashMap<String, Object>() {{
                put("totalNodes", collectionResult.getTotalNodes());
                put("successCount", collectionResult.getSuccessCount());
                put("failedCount", collectionResult.getFailedCount());
            }});

            logger.info("Unified collection (Nacos) and incremental report generation successful for {}", operationSummary);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Unified collect-and-report-incremental (Nacos-driven) failed for {}. Error: {}", operationSummary, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Unified collect-and-report-incremental (Nacos-driven) failed for " + operationSummary + ": " + e.getMessage());
            response.put("appName", appName);
            response.put("clusterName", clusterName);
            response.put("tag", tag);
            response.put("baseRef", baseRef);
            return ResponseEntity.status(500).body(response);
        }
    }

    // This endpoint is now deprecated in favor of /collect-and-report-incremental
    @Deprecated
    @PostMapping("/collect-multi-node-and-report-incremental")
    public ResponseEntity<?> oldCollectMultiNodeAndGenerateIncrementalReport(
            @RequestParam String appName,
            @RequestParam String clusterName,
            @RequestParam String tag,
            @RequestParam String baseRef,
            @RequestParam String newRef) {
        logger.warn("Endpoint /collect-multi-node-and-report-incremental is deprecated. Use /collect-and-report-incremental instead.");
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", "Endpoint /collect-multi-node-and-report-incremental is deprecated. Please use /collect-and-report-incremental.");
        return ResponseEntity.status(410).body(errorResponse); // 410 Gone
    }

    /**
     * Triggers a full coverage analysis and integrates the result with SonarQube.
     *
     * @param projectKey The unique key of the project in SonarQube (e.g., appid).
     * @param appName The name of the application.
     * @param clusterName The name of the cluster.
     * @param tag The git tag or branch name of the code version being analyzed.
     * @param specificDumpFilePath Optional path to a specific dump file.
     * @param mergeAllDumps Whether to merge all available dump files.
     * @return A JSON object containing the SonarQube analysis URL and the coverage percentage.
     */
    @PostMapping("/sonar-reports/full")
    public ResponseEntity<?> generateFullReportWithSonar(
            @RequestParam String projectKey,
            @RequestParam String appName,
            @RequestParam(required = false) String clusterName,
            @RequestParam String tag,
            @RequestParam(required = false) String specificDumpFilePath,
            @RequestParam(defaultValue = "false") boolean mergeAllDumps) {
        try {
            SonarAnalysisResult result = reportGeneratorService.generateFullReportWithSonar(
                    projectKey, appName, clusterName, tag, specificDumpFilePath, mergeAllDumps
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to generate full SonarQube report.");
            errorResponse.put("details", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Triggers an incremental coverage analysis (Pull Request analysis) and integrates with SonarQube.
     *
     * @param projectKey The unique key of the project in SonarQube (e.g., appid).
     * @param appName The name of the application.
     * @param clusterName The name of the cluster.
     * @param baseRef The base branch for comparison (e.g., 'main' or 'develop').
     * @param newRefAsTag The feature branch or tag being analyzed.
     * @param prKey The key/ID of the pull request.
     * @param specificDumpFilePath Optional path to a specific dump file.
     * @param mergeAllDumps Whether to merge all available dump files.
     * @return A JSON object containing the SonarQube analysis URL and the coverage percentage.
     */
    @PostMapping("/sonar-reports/incremental")
    public ResponseEntity<?> generateIncrementalReportWithSonar(
            @RequestParam(required = false) String projectKey,
            @RequestParam String appName,
            @RequestParam(required = false) String clusterName,
            @RequestParam String baseRef,
            @RequestParam String newRefAsTag,
            @RequestParam String prKey, // Pull Request Key
            @RequestParam(required = false) String specificDumpFilePath,
            @RequestParam(defaultValue = "false") boolean mergeAllDumps) {
        try {
            SonarAnalysisResult result = reportGeneratorService.generateIncrementalReportWithSonar(
                    projectKey, appName, clusterName, baseRef, newRefAsTag, prKey, specificDumpFilePath, mergeAllDumps
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to generate incremental SonarQube report.");
            errorResponse.put("details", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
} 