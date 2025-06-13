package com.mofari.coveragecollector.service;

import com.mofari.coveragecollector.config.CoverageConfig;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DumpMergeService {
    
    private static final Logger logger = LoggerFactory.getLogger(DumpMergeService.class);
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    private Path getDumpDirectoryPath(String appName, String clusterName, String tag) {
        Path basePath = Paths.get(coverageConfig.getDumpDirectory(), appName);
        if (StringUtils.hasText(clusterName)) {
            return basePath.resolve(clusterName).resolve(tag);
        }
        return basePath.resolve(tag);
    }
    
    /**
     * 合并同一tag下的所有dump文件
     * @param appName 应用名称
     * @param clusterName 环境名称
     * @param tag 版本标签
     * @return 合并后的dump文件路径
     * @throws Exception
     */
    public String mergeDumpFiles(String appName, String clusterName, String tag) throws Exception {
        logger.info("开始合并dump文件，应用: {}, 环境: {}, 标签: {}", appName, clusterName, tag);
        
        // 获取tag目录下的所有dump文件
        File tagDir = getDumpDirectoryPath(appName, clusterName, tag).toFile();
        if (!tagDir.exists() || !tagDir.isDirectory()) {
            throw new FileNotFoundException("目录不存在: " + tagDir.getAbsolutePath());
        }
        
        File[] dumpFiles = tagDir.listFiles((dir, name) -> name.endsWith(".exec") && !name.startsWith("jacoco_merged_"));
        if (dumpFiles == null || dumpFiles.length == 0) {
            throw new IllegalArgumentException("未找到dump文件在目录: " + tagDir.getAbsolutePath());
        }
        
        logger.info("找到 {} 个dump文件需要合并", dumpFiles.length);
        
        // 如果只有一个文件，直接返回
        if (dumpFiles.length == 1) {
            logger.info("只有一个dump文件，无需合并: {}", dumpFiles[0].getAbsolutePath());
            return dumpFiles[0].getAbsolutePath();
        }
        
        // 合并所有dump文件
        ExecutionDataStore mergedExecutionDataStore = new ExecutionDataStore();
        SessionInfoStore mergedSessionInfoStore = new SessionInfoStore();
        
        for (File dumpFile : dumpFiles) {
            logger.debug("合并文件: {}", dumpFile.getName());
            
            try (FileInputStream fis = new FileInputStream(dumpFile)) {
                org.jacoco.core.data.ExecutionDataReader reader = 
                    new org.jacoco.core.data.ExecutionDataReader(fis);
                
                reader.setSessionInfoVisitor(mergedSessionInfoStore);
                reader.setExecutionDataVisitor(mergedExecutionDataStore);
                reader.read();
                
            } catch (Exception e) {
                logger.error("读取dump文件失败: {}", dumpFile.getName(), e);
                throw new Exception("读取dump文件失败: " + dumpFile.getName(), e);
            }
        }
        
        // 生成合并后的文件名
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        String mergedFileName = String.format("jacoco_merged_%s.exec", timestamp);
        File mergedFile = new File(tagDir, mergedFileName);
        
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
    
    /**
     * 获取指定tag下的所有dump文件
     * @param appName 应用名称
     * @param clusterName 环境名称
     * @param tag 版本标签
     * @return dump文件列表
     */
    public List<String> getDumpFiles(String appName, String clusterName, String tag) {
        File tagDir = getDumpDirectoryPath(appName, clusterName, tag).toFile();
        if (!tagDir.exists() || !tagDir.isDirectory()) {
            return Collections.emptyList();
        }
        
        File[] dumpFiles = tagDir.listFiles((dir, name) -> name.endsWith(".exec"));
        if (dumpFiles == null) {
            return Collections.emptyList();
        }
        
        return Arrays.stream(dumpFiles)
                .map(File::getAbsolutePath)
                .sorted()
                .collect(Collectors.toList());
    }
    
    /**
     * 获取最新的dump文件
     * @param appName 应用名称
     * @param clusterName 环境名称
     * @param tag 版本标签
     * @return 最新的dump文件路径，如果不存在则返回null
     */
    public String getLatestDumpFile(String appName, String clusterName, String tag) {
        List<String> dumpFiles = getDumpFiles(appName, clusterName, tag);
        if (dumpFiles.isEmpty()) {
            return null;
        }
        
        // 按文件名排序，获取最新的文件（文件名包含时间戳）
//        return dumpFiles.stream()
//                .filter(s -> s.contains("jacoco_merged_"))
//                .max(String::compareTo)
//                .orElseGet(() -> dumpFiles.stream()
//                                .filter(s -> !s.contains("jacoco_merged_"))
//                                .max(String::compareTo)
//                                .orElse(null));
        // 使用自定义比较器，该比较器提取并比较每个文件名的时间戳
        return dumpFiles.stream()
                .max(Comparator.comparing(this::extractTimestamp))
                .orElse(null);
    }

    /**
     * 从文件名中提取时间戳字符串。
     * @param fileName 文件名
     * @return 提取到的时间戳字符串，如果未找到则返回空字符串。
     */
    private String extractTimestamp(String fileName) {
        if (fileName == null) {
            return "";
        }
        Pattern TIMESTAMP_PATTERN = Pattern.compile("_(\\d{8}_\\d{6}_\\d{3})");

        Matcher matcher = TIMESTAMP_PATTERN.matcher(fileName);
        if (matcher.find()) {
            // group(1) 返回第一个捕获组的内容，即括号内的部分
            return matcher.group(1);
        }
        // 如果文件名中没有匹配到时间戳，返回空字符串
        // 这将使没有时间戳的文件在排序中被视为“最早的”
        return "";
    }

    /**
     * 清理旧的dump文件，只保留最新的几个文件
     * @param appName 应用名称
     * @param clusterName 环境名称
     * @param tag 版本标签
     * @param keepCount 保留的文件数量
     */
    public void cleanupOldDumpFiles(String appName, String clusterName, String tag, int keepCount) {
        File tagDir = getDumpDirectoryPath(appName, clusterName, tag).toFile();
        if (!tagDir.exists() || !tagDir.isDirectory()) {
            logger.warn("Dump directory for cleanup not found: {}", tagDir.getAbsolutePath());
            return;
        }
        
        // Cleanup non-merged files first
        File[] individualDumpFiles = tagDir.listFiles((dir, name) -> name.endsWith(".exec") && !name.startsWith("jacoco_merged_"));
        if (individualDumpFiles != null && individualDumpFiles.length > keepCount) {
            Arrays.sort(individualDumpFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())); // Newest first
            for (int i = keepCount; i < individualDumpFiles.length; i++) {
                if (individualDumpFiles[i].delete()) {
                    logger.info("Deleted old individual dump file: {}", individualDumpFiles[i].getAbsolutePath());
                } else {
                    logger.warn("Failed to delete old individual dump file: {}", individualDumpFiles[i].getAbsolutePath());
                }
            }
        }
        
        // Cleanup merged files, keep a smaller number, e.g., keep 2 merged files
        int keepMergedCount = Math.max(1, keepCount / 2); // Keep at least 1 merged file
        File[] mergedDumpFiles = tagDir.listFiles((dir, name) -> name.startsWith("jacoco_merged_") && name.endsWith(".exec"));
        if (mergedDumpFiles != null && mergedDumpFiles.length > keepMergedCount) {
            Arrays.sort(mergedDumpFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified())); // Newest first
            for (int i = keepMergedCount; i < mergedDumpFiles.length; i++) {
                if (mergedDumpFiles[i].delete()) {
                    logger.info("Deleted old merged dump file: {}", mergedDumpFiles[i].getAbsolutePath());
                } else {
                    logger.warn("Failed to delete old merged dump file: {}", mergedDumpFiles[i].getAbsolutePath());
                }
            }
        }
    }
    
    // --- Overloaded methods for backward compatibility (without clusterName) ---
    
    public String mergeDumpFiles(String appName, String tag) throws Exception {
        return mergeDumpFiles(appName, null, tag);
    }
    
    public List<String> getDumpFiles(String appName, String tag) {
        return getDumpFiles(appName, null, tag);
    }
    
    public String getLatestDumpFile(String appName, String tag) {
        return getLatestDumpFile(appName, null, tag);
    }
    
    public void cleanupOldDumpFiles(String appName, String tag, int keepCount) {
        cleanupOldDumpFiles(appName, null, tag, keepCount);
    }
} 