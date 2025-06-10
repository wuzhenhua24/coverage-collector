package com.mofari.coveragecollector.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "coverage")
public class CoverageConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(CoverageConfig.class);
    
    /**
     * JaCoCo agent的地址
     */
    private String agentHost = "localhost";
    
    /**
     * JaCoCo agent的端口
     */
    private int agentPort = 6300;
    
    /**
     * 基础项目路径，所有应用都在这个路径下
     */
    private String baseProjectPath = "~/project";
    
    /**
     * 应用源码路径列表 (支持多模块，可以从打包机获取)
     */
    private List<String> sourceDirectories = new ArrayList<>();
    
    /**
     * 应用class文件路径列表 (支持多模块，可以从打包机获取)
     */
    private List<String> classDirectories = new ArrayList<>();
    
    /**
     * 报告输出目录根路径
     */
    private String reportOutputDirectory = "./coverage-reports";
    
    /**
     * dump文件保存目录根路径
     */
    private String dumpDirectory = "./dump-files";
    
    /**
     * 多应用配置
     */
    private Map<String, ApplicationConfig> applications = new HashMap<>();
    
    /**
     * jacoco端口映射配置
     */
    @Value("#{${coverage.jacoco-ports:{}}}")
    private Map<String, Integer> jacocoPortsMap = new HashMap<>();
    
    /**
     * 自动发现应用的源码和class路径
     * @param appName 应用名称
     * @return 应用配置
     */
    public ApplicationConfig getApplicationConfig(String appName) {
        // 1. 优先从配置文件获取
        ApplicationConfig config = applications.get(appName);
        if (config != null) {
            logger.debug("从配置文件获取应用配置: {}", appName);
            return config;
        }
        
        // 2. 自动发现路径
        logger.info("自动发现应用路径: {}", appName);
        return discoverApplicationPaths(appName);
    }
    
    /**
     * 自动发现应用的源码和class路径
     */
    private ApplicationConfig discoverApplicationPaths(String appName) {
        ApplicationConfig config = new ApplicationConfig();
        config.setName(appName);
        config.setAgentHost(agentHost);
        config.setAgentPort(getJacocoPort(appName));
        
        // 解析基础路径
        String basePath = expandPath(baseProjectPath);
        String appPath = basePath + File.separator + appName;
        
        logger.debug("搜索应用路径: {}", appPath);
        
        File appDir = new File(appPath);
        if (!appDir.exists() || !appDir.isDirectory()) {
            logger.warn("应用目录不存在: {}", appPath);
            // 返回默认配置
            config.setSourceDirectories(sourceDirectories);
            config.setClassDirectories(classDirectories);
            return config;
        }
        
        // 自动发现源码路径
        List<String> discoveredSourceDirs = findDirectories(appDir, "src/main/java");
        List<String> discoveredClassDirs = findDirectories(appDir, "target/classes");
        
        config.setSourceDirectories(discoveredSourceDirs);
        config.setClassDirectories(discoveredClassDirs);
        
        logger.info("应用 {} 自动发现路径完成，源码目录: {}, class目录: {}", 
                   appName, discoveredSourceDirs.size(), discoveredClassDirs.size());
        
        return config;
    }
    
    /**
     * 递归查找指定模式的目录
     */
    private List<String> findDirectories(File rootDir, String targetPattern) {
        List<String> foundPaths = new ArrayList<>();
        String[] patternParts = targetPattern.split("/");
        
        findDirectoriesRecursive(rootDir, patternParts, 0, foundPaths);
        
        return foundPaths;
    }
    
    /**
     * 递归查找目录的实现
     */
    private void findDirectoriesRecursive(File currentDir, String[] patternParts, int depth, List<String> foundPaths) {
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            return;
        }
        
        if (depth >= patternParts.length) {
            foundPaths.add(currentDir.getAbsolutePath());
            return;
        }
        
        String currentPattern = patternParts[depth];
        File[] children = currentDir.listFiles();
        
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && child.getName().equals(currentPattern)) {
                    findDirectoriesRecursive(child, patternParts, depth + 1, foundPaths);
                }
            }
        }
    }
    
    /**
     * 展开路径中的~符号
     */
    private String expandPath(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
    
    /**
     * 获取应用的jacoco端口，如果没有配置则返回默认端口6300
     */
    public int getJacocoPort(String appName) {
        // Try app-specific config first for port
        ApplicationConfig appConfig = applications.get(appName);
        if (appConfig != null && appConfig.getAgentPort() != 0 && appConfig.getAgentPort() != 6300) { // Check if explicitly set and not default
            return appConfig.getAgentPort();
        }
        // Then try jacocoPortsMap (for potential overrides or different port discovery mechanisms)
        Integer portFromMap = jacocoPortsMap.get(appName);
        if (portFromMap != null) {
            return portFromMap;
        }
        // Fallback to global default if any specific app config exists but port isn't set there
        if (appConfig != null) {
            return appConfig.getAgentPort(); // Will be the default 6300 if not overridden in appConfig
        }
        // Global default port if no app specific config and not in map
        return this.agentPort; 
    }
    
    /**
     * 单个应用配置类
     */
    public static class ApplicationConfig {
        private String name;
        private String agentHost = "localhost";
        private int agentPort = 6300;
        private String clusterName;             // Optional: For multi-node identification and Nacos discovery
        private List<String> sourceDirectories = new ArrayList<>();
        private List<String> classDirectories = new ArrayList<>();
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getAgentHost() {
            return agentHost;
        }
        
        public void setAgentHost(String agentHost) {
            this.agentHost = agentHost;
        }
        
        public int getAgentPort() {
            return agentPort;
        }
        
        public void setAgentPort(int agentPort) {
            this.agentPort = agentPort;
        }
        
        public String getClusterName() {
            return clusterName;
        }
        
        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }
        
        public List<String> getSourceDirectories() {
            return sourceDirectories;
        }
        
        public void setSourceDirectories(List<String> sourceDirectories) {
            this.sourceDirectories = sourceDirectories;
        }
        
        public List<String> getClassDirectories() {
            return classDirectories;
        }
        
        public void setClassDirectories(List<String> classDirectories) {
            this.classDirectories = classDirectories;
        }
    }
    
    // Getters and Setters
    public String getAgentHost() {
        return agentHost;
    }
    
    public void setAgentHost(String agentHost) {
        this.agentHost = agentHost;
    }
    
    public int getAgentPort() {
        return agentPort;
    }
    
    public void setAgentPort(int agentPort) {
        this.agentPort = agentPort;
    }
    
    public String getBaseProjectPath() {
        return baseProjectPath;
    }
    
    public void setBaseProjectPath(String baseProjectPath) {
        this.baseProjectPath = baseProjectPath;
    }
    
    public List<String> getSourceDirectories() {
        return sourceDirectories;
    }
    
    public void setSourceDirectories(List<String> sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }
    
    public List<String> getClassDirectories() {
        return classDirectories;
    }
    
    public void setClassDirectories(List<String> classDirectories) {
        this.classDirectories = classDirectories;
    }
    
    public String getReportOutputDirectory() {
        return reportOutputDirectory;
    }
    
    public void setReportOutputDirectory(String reportOutputDirectory) {
        this.reportOutputDirectory = reportOutputDirectory;
    }
    
    public String getDumpDirectory() {
        return dumpDirectory;
    }
    
    public void setDumpDirectory(String dumpDirectory) {
        this.dumpDirectory = dumpDirectory;
    }
    
    public Map<String, ApplicationConfig> getApplications() {
        return applications;
    }
    
    public void setApplications(Map<String, ApplicationConfig> applications) {
        this.applications = applications;
    }
    
    public Map<String, Integer> getJacocoPortsMap() {
        return jacocoPortsMap;
    }
    
    public void setJacocoPortsMap(Map<String, Integer> jacocoPortsMap) {
        this.jacocoPortsMap = jacocoPortsMap;
    }
} 