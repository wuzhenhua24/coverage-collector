package com.mofari.coveragecollector.model;

public class FullCoverageReport {
    private String appName;
    private String clusterName;
    private String tag;
    private String reportPath;
    private String reportUrl;
    private long totalLineCount;
    private long coveredLineCount;
    private double lineCoveragePercentage;

    public FullCoverageReport() {
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public String getReportUrl(){ return reportUrl;}

    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }

    public long getTotalLineCount() {
        return totalLineCount;
    }

    public void setTotalLineCount(long totalLineCount) {
        this.totalLineCount = totalLineCount;
    }

    public long getCoveredLineCount() {
        return coveredLineCount;
    }

    public void setCoveredLineCount(long coveredLineCount) {
        this.coveredLineCount = coveredLineCount;
    }

    public double getLineCoveragePercentage() {
        return lineCoveragePercentage;
    }

    public void setLineCoveragePercentage(double lineCoveragePercentage) {
        this.lineCoveragePercentage = lineCoveragePercentage;
    }
} 