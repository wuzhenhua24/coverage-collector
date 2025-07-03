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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SonarQubeIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(SonarQubeIntegrationService.class);
    private static final Pattern TASK_URL_PATTERN = Pattern.compile(".*task\\?id=([\\w-]+).*");
    private static final long TASK_POLL_INTERVAL_MS = 10000; // 10 seconds
    private static final long TASK_POLL_TIMEOUT_MS = 300000; // 5 minutes

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

        // Step 1: Run the synchronous SonarScanner process and wait for server-side completion
        runSonarScanner(projectKey, projectBaseDir, sourceDirs, classDirs, jacocoReportPath, additionalSonarParams);

        // Step 2: After the task is complete, actively pull the coverage metric from SonarQube's API
        Double coverage = fetchCoverageMetric(projectKey);

        // Step 3: Construct the final URL and result object
        String coverageUrl = String.format("%s/component_measures?metric=Coverage&id=%s",
                coverageConfig.getSonar().getHostUrl(), projectKey);

        return new SonarAnalysisResult(coverageUrl, coverage);
    }


    /**
     * Executes the SonarScanner command process and waits for the background task to complete.
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

        logger.info("Executing SonarScanner command:\n{}", String.join(" \\\n  ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectBaseDir.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        String taskId = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[sonar-scanner] " + line);
                Matcher matcher = TASK_URL_PATTERN.matcher(line);
                if (matcher.matches()) {
                    taskId = matcher.group(1);
                    logger.info("SonarQube analysis task ID found: {}", taskId);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            logger.error("SonarScanner execution failed with exit code: {}", exitCode);
            throw new IOException("SonarScanner execution failed. Check logs for details.");
        }

        if (taskId == null) {
            logger.warn("Could not find SonarQube task ID in scanner output. Skipping wait for completion. Metrics might be stale.");
            return;
        }

        // NEW: Wait for the background task on the server to complete.
        waitForTaskCompletion(taskId);

        logger.info("SonarQube server-side analysis report completed successfully.");
    }

    /**
     * NEW: Polls the SonarQube API until the background task is no longer in progress.
     */
    private void waitForTaskCompletion(String taskId) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < TASK_POLL_TIMEOUT_MS) {
            TaskResponse taskResponse = getTaskStatus(taskId);
            if (taskResponse != null && taskResponse.getTask() != null) {
                String status = taskResponse.getTask().getStatus();
                logger.info("Polling SonarQube task '{}'. Current status: {}", taskId, status);
                switch (status) {
                    case "SUCCESS":
                        return; // Task completed successfully
                    case "FAILED":
                    case "CANCELED":
                        throw new IOException("SonarQube analysis task " + taskId + " failed with status: " + status);
                    case "PENDING":
                    case "IN_PROGRESS":
                        // Continue polling
                        Thread.sleep(TASK_POLL_INTERVAL_MS);
                        break;
                    default:
                        throw new IOException("Unknown SonarQube task status: " + status);
                }
            } else {
                // Wait and retry if the task is not found immediately
                logger.warn("SonarQube task '{}' not found yet, will retry...", taskId);
                Thread.sleep(TASK_POLL_INTERVAL_MS);
            }
        }
        throw new IOException("Timed out waiting for SonarQube task " + taskId + " to complete.");
    }

    /**
     * NEW: Fetches the status of a single background task.
     */
    private TaskResponse getTaskStatus(String taskId) {
        CoverageConfig.SonarConfig sonarConfig = coverageConfig.getSonar();
        String apiUrl = sonarConfig.getHostUrl() + "/api/ce/task";
        String token = sonarConfig.getLoginToken();

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("id", taskId);

        try {
            HttpHeaders headers = new HttpHeaders();
            String authHeader = "Bearer " + token;
            headers.set("Authorization", authHeader);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, entity, String.class);

            return objectMapper.readValue(responseEntity.getBody(), TaskResponse.class);

        } catch (HttpClientErrorException.NotFound e) {
            // It's possible to query for the task before it's registered. Treat as a non-fatal poll failure.
            return null;
        } catch (Exception e) {
            logger.error("Failed to get SonarQube task status for task ID: {}", taskId, e);
            return null;
        }
    }

    /**
     * Fetches the coverage metric for a component from the SonarQube Web API.
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
            String authHeader = "Bearer " + token;
            headers.set("Authorization", authHeader);
            logger.info("Sending request to SonarQube with headers: {}", headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    builder.toUriString(), HttpMethod.GET, entity, String.class);

            String responseJson = responseEntity.getBody();

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class TaskResponse {
        private Task task;
        public Task getTask() { return task; }
        public void setTask(Task task) { this.task = task; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Task {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
