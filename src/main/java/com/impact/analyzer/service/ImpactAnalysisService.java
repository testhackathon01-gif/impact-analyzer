package com.impact.analyzer.service;


import com.impact.analyzer.api.model.AggregatedChangeReport;
import java.util.List;
import java.util.Map;

public interface ImpactAnalysisService {

    /**
     * Executes the impact analysis process based on the provided inputs.
     * * @param repositoryUrls List of remote git URLs (if needed).
     * @param changedCode The base directory containing the original/modified code.
     * @param targetFilename The specific file to diff and analyze.
     * @return A list of reports for each detected change.
     */
    List<AggregatedChangeReport> runAnalysis(
            String selectedRepository,
            List<String> repositoryUrls,
            String changedCode,
            String targetFilename
    ) throws Exception;

    Map<String, Map<String, String>> getAvailableRepositories();

     String getClassCode(String selectedRepo,  String targetFilename);
}