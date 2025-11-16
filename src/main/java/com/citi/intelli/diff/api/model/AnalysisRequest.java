package com.citi.intelli.diff.api.model;

import java.util.List;

// --- 1. Request Body DTO ---
public class AnalysisRequest {
    private List<String> compareRepositoryUrls;
    private String localFilePath; // Base path for local analysis
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
    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
    public String getTargetFilename() { return targetFilename; }
    public void setTargetFilename(String targetFilename) { this.targetFilename = targetFilename; }
}
