package com.citi.intelli.diff.service;

import com.citi.intelli.diff.analyzer.GitRepoLister;
import com.citi.intelli.diff.analyzer.TemporaryCacheGitFetcher;
import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import com.citi.intelli.diff.util.ImpactAnalyzerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.citi.intelli.diff.util.ImpactAnalyzerUtil.getImpactAnalysisReport;

@Service
public class ImpactAnalyzerServiceImpl implements ImpactAnalyzerService{

    @Autowired
    GitRepoLister gitRepoLister;

    @Autowired
    TemporaryCacheGitFetcher temporaryCacheGitFetcher;

    @Override
    public List<AggregatedChangeReport> runAnalysis(String selectedRepo, List<String> compareRepositoryUrls, String localFilePath, String targetFilename) throws Exception {

        Map<String,Map<String,String>> localRepoCache= temporaryCacheGitFetcher.getRepoMetaData();

        System.out.println("repos:--"+localRepoCache);
        System.out.println("selectedRepo:--"+selectedRepo);
        System.out.println("compareRepositoryUrls:--"+compareRepositoryUrls);

        List<String> allFiles= getSubMapKeysForSelectedRepos(localRepoCache,compareRepositoryUrls);

        System.out.println(allFiles);


        /*List<String> totalFileList=new ArrayList<>();
        totalFileList.add("com.app.modulea.A_Helper");
        totalFileList.add("com.app.modulea.DataGenerator");
        totalFileList.add("com.app.moduleb.FinalReporter");
        totalFileList.add("com.app.modulec.HashConsumer");
        totalFileList.add("com.app.moduled.Service");*/

        // 4. EXECUTE ANALYSIS LOOP
        List<AggregatedChangeReport> masterReportList =getImpactAnalysisReport(allFiles,selectedRepo,localFilePath,targetFilename);

        return masterReportList;
    }


    /**
     * Retrieves a combined list of all sub-map keys for a given list of main map keys.
     * * @param localRepoCache The nested map structure (Repo -> Filename -> Hash).
     * @param targetRepoKeys The List of main map keys (e.g., repository names) to check.
     * @return A List<String> containing all inner keys (class/file names) from the selected repositories.
     */
    public List<String> getSubMapKeysForSelectedRepos(
            Map<String, Map<String, String>> localRepoCache,
            List<String> targetRepoKeys) {

        List<String> combinedSubMapKeys = new ArrayList<>();

        // 1. Iterate through the provided list of main keys
        for (String repoKey : targetRepoKeys) {

            // 2. Get the inner map (Map<String, String>) using the main key
            Map<String, String> innerMap = localRepoCache.get(repoKey);

            // 3. Check if the key exists and the map is not null
            if (innerMap != null) {

                // 4. Add all keys from the inner map to the combined list
                combinedSubMapKeys.addAll(innerMap.keySet());
            } else {
                // Optional: Print a message if a key in your list doesn't exist in the cache
                System.out.println("Warning: Main key '" + repoKey + "' not found in the cache.");
            }
        }

        return combinedSubMapKeys;
    }

    public List<String> getAvailableRepositories() {
        return gitRepoLister.getPublicRepoList();
    }

    public String getClassCode(String repoIdentifier, String fileName){
        return ImpactAnalyzerUtil.
                getSubMapValue(temporaryCacheGitFetcher.getRepoMetaData(),repoIdentifier,fileName);
    }


}
