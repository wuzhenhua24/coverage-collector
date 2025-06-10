package com.mofari.coveragecollector.model.incremental;

import java.util.ArrayList;
import java.util.List;

public class FileCoverage {
    private String filePath;
    private FileCoverageSummary summary;
    private List<LineCoverageDetail> changedLineDetails;

    public FileCoverage(String filePath) {
        this.filePath = filePath;
        this.changedLineDetails = new ArrayList<>();
    }

    public void addChangedLineDetail(LineCoverageDetail detail) {
        this.changedLineDetails.add(detail);
    }

    public void setSummary(int totalChanged, int covered, int notCovered, int partiallyCovered) {
        this.summary = new FileCoverageSummary(totalChanged, covered, notCovered, partiallyCovered);
    }

    // Getters and Setters
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public FileCoverageSummary getSummary() {
        return summary;
    }

    public void setSummary(FileCoverageSummary summary) {
        this.summary = summary;
    }

    public List<LineCoverageDetail> getChangedLineDetails() {
        return changedLineDetails;
    }

    public void setChangedLineDetails(List<LineCoverageDetail> changedLineDetails) {
        this.changedLineDetails = changedLineDetails;
    }
} 