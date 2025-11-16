package com.citi.intelli.diff.service;

import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.citi.intelli.diff.util.ImpactAnalyzerUtil.getImpactAnalysisReport;

@Service
public class ImpactAnalyzerServiceImpl implements ImpactAnalyzerService{

    @Override
    public List<AggregatedChangeReport> runAnalysis(List<String> repositoryUrls, String localFilePath, String targetFilename) throws Exception {

        Map<String,Map<String,String>> localRepoCache= new HashMap<>() ;

        Map<String,String> repoCode= new HashMap<>();
        repoCode.put("com.app.modulea.A_Helper","");
        repoCode.put("com.app.modulea.DataGenerator","");

        Map<String,String> repoCode2= new HashMap<>();
        repoCode2.put("com.app.moduleb.FinalReporter","");

        Map<String,String> repoCode3= new HashMap<>();
        repoCode3.put("com.app.moduled.Service","");

        localRepoCache.put("repo1",repoCode);
        localRepoCache.put("repoCode2",repoCode2);
        localRepoCache.put("repoCode3",repoCode3);




        List<String> totalFileList=new ArrayList<>();
        totalFileList.add("com.app.modulea.A_Helper");
        totalFileList.add("com.app.modulea.DataGenerator");
        totalFileList.add("com.app.moduleb.FinalReporter");
        totalFileList.add("com.app.modulec.HashConsumer");
        totalFileList.add("com.app.moduled.Service");

        // 4. EXECUTE ANALYSIS LOOP
        List<AggregatedChangeReport> masterReportList =getImpactAnalysisReport(totalFileList);

        return masterReportList;
    }

    public List<String> getAvailableRepositories() {

        // --- Mock Data for Testing ---
        return List.of(
                /*new RepositoryInfo("CoreBankingModuleA", "https://git.corp/core-a", "2025-11-14"),
                new RepositoryInfo("RiskEngine", "https://git.corp/risk-engine", "2025-11-15"),
                new RepositoryInfo("SharedUtils", "https://git.corp/shared-utils", "2025-11-10"),
                new RepositoryInfo("LegacyServiceB", "https://git.corp/legacy-b", "2025-10-28")*/
        );
    }

    public String getClassCode(String repoIdentifier, String filename){
        return null;
    }
}
