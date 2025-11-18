package com.citi.intelli.diff.api.model;

import java.util.List;

// --- 1. Request Body DTO ---
public class AnalysisRequest {
    private List<String> compareRepositoryUrls;
    private String changedCode; // Base path for local analysis
    private String targetFilename; // Specific file to analyze (e.g., A_Helper.java)
    private String selectedRepository;

    public String getSelectedRepository() {
        return selectedRepository;
    }

    public void setSelectedRepository(String selectedRepository) {
        this.selectedRepository = selectedRepository;
    }
    // Getters and Setters (omitted for brevity)
    public List<String> getCompareRepositoryUrls() { return compareRepositoryUrls; }
    public void setCompareRepositoryUrls(List<String> repositoryUrls) { this.compareRepositoryUrls = repositoryUrls; }
    public String getChangedCode() { return changedCode; }
    public void setChangedCode(String changedCode) { this.changedCode = changedCode; }
    public String getTargetFilename() { return targetFilename; }
    public void setTargetFilename(String targetFilename) { this.targetFilename = targetFilename; }
}
