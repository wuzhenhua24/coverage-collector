package com.mofari.coveragecollector.model.incremental;

import java.util.List;

public class IncrementalCoverageReport {
    private String appName;
    private String clusterName;
    private String tag; // Tag or newRef used for the "current" version
    private String baseRef; // Base reference for comparison (e.g., master branch)
    private String newRef; // Explicit new reference (branch/tag/commit) compared against baseRef
    private String reportTimestamp;
    private String reportPath; // Path to the generated JSON report file
    private OverallCoverageStats overallStats;
    private List<FileCoverage> files;

    // Getters and Setters
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getClusterName() {return clusterName;}

    public void setClusterName(String clusterName) {this.clusterName = clusterName;}

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public void setBaseRef(String baseRef) {
        this.baseRef = baseRef;
    }

    public String getNewRef() {
        return newRef;
    }

    public void setNewRef(String newRef) {
        this.newRef = newRef;
    }

    public String getReportTimestamp() {
        return reportTimestamp;
    }

    public void setReportTimestamp(String reportTimestamp) {
        this.reportTimestamp = reportTimestamp;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public OverallCoverageStats getOverallStats() {
        return overallStats;
    }

    public void setOverallStats(OverallCoverageStats overallStats) {
        this.overallStats = overallStats;
    }

    public List<FileCoverage> getFiles() {
        return files;
    }

    public void setFiles(List<FileCoverage> files) {
        this.files = files;
    }
} 