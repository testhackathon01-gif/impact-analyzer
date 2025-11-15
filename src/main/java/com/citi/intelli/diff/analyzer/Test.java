package com.citi.intelli.diff.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Test {

    // --- AGGREGATION CONTAINER ---
    /**
     * Used to collect individual analysis reports within the loop.
     */
    static class AggregatedChangeReport {
        String changedMethod;
        ImpactReport llmReport;
    }

    // --- HELPER METHODS ---

    /**
     * 1. Setup Mock Files: Writes code strings to a temporary directory for Symbol Solver
     */
    private static void setupMockFiles(Map<String, String> moduleCodes) throws Exception {
        Path tempDir = Files.createTempDirectory("mock-project-root");
        Path srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);

        System.setProperty("PROJECT_ROOT", srcDir.toString());

        for (Map.Entry<String, String> entry : moduleCodes.entrySet()) {
            String fqn = entry.getKey();
            String code = entry.getValue();

            String filePath = fqn.replace('.', File.separatorChar) + ".java";
            Path file = srcDir.resolve(filePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, code);
        }
    }

    /**
     * 2. FQN Extraction: String-based (Robust)
     */
    private static Optional<String> extractFQN(String code) {
        String packageName = "";
        String className = null;

        for (String line : code.split("[\r\n]")) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("package ")) {
                packageName = trimmedLine.substring("package ".length()).split(";")[0].trim() + ".";
            }
            if (className == null &&
                    (trimmedLine.contains("public class ") || trimmedLine.contains("public interface "))) {

                String[] parts;
                if (trimmedLine.contains("public class ")) {
                    parts = trimmedLine.split("public class ");
                } else {
                    parts = trimmedLine.split("public interface ");
                }

                if (parts.length > 1) {
                    className = parts[1].split("\\s|\\{")[0].trim();
                }
            }
        }

        if (className != null) {
            return Optional.of(packageName + className);
        }
        return Optional.empty();
    }

    /**
     * 3. Target Method Name Extraction: Extracts method name from a single diff snippet.
     */
    private static String extractTargetMethodName(String diffSnippet) {
        int startIndex = diffSnippet.indexOf("// NEW Signature:");
        if (startIndex == -1) return "unknownMethod";

        int headerEndIndex = diffSnippet.indexOf('\n', startIndex);
        if (headerEndIndex == -1) return "unknownMethod";

        String methodCodeBlock = diffSnippet.substring(headerEndIndex).trim();

        try {
            String wrappedCode = "class Dummy {" + methodCodeBlock + "}";
            CompilationUnit cu = StaticJavaParser.parse(wrappedCode);
            Optional<MethodDeclaration> method = cu.findFirst(MethodDeclaration.class);

            if (method.isPresent()) {
                return method.get().getNameAsString();
            }
        } catch (Exception e) {
            System.err.println("Critical error: Failed to parse method code for dynamic name extraction. " + e.getMessage());
        }

        return "unknownMethod"; // Fallback
    }

    /**
     * 4. Semantic Diff Generation: Returns a List of snippets, one for each structural change.
     */
    public static List<String> generateSemanticDiff(String originalContent, String modifiedContent) {
        try {
            CompilationUnit originalCu = StaticJavaParser.parse(originalContent);
            CompilationUnit modifiedCu = StaticJavaParser.parse(modifiedContent);

            List<String> semanticDiffs = new java.util.ArrayList<>();

            for (MethodDeclaration modifiedMethod : modifiedCu.findAll(MethodDeclaration.class)) {
                String methodName = modifiedMethod.getNameAsString();

                //if (modifiedMethod.isConstructor()) continue;

                Optional<MethodDeclaration> originalMethodOpt = originalCu.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(m -> m.getNameAsString().equals(methodName))
                        .findFirst();

                if (originalMethodOpt.isPresent()) {
                    MethodDeclaration originalMethod = originalMethodOpt.get();

                    boolean isSignatureChanged = !originalMethod.getType().equals(modifiedMethod.getType()) ||
                            !originalMethod.getParameters().equals(modifiedMethod.getParameters());

                    if (isSignatureChanged) {
                        StringBuilder semanticDiff = new StringBuilder();
                        String oldSignature = originalMethod.getDeclarationAsString(false, true, true);
                        String newSignature = modifiedMethod.getDeclarationAsString(false, true, true);
                        String newBody = modifiedMethod.toString();

                        semanticDiff.append("// OLD Signature: ").append(oldSignature).append(" \n");
                        semanticDiff.append("// NEW Signature: ").append(newSignature).append(" \n");
                        semanticDiff.append(newBody).append("\n");

                        semanticDiffs.add(semanticDiff.toString());
                    }
                }
            }
            return semanticDiffs;
        } catch (Exception e) {
            System.err.println("Semantic Diff Error: " + e.getMessage());
            return List.of();
        }
    }

    // --- MAIN METHOD ---

    public static void main(String[] args) {
        // --- 1. DEFINE ALL CODE MODULES (Simulated Git Checkout) ---

        // A. Module A (The changed module with 2 structural changes)
        String originalModuleACode =
                "package com.app.modulea;\n" +
                        "public class DataGenerator {\n" +
                        "    public String generateData() { return new String(\"hello\"); }\n" +
                        "    public int otherMethod(String param) { return param.length(); }\n" +
                        "    public static class SimplifiedOutput { private String status; public SimplifiedOutput(String id, String status) { this.status = status; } public String getStatus() { return status; } }\n" +
                        "}";

        String modifiedModuleACode =
                "package com.app.modulea;\n" +
                        "public class DataGenerator {\n" +
                        "    public SimplifiedOutput generateData() { return new SimplifiedOutput(\"id1\", \"CLEAN\"); }\n" +
                        "    public long otherMethod(String param) { return param.length() * 2L; }\n" +
                        "    public static class SimplifiedOutput { private String status; public SimplifiedOutput(String id, String status) { this.status = status; } public String getStatus() { return status; } }\n" +
                        "}";

        // C. Module C (Depends on generateData)
        String moduleCCode =
                "package com.app.modulec; \n" +
                        "import com.app.modulea.DataGenerator; \n" +
                        "public class FinalReporter {\n" +
                        "    public void runReport() {\n" +
                        "        DataGenerator a = new DataGenerator();\n" +
                        "        DataGenerator.SimplifiedOutput outputA = a.generateData();\n" +
                        "        System.out.println(outputA.getStatus());\n" +
                        "    }\n" +
                        "}";

        // D. Module D (Depends on otherMethod)
        String moduleDCode =
                "package com.app.moduled; \n" +
                        "import com.app.modulea.DataGenerator; \n" +
                        "public class Processor {\n" +
                        "    public void execute() {\n" +
                        "        DataGenerator a = new DataGenerator();\n" +
                        "        long result = a.otherMethod(\"test\");\n" +
                        "        System.out.println(result);\n" +
                        "    }\n" +
                        "}";

        // E. Module E (No dependency, should be ignored)
        String moduleECode =
                "package com.app.modulee; \n" +
                        "public class Service { public void init() {} }";


        Map<String, String> allModuleCodes = Map.of(
                "modifiedModuleACode", modifiedModuleACode,
                "moduleCCode", moduleCCode,
                "moduleDCode", moduleDCode,
                "moduleECode", moduleECode
        );

        // --- 2. DYNAMIC DIFF & METADATA EXTRACTION ---
        try {
            // A. Generate ALL LLM Input Diff Strings (List of 2 snippets)
            List<String> moduleADiffs = generateSemanticDiff(originalModuleACode, modifiedModuleACode);
            System.out.println("--- Found " + moduleADiffs.size() + " structural changes in Module A ---");

            if (moduleADiffs.isEmpty()) {
                System.out.println("No structural changes found. Analysis complete.");
                return;
            }

            // B. Setup Environment
            setupMockFiles(allModuleCodes);
            DependencyResolver resolver = new DependencyResolver(
                    List.of(System.getProperty("PROJECT_ROOT"))
            );

            // --- 3. DYNAMIC ANALYSIS LOOP & AGGREGATION ---

            // Master list to hold all individual reports
            List<AggregatedChangeReport> masterReportList = new java.util.ArrayList<>();
            int totalRiskScore = 0;

            // Loop through EACH change and run the FULL analysis pipeline
            for (String changeDiffSnippet : moduleADiffs) {

                String targetMethodName = extractTargetMethodName(changeDiffSnippet);
                String moduleAFQN = extractFQN(modifiedModuleACode).orElseThrow(() -> new IllegalStateException("FQN failed for Module A."));

                // Dynamic Dependency Discovery and LLM Context Prep
                Map<String, List<MethodDeclaration>> allCallersMap =
                        resolver.findAllCallersOfMethod(moduleAFQN, targetMethodName, allModuleCodes);

                Map<String, CompilationUnit> llmContextMap = new java.util.HashMap<>();
                for (String callingModuleFQN : allCallersMap.keySet()) {
                    String moduleCode = allModuleCodes.get(callingModuleFQN);
                    if (moduleCode != null) {
                        llmContextMap.put(callingModuleFQN, StaticJavaParser.parse(moduleCode));
                    }
                }

                // --- 4. RUN LLM ANALYZER ---
                ImpactAnalyzer analyzer = new ImpactAnalyzer();
                ImpactReport report = analyzer.analyze(
                        changeDiffSnippet,
                        llmContextMap,
                        targetMethodName
                );

                // --- AGGREGATION STEP ---
                AggregatedChangeReport aggReport = new AggregatedChangeReport();
                aggReport.changedMethod = targetMethodName;
                aggReport.llmReport = report;
                masterReportList.add(aggReport);

                totalRiskScore += report.getRiskScore();
            }

            // --- 5. SINGLE CONSOLIDATED OUTPUT ---
            System.out.println("\n\n==============================================================");
            System.out.println("====== 🎯 FINAL CONSOLIDATED IMPACT ANALYSIS REPORT 🎯 ======");
            System.out.println("==============================================================");

            // Calculate overall metrics
            double averageRisk = masterReportList.isEmpty() ? 0 : (double)totalRiskScore / masterReportList.size();
            System.out.printf("Overall Average Risk Score: **%.1f/10** (Based on %d analyzed changes)\n", averageRisk, masterReportList.size());
            System.out.println("--------------------------------------------------------------");

            // List all individual impacts together
            System.out.println("## 🔍 Detailed Impact Breakdown by Changed Method");

            for (AggregatedChangeReport aggReport : masterReportList) {
                String method = aggReport.changedMethod;
                ImpactReport report = aggReport.llmReport;

                System.out.println("\n### ➡️ Change: **" + method + "()** (Risk: " + report.getRiskScore() + "/10)");
                System.out.println("> **LLM Reasoning:** " + report.getReasoning());

                if (report.getImpactedModules() != null && !report.getImpactedModules().isEmpty()) {
                    System.out.println("\n* **Impacted Modules:**");
                    report.getImpactedModules().stream()
                            .sorted(java.util.Comparator.comparing(ImpactReport.ImpactedModule::getModuleName))
                            .forEach(module -> {
                                System.out.println("  * **" + module.getModuleName() + "** (Type: " + module.getImpactType() + ")");
                                System.out.println("    > Description: " + module.getDescription());
                            });
                } else {
                    System.out.println("* **No impacted modules found by dependency analysis.**");
                }
            }
            System.out.println("\n==============================================================");


        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\n!!! A runtime error occurred. Check stack trace for detail. !!!");
        }
    }
}