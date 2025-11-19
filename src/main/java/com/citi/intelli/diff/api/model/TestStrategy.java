package com.citi.intelli.diff.api.model;

import lombok.Data;

import java.util.List;

@Data
public class TestStrategy {
    public String scope;
    public String priority;
    public List<TestCaseRequired> testCasesRequired;

}