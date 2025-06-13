package com.mofari.coveragecollector.service;

import com.mofari.coveragecollector.config.CoverageConfig;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.runtime.RemoteControlReader;
import org.jacoco.core.runtime.RemoteControlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class MultiNodeCoverageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeCoverageService.class);
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    @Autowired
    private NacosDiscoveryService nacosDiscoveryService;
    
    /**
     * 从所有节点收集覆盖率数据
     */
    public MultiNodeCollectionResult collectFromAllNodes(String appName, String clusterName, String tag) throws Exception {
        logger.info("开始从所有节点收集覆盖率数据，应用: {}, 集群: {}, 标签: {}", appName, clusterName, tag);
        
        // 从Nacos获取节点信息
        List<NacosDiscoveryService.ApplicationInstance> instances = 
                nacosDiscoveryService.getApplicationInstances(appName, clusterName);
        
        MultiNodeCollectionResult result = new MultiNodeCollectionResult();
        result.setAppName(appName);
        result.setClusterName(clusterName);
        result.setTag(tag);
        result.setTotalNodes(instances.size());
        
        if (instances.isEmpty()) {
            logger.warn("未找到应用实例，appName: {}, clusterName: {}", appName, clusterName);
            result.setSuccessfulDumps(new ArrayList<>());
            result.setFailedNodes(new ArrayList<>());
            result.setSuccessCount(0);
            result.setFailedCount(0);
            return result;
        }
        
        List<String> successfulDumps = new ArrayList<>();
        List<String> failedNodes = new ArrayList<>();
        
        // 并行收集各节点数据
        for (NacosDiscoveryService.ApplicationInstance instance : instances) {
            try {
                logger.info("正在从节点收集数据: {} ({}:{})", 
                           instance.getNodeId(), instance.getIp(), instance.getJacocoPort());
                
                String dumpFilePath = collectFromSingleNode(appName, clusterName, tag, instance);
                successfulDumps.add(dumpFilePath);
                
                logger.info("节点 {} 数据收集成功", instance.getNodeId());
                
            } catch (Exception e) {
                String errorMsg = String.format("节点 %s 数据收集失败: %s", 
                                               instance.getNodeId(), e.getMessage());
                logger.error(errorMsg, e);
                failedNodes.add(instance.getNodeId());
            }
        }

        //如果有多个节点成功，需要合并各个节点最新的文件，不然后续获得最新的dump文件就只有一个节点
        if (successfulDumps.size() > 1) {
            logger.info("Merging all node dump files");
            String mergedPath = MultiNodeCollectionResult.getMergedAllNodeLatestDumpFilePath(successfulDumps);
            result.setMergedAllNodeDumpFilePath(mergedPath);
        }
        
        result.setSuccessfulDumps(successfulDumps);
        result.setFailedNodes(failedNodes);
        result.setSuccessCount(successfulDumps.size());
        result.setFailedCount(failedNodes.size());
        
        logger.info("多节点数据收集完成，成功: {}, 失败: {}", 
                   result.getSuccessCount(), result.getFailedCount());
        
        return result;
    }
    
    /**
     * 从单个节点收集数据
     */
    private String collectFromSingleNode(String appName, String clusterName, String tag, 
                                        NacosDiscoveryService.ApplicationInstance instance) throws Exception {
        // 创建dump目录
        File dumpDir = new File(coverageConfig.getDumpDirectory(), 
                               appName + "/" + clusterName + "/" + tag);
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }
        
        // 生成dump文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        String dumpFileName = String.format("jacoco_%s_%s.exec", instance.getNodeId(), timestamp);
        File dumpFile = new File(dumpDir, dumpFileName);
        
        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        SessionInfoStore sessionInfoStore = new SessionInfoStore();
        
        // 连接到JaCoCo agent
        try (Socket socket = new Socket(instance.getIp(), instance.getJacocoPort())) {
            RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
            RemoteControlReader reader = new RemoteControlReader(socket.getInputStream());
            
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.setExecutionDataVisitor(executionDataStore);
            
            writer.visitDumpCommand(true, false);
            
            if (!reader.read()) {
                throw new IOException("读取数据失败");
            }
        }
        
        // 保存dump文件
        try (FileOutputStream fos = new FileOutputStream(dumpFile)) {
            org.jacoco.core.data.ExecutionDataWriter writer = 
                new org.jacoco.core.data.ExecutionDataWriter(fos);
            sessionInfoStore.accept(writer);
            executionDataStore.accept(writer);
        }
        
        return dumpFile.getAbsolutePath();
    }
    
    /**
     * 重置所有节点的覆盖率数据
     */
    public MultiNodeResetResult resetAllNodes(String appName, String clusterName) {
        logger.info("开始重置所有节点的覆盖率数据，应用: {}, 集群: {}", appName, clusterName);
        
        List<NacosDiscoveryService.ApplicationInstance> instances = 
                nacosDiscoveryService.getApplicationInstances(appName, clusterName);
        
        MultiNodeResetResult result = new MultiNodeResetResult();
        result.setAppName(appName);
        result.setClusterName(clusterName);
        result.setTotalNodes(instances.size());
        
        if (instances.isEmpty()) {
            logger.warn("未找到应用实例，appName: {}, clusterName: {}", appName, clusterName);
            result.setFailedNodes(new ArrayList<>());
            result.setSuccessCount(0);
            result.setFailedCount(0);
            return result;
        }
        
        List<String> failedNodes = new ArrayList<>();
        
        // 重置每个节点的数据
        for (NacosDiscoveryService.ApplicationInstance instance : instances) {
            try {
                logger.info("正在重置节点数据: {} ({}:{})", 
                           instance.getNodeId(), instance.getIp(), instance.getJacocoPort());
                
                try (Socket socket = new Socket(instance.getIp(), instance.getJacocoPort())) {
                    RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
                    
                    // 发送重置命令
                    writer.visitDumpCommand(false, true);
                    
                    logger.info("节点 {} 数据重置成功", instance.getNodeId());
                }
                
            } catch (Exception e) {
                String errorMsg = String.format("节点 %s 数据重置失败: %s", 
                                               instance.getNodeId(), e.getMessage());
                logger.error(errorMsg, e);
                failedNodes.add(instance.getNodeId());
            }
        }
        
        result.setFailedNodes(failedNodes);
        result.setSuccessCount(instances.size() - failedNodes.size());
        result.setFailedCount(failedNodes.size());
        
        logger.info("多节点数据重置完成，成功: {}, 失败: {}", result.getSuccessCount(), result.getFailedCount());
        
        return result;
    }
    
    // 结果类定义
    public static class MultiNodeCollectionResult {
        private String appName;
        private String clusterName;
        private String tag;
        private int totalNodes;
        private int successCount;
        private int failedCount;
        private List<String> successfulDumps;
        private String mergedAllNodeDumpFilePath;
        private List<String> failedNodes;
        
        // Getters and Setters
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }
        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }
        public int getTotalNodes() { return totalNodes; }
        public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public List<String> getSuccessfulDumps() { return successfulDumps; }
        public void setSuccessfulDumps(List<String> successfulDumps) { this.successfulDumps = successfulDumps; }
        public String getMergedAllNodeDumpFilePath() { return mergedAllNodeDumpFilePath;}
        public void setMergedAllNodeDumpFilePath(String mergedAllNodeDumpFilePath) {
            this.mergedAllNodeDumpFilePath = mergedAllNodeDumpFilePath;}
        public List<String> getFailedNodes() { return failedNodes; }
        public void setFailedNodes(List<String> failedNodes) { this.failedNodes = failedNodes; }

        public static  String getMergedAllNodeLatestDumpFilePath(List<String> successfulDumps) throws Exception {

            // 合并所有dump文件
            ExecutionDataStore mergedExecutionDataStore = new ExecutionDataStore();
            SessionInfoStore mergedSessionInfoStore = new SessionInfoStore();
            StringBuilder stringBuilder = new StringBuilder();
            for (String dumpPath : successfulDumps) {
                stringBuilder.append(getIpFromDumpPath(dumpPath)).append("_");
                try (FileInputStream fis = new FileInputStream(dumpPath)) {
                    org.jacoco.core.data.ExecutionDataReader reader =
                            new org.jacoco.core.data.ExecutionDataReader(fis);

                    reader.setSessionInfoVisitor(mergedSessionInfoStore);
                    reader.setExecutionDataVisitor(mergedExecutionDataStore);
                    reader.read();

                } catch (Exception e) {
                    throw new Exception("读取dump文件失败: " + dumpPath, e);
                }
            }

            // 生成合并后的文件名
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            String mergedFileName = String.format("jacoco_merged_%s.exec_" + stringBuilder + "%s.exec", timestamp);
            File mergedFile = new File(new File(successfulDumps.get(0)).getParent(), mergedFileName);

            // 保存合并后的数据
            try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
                org.jacoco.core.data.ExecutionDataWriter writer =
                        new org.jacoco.core.data.ExecutionDataWriter(fos);

                // 写入合并后的session信息和执行数据
                mergedSessionInfoStore.accept(writer);
                mergedExecutionDataStore.accept(writer);

                logger.info("合并后的dump文件已保存: {}", mergedFile.getAbsolutePath());

            } catch (Exception e) {
                logger.error("保存合并后的dump文件失败", e);
                throw new Exception("保存合并后的dump文件失败: " + e.getMessage(), e);
            }

            return mergedFile.getAbsolutePath();
        }

        private static String getIpFromDumpPath(String dumpPath) {
            String fileName = new File(dumpPath).getName();
            String[] parts = fileName.split("_");
            if (parts.length > 4) {
                String[] ipParts = Arrays.copyOfRange(parts, 1,5);
                return String.join("_", ipParts);
            }else {
                return null;
            }
        }
    }


    
    /**
     * 多节点重置结果类
     */
    public static class MultiNodeResetResult {
        private String appName;
        private String clusterName;
        private int totalNodes;
        private int successCount;
        private int failedCount;
        private List<String> failedNodes;
        
        // Getters and Setters
        public String getAppName() { return appName; }
        public void setAppName(String appName) { this.appName = appName; }
        public String getClusterName() { return clusterName; }
        public void setClusterName(String clusterName) { this.clusterName = clusterName; }
        public int getTotalNodes() { return totalNodes; }
        public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public List<String> getFailedNodes() { return failedNodes; }
        public void setFailedNodes(List<String> failedNodes) { this.failedNodes = failedNodes; }
    }
    
}
