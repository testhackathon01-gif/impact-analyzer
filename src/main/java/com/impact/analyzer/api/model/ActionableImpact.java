package com.impact.analyzer.api.model;


public class ActionableImpact {
    public String moduleName;
    public String impactType; // e.g., SYNTACTIC_BREAK, SEMANTIC_BREAK
    public String issue;      // The concise, actionable description

    // Getters and Setters (omitted for brevity)

    @Override
    public String toString() {
        return "Module: " + moduleName +
                ", Impact: " + impactType +
                ", Issue: " + issue;
    }
}