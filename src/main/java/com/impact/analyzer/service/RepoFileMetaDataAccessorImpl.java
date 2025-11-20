package com.impact.analyzer.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RepoFileMetaDataAccessorImpl implements RepoFileMetaDataAccessor {

    @Override
    public List<String> getFileFQCNSForSelectedRepos(
            Map<String, Map<String, String>> localRepoCache,
            List<String> targetRepoUrls) {

        List<String> combinedSubMapKeys = new ArrayList<>();

        for (String repoUrl : targetRepoUrls) {
            Map<String, String> innerMap = localRepoCache.get(repoUrl);

            if (innerMap != null) {
                combinedSubMapKeys.addAll(innerMap.keySet());
            }
            // Logging should happen in the service or in the JGitSourceCodeManager if cache misses are critical
        }
        return combinedSubMapKeys;
    }

    @Override
    public Map<String, String> getFileMetaDataForRepos(
            Map<String, Map<String, String>> localRepoCache,
            List<String> targetRepoUrls) {

        Map<String, String> resultMaps = new HashMap<>();

        for (String repoUrl : targetRepoUrls) {
            Map<String, String> subMap = localRepoCache.get(repoUrl);
            if (subMap != null) {
                resultMaps.putAll(subMap);
            }
        }
        return resultMaps;
    }

    @Override
    public String getSubMapValue(Map<String, Map<String, String>> localRepoCache, String repoKey, String fileKey) {
        // 1. Get the inner map using the repository key
        Map<String, String> innerMap = localRepoCache.get(repoKey);

        // 2. Check if the repository key exists
        if (innerMap == null) {
            return null; // Repository not found
        }

        // 3. Get the file content using the file key
        return innerMap.get(fileKey);
    }
}