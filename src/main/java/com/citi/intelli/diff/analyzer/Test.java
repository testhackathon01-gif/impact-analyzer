package com.citi.intelli.diff.analyzer;

// --- JavaParser Imports ---

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.util.HashMap;
import java.util.Map;

public class Test {

    public static void main(String[] args) {
        try {
            // --- 1. MOCK CODE INPUT (Simulating Code Parsing Phase) ---

            // The change in Module A
            String moduleADiff =
                    "// OLD Signature: public String generateData() \n" +
                            "// NEW Signature: public SimplifiedOutput generateData() \n" +
                            "public SimplifiedOutput generateData() {\n" +
                            "   // Data value changes from \"TypeX\" to \"CLEAN\"\n" +
                            "   return new SimplifiedOutput(id, \"CLEAN\");\n" +
                            "}";

            // Mock ASTs for Modules B and C
            Map<String, CompilationUnit> relevantContextASTs = new HashMap<>();

            // Module B (Indirect Dependency)
            String moduleBCode =
                    "package com.app.moduleb; \n" +
                            "public class DataProcessor {\n" +
                            "    public void processData(InputData input) {\n" +
                            "        // This logic breaks if the status is CLEAN instead of TypeY\n" +
                            "        if (input.getStatus().equals(\"TypeY\")) {\n" +
                            "            System.out.println(\"Processing TypeY data\");\n" +
                            "        }\n" +
                            "    }\n" +
                            "}";
            relevantContextASTs.put("com.app.moduleb.DataProcessor", StaticJavaParser.parse(moduleBCode));

            // Module C (Direct Dependency)
            String moduleCCode =
                    "package com.app.modulec; \n" +
                            "public class FinalReporter {\n" +
                            "    // Assume 'a' is a DataGenerator (Module A)\n" +
                            "    public void runReport(DataGenerator a) {\n" +
                            "        // DIRECT BREAK: Expects String, gets SimplifiedOutput\n" +
                            "        String outputA = a.generateData();\n" +
                            "        // ... logic that uses Module B's indirect output ...\n" +
                            "    }\n" +
                            "}";
            relevantContextASTs.put("com.app.modulec.FinalReporter", StaticJavaParser.parse(moduleCCode));

            // --- 2. RUN ANALYZER ---
            ImpactAnalyzer analyzer = new ImpactAnalyzer();
            ImpactReport report = analyzer.analyze(moduleADiff, relevantContextASTs, "generateData");

            // --- 3. DISPLAY RESULTS ---
            System.out.println("\n--- Impact Analysis Report ---");
            System.out.println("Analysis ID: " + report.getAnalysisId());
            System.out.println("Risk Score: " + report.getRiskScore() + "/10");
            System.out.println("Reasoning: " + report.getReasoning());
            System.out.println("------------------------------");

            if (report.getImpactedModules() != null && !report.getImpactedModules().isEmpty()) {
                for (ImpactReport.ImpactedModule module : report.getImpactedModules()) {
                    System.out.println("\n[IMPACT FOUND: " + module.getModuleName() + "]");
                    System.out.println("  Type: " + module.getImpactType());
                    System.out.println("  Description: " + module.getDescription());
                }
            } else {
                System.out.println("\nNo impacted modules detected.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\n!!! ERROR: Check your Gemini API key and dependencies. !!!");
        }
    }
}