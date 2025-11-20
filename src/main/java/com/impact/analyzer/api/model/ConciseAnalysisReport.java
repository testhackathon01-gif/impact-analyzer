package com.impact.analyzer.api.model;
 // Adjust package as needed

import lombok.Data;

import java.util.List;

@Data
public class ConciseAnalysisReport {
    public String changedMember;
    public String memberType;
    public int riskScore;
    public String summaryReasoning;
    public TestStrategy testStrategy;
    public List<ActionableImpact> actionableImpacts;

}