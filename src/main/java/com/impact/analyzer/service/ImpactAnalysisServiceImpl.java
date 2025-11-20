package com.impact.analyzer.service;

import com.impact.analyzer.analyzer.JGitSourceCodeManager; // Renamed dependency
import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.util.AnalysisOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("ImpactAnalysisService") // Name the bean explicitly if desired
@Slf4j
public class ImpactAnalysisServiceImpl implements ImpactAnalysisService { // Renamed the class


    // Dependencies injected via constructor
    private final AnalysisOrchestrator analysisOrchestrator;
    private final GitHubRepoService gitHubRepoService; // Renamed dependency
    private final JGitSourceCodeManager sourceCodeManager; // Renamed dependency
    private final RepoFileMetaDataAccessor metaDataAccessor; // New helper dependency

    /**
     * Constructor Injection is used for testability and clarity.
     */
    public ImpactAnalysisServiceImpl(
            AnalysisOrchestrator analysisOrchestrator,
            GitHubRepoService gitHubRepoService,
            JGitSourceCodeManager sourceCodeManager,
            RepoFileMetaDataAccessor metaDataAccessor) { // Inject the new accessor
        this.analysisOrchestrator = analysisOrchestrator;
        this.gitHubRepoService = gitHubRepoService;
        this.sourceCodeManager = sourceCodeManager;
        this.metaDataAccessor = metaDataAccessor;
        log.info("ImpactAnalysisServiceImpl initialized.");
    }

    // --- Core Analysis Method ---

    @Override
    public List<AggregatedChangeReport> runAnalysis(
            String selectedRepo,
            List<String> compareRepositoryUrls,
            String changedCode,
            String targetFilename
    ) throws Exception {

        // Basic validation
        if (selectedRepo == null || selectedRepo.isBlank() || changedCode == null || changedCode.isBlank()) {
            log.error("Analysis input validation failed: selectedRepo or changedCode is missing.");
            throw new IllegalArgumentException("Selected repository or changed code content cannot be empty.");
        }

        // 1. Get the current source code cache from the manager
        Map<String, Map<String, String>> localRepoCache = sourceCodeManager.getRepoMetaData();

        // Ensure the list is mutable and includes the repository containing the change
        List<String> allReposToCompare = new ArrayList<>(compareRepositoryUrls);
        if (!allReposToCompare.contains(selectedRepo)) {
            allReposToCompare.add(selectedRepo);
        }

        log.info("Starting analysis for target file '{}' across {} repositories.", targetFilename, allReposToCompare.size());
        log.debug("Repositories included in comparison: {}", allReposToCompare);

        // 2. Extract the complete list of files (FQCNs) and their content from the selected repos
        List<String> allFilesFqcnList = metaDataAccessor.getFileFQCNSForSelectedRepos(localRepoCache, allReposToCompare);
        Map<String, String> allFilesWithMetaDataMap = metaDataAccessor.getFileMetaDataForRepos(localRepoCache, allReposToCompare);

        log.debug("Total {} files collected for dependency tracing.", allFilesFqcnList.size());

        // 3. EXECUTE ANALYSIS LOOP using the utility
        List<AggregatedChangeReport> masterReportList = analysisOrchestrator.getImpactAnalysisReport(
                allFilesFqcnList,
                allFilesWithMetaDataMap,
                changedCode,
                targetFilename
        );

        log.info("Analysis complete. Generated {} master reports.", masterReportList.size());
        return masterReportList;
    }

    // --- Accessor Methods ---

    @Override
    public Map<String, Map<String, String>> getAvailableRepositories() {
        // Renamed dependency call
        return sourceCodeManager.getRepoMetaData();
    }

    @Override
    public String getClassCode(String repoIdentifier, String fileName) {
        log.debug("Fetching code for file {} in repository {}", fileName, repoIdentifier);

        // 1. Get the entire cached repository data (Map<Repo URL, Map<FQCN, Content>>)
        Map<String, Map<String, String>> localRepoCache = sourceCodeManager.getRepoMetaData();

        // 2. Use the accessor to retrieve the specific content
        String fileContent = metaDataAccessor.getSubMapValue(
                localRepoCache,
                repoIdentifier,
                fileName
        );

        if (fileContent == null) {
            log.warn("Code not found for Repo: {} File: {}", repoIdentifier, fileName);
        } else {
            log.trace("Successfully fetched {} bytes of code for {}", fileContent.length(), fileName);
        }

        return fileContent;
    }
}