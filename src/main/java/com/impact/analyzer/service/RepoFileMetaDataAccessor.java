package com.impact.analyzer.service;

// package com.impact.analyzer.service;

import java.util.List;
import java.util.Map;

public interface RepoFileMetaDataAccessor {

    /**
     * Retrieves a combined list of all Fully Qualified Class Names (FQCNs)
     * contained in the source code cache for the specified repositories.
     * @param localRepoCache The nested map (Repo URL -> FQCN -> Content).
     * @param targetRepoUrls The URLs of the repositories to include.
     * @return A list of all FQCNs.
     */
    List<String> getFileFQCNSForSelectedRepos(
            Map<String, Map<String, String>> localRepoCache,
            List<String> targetRepoUrls);

    /**
     * Retrieves a flat map of all file content (FQCN -> Content)
     * from the specified repositories.
     * @param localRepoCache The nested map (Repo URL -> FQCN -> Content).
     * @param targetRepoUrls The URLs of the repositories to include.
     * @return A flat map of FQCN to file content.
     */
    Map<String, String> getFileMetaDataForRepos(
            Map<String, Map<String, String>> localRepoCache,
            List<String> targetRepoUrls);

    /**
     * Retrieves the file content (value) from a nested map structure
     * given the outer key (repository) and the inner key (FQCN/filename).
     * @param localRepoCache The nested map (Repo URL -> FQCN -> Content).
     * @param repoKey The URL of the repository.
     * @param fileKey The Fully Qualified Class Name (FQCN) of the file.
     * @return The file content, or null if not found.
     */
    String getSubMapValue(Map<String, Map<String, String>> localRepoCache, String repoKey, String fileKey);
}