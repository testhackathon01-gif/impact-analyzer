package com.impact.analyzer.api.model;

import lombok.Data;

@Data
public class TestCaseRequired {
    public String moduleName;
    public String testType;
    public String focus;
}