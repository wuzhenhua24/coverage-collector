package com.mofari.coveragecollector.model.incremental;

public class LineCoverageDetail {
    private int lineNumber;
    private LineCoverageStatus status;
    private int coveredInstructions;
    private int missedInstructions;

    public LineCoverageDetail(int lineNumber) {
        this.lineNumber = lineNumber;
        this.status = LineCoverageStatus.NOT_COVERED; // Default status
        this.coveredInstructions = 0;
        this.missedInstructions = 0;
    }

    public LineCoverageDetail(int lineNumber, LineCoverageStatus status, int coveredInstructions, int missedInstructions) {
        this.lineNumber = lineNumber;
        this.status = status;
        this.coveredInstructions = coveredInstructions;
        this.missedInstructions = missedInstructions;
    }

    // Getters and Setters
    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public LineCoverageStatus getStatus() {
        return status;
    }

    public void setStatus(LineCoverageStatus status) {
        this.status = status;
    }

    public int getCoveredInstructions() {
        return coveredInstructions;
    }

    public void setCoveredInstructions(int coveredInstructions) {
        this.coveredInstructions = coveredInstructions;
    }

    public int getMissedInstructions() {
        return missedInstructions;
    }

    public void setMissedInstructions(int missedInstructions) {
        this.missedInstructions = missedInstructions;
    }
} 