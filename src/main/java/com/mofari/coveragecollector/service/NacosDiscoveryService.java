package com.mofari.coveragecollector.service;

import com.mofari.coveragecollector.config.CoverageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NacosDiscoveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(NacosDiscoveryService.class);
    
    @Value("${nacos.discovery.server-addr:localhost:8848}")
    private String nacosServerAddr;
    
    @Value("${nacos.discovery.namespace-id:public}")
    private String namespaceId;
    
    @Value("${nacos.discovery.group-name:DEFAULT_GROUP}")
    private String groupName;
    
    @Value("${nacos.discovery.username:nacos}")
    private String username;
    
    @Value("${nacos.discovery.password:nacos123456}")
    private String password;
    
    @Autowired
    private CoverageConfig coverageConfig;
    
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    
    @PostConstruct
    public void init() {
        logger.info("Nacos服务发现初始化，服务器地址: {}", nacosServerAddr);
        restTemplate = new RestTemplate();
        objectMapper = new ObjectMapper();
    }
    
    /**
     * 获取应用在指定集群的所有实例信息
     * @param appName 应用名称
     * @param clusterName 集群名称（对应之前的envName）
     * @return 应用实例列表
     */
    public List<ApplicationInstance> getApplicationInstances(String appName, String clusterName) {
        String serviceName = buildServiceName(appName);
        logger.info("获取应用实例，服务名: {}, 集群: {}", serviceName, clusterName);
        
        try {
            // 构建请求URL
            String url = buildNacosUrl(serviceName);
            logger.debug("请求URL: {}", url);
            
            // 发送HTTP请求
            String responseBody = restTemplate.getForObject(url, String.class);
            logger.debug("Nacos响应: {}", responseBody);
            
            if (responseBody == null) {
                logger.warn("Nacos返回空响应");
                return new ArrayList<>();
            }
            
            // 解析响应
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return parseNacosResponse(jsonNode, clusterName, appName);
            
        } catch (Exception e) {
            logger.error("从Nacos获取应用实例失败，appName: {}, clusterName: {}", appName, clusterName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建服务名：appName.app
     */
    private String buildServiceName(String appName) {
        return appName + ".app";
    }
    
    /**
     * 构建Nacos请求URL
     */
    private String buildNacosUrl(String serviceName) throws Exception {
        String baseUrl = String.format("http://%s/nacos/v1/ns/instance/list", nacosServerAddr);
        
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("?serviceName=").append(URLEncoder.encode(serviceName, StandardCharsets.UTF_8.name()));
        url.append("&groupName=").append(URLEncoder.encode(groupName, StandardCharsets.UTF_8.name()));
        url.append("&namespaceId=").append(URLEncoder.encode(namespaceId, StandardCharsets.UTF_8.name()));
        
        // 如果需要认证，可以在这里添加
        if (username != null && !username.isEmpty()) {
            url.append("&username=").append(URLEncoder.encode(username, StandardCharsets.UTF_8.name()));
            url.append("&password=").append(URLEncoder.encode(password, StandardCharsets.UTF_8.name()));
        }
        
        return url.toString();
    }
    
    /**
     * 解析Nacos响应，提取指定集群的实例
     */
    private List<ApplicationInstance> parseNacosResponse(JsonNode jsonNode, String clusterName, String appName) {
        List<ApplicationInstance> instances = new ArrayList<>();
        
        if (jsonNode.has("hosts")) {
            JsonNode hostsNode = jsonNode.get("hosts");
            
            for (JsonNode hostNode : hostsNode) {
                try {
                    // 检查实例是否健康和启用
                    boolean healthy = hostNode.path("healthy").asBoolean(false);
                    boolean enabled = hostNode.path("enabled").asBoolean(false);
                    
                    if (!healthy || !enabled) {
                        continue;
                    }
                    
                    // 获取集群名称并处理特殊格式
                    String instanceCluster = hostNode.path("clusterName").asText("");
                    if (!matchesCluster(instanceCluster, clusterName)) {
                        continue;
                    }
                    
                    // 提取IP和端口
                    String ip = hostNode.path("ip").asText("");
                    int port = hostNode.path("port").asInt(8080); // 这是应用端口，不是jacoco端口
                    
                    // 获取元数据
                    Map<String, String> metadata = new HashMap<>();
                    JsonNode metadataNode = hostNode.path("metadata");
                    if (metadataNode.isObject()) {
                        metadataNode.fields().forEachRemaining(entry -> 
                            metadata.put(entry.getKey(), entry.getValue().asText()));
                    }
                    
                    // 获取jacoco端口
                    int jacocoPort = getJacocoPort(appName, metadata);
                    
                    ApplicationInstance instance = new ApplicationInstance(ip, jacocoPort, metadata);
                    instances.add(instance);
                    
                    logger.debug("找到实例: {}", instance);
                    
                } catch (Exception e) {
                    logger.warn("解析实例数据失败", e);
                }
            }
        }
        
        logger.info("找到 {} 个匹配的实例，集群: {}", instances.size(), clusterName);
        return instances;
    }
    
    /**
     * 匹配集群名称
     * 处理clusterName=p2 -> daily-p2 这样的转换
     */
    private boolean matchesCluster(String instanceCluster, String requestedCluster) {
        if (instanceCluster.equals(requestedCluster)) {
            return true;
        }
        
        // 处理特殊格式：p2 -> daily-p2
        if (instanceCluster.startsWith("daily-") && instanceCluster.endsWith(requestedCluster)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 获取jacoco端口
     * 优先从配置文件获取，然后从元数据获取，最后使用默认端口6300
     */
    private int getJacocoPort(String appName, Map<String, String> metadata) {
        // 1. 优先从配置文件获取
        int configPort = coverageConfig.getJacocoPort(appName);
        if (configPort != 6300) { // 如果配置了非默认端口
            return configPort;
        }
        
        // 2. 从元数据获取
        String jacocoPortStr = metadata.get("jacoco.port");
        if (jacocoPortStr != null && !jacocoPortStr.isEmpty()) {
            try {
                return Integer.parseInt(jacocoPortStr);
            } catch (NumberFormatException e) {
                logger.warn("JaCoCo端口配置无效: {}, 使用默认端口6300", jacocoPortStr);
            }
        }
        
        // 3. 使用默认端口
        return 6300;
    }
    
    /**
     * 检查服务是否存在
     */
    public boolean isServiceExists(String appName, String clusterName) {
        List<ApplicationInstance> instances = getApplicationInstances(appName, clusterName);
        return !instances.isEmpty();
    }
    
    /**
     * 应用实例信息类
     */
    public static class ApplicationInstance {
        private String ip;
        private int jacocoPort;
        private Map<String, String> metadata;
        
        public ApplicationInstance(String ip, int jacocoPort, Map<String, String> metadata) {
            this.ip = ip;
            this.jacocoPort = jacocoPort;
            this.metadata = metadata != null ? metadata : new HashMap<>();
        }
        
        public String getIp() {
            return ip;
        }
        
        public int getJacocoPort() {
            return jacocoPort;
        }
        
        public Map<String, String> getMetadata() {
            return metadata;
        }
        
        public String getNodeId() {
            return ip.replace(".", "_");
        }
        
        @Override
        public String toString() {
            return String.format("ApplicationInstance{ip='%s', jacocoPort=%d}", ip, jacocoPort);
        }
    }
}
