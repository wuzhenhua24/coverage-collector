package com.mofari.coveragecollector.util;

import com.mofari.coveragecollector.config.CoverageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 路径发现测试工具类
 */
@Component
public class PathDiscoveryTester {
    
    private static final Logger logger = LoggerFactory.getLogger(PathDiscoveryTester.class);
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    @PostConstruct
    public void testPathDiscovery() {
        logger.info("=== 路径发现功能测试 ===");
        logger.info("基础项目路径: {}", coverageConfig.getBaseProjectPath());
        
        // 测试几个示例应用
        String[] testApps = {"user-service", "order-service", "payment-service", "special-service"};
        
        for (String appName : testApps) {
            testApplicationPaths(appName);
        }
        
        logger.info("=== 路径发现测试完成 ===");
    }
    
    private void testApplicationPaths(String appName) {
        logger.info("--- 测试应用: {} ---", appName);
        
        try {
            CoverageConfig.ApplicationConfig config = coverageConfig.getApplicationConfig(appName);
            
            logger.info("应用名: {}", config.getName());
            logger.info("JaCoCo端口: {}", config.getAgentPort());
            logger.info("源码目录数量: {}", config.getSourceDirectories().size());
            logger.info("Class目录数量: {}", config.getClassDirectories().size());
            
            if (!config.getSourceDirectories().isEmpty()) {
                logger.info("源码目录:");
                for (String dir : config.getSourceDirectories()) {
                    logger.info("  - {}", dir);
                }
            }
            
            if (!config.getClassDirectories().isEmpty()) {
                logger.info("Class目录:");
                for (String dir : config.getClassDirectories()) {
                    logger.info("  - {}", dir);
                }
            }
            
        } catch (Exception e) {
            logger.error("测试应用 {} 时发生错误", appName, e);
        }
        
        logger.info("");
    }
} 