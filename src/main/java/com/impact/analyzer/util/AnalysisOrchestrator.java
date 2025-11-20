package com.impact.analyzer.util;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.impact.analyzer.analyzer.DependencyResolver;
import com.impact.analyzer.analyzer.ImpactAnalyzer;
import com.impact.analyzer.analyzer.JGitSourceCodeManager;
import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.api.model.ImpactReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Orchestrator class responsible for sequencing the analysis:
 * 1. Loading code maps (original/modified).
 * 2. Generating semantic diffs for the changed file (Module A).
 * 3. Finding cross-module callers (Module B, C, etc.).
 * 4. Executing parallel LLM analysis tasks.
 */
@Component
@Slf4j
public class AnalysisOrchestrator { // 💡 Renamed from ImpactAnalyzerUtil

    private final JGitSourceCodeManager sourceCodeManager; // Renamed dependency
    private final ImpactAnalyzer impactAnalyzer;
    private final DependencyResolver dependencyResolver;
    private final SemanticDiffGenerator diffGenerator; // New dependency
    private final CodeMetadataExtractor metadataExtractor; // New dependency

    // Constructor Injection
    public AnalysisOrchestrator(
            JGitSourceCodeManager sourceCodeManager,
            ImpactAnalyzer impactAnalyzer,
            DependencyResolver dependencyResolver,
            SemanticDiffGenerator diffGenerator,
            CodeMetadataExtractor metadataExtractor) {
        this.sourceCodeManager = sourceCodeManager;
        this.impactAnalyzer = impactAnalyzer;
        this.dependencyResolver = dependencyResolver;
        this.diffGenerator = diffGenerator;
        this.metadataExtractor = metadataExtractor;
        log.info("AnalysisOrchestrator initialized.");
    }

    // --- Main Workflow Method ---

