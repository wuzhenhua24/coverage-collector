package com.mofari.coveragecollector.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mofari.coveragecollector.config.CoverageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SonarQubeIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(SonarQubeIntegrationService.class);

    @Autowired
    private CoverageConfig coverageConfig;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * A DTO to hold the final analysis result, including coverage data.
     */
    public static class SonarAnalysisResult {
        private String analysisUrl;
        private Double coverage;

        public SonarAnalysisResult(String analysisUrl, Double coverage) {
            this.analysisUrl = analysisUrl;
            this.coverage = coverage;
        }

        public String getAnalysisUrl() { return analysisUrl; }
        public Double getCoverage() { return coverage; }
    }

    /**
     * Executes the SonarScanner and then fetches the coverage result from SonarQube API.
     * This is the main orchestration method.
     */
    public SonarAnalysisResult runScannerAndGetCoverage(String projectKey, Path projectBaseDir, List<String> sourceDirs, List<String> classDirs,
                                                        Path jacocoReportPath, Map<String, String> additionalSonarParams)
            throws IOException, InterruptedException {

        // Step 1: Run the synchronous SonarScanner process
        runSonarScanner(projectKey, projectBaseDir, sourceDirs, classDirs, jacocoReportPath, additionalSonarParams);

        // Step 2: After the scanner is done, actively pull the coverage metric from SonarQube's API
        Double coverage = fetchCoverageMetric(projectKey);

        // Step 3: Construct the final URL and result object
        String coverageUrl = String.format("%s/component_measures?metric=Coverage&id=%s",
                coverageConfig.getSonar().getHostUrl(), projectKey);

        return new SonarAnalysisResult(coverageUrl, coverage);
    }


    /**
     * Executes the SonarScanner command process. This is a blocking operation.
     */
    private void runSonarScanner(String projectKey, Path projectBaseDir, List<String> sourceDirs, List<String> classDirs,
                                 Path jacocoReportPath, Map<String, String> additionalSonarParams)
            throws IOException, InterruptedException {

        CoverageConfig.SonarConfig sonarConfig = coverageConfig.getSonar();
        if (sonarConfig == null || sonarConfig.getHostUrl() == null || sonarConfig.getScannerPath() == null || sonarConfig.getLoginToken() == null) {
            throw new IllegalStateException("SonarQube configuration (hostUrl, token, scannerPath) is missing in application.yml");
        }

        List<String> command = new ArrayList<>();
        command.add(sonarConfig.getScannerPath());

        // Add a parameter to make the scanner wait for the server-side analysis to complete.
        // This makes the process truly synchronous.
        // 达不到门禁会失败
//        command.add("-Dsonar.qualitygate.wait=true");

        command.add("-Dsonar.projectKey=" + projectKey);
        command.add("-Dsonar.projectName=" + projectKey);
        command.add("-Dsonar.projectBaseDir=" + projectBaseDir.toAbsolutePath().toString());
        command.add("-Dsonar.host.url=" + sonarConfig.getHostUrl());
        command.add("-Dsonar.token=" + sonarConfig.getLoginToken());

        String sources = sourceDirs.stream().map(s -> projectBaseDir.relativize(Paths.get(s)).toString().replace('\\', '/')).collect(Collectors.joining(","));
        String binaries = classDirs.stream().map(c -> projectBaseDir.relativize(Paths.get(c)).toString().replace('\\', '/')).collect(Collectors.joining(","));

        command.add("-Dsonar.sources=" + sources);
        command.add("-Dsonar.java.binaries=" + binaries);
        command.add("-Dsonar.sourceEncoding=UTF-8");
        command.add("-Dsonar.coverage.jacoco.xmlReportPaths=" + projectBaseDir.relativize(jacocoReportPath).toString().replace('\\', '/'));

        logger.info("Skipping dependency resolution for sonar.java.libraries to keep the tool focused on code coverage.");

        if (additionalSonarParams != null) {
            for (Map.Entry<String, String> entry : additionalSonarParams.entrySet()) {
                command.add(String.format("-D%s=%s", entry.getKey(), entry.getValue()));
            }
        }

        logger.info("Executing SonarScanner command and waiting for completion:\n{}", String.join(" \\\n  ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectBaseDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[sonar-scanner] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("SonarScanner execution failed with exit code: {}", exitCode);
            throw new IOException("SonarScanner execution failed. Check logs for details.");
        }
        logger.info("SonarScanner analysis completed successfully on server.");
    }

    /**
     * NEW: Fetches the coverage metric for a component from the SonarQube Web API.
     */
    private Double fetchCoverageMetric(String projectKey) {
        CoverageConfig.SonarConfig sonarConfig = coverageConfig.getSonar();
        String apiUrl = sonarConfig.getHostUrl() + "/api/measures/component";
        String token = sonarConfig.getLoginToken();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("component", projectKey)
                .queryParam("metricKeys", "coverage");

        logger.info("Fetching coverage metric from SonarQube API: {}", builder.toUriString());

        try {
            HttpHeaders headers = new HttpHeaders();
            // FIXED: Use Bearer token authentication, which matches the successful curl command.
            String authHeader = "Bearer " + token;
            headers.set("Authorization", authHeader);

            // --- DIAGNOSTIC LOGGING ADDED ---
            logger.info("Sending request to SonarQube with headers: {}", headers);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String responseJson = responseEntity.getBody();

            // Parse the JSON response to extract the coverage value
            SonarMeasuresResponse response = objectMapper.readValue(responseJson, SonarMeasuresResponse.class);
            if (response != null && response.getComponent() != null && response.getComponent().getMeasures() != null) {
                for (Measure measure : response.getComponent().getMeasures()) {
                    if ("coverage".equals(measure.getMetric())) {
                        double coverageValue = Double.parseDouble(measure.getValue());
                        logger.info("Successfully fetched coverage value: {}%", coverageValue);
                        return coverageValue;
                    }
                }
            }
            logger.warn("Coverage metric not found in SonarQube API response for projectKey: {}", projectKey);
            return null;

        } catch (Exception e) {
            logger.error("Failed to fetch or parse coverage metric from SonarQube API for projectKey: {}", projectKey, e);
            return null;
        }
    }

    // --- DTOs for parsing SonarQube API JSON response ---
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SonarMeasuresResponse {
        private Component component;
        public Component getComponent() { return component; }
        public void setComponent(Component component) { this.component = component; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Component {
        private List<Measure> measures;
        public List<Measure> getMeasures() { return measures; }
        public void setMeasures(List<Measure> measures) { this.measures = measures; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Measure {
        private String metric;
        private String value;
        public String getMetric() { return metric; }
        public void setMetric(String metric) { this.metric = metric; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}