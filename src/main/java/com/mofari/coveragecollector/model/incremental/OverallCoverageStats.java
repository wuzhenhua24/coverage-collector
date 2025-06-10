package com.mofari.coveragecollector.model.incremental;

public class OverallCoverageStats {
    private long changedLines;
    private long coveredLines;
    private long uncoveredLines;
    private long partiallyCoveredLines;
    private double coveragePercentage;

    public OverallCoverageStats() {
        this.changedLines = 0;
        this.coveredLines = 0;
        this.uncoveredLines = 0;
        this.partiallyCoveredLines = 0;
        this.coveragePercentage = 0.0;
        // Any other fields should also be initialized to sensible defaults
    }

    public OverallCoverageStats(long changedLines, long coveredLines, long uncoveredLines, long partiallyCoveredLines, double coveragePercentage) {
        this.changedLines = changedLines;
        this.coveredLines = coveredLines;
        this.uncoveredLines = uncoveredLines;
        this.partiallyCoveredLines = partiallyCoveredLines;
        this.coveragePercentage = coveragePercentage;
    }

    public void incrementChangedLines() {
        this.changedLines++;
    }

    public void incrementCoveredLines() {
        this.coveredLines++;
    }

    public void incrementUncoveredLines() {
        this.uncoveredLines++;
    }

    public void incrementPartiallyCoveredLines() {
        this.partiallyCoveredLines++;
    }

    public void calculateCoveragePercentage() {
        if (this.changedLines == 0) {
            this.coveragePercentage = 0.0;
        } else {
            // Considering partially covered as 0.5 covered for percentage calculation
            this.coveragePercentage = ((double) this.coveredLines + (0.5 * this.partiallyCoveredLines)) / this.changedLines * 100.0;
        }
    }

    // Getters and Setters
    public long getChangedLines() {
        return changedLines;
    }

    public void setChangedLines(long changedLines) {
        this.changedLines = changedLines;
    }

    public long getCoveredLines() {
        return coveredLines;
    }

    public void setCoveredLines(long coveredLines) {
        this.coveredLines = coveredLines;
    }

    public long getUncoveredLines() {
        return uncoveredLines;
    }

    public void setUncoveredLines(long uncoveredLines) {
        this.uncoveredLines = uncoveredLines;
    }

    public long getPartiallyCoveredLines() {
        return partiallyCoveredLines;
    }

    public void setPartiallyCoveredLines(long partiallyCoveredLines) {
        this.partiallyCoveredLines = partiallyCoveredLines;
    }

    public double getCoveragePercentage() {
        return coveragePercentage;
    }

    public void setCoveragePercentage(double coveragePercentage) {
        this.coveragePercentage = coveragePercentage;
    }
} 