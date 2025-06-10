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
import java.util.Date;

@Service
public class JaCoCoClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(JaCoCoClientService.class);
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    /**
     * 从JaCoCo agent收集执行数据并保存dump文件
     * @param appName 应用名称
     * @param tag 版本标签
     * @return dump文件路径
     * @throws Exception
     */
    public String collectCoverageData(String appName, String tag) throws Exception {
        return collectCoverageData(appName, tag, null, null);
    }
    
    /**
     * 从JaCoCo agent收集执行数据并保存dump文件（支持指定host和port）
     * @param appName 应用名称
     * @param tag 版本标签
     * @param agentHost JaCoCo agent主机地址（可选）
     * @param agentPort JaCoCo agent端口（可选）
     * @return dump文件路径
     * @throws Exception
     */
    public String collectCoverageData(String appName, String tag, String agentHost, Integer agentPort) throws Exception {
        // 确定连接参数
        String host = agentHost != null ? agentHost : getAgentHost(appName);
        int port = agentPort != null ? agentPort : getAgentPort(appName);
        
        logger.info("开始收集覆盖率数据，应用: {}, 标签: {}, 连接到 {}:{}", 
                    appName, tag, host, port);
        
        // 创建dump目录：~/dump-files/appname/tag
        File dumpDir = new File(coverageConfig.getDumpDirectory(), appName + "/" + tag);
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }
        
        // 生成dump文件名（带时间戳）
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        String dumpFileName = String.format("jacoco_%s.exec", timestamp);
        File dumpFile = new File(dumpDir, dumpFileName);
        
        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        SessionInfoStore sessionInfoStore = new SessionInfoStore();
        
        // 连接到JaCoCo agent
        try (Socket socket = new Socket(host, port)) {
            logger.info("成功连接到JaCoCo agent {}:{}", host, port);
            
            RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
            RemoteControlReader reader = new RemoteControlReader(socket.getInputStream());
            
            reader.setSessionInfoVisitor(sessionInfoStore);
            reader.setExecutionDataVisitor(executionDataStore);
            
            // 请求dump数据
            writer.visitDumpCommand(true, false);
            
            // 读取响应
            if (!reader.read()) {
                throw new IOException("从JaCoCo agent读取数据失败");
            }
            
            logger.info("成功从agent收集到执行数据");
            
        } catch (Exception e) {
            logger.error("连接JaCoCo agent失败: {}", e.getMessage());
            throw new Exception("连接JaCoCo agent失败: " + e.getMessage(), e);
        }
        
        // 将数据写入dump文件
        try (FileOutputStream fos = new FileOutputStream(dumpFile)) {
            org.jacoco.core.data.ExecutionDataWriter writer = 
                new org.jacoco.core.data.ExecutionDataWriter(fos);
            
            // 写入session信息
            sessionInfoStore.accept(writer);
            // 写入执行数据
            executionDataStore.accept(writer);
            
            logger.info("dump文件已保存到: {}", dumpFile.getAbsolutePath());
            
        } catch (Exception e) {
            logger.error("保存dump文件失败: {}", e.getMessage());
            throw new Exception("保存dump文件失败: " + e.getMessage(), e);
        }
        
        return dumpFile.getAbsolutePath();
    }
    
    /**
     * 重置JaCoCo agent的覆盖率数据
     * @param appName 应用名称
     * @throws Exception
     */
    public void resetCoverageData(String appName) throws Exception {
        resetCoverageData(appName, null, null);
    }
    
    /**
     * 重置JaCoCo agent的覆盖率数据（支持指定host和port）
     * @param appName 应用名称
     * @param agentHost JaCoCo agent主机地址（可选）
     * @param agentPort JaCoCo agent端口（可选）
     * @throws Exception
     */
    public void resetCoverageData(String appName, String agentHost, Integer agentPort) throws Exception {
        // 确定连接参数
        String host = agentHost != null ? agentHost : getAgentHost(appName);
        int port = agentPort != null ? agentPort : getAgentPort(appName);
        
        logger.info("开始重置覆盖率数据，应用: {}, 连接到 {}:{}", appName, host, port);
        
        try (Socket socket = new Socket(host, port)) {
            RemoteControlWriter writer = new RemoteControlWriter(socket.getOutputStream());
            
            // 发送重置命令
            writer.visitDumpCommand(false, true);
            
            logger.info("成功重置覆盖率数据");
            
        } catch (Exception e) {
            logger.error("重置覆盖率数据失败: {}", e.getMessage());
            throw new Exception("重置覆盖率数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取应用的agent主机地址
     */
    private String getAgentHost(String appName) {
        CoverageConfig.ApplicationConfig appConfig = coverageConfig.getApplicationConfig(appName);
        return appConfig != null ? appConfig.getAgentHost() : coverageConfig.getAgentHost();
    }
    
    /**
     * 获取应用的agent端口
     */
    private int getAgentPort(String appName) {
        CoverageConfig.ApplicationConfig appConfig = coverageConfig.getApplicationConfig(appName);
        return appConfig != null ? appConfig.getAgentPort() : coverageConfig.getAgentPort();
    }
    
    // 保持向后兼容的方法
    @Deprecated
    public String collectCoverageData() throws Exception {
        return collectCoverageData("default", "latest");
    }
    
    @Deprecated
    public void resetCoverageData() throws Exception {
        resetCoverageData("default");
    }
}
