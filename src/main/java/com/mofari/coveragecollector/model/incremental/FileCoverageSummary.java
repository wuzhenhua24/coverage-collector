package com.mofari.coveragecollector.model.incremental;

public class FileCoverageSummary {
    private int totalChangedLinesInFile;
    private int covered;
    private int notCovered;
    private int partiallyCovered;

    public FileCoverageSummary() {
        this.totalChangedLinesInFile = 0;
        this.covered = 0;
        this.notCovered = 0;
        this.partiallyCovered = 0;
    }

    public FileCoverageSummary(int totalChangedLinesInFile, int covered, int notCovered, int partiallyCovered) {
        this.totalChangedLinesInFile = totalChangedLinesInFile;
        this.covered = covered;
        this.notCovered = notCovered;
        this.partiallyCovered = partiallyCovered;
    }

    public void incrementCovered() {
        this.covered++;
    }

    public void incrementNotCovered() {
        this.notCovered++;
    }

    public void incrementPartiallyCovered() {
        this.partiallyCovered++;
    }

    // Getters and Setters
    public int getTotalChangedLinesInFile() {
        return totalChangedLinesInFile;
    }

    public void setTotalChangedLinesInFile(int totalChangedLinesInFile) {
        this.totalChangedLinesInFile = totalChangedLinesInFile;
    }

    public int getCovered() {
        return covered;
    }

    public void setCovered(int covered) {
        this.covered = covered;
    }

    public int getNotCovered() {
        return notCovered;
    }

    public void setNotCovered(int notCovered) {
        this.notCovered = notCovered;
    }

    public int getPartiallyCovered() {
        return partiallyCovered;
    }

    public void setPartiallyCovered(int partiallyCovered) {
        this.partiallyCovered = partiallyCovered;
    }
} 