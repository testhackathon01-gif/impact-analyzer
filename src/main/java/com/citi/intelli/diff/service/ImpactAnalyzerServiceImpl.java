package com.citi.intelli.diff.service;

import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.citi.intelli.diff.util.ImpactAnalyzerUtil.getImpactAnalysisReport;

@Service
public class ImpactAnalyzerServiceImpl implements ImpactAnalyzerService{

    @Override
    public List<AggregatedChangeReport> runAnalysis(List<String> repositoryUrls, String localFilePath, String targetFilename) throws Exception {
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
}