    /**
     * Executes the end-to-end impact analysis pipeline.
     */
    public List<AggregatedChangeReport> getImpactAnalysisReport(
            List<String> totalFileList,
            Map<String, String> allFilesWithMetaDataMap,
            String changedCode,
            String targetFilename) throws Exception {

        // 1. Prepare Code Maps (Original and Modified)
        Map<String, Map<String, String>> codeMaps = loadDynamicCodeMaps(totalFileList, allFilesWithMetaDataMap, changedCode, targetFilename);
        Map<String, String> originalDependentCodes = codeMaps.get("original");
        Map<String, String> modifiedDependentCodes = codeMaps.get("modified");

        // Determine the FQN of the changed file
        String changedFileFQN = totalFileList.stream()
                .filter(fqn -> fqn.endsWith(targetFilename.replace(".java", "")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target file FQN not found in file list."));

        // 2. DYNAMIC DIFF & METADATA EXTRACTION (Semantic Diff)
        Map<String, List<String>> allModuleADiffs = diffGenerator.generateSemanticDiffs(
                originalDependentCodes,
                modifiedDependentCodes,
                changedFileFQN
        );
        log.info("--- Found structural changes in {} file(s) within Module A ---", allModuleADiffs.size());

        // 3. EXECUTE ANALYSIS LOOP (Parallel)
        return executeParallelAnalysis(
                allModuleADiffs,
                originalDependentCodes,
                modifiedDependentCodes
        );
    }

    // --- Parallel Orchestration ---

    private List<AggregatedChangeReport> executeParallelAnalysis(
            Map<String, List<String>> allModuleADiffs,
            Map<String, String> originalDependentCodes,
            Map<String, String> modifiedDependentCodes) throws Exception {

        // 1. Collect all Futures (tasks)
        List<CompletableFuture<AggregatedChangeReport>> futures = executeAnalysisLoop(
                allModuleADiffs,
                originalDependentCodes,
                modifiedDependentCodes
        );

        if (futures.isEmpty()) {
            AggregatedChangeReport aggReport = new AggregatedChangeReport();
            aggReport.changedMethod = "No Structural Changes Detected";
            log.warn("No structural changes found. Returning empty report list.");
            return List.of(aggReport);
        }

        // 2. Combine all Futures and wait for all to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        List<AggregatedChangeReport> masterReportList = allFutures.thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .join(); // Blocks the current thread until all tasks complete

        log.info("All {} analysis tasks completed.", masterReportList.size());
        return masterReportList;
    }

    // --- Core Analysis Loop (Generates Futures) ---

    private List<CompletableFuture<AggregatedChangeReport>> executeAnalysisLoop(
            Map<String, List<String>> allModuleADiffs,
            Map<String, String> originalDependentCodes,
            Map<String, String> modifiedDependentCodes) throws Exception {

        // Setup mock files for DependencyResolver (This is a fragile, synchronous step)
        setupMockFiles(modifiedDependentCodes);

        List<CompletableFuture<AggregatedChangeReport>> analysisFutures = new ArrayList<>();

        for (Map.Entry<String, List<String>> fileChangeEntry : allModuleADiffs.entrySet()) {
            String changedFileFQN = fileChangeEntry.getKey();
            List<String> diffSnippets = fileChangeEntry.getValue();

            for (String changeDiffSnippet : diffSnippets) {
                String targetMethodName = metadataExtractor.extractTargetMemberName(changeDiffSnippet); // Use new extractor

                log.debug("Processing diff for file: {} - Member: {}", changedFileFQN, targetMethodName);

                // D. Find ALL callers (Synchronous step - must happen before LLM call)
                // Use the injected DependencyResolver
                Map<String, List<MethodDeclaration>> allCallersMap =
                        dependencyResolver.findPotentialCallers(changedFileFQN, targetMethodName, originalDependentCodes);

                // E. Prepare LLM Context (Synchronous step)
                Map<String, CompilationUnit> llmContextMap = new HashMap<>();
                for (String callingModuleFQN : allCallersMap.keySet()) {
                    String moduleCode = originalDependentCodes.get(callingModuleFQN);
                    if (moduleCode != null) {
                        try {
                            llmContextMap.put(callingModuleFQN, StaticJavaParser.parse(moduleCode));
                        } catch (Exception e) {
                            log.error("Failed to parse context AST for {}.", callingModuleFQN, e);
                        }
                    }
                }

                // --- 4. CREATE ASYNCHRONOUS TASK ---
                CompletableFuture<ImpactReport> reportFuture = impactAnalyzer.analyze(
                        changeDiffSnippet,
                        llmContextMap,
                        targetMethodName
                );

                // --- 5. AGGREGATION STEP (Mapping the Future) ---
                CompletableFuture<AggregatedChangeReport> aggFuture = reportFuture.thenApply(report -> {
                    AggregatedChangeReport aggReport = new AggregatedChangeReport();
                    aggReport.changedMethod = targetMethodName;
                    aggReport.llmReport = report;
                    log.debug("Successfully received and aggregated LLM report for {}.", targetMethodName);
                    return aggReport;
                }).exceptionally(ex -> {
                    log.error("Error analyzing task for method {}: {}", targetMethodName, ex.getMessage(), ex);
                    // Return a failure report
                    AggregatedChangeReport failedReport = new AggregatedChangeReport();
                    failedReport.changedMethod = targetMethodName + " (Failed)";
                    failedReport.llmReport = new ImpactReport();
                    return failedReport;
                });

                analysisFutures.add(aggFuture);
            }
        }

        return analysisFutures;
    }

    // --- Data Loading/Preparation Methods ---

    /**
     * Loads the FQN list into two maps: 'original' (all files) and 'modified' (where only the target file is changed).
     */
    public Map<String, Map<String, String>> loadDynamicCodeMaps(
            List<String> data,
            Map<String, String> allFilesWithMetaDataMap,
            String changedCode,
            String targetFilename) {

        Map<String, String> originalDependentCodes = new HashMap<>();
        Map<String, String> modifiedDependentCodes = new HashMap<>();

        // Remove duplicates and find the target FQN
        List<String> uniqueFqns = new ArrayList<>(new HashSet<>(data));
        String targetFQN = uniqueFqns.stream()
                .filter(fqn -> fqn.endsWith(targetFilename.replace(".java", "")))
                .findFirst().orElse(null);

        if (targetFQN == null) {
            log.error("Target file {} not found in the unique FQN list.", targetFilename);
            return Map.of("original", Collections.emptyMap(), "modified", Collections.emptyMap());
        }

        for (String fqn : uniqueFqns) {
            String originalContent = allFilesWithMetaDataMap.get(fqn);

            if (originalContent == null) {
                log.warn("Original content for FQN {} not found in metadata map.", fqn);
                continue;
            }

            // All files get their original content
            originalDependentCodes.put(fqn, originalContent);

            if (fqn.equals(targetFQN)) {
                // The target file gets the modified content
                modifiedDependentCodes.put(fqn, changedCode);
                log.debug("Loaded modified code for target FQN: {}", fqn);
            } else {
                // All other files remain unchanged
                modifiedDependentCodes.put(fqn, originalContent);
            }
        }

        return Map.of("original", originalDependentCodes, "modified", modifiedDependentCodes);
    }

    // --- Temporary File Management (Fragile, retained for DependencyResolver stub) ---

    private static void setupMockFiles(Map<String, String> moduleCodes) throws Exception {
        Path tempDir = Files.createTempDirectory("mock-project-root");
        Path srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);

        System.setProperty("PROJECT_ROOT", srcDir.toString());
        log.warn("SETTING SYSTEM PROPERTY PROJECT_ROOT: {}. This is required by the DependencyResolver stub.", srcDir);

        for (Map.Entry<String, String> entry : moduleCodes.entrySet()) {
            String fqn = entry.getKey();
            String code = entry.getValue();

            String filePath = fqn.replace('.', File.separatorChar) + ".java";
            Path file = srcDir.resolve(filePath);
            Files.createDirectories(file.getParent());
            Files.writeString(file, code);
        }
    }
}