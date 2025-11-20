package com.impact.analyzer.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ImpactReport {

    @JsonProperty("analysisId")
    private String analysisId;

    @JsonProperty("riskScore")
    private int riskScore;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("testStrategy")
    private TestStrategy testStrategy;

    @JsonProperty("impactedModules")
    private List<ImpactedModule> impactedModules;

    // Constructors
    public ImpactReport() {}

    public ImpactReport(String analysisId, int riskScore, String reasoning, TestStrategy testStrategy, List<ImpactedModule> impactedModules) {
        this.analysisId = analysisId;
        this.riskScore = riskScore;
        this.reasoning = reasoning;
        this.testStrategy = testStrategy; // <-- Added to constructor
        this.impactedModules = impactedModules;
    }

    // Getters and Setters
    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    // NEW GETTER AND SETTER
    public TestStrategy getTestStrategy() {
        return testStrategy;
    }

    public void setTestStrategy(TestStrategy testStrategy) {
        this.testStrategy = testStrategy;
    }

    public List<ImpactedModule> getImpactedModules() {
        return impactedModules;
    }

    public void setImpactedModules(List<ImpactedModule> impactedModules) {
        this.impactedModules = impactedModules;
    }

    @Data
    public static class ImpactedModule {
        @JsonProperty("moduleName")
        private String moduleName;

        @JsonProperty("impactType")
        private String impactType;

        @JsonProperty("description")
        private String description;
    }

    @Override
    public String toString() {
        return "ImpactReport{" +
                "analysisId='" + analysisId + '\'' +
                ", riskScore=" + riskScore +
                ", reasoning='" + reasoning + '\'' +
                ", testStrategy=" + testStrategy +
                ", impactedModules=" + impactedModules +
                '}';
    }
}