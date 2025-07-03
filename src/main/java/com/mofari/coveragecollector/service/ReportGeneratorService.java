package com.mofari.coveragecollector.service;

import com.mofari.coveragecollector.config.CoverageConfig;
import com.mofari.coveragecollector.model.incremental.*;
import com.mofari.coveragecollector.model.FullCoverageReport;
import com.mofari.coveragecollector.service.SonarQubeIntegrationService.SonarAnalysisResult;
import com.mofari.coveragecollector.util.ReportUrlGenerator;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
public class ReportGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(ReportGeneratorService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS").withZone(ZoneId.systemDefault());

    @Autowired
    private CoverageConfig coverageConfig;

    @Autowired
    private DumpMergeService dumpMergeService;

    @Autowired
    private GitDiffService gitDiffService;

    @Autowired
    private SonarQubeIntegrationService sonarQubeIntegrationService;

    // Helper method to determine which dump file to use
    private File determineDumpFileToUse(String appName, String clusterName, String tag, String specificDumpFilePath, boolean mergeAllDumps) throws IOException {
        if (specificDumpFilePath != null && !specificDumpFilePath.trim().isEmpty()) {
            File specificFile = new File(specificDumpFilePath);
            if (!specificFile.exists()) {
                throw new FileNotFoundException("Specified dump file does not exist: " + specificDumpFilePath);
            }
            logger.info("Using specified dump file: {}", specificFile.getAbsolutePath());
            return specificFile;
        }
        try {
            if (mergeAllDumps) {
                logger.info("Merging all dump files for app: {}, env: {}, tag: {}", appName, clusterName, tag);
                String mergedPath = dumpMergeService.mergeDumpFiles(appName, clusterName, tag);
                if (mergedPath == null) {
                    throw new FileNotFoundException("Failed to merge dump files or no files found for app: " + appName + ", env: " + clusterName + ", tag: " + tag);
                }
                return new File(mergedPath);
            } else {
                logger.info("Getting latest dump file for app: {}, env: {}, tag: {}", appName, clusterName, tag);
                String latestPath = dumpMergeService.getLatestDumpFile(appName, clusterName, tag);
                if (latestPath == null) {
                    throw new FileNotFoundException("No dump file found for app: " + appName + ", env: " + clusterName + ", tag: " + tag);
                }
                return new File(latestPath);
            }
        } catch (IOException e) {
            throw e; // Re-throw IOException
        } catch (Exception e) { // Catch other potential exceptions from DumpMergeService
            throw new IOException("Error interacting with DumpMergeService for app: "+ appName + ", env: " + clusterName + ", tag: " + tag + ": " + e.getMessage(), e);
        }
    }

    // Helper method to load execution data and session info from a dump file
    private ExecutionDataStore loadExecutionData(File dumpFile, SessionInfoStore sessionInfoStore) throws IOException {
        ExecutionDataStore executionDataStore = new ExecutionDataStore();
        try (FileInputStream fis = new FileInputStream(dumpFile)) {
            org.jacoco.core.data.ExecutionDataReader reader = new org.jacoco.core.data.ExecutionDataReader(fis);
            reader.setExecutionDataVisitor(executionDataStore);
            if (sessionInfoStore != null) {
                reader.setSessionInfoVisitor(sessionInfoStore);
            }
            reader.read();
        }
        logger.info("Successfully loaded execution data and session info from: {}", dumpFile.getAbsolutePath());
        return executionDataStore;
    }

    // Helper method to analyze coverage
    private IBundleCoverage analyzeCoverage(ExecutionDataStore executionDataStore, List<String> classDirectories, String bundleName) throws IOException {
        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
        if (classDirectories != null) {
            for (String classDirStr : classDirectories) {
                File classDir = new File(classDirStr);
                if (classDir.exists()) {
                    analyzer.analyzeAll(classDir);
                    logger.info("Analyzed class directory: {}", classDirStr);
                } else {
                    logger.warn("Class directory not found, skipping: {}", classDirStr);
                }
            }
        } else {
            logger.warn("Class directories list is null for bundle: {}. Analysis might be incomplete.", bundleName);
        }
        return coverageBuilder.getBundle(bundleName);
    }

    public FullCoverageReport generateReport(String appName, String clusterName, String tag, String specificDumpFilePath, boolean mergeAllDumps) throws Exception {
        logger.info("Generating full JaCoCo report for app: {}, env: {}, tag: {}, mergeDumps: {}", appName, clusterName, tag, mergeAllDumps);

        CoverageConfig.ApplicationConfig appConfig = coverageConfig.getApplicationConfig(appName);
        if (appConfig == null) {
            logger.info("Application configuration not found for appName: {} ", appName);
        }
        List<String> sourceDirs = getSourceDirectories(appName,tag);
        List<String> classDirs = getClassDirectories(appName,tag);
        validateDirectories(sourceDirs, classDirs);

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        Path reportDirName = Paths.get("report_" + timestamp);
        Path reportOutputDirPath;
        if (StringUtils.hasText(clusterName)) {
            reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, clusterName, tag).resolve(reportDirName);
        } else {
            reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, tag).resolve(reportDirName);
        }
        Files.createDirectories(reportOutputDirPath);
        File reportOutputDir = reportOutputDirPath.toFile();

        File actualDumpFile = determineDumpFileToUse(appName, clusterName, tag, specificDumpFilePath, mergeAllDumps);

        SessionInfoStore sessionInfoStore = new SessionInfoStore();
        ExecutionDataStore executionDataStore = loadExecutionData(actualDumpFile, sessionInfoStore);
        IBundleCoverage bundleCoverage = analyzeCoverage(executionDataStore, classDirs, appName + " Coverage Report");
        ISourceFileLocator sourceLocator = createMultiSourceFileLocator(sourceDirs);

        generateHtmlReport(bundleCoverage, sourceLocator, reportOutputDir, sessionInfoStore, executionDataStore);
        generateXmlReport(bundleCoverage, sourceLocator, reportOutputDir, sessionInfoStore, executionDataStore);

        logger.info("Full JaCoCo reports generated at: {}", reportOutputDir.getAbsolutePath());

        // Create and populate the full coverage report
        FullCoverageReport report = new FullCoverageReport();
        report.setAppName(appName);
        report.setClusterName(clusterName);
        report.setTag(tag);
        report.setReportPath(reportOutputDir.getAbsolutePath());
        report.setReportUrl(new ReportUrlGenerator().generateReportUrl(reportOutputDir.getAbsolutePath()));

        // Calculate coverage metrics from the bundle
        long totalLineCount = bundleCoverage.getLineCounter().getTotalCount();
        long coveredLineCount = bundleCoverage.getLineCounter().getCoveredCount();
        double lineCoveragePercentage = totalLineCount > 0 ?
                (double) coveredLineCount / totalLineCount * 100 : 0.0;

        report.setTotalLineCount(totalLineCount);
        report.setCoveredLineCount(coveredLineCount);
        report.setLineCoveragePercentage(Math.round(lineCoveragePercentage * 100.0) / 100.0); // Round to 2 decimal places

        return report;
    }

    private ISourceFileLocator createMultiSourceFileLocator(List<String> sourceDirectories) {
        MultiSourceFileLocator multiLocator = new MultiSourceFileLocator(4); // tabWidth = 4
        if (sourceDirectories != null) {
            for (String sourceDirectory : sourceDirectories) {
                File sourceDir = new File(sourceDirectory);
                if (sourceDir.exists()) {
                    multiLocator.add(new DirectorySourceFileLocator(sourceDir, "utf-8", 4));
                    logger.debug("Added source directory to locator: {}", sourceDirectory);
                } else {
                    logger.warn("Source directory not found for locator, skipping: {}", sourceDirectory);
                }
            }
        }
        return multiLocator;
    }

    private void validateDirectories(List<String> sourceDirectories, List<String> classDirectories) throws FileNotFoundException {
        boolean hasValidSource = sourceDirectories != null && sourceDirectories.stream().anyMatch(s -> new File(s).exists());
        boolean hasValidClass = classDirectories != null && classDirectories.stream().anyMatch(c -> new File(c).exists());

        boolean sourceDirsProvided = sourceDirectories != null && !sourceDirectories.isEmpty();
        boolean classDirsProvided = classDirectories != null && !classDirectories.isEmpty();

        if (!hasValidSource && sourceDirsProvided) {
            throw new FileNotFoundException("None of the configured source directories exist: " + sourceDirectories);
        }
        if (!hasValidClass && classDirsProvided) {
            throw new FileNotFoundException("None of the configured class directories exist: " + classDirectories);
        }
        if (!sourceDirsProvided) {
            logger.warn("Source directories list is empty or null for the application.");
        }
        if (!classDirsProvided) {
            logger.warn("Class directories list is empty or null for the application.");
        }
    }

    private List<String> getSourceDirectories(String appName, String tag) {
        CoverageConfig.ApplicationConfig appConfig = coverageConfig.discoverApplicationPaths(appName, tag);
        if (appConfig != null && appConfig.getSourceDirectories() != null && !appConfig.getSourceDirectories().isEmpty()) {
            return appConfig.getSourceDirectories();
        }
        return coverageConfig.getSourceDirectories() != null ? coverageConfig.getSourceDirectories() : new ArrayList<>();
    }

    private List<String> getClassDirectories(String appName, String tag) {
        CoverageConfig.ApplicationConfig appConfig = coverageConfig.discoverApplicationPaths(appName, tag);
        if (appConfig != null && appConfig.getClassDirectories() != null && !appConfig.getClassDirectories().isEmpty()) {
            return appConfig.getClassDirectories();
        }
        return coverageConfig.getClassDirectories() != null ? coverageConfig.getClassDirectories() : new ArrayList<>();
    }

    private void generateHtmlReport(IBundleCoverage bundleCoverage, ISourceFileLocator sourceLocator,
                                    File reportDir, SessionInfoStore sessionInfoStore,
                                    ExecutionDataStore executionDataStore) throws IOException {
        File htmlReportDir = new File(reportDir, "html");
        htmlReportDir.mkdirs();
        HTMLFormatter htmlFormatter = new HTMLFormatter();
        FileMultiReportOutput multiReportOutput = null;
        IReportVisitor visitor = null;
        try {
            multiReportOutput = new FileMultiReportOutput(htmlReportDir);
            visitor = htmlFormatter.createVisitor(multiReportOutput);
            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());
            visitor.visitBundle(bundleCoverage, sourceLocator);
            visitor.visitEnd();
        } finally {
            if (multiReportOutput != null) {
                try {
                    multiReportOutput.close();
                } catch (IOException e) {
                    logger.warn("Failed to close FileMultiReportOutput for HTML report", e);
                }
            }
        }
        logger.info("HTML report generated at: {}", htmlReportDir.getAbsolutePath());
    }

    public IncrementalCoverageReport generateIncrementalReport(
            String appName,
            String clusterName,
            String tag,
            String baseRef,
            String newRef, // tag-- tags/xxxx
            String specificDumpFilePath,
            boolean mergeAllDumps)
            throws IOException, InterruptedException, ParserConfigurationException, SAXException {

        logger.info("Generating incremental JaCoCo report for app: {}, cluster: {}, tag: {}, baseRef: {}, newRef/Tag: {}, mergeDumps: {}",
                appName, clusterName, tag, baseRef, newRef, mergeAllDumps);

        CoverageConfig.ApplicationConfig appConfig = coverageConfig.getApplicationConfig(appName);
        if (appConfig == null) {
            throw new IllegalArgumentException("Application configuration not found for appName: " + appName);
        }

        // Determine project path for git diff
        String projectPath = coverageConfig.getBaseProjectPath();
        if (!StringUtils.hasText(projectPath)) {
            throw new IllegalArgumentException("coverage.base-project-path is not configured. It's required for Git diff operations.");
        }
        Path gitRepoPath = Paths.get(projectPath, appName + "-" + tag);
        if (!Files.isDirectory(gitRepoPath) || !Files.exists(gitRepoPath.resolve(".git"))) {
            throw new FileNotFoundException("Git repository not found for app '" + appName + "' at expected path: " + gitRepoPath +
                    ". Ensure coverage.base-project-path is set correctly and contains the '<appName>' git project.");
        }


        logger.info("Getting changed lines for app: {}, base: {}, new: {}", appName, baseRef, newRef);
        // Use newRefAsTag for the git diff operation
        Map<String, Set<Integer>> changedLinesMap = gitDiffService.getChangedLines(gitRepoPath.toString(), baseRef, newRef);

        // *** START: NEW CODE TO NORMALIZE FILE PATHS ***
        List<String> sourceDirs = getSourceDirectories(appName, tag);
        logger.info("Normalizing Git diff paths to match JaCoCo's package-based paths...");
        Map<String, Set<Integer>> jacocoFormattedChangedLines = new HashMap<>();

        for (Map.Entry<String, Set<Integer>> entry : changedLinesMap.entrySet()) {
            String gitRelativePath = entry.getKey().replace("\\", "/");
            Path absoluteGitFilePath = gitRepoPath.resolve(gitRelativePath).normalize();
            boolean foundMatch = false;

            for (String sourceDirStr : sourceDirs) {
                Path sourceDirPath = Paths.get(sourceDirStr).normalize();
                if (absoluteGitFilePath.startsWith(sourceDirPath)) {
                    String jacocoPath = sourceDirPath.relativize(absoluteGitFilePath).toString().replace("\\", "/");
                    jacocoFormattedChangedLines.put(jacocoPath, entry.getValue());
                    logger.debug("Transformed path: '{}' -> '{}'", gitRelativePath, jacocoPath);
                    foundMatch = true;
                    break; // Found the correct source root for this file, move to the next file
                }
            }
            if (!foundMatch) {
                logger.warn("Could not find a matching source directory for changed file: {}. It will not be included in the incremental report.", gitRelativePath);
            }
        }
        // *** END: NEW CODE TO NORMALIZE FILE PATHS ***

        IncrementalCoverageReport report = new IncrementalCoverageReport();
        report.setAppName(appName);
        report.setBaseRef(baseRef);
        report.setNewRef(newRef);
        report.setTag(tag);
        report.setReportTimestamp(TIMESTAMP_FORMATTER.format(Instant.now()));
        if (StringUtils.hasText(clusterName)){
            report.setClusterName(clusterName);
        }


        if (jacocoFormattedChangedLines.isEmpty()) { // Use the new, normalized map for the check
            logger.info("No changed Java files found or matched between {} and {}. Returning an empty incremental report.", baseRef, newRef);
            report.setOverallStats(new OverallCoverageStats()); // Empty stats
            // Still set a report path for consistency, even if empty
            String timestampForPath = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            Path reportDirName = Paths.get("incremental_" + timestampForPath);
            Path reportOutputDirPath;
            if (StringUtils.hasText(clusterName)) {
                reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, clusterName, tag, "incremental").resolve(reportDirName);
            } else {
                reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, tag, "incremental").resolve(reportDirName);
            }
            Files.createDirectories(reportOutputDirPath);
            File jsonReportFile = reportOutputDirPath.resolve("incremental_coverage.json").toFile();
            report.setReportPath(jsonReportFile.getAbsolutePath());
            // Save the empty/minimal report as JSON
            String jsonReport = convertReportToJson(report);
            try (FileOutputStream fos = new FileOutputStream(jsonReportFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(osw)) {
                writer.write(jsonReport);
            }
            logger.info("Empty incremental report (no changes) saved to: {}", jsonReportFile.getAbsolutePath());
            return report;
        }

        // Use tag as the 'tag' for locating dump files
        File actualDumpFile = determineDumpFileToUse(appName, clusterName, tag, specificDumpFilePath, mergeAllDumps);

        SessionInfoStore sessionInfoStore = new SessionInfoStore(); // Needed for XML report generation context
        ExecutionDataStore executionDataStore = loadExecutionData(actualDumpFile, sessionInfoStore);

        List<String> classDirs = getClassDirectories(appName,tag);
        validateDirectories(sourceDirs, classDirs); // Ensure dirs exist before analysis

        IBundleCoverage bundleCoverage = analyzeCoverage(executionDataStore, classDirs, appName + " Incremental Base Analysis");
        ISourceFileLocator sourceLocator = createMultiSourceFileLocator(sourceDirs);

        // Generate a full XML report to a temporary location first
        Path tempReportDir = Files.createTempDirectory("jacoco_temp_xml_");
        File tempXmlFile = new File(tempReportDir.toFile(), "jacoco_temp.xml");

        generateXmlReport(bundleCoverage, sourceLocator, tempReportDir.toFile(), sessionInfoStore, executionDataStore, tempXmlFile.getName());

        // Parse the temporary XML and filter based on changed lines
        // Pass the new, normalized map to the parsing method
        IncrementalCoverageReport populatedReport = parseJaCoCoXmlAndFilter(tempXmlFile, jacocoFormattedChangedLines, appName, baseRef, tag);
        // Preserve fields already set on 'report'
        populatedReport.setAppName(report.getAppName());
        populatedReport.setBaseRef(report.getBaseRef());
        populatedReport.setNewRef(report.getNewRef()); // Should be newRefAsTag
        populatedReport.setTag(report.getTag());       // Should be newRefAsTag
        populatedReport.setReportTimestamp(report.getReportTimestamp());
        if(StringUtils.hasText(clusterName)) populatedReport.setClusterName(clusterName);


        // Determine final report output path using newRefAsTag as 'tag'
        String timestampForPath = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path reportDirName = Paths.get("incremental_" + timestampForPath);
        Path reportOutputDirPath;
        // Use newRefAsTag for the directory structure where 'tag' was used
        if (StringUtils.hasText(clusterName)) {
            reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, clusterName, tag, "incremental").resolve(reportDirName);
        } else {
            reportOutputDirPath = Paths.get(coverageConfig.getReportOutputDirectory(), appName, tag, "incremental").resolve(reportDirName);
        }
        Files.createDirectories(reportOutputDirPath);
        File jsonReportFile = reportOutputDirPath.resolve("incremental_coverage.json").toFile();
        populatedReport.setReportPath(jsonReportFile.getAbsolutePath());

        // Convert final IncrementalCoverageReport to JSON and save it
        String jsonReport = convertReportToJson(populatedReport);
        try (FileOutputStream fos = new FileOutputStream(jsonReportFile);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             BufferedWriter writer = new BufferedWriter(osw)) {
            writer.write(jsonReport);
        }

        logger.info("Incremental JaCoCo report (JSON) generated at: {}", jsonReportFile.getAbsolutePath());

        // Cleanup temporary XML report directory
        try {
            Files.deleteIfExists(tempXmlFile.toPath());
            Files.deleteIfExists(tempReportDir);
            logger.debug("Cleaned up temporary XML report directory: {}", tempReportDir);
        } catch (IOException e) {
            logger.warn("Failed to cleanup temporary XML report directory: {}", tempReportDir, e);
        }

        return populatedReport;
    }

    private IncrementalCoverageReport parseJaCoCoXmlAndFilter(
            File jacocoXmlFile,
            Map<String, Set<Integer>> changedLinesMap, // This map now contains JaCoCo-style paths
            String appName,
            String baseRef,
            String newRefAsTag) // Consolidated parameter
            throws ParserConfigurationException, IOException, SAXException {

        IncrementalCoverageReport report = new IncrementalCoverageReport();
        report.setAppName(appName);
        report.setBaseRef(baseRef);
        report.setNewRef(newRefAsTag); // newRef in report is newRefAsTag
        report.setTag(newRefAsTag);    // tag in report is also newRefAsTag

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        //设置安全特性 (关键步骤)
        // 这是防止 XXE 的最核心和标准的设置
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // Mitigate XXE
        // 为了更彻底地禁用 XXE，还可以设置以下特性
        // 禁止解析外部通用实体
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        // 禁止解析外部参数实体
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // 禁用外部 DTDs. 这是比 disallow-doctype-decl 更温和的替代方案
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        // 确保禁用 XInclude 处理
        factory.setXIncludeAware(false);
        // 禁止实体引用扩展，这可以防止 "Billion Laughs" 攻击
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(jacocoXmlFile);
        doc.getDocumentElement().normalize();

        NodeList packageNodes = doc.getElementsByTagName("package");
        List<FileCoverage> fileCoverages = new ArrayList<>();
        OverallCoverageStats overallStats = new OverallCoverageStats();

        for (int i = 0; i < packageNodes.getLength(); i++) {
            Element packageElement = (Element) packageNodes.item(i);
            String packageName = packageElement.getAttribute("name");
            NodeList sourceFileNodes = packageElement.getElementsByTagName("sourcefile");

            for (int j = 0; j < sourceFileNodes.getLength(); j++) {
                Element sourceFileElement = (Element) sourceFileNodes.item(j);
                String sourceFileName = sourceFileElement.getAttribute("name");
                // Construct full path relative to package
                String fullFilePath = packageName.isEmpty() ? sourceFileName : packageName + "/" + sourceFileName;

                // This check will now work correctly!
                if (changedLinesMap.containsKey(fullFilePath)) {
                    Set<Integer> changedLinesInFile = changedLinesMap.get(fullFilePath);
                    FileCoverage fileCoverage = new FileCoverage(fullFilePath);
                    FileCoverageSummary fileSummary = new FileCoverageSummary();
                    fileSummary.setTotalChangedLinesInFile(changedLinesInFile.size());

                    NodeList lineNodes = sourceFileElement.getElementsByTagName("line");
                    for (int k = 0; k < lineNodes.getLength(); k++) {
                        Element lineElement = (Element) lineNodes.item(k);
                        int lineNumber = Integer.parseInt(lineElement.getAttribute("nr"));

                        if (changedLinesInFile.contains(lineNumber)) {
                            int missedInstructions = Integer.parseInt(lineElement.getAttribute("mi"));
                            int coveredInstructions = Integer.parseInt(lineElement.getAttribute("ci"));
                            int missedBranches = Integer.parseInt(lineElement.getAttribute("mb"));
                            int coveredBranches = Integer.parseInt(lineElement.getAttribute("cb"));

                            LineCoverageDetail lineDetail = new LineCoverageDetail(lineNumber);
                            lineDetail.setMissedInstructions(missedInstructions);
                            lineDetail.setCoveredInstructions(coveredInstructions);
                            // Determine line coverage status based on instruction coverage
                            if (missedInstructions == 0 && coveredInstructions > 0) {
                                lineDetail.setStatus(LineCoverageStatus.COVERED);
                                fileSummary.incrementCovered();
                                overallStats.incrementCoveredLines();
                            } else if (missedInstructions > 0 && coveredInstructions > 0) {
                                lineDetail.setStatus(LineCoverageStatus.PARTIALLY_COVERED);
                                fileSummary.incrementPartiallyCovered();
                                overallStats.incrementPartiallyCoveredLines();
                            } else { // mi > 0 && ci == 0
                                lineDetail.setStatus(LineCoverageStatus.NOT_COVERED);
                                fileSummary.incrementNotCovered();
                                overallStats.incrementUncoveredLines();
                            }
                            overallStats.incrementChangedLines(); // Total changed lines processed
                            fileCoverage.addChangedLineDetail(lineDetail);
                        }
                    }
                    if (!fileCoverage.getChangedLineDetails().isEmpty()) {
                        fileCoverage.setSummary(fileSummary);
                        fileCoverages.add(fileCoverage);
                    }
                }
            }
        }
        report.setFiles(fileCoverages);
        report.setOverallStats(overallStats);
        return report;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "null"; // Return the string literal "null" for JSON null
        }
        // Simplified JSON string escapes - primarily for double quotes and backslashes
        String escaped = value;
        escaped = escaped.replace("\\", "\\\\"); // Must be done first
        escaped = escaped.replace("\"", "\\\"");   // Escape double quote
        // For simplicity and to avoid linter issues, other escapes like \n, \r, \t are omitted for now.
        // A robust solution would include them.

        // Construct the final JSON string using StringBuilder to avoid potential literal issues
        StringBuilder sb = new StringBuilder();
        sb.append('"'); // Append opening quote character
        sb.append(escaped);
        sb.append('"'); // Append closing quote character
        return sb.toString();
    }

    private String convertReportToJson(IncrementalCoverageReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"appName\": ").append(escapeJson(report.getAppName())).append(",");
        sb.append("\"tag\": ").append(escapeJson(report.getTag())).append(",");
        sb.append("\"baseRef\": ").append(escapeJson(report.getBaseRef())).append(",");
        sb.append("\"newRef\": ").append(escapeJson(report.getNewRef())).append(",");
        sb.append("\"reportTimestamp\": ").append(escapeJson(report.getReportTimestamp())).append(",");
        String reportPathVal = report.getReportPath() != null ? report.getReportPath().replace("\\", "/") : ""; // Ensure path is JSON-friendly
        sb.append("\"reportPath\": ").append(escapeJson(reportPathVal)).append(",");

        OverallCoverageStats overall = report.getOverallStats();
        sb.append("\"overallStats\": {");
        sb.append("\"changedLines\": ").append(overall.getChangedLines()).append(",");
        sb.append("\"coveredLines\": ").append(overall.getCoveredLines()).append(",");
        sb.append("\"uncoveredLines\": ").append(overall.getUncoveredLines()).append(",");
        sb.append("\"partiallyCoveredLines\": ").append(overall.getPartiallyCoveredLines()).append(",");
        sb.append("\"coveragePercentage\": ").append(String.format("%.2f", overall.getCoveragePercentage())); // No trailing comma for last item in object
        sb.append("},"); // Comma after overallStats object

        sb.append("\"files\": [");
        List<FileCoverage> files = report.getFiles();
        for (int i = 0; i < files.size(); i++) {
            FileCoverage file = files.get(i);
            sb.append("{");
            sb.append("\"filePath\": ").append(escapeJson(file.getFilePath())).append(",");
            FileCoverageSummary summary = file.getSummary();
            sb.append("\"summary\": {");
            sb.append("\"totalChangedLinesInFile\": ").append(summary.getTotalChangedLinesInFile()).append(",");
            sb.append("\"covered\": ").append(summary.getCovered()).append(",");
            sb.append("\"notCovered\": ").append(summary.getNotCovered()).append(",");
            sb.append("\"partiallyCovered\": ").append(summary.getPartiallyCovered()); // No trailing comma for last item in object
            sb.append("},"); // Comma after summary object
            sb.append("\"changedLineDetails\": [");
            List<LineCoverageDetail> details = file.getChangedLineDetails();
            for (int j = 0; j < details.size(); j++) {
                LineCoverageDetail detail = details.get(j);
                sb.append("{");
                sb.append("\"lineNumber\": ").append(detail.getLineNumber()).append(",");
                sb.append("\"status\": ").append(escapeJson(detail.getStatus().name())).append(",");
                sb.append("\"coveredInstructions\": ").append(detail.getCoveredInstructions()).append(",");
                sb.append("\"missedInstructions\": ").append(detail.getMissedInstructions()); // No trailing comma for last item in object
                sb.append("}");
                if (j < details.size() - 1) sb.append(",");
            }
            sb.append("]"); // End of changedLineDetails array
            sb.append("}"); // End of file object
            if (i < files.size() - 1) sb.append(",");
        }
        sb.append("]"); // End of files array
        sb.append("}"); // End of report object
        return sb.toString();
    }

    // Refactored public overload for incremental report (without clusterName)
    public IncrementalCoverageReport generateIncrementalReport(
            String appName,
            String baseRef,
            String newRefAsTag, // Consolidated parameter
            String specificDumpFilePath,
            boolean mergeAllDumps)
            throws IOException, InterruptedException, ParserConfigurationException, SAXException {
        // Calls the main refactored method with clusterName as null
        return generateIncrementalReport(appName, null, newRefAsTag,baseRef, newRefAsTag, specificDumpFilePath, mergeAllDumps);
    }

    // --- Methods for Full Report (HTML/XML) and Incremental Temp XML ---
    // This is the 6-argument version, used by incremental flow for specific temp file.
    private void generateXmlReport(IBundleCoverage bundleCoverage, ISourceFileLocator sourceLocator,
                                   File reportDir, SessionInfoStore sessionInfoStore,
                                   ExecutionDataStore executionDataStore, String outputFileName) throws IOException {
        File xmlFile = new File(reportDir, outputFileName);
        XMLFormatter xmlFormatter = new XMLFormatter();
        FileMultiReportOutput multiReportOutput = null;
        IReportVisitor visitor = null;
        try {
            multiReportOutput = new FileMultiReportOutput(xmlFile.getParentFile());
            visitor = xmlFormatter.createVisitor(multiReportOutput.createFile(xmlFile.getName()));
            visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());
            visitor.visitBundle(bundleCoverage, sourceLocator);
            visitor.visitEnd();
        } finally {
            if (multiReportOutput != null) {
                try {
                    multiReportOutput.close();
                } catch (IOException e) {
                    logger.warn("Failed to close FileMultiReportOutput for XML report: {}", xmlFile.getName(), e);
                }
            }
        }
        logger.info("XML report data written to: {}", xmlFile.getAbsolutePath());
    }

    // This is the 5-argument overload, used by the main full report flow (generateReport)
    // It calls the 6-argument version with a default filename "jacoco.xml"
    private void generateXmlReport(IBundleCoverage bundleCoverage, ISourceFileLocator sourceLocator,
                                   File reportDir, SessionInfoStore sessionInfoStore,
                                   ExecutionDataStore executionDataStore) throws IOException {
        generateXmlReport(bundleCoverage, sourceLocator, reportDir, sessionInfoStore, executionDataStore, "jacoco.xml");
    }

    // --- Deprecated Methods ---
    @Deprecated
    public String generateReport(String dumpFilePath) throws Exception {
        // This old method likely assumed a single 'default' app and 'latest' tag.
        // It's hard to adapt directly without more context on its original intent.
        // For now, let's log a warning and throw an unsupported operation,
        // or adapt if a clear mapping to new params is possible.
        logger.warn("Deprecated generateReport(String dumpFilePath) called. This method is not fully supported with multi-app/tag structure.");
        throw new UnsupportedOperationException("generateReport(String dumpFilePath) is deprecated and not fully compatible with current features.");
        // If you need to revive this, you'd need to define how 'appName' and 'tag' are derived.
        // Example: return generateReport("default", "latest", dumpFilePath, false, null);
    }

    /**
     * Generates a full coverage report and then triggers a SonarQube analysis.
     *
     * @param projectKey The SonarQube project key.
     * @param appName The application name.
     * @param clusterName The cluster name.
     * @param tag The git tag/branch.
     * @param specificDumpFilePath Optional specific dump file path.
     * @param mergeAllDumps Flag to merge dumps.
     * @return The SonarQube analysis result including coverage data.
     * @throws Exception on failure.
     */
    public SonarAnalysisResult generateFullReportWithSonar(String projectKey, String appName, String clusterName, String tag,
                                                                                       String specificDumpFilePath, boolean mergeAllDumps) throws Exception {
        logger.info("Starting full report generation for SonarQube integration for app: {}", appName);

        // 1. Generate the full JaCoCo XML report first
        FullCoverageReport fullReport = generateReport(appName, clusterName, tag, specificDumpFilePath, mergeAllDumps);
        Path reportPath = Paths.get(fullReport.getReportPath());
        File jacocoXmlFile = reportPath.resolve("jacoco.xml").toFile();

        if (!jacocoXmlFile.exists()) {
            throw new IOException("jacoco.xml was not generated at the expected path: " + jacocoXmlFile.getAbsolutePath());
        }

        // 2. Prepare parameters for SonarScanner
        String projectPath = coverageConfig.getBaseProjectPath();
        Path gitRepoPath = Paths.get(projectPath, appName + "-" + tag);
        List<String> sourceDirs = getSourceDirectories(appName, tag);
        List<String> classDirs = getClassDirectories(appName, tag);

        // For a full scan, we might not have specific PR parameters.
        // We can add branch information if available.
        // Developer edition or above is required for this property
//        Map<String, String> sonarParams = new HashMap<>();
//        sonarParams.put("sonar.branch.name", tag);

        // 3. Run SonarScanner
        return sonarQubeIntegrationService.runScannerAndGetCoverage(
                projectKey, gitRepoPath, sourceDirs, classDirs, jacocoXmlFile.toPath(), null
        );
    }

    // --- NEW METHOD FOR INCREMENTAL SONARQUBE INTEGRATION ---

    /**
     * Generates an incremental coverage report and then triggers a SonarQube Pull Request analysis.
     *
     * @param projectKey The SonarQube project key.
     * @param appName The application name.
     * @param clusterName The cluster name.
     * @param baseRef The base branch for comparison.
     * @param newRefAsTag The feature branch being analyzed.
     * @param prKey The Pull Request key/ID.
     * @param specificDumpFilePath Optional specific dump file path.
     * @param mergeAllDumps Flag to merge dumps.
     * @return The SonarQube analysis result including coverage data.
     * @throws Exception on failure.
     */
    public SonarAnalysisResult generateIncrementalReportWithSonar(String projectKey, String appName, String clusterName, String baseRef,
                                                     String newRefAsTag, String prKey, String specificDumpFilePath, boolean mergeAllDumps) throws Exception {
        logger.info("Starting incremental report generation for SonarQube PR analysis for app: {}", appName);

        // 1. Generate the JaCoCo XML report (this happens inside generateIncrementalReport)
        // We need to slightly modify the flow to get the path to the temp XML file.
        // For now, let's assume generateIncrementalReport already creates a jacoco.xml.
        // A better approach would be to refactor generateIncrementalReport to return the XML path.
        // Let's first generate the full report to get the XML, which is a prerequisite.
        FullCoverageReport fullReport = generateReport(appName, clusterName, newRefAsTag, specificDumpFilePath, mergeAllDumps);
        Path reportPath = Paths.get(fullReport.getReportPath());
        File jacocoXmlFile = reportPath.resolve("jacoco.xml").toFile();

        if (!jacocoXmlFile.exists()) {
            throw new IOException("jacoco.xml was not generated at the expected path: " + jacocoXmlFile.getAbsolutePath());
        }

        // 2. Prepare parameters for SonarScanner
        String projectPath = coverageConfig.getBaseProjectPath();
        Path gitRepoPath = Paths.get(projectPath, appName + "-" + newRefAsTag);
        List<String> sourceDirs = getSourceDirectories(appName, newRefAsTag);
        List<String> classDirs = getClassDirectories(appName, newRefAsTag);

        // These are the crucial parameters for a Pull Request analysis
        Map<String, String> sonarParams = new HashMap<>();
        sonarParams.put("sonar.pullrequest.key", prKey);
        sonarParams.put("sonar.pullrequest.branch", newRefAsTag);
        sonarParams.put("sonar.pullrequest.base", baseRef);

        // 3. Run SonarScanner
        return sonarQubeIntegrationService.runScannerAndGetCoverage(
                projectKey, gitRepoPath, sourceDirs, classDirs, jacocoXmlFile.toPath(), sonarParams
        );
    }

}
