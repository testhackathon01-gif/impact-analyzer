package com.impact.analyzer.api.model;

import lombok.Data;

@Data
public class TestCaseRequired {
    public String moduleName;
    public String testType; // e.g., Unit/Integration Test, E2E Test
    public String focus;    // Specific area to test

    // Getters and Setters (omitted for brevity)
}