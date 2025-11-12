
package com.citi.intelli.diff.analyzer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ImpactReport {

    @JsonProperty("analysisId")
    private String analysisId;

    @JsonProperty("riskScore")
    private int riskScore;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("impactedModules")
    private List<ImpactedModule> impactedModules;

    // Constructors
    public ImpactReport() {}

    public ImpactReport(String analysisId, int riskScore, String reasoning, List<ImpactedModule> impactedModules) {
        this.analysisId = analysisId;
        this.riskScore = riskScore;
        this.reasoning = reasoning;
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

    public List<ImpactedModule> getImpactedModules() {
        return impactedModules;
    }

    public void setImpactedModules(List<ImpactedModule> impactedModules) {
        this.impactedModules = impactedModules;
    }

    // Nested class for impacted modules
    public static class ImpactedModule {

        @JsonProperty("moduleName")
        private String moduleName;

        @JsonProperty("impactType")
        private String impactType;

        @JsonProperty("description")
        private String description;

        // Constructors
        public ImpactedModule() {}

        public ImpactedModule(String moduleName, String impactType, String description) {
            this.moduleName = moduleName;
            this.impactType = impactType;
            this.description = description;
        }

        // Getters and Setters
        public String getModuleName() {
            return moduleName;
        }

        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }

        public String getImpactType() {
            return impactType;
        }

        public void setImpactType(String impactType) {
            this.impactType = impactType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "ImpactedModule{" +
                    "moduleName='" + moduleName + '\'' +
                    ", impactType='" + impactType + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ImpactReport{" +
                "analysisId='" + analysisId + '\'' +
                ", riskScore=" + riskScore +
                ", reasoning='" + reasoning + '\'' +
                ", impactedModules=" + impactedModules +
                '}';
    }
}