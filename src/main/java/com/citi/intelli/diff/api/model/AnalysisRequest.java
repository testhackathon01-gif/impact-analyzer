package com.citi.intelli.diff.api.model;

import java.util.List;

// --- 1. Request Body DTO ---
public class AnalysisRequest {
    private List<String> repositoryUrls;
    private String localFilePath; // Base path for local analysis
    private String targetFilename; // Specific file to analyze (e.g., A_Helper.java)

    // Getters and Setters (omitted for brevity)
    public List<String> getRepositoryUrls() { return repositoryUrls; }
    public void setRepositoryUrls(List<String> repositoryUrls) { this.repositoryUrls = repositoryUrls; }
    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
    public String getTargetFilename() { return targetFilename; }
    public void setTargetFilename(String targetFilename) { this.targetFilename = targetFilename; }
}
