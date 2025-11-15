package com.citi.intelli.diff.service;


import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import java.util.List;

public interface ImpactAnalyzerService {

    /**
     * Executes the impact analysis process based on the provided inputs.
     * * @param repositoryUrls List of remote git URLs (if needed).
     * @param localFilePath The base directory containing the original/modified code.
     * @param targetFilename The specific file to diff and analyze.
     * @return A list of reports for each detected change.
     */
    List<AggregatedChangeReport> runAnalysis(
            List<String> repositoryUrls,
            String localFilePath,
            String targetFilename
    ) throws Exception;
}