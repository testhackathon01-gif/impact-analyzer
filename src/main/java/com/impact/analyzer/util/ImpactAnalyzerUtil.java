package com.impact.analyzer.util;

import com.impact.analyzer.analyzer.DependencyResolver;
import com.impact.analyzer.analyzer.ImpactAnalyzer;
import com.impact.analyzer.analyzer.TemporaryCacheGitFetcher;
import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.api.model.ImpactReport;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class ImpactAnalyzerUtil {

    final static String FILE_SPLIT_MARKER = "\n// --- FILE SPLIT --- \n";

    @Autowired
    TemporaryCacheGitFetcher temporaryCacheGitFetcher;

    @Autowired
    private ImpactAnalyzer impactAnalyzer;

    //public static final String ORIGINAL_FOLDER_PATH = "C:\\Users\\DELL\\Downloads\\impact-analyzer\\original\\";
    //public static final String MODIFIED_FOLDER_PATH = "C:\\Users\\DELL\\Downloads\\impact-analyzer\\modified\\";


    private static String extractTargetMemberName(String diffSnippet) {
        // 1. Check for FIELD change markers
        if (diffSnippet.contains("// TYPE: FIELD_MODIFIED") ||
                diffSnippet.contains("// TYPE: FIELD_ADDED") ||
                diffSnippet.contains("// TYPE: FIELD_REMOVED")) {

            // Look for the specific FIELD name marker
            int startIndex = diffSnippet.indexOf("// FIELD:");
            if (startIndex != -1) {
                int nameStart = startIndex + "// FIELD:".length();
                int nameEnd = diffSnippet.indexOf('\n', nameStart);

                // Extract the field name and trim whitespace
                return diffSnippet.substring(nameStart, nameEnd).trim();
            }
        }

        // 2. Fallback to METHOD change markers (your existing logic, modified)
        int methodStartIndex = diffSnippet.indexOf("// NEW Signature:");
        if (methodStartIndex != -1) {

            // Find the start of the actual method code block
            int headerEndIndex = diffSnippet.indexOf('\n', methodStartIndex);
            if (headerEndIndex == -1) return "unknownMember";

            String methodCodeBlock = diffSnippet.substring(headerEndIndex).trim();

            try {
                // Safely wrap and parse the method code block
                String wrappedCode = "class Dummy {" + methodCodeBlock + "}";
                CompilationUnit cu = StaticJavaParser.parse(wrappedCode);
                Optional<MethodDeclaration> method = cu.findFirst(MethodDeclaration.class);

                if (method.isPresent()) {
                    return method.get().getNameAsString();
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to parse method code for name extraction. " + e.getMessage());
            }
        }

        // 3. Fallback for other Type or Metadata changes
        if (diffSnippet.contains("// TYPE: TYPE_") || diffSnippet.contains("// TYPE: STRUCTURAL_")) {
            // Look for the generic MEMBER name marker
            int startIndex = diffSnippet.indexOf("// MEMBER:");
            if (startIndex != -1) {
                int nameStart = startIndex + "// MEMBER:".length();
                int nameEnd = diffSnippet.indexOf('\n', nameStart);
                return diffSnippet.substring(nameStart, nameEnd).trim();
            }
            // If it's metadata, just return a generic indicator
            return "FileMetadataChange";
        }

        return "unknownMember";
    }

    public static String getSubMapValue(Map<String,Map<String,String>> localRepoCache,String repoKey,
                                        String fileKey) {
        // 1. Get the inner map using the repository key
        Map<String, String> innerMap = localRepoCache.get(repoKey);
        // Check if the repository key exists
        if (innerMap == null) {
            // Repository not found, return null
            return null;
        }
        // 2. Get the final value using the file key on the inner map
        return innerMap.get(fileKey);
    }

// Inside ImpactAnalyzerUtil.java

    public List<CompletableFuture<AggregatedChangeReport>> executeAnalysisLoop( // <-- Changed return type
                                                                                Map<String, List<String>> allModuleADiffs,
                                                                                Map<String, String> originalDependentCodes,
                                                                                Map<String, String> modifiedDependentCodes) throws Exception {

        if (allModuleADiffs.isEmpty()) {
            System.out.println("No structural changes found. Analysis complete.");
            return List.of();
        }

        // DependencyResolver setup... (remains the same)
        setupMockFiles(modifiedDependentCodes);
        DependencyResolver resolver = new DependencyResolver(
                List.of(System.getProperty("PROJECT_ROOT"))
        );

        // 💡 CHANGED: List to store asynchronous futures, not reports
        List<CompletableFuture<AggregatedChangeReport>> analysisFutures = new ArrayList<>();

        for (Map.Entry<String, List<String>> fileChangeEntry : allModuleADiffs.entrySet()) {
            String changedFileFQN = fileChangeEntry.getKey();
            List<String> diffSnippets = fileChangeEntry.getValue();

            for (String changeDiffSnippet : diffSnippets) {
                String targetMethodName = extractTargetMemberName(changeDiffSnippet);

                // ... (Debugging/Logging) ...

                // D. Find ALL callers (Synchronous step - must happen before LLM call)
                Map<String, List<MethodDeclaration>> allCallersMap =
                        resolver.findAllCallersOfMethod(changedFileFQN, targetMethodName, originalDependentCodes);

                // E. Prepare LLM Context (Synchronous step)
                Map<String, CompilationUnit> llmContextMap = new HashMap<>();
                for (String callingModuleFQN : allCallersMap.keySet()) {
                    String moduleCode = originalDependentCodes.get(callingModuleFQN);
                    if (moduleCode != null) {
                        llmContextMap.put(callingModuleFQN, StaticJavaParser.parse(moduleCode));
                    }
                }

                // --- 4. CREATE ASYNCHRONOUS TASK ---
                // 💡 CHANGED: Call the injected analyzer and get a Future
                CompletableFuture<ImpactReport> reportFuture = impactAnalyzer.analyze( // Use the injected field
                        changeDiffSnippet,
                        llmContextMap,
                        targetMethodName
                );

                // --- 5. AGGREGATION STEP (Mapping the Future) ---
                // 💡 CHANGED: Map the result of the Future to the AggregatedChangeReport
                CompletableFuture<AggregatedChangeReport> aggFuture = reportFuture.thenApply(report -> {
                    AggregatedChangeReport aggReport = new AggregatedChangeReport();
                    aggReport.changedMethod = targetMethodName;
                    aggReport.llmReport = report;
                    return aggReport;
                }).exceptionally(ex -> {
                    System.err.println("Error analyzing task for method " + targetMethodName + ": " + ex.getMessage());
                    // Return a failure report
                    AggregatedChangeReport failedReport = new AggregatedChangeReport();
                    failedReport.changedMethod = targetMethodName + " (Failed)";
                    failedReport.llmReport = new ImpactReport(); // Empty report or specific error object
                    return failedReport;
                });

                analysisFutures.add(aggFuture);
            }
        }

        // No need for the empty report check here; it's handled in the final step

        return analysisFutures; // Return the list of Futures
    }

    // Inside ImpactAnalyzerUtil.java

    public List<AggregatedChangeReport> getImpactAnalysisReport(List<String> totalFileList,Map<String, String> allFilesWithMetaDataMap,String changedCode, String fileName) throws Exception {

        Map<String, Map<String, String>> data = loadDynamicCodeMaps(totalFileList,allFilesWithMetaDataMap,changedCode, fileName);
        Map<String, String> originalDependentCodes = data.get("original");
        Map<String, String> modifiedDependentCodes = data.get("modified");

        String fileNameWithPackage = null;
        for (String fqn : totalFileList) {
            // Convert FQN to a relative file path (e.g., com.app.modulea.A_Helper -> com/app/modulea/A_Helper.java)
            String relativeFilePath = fqn.replace('.', File.separatorChar) + ".java";
            try {
                if(relativeFilePath.contains(fileName)){
                    fileNameWithPackage=fqn;
                }
            } catch (Exception e) {
                // Handle files that might not exist in one version (e.g., deleted or added)
                System.err.println("Warning: Could not load code for FQN " + fqn + ". " + e.getMessage());
                // In a real system, you'd decide how to handle missing files (e.g., put null)
            }
        }

        // 3. DYNAMIC DIFF & METADATA EXTRACTION
        Map<String, List<String>> allModuleADiffs = generateDiffs(originalDependentCodes, modifiedDependentCodes, fileNameWithPackage);
        System.out.println("--- Found structural changes in " + allModuleADiffs.size() + " file(s) within Module A ---");

        // 4. EXECUTE ANALYSIS LOOP (Now calling the parallel orchestrator)
        List<AggregatedChangeReport> masterReportList = executeParallelAnalysis( // 💡 CHANGED LINE
                allModuleADiffs,
                originalDependentCodes,
                modifiedDependentCodes
        );
        return masterReportList;
    }

    // Inside ImpactAnalyzerUtil.java

    public List<AggregatedChangeReport> executeParallelAnalysis(
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
            return List.of();
        }

        // 2. Combine all Futures into a single Future that completes when ALL are done
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        // 3. Wait for all to complete and collect the results
        List<AggregatedChangeReport> masterReportList = allFutures.thenApply(v -> futures.stream()
                        .map(future -> future.join()) // .join() retrieves the result (and throws unchecked exception on failure)
                        .collect(Collectors.toList()))
                .join(); // Blocks the current thread until all tasks complete

        // 4. Handle the scenario where no changes were found but we still want a report for consistency
        if(masterReportList.isEmpty()){
            AggregatedChangeReport aggReport = new AggregatedChangeReport();
            aggReport.changedMethod = "No Impact Found!!";
            masterReportList.add(aggReport);
        }

        return masterReportList;
    }

    private static String readFileContent(String path) throws Exception {
        Path filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            // This is important for robust error handling in a dynamic system
            throw new FileNotFoundException("File not found at expected path: " + path);
        }

        // 💡 Best practice for reading small-to-medium files in Java 11+
        // Reads all content into a String, assuming UTF-8 encoding.
        // Automatically handles opening and closing the file resource.
        return Files.readString(filePath);

        // Alternative for Java 7/8/9/10:
    /*
    byte[] bytes = Files.readAllBytes(filePath);
    return new String(bytes, StandardCharsets.UTF_8);
    */
    }

    public Map<String, Map<String, String>> loadDynamicCodeMaps(List<String> data, Map<String, String> allFilesWithMetaDataMap, String changedCode, String fileName) {

        // 1. Create the two maps to hold the final FQN -> Code content
        Map<String, String> originalDependentCodes = new HashMap<>();
        Map<String, String> modifiedDependentCodes = new HashMap<>();

        data= removeDuplicatesUsingSet(data);
        // 2. Iterate over every FQN defined in the TestData
        for (String fqn : data) {

            // Convert FQN to a relative file path (e.g., com.app.modulea.A_Helper -> com/app/modulea/A_Helper.java)
            String relativeFilePath = fqn.replace('.', File.separatorChar) + ".java";

            try {
                String originalContent= allFilesWithMetaDataMap.get(fqn);
                if(relativeFilePath.contains(fileName)){
                    fileName= fqn;
                    //String modifiedPath = changedCode + File.separator + relativeFilePath;
                    //String modifiedContent = readFileContent(modifiedPath);
                    originalDependentCodes.put(fqn, originalContent);
                    modifiedDependentCodes.put(fqn, changedCode);
                }else{
                    originalDependentCodes.put(fqn, originalContent);
                    modifiedDependentCodes.put(fqn, originalContent);
                }

            } catch (Exception e) {
                // Handle files that might not exist in one version (e.g., deleted or added)
                System.err.println("Warning: Could not load code for FQN " + fqn + ". " + e.getMessage());
                // In a real system, you'd decide how to handle missing files (e.g., put null)
            }
        }
        Map<String, Map<String, String>> maps = new HashMap<>();
        maps.put("original", originalDependentCodes);
        maps.put("modified", modifiedDependentCodes);
        return maps;
    }

    public static List<String> removeDuplicatesUsingSet(List<String> listWithDuplicates) {

        // 1. Convert the ArrayList to a HashSet. The Set automatically filters out duplicates.
        Set<String> setWithoutDuplicates = new HashSet<>(listWithDuplicates);

        // 2. Convert the Set back to an ArrayList.
        List<String> listWithoutDuplicates = new ArrayList<>(setWithoutDuplicates);

        return listWithoutDuplicates;
    }

    public static Map<String, List<String>> generateDiffs(
            Map<String, String> originalCodes,
            Map<String, String> modifiedCodes, String targetFileName) {

        String originalMonolith = originalCodes.get(targetFileName);
        String modifiedMonolith = modifiedCodes.get(targetFileName);

        if (originalMonolith == null || modifiedMonolith == null) {
            System.err.println("Error: Target file A_Helper not found in dynamic maps.");
            return new HashMap<>();
        }

        // 2. Call the core semantic diff method
        return generateSemanticDiff(originalMonolith, modifiedMonolith);
    }

    public static Map<String, List<String>> generateSemanticDiff(String originalInput, String modifiedInput) {
        String[] originalFiles = originalInput.split(FILE_SPLIT_MARKER);
        String[] modifiedFiles = modifiedInput.split(FILE_SPLIT_MARKER);

        if (originalFiles.length != modifiedFiles.length) {
            throw new IllegalStateException("Original and Modified inputs must contain the same number of file fragments.");
        }

        Map<String, List<String>> allSemanticDiffs = new HashMap<>();

        for (int i = 0; i < originalFiles.length; i++) {
            String originalContent = originalFiles[i].trim();
            String modifiedContent = modifiedFiles[i].trim();

            if (originalContent.isEmpty() || modifiedContent.isEmpty()) continue;

            Optional<String> fqnOpt = extractFQN(modifiedContent);
            if (fqnOpt.isEmpty()) continue;
            String fqn = fqnOpt.get();

            if (originalContent.equals(modifiedContent)) continue;

            try {
                List<String> diffsForFile = generateSemanticDiffForFile(originalContent, modifiedContent);

                if (!diffsForFile.isEmpty()) {
                    allSemanticDiffs.put(fqn, diffsForFile);
                }
            } catch (Exception e) {
                System.err.println("Error parsing file fragment for FQN: " + fqn + ". " + e.getMessage());
            }
        }
        return allSemanticDiffs;
    }

    private static List<String> generateSemanticDiffForFile(String originalContent, String modifiedContent) {
        try {
            // Use JavaParser to parse the Abstract Syntax Trees (AST)
            CompilationUnit originalCu = StaticJavaParser.parse(originalContent);
            CompilationUnit modifiedCu = StaticJavaParser.parse(modifiedContent);

            List<String> semanticDiffs = new ArrayList<>();

            // --- 1. COLLECT ALL MEMBERS FROM ORIGINAL VERSION ---
            // Maps store the member's unique name as the key and its full string declaration as the value.

            // Map for Methods (Key: methodName)
            Map<String, String> originalMethodMap = new HashMap<>();
            originalCu.findAll(MethodDeclaration.class).forEach(m ->
                    // Use the full method declaration string for robust comparison
                    originalMethodMap.put(m.getNameAsString(), m.toString())
            );

            // Map for Fields (Key: fieldName)
            Map<String, String> originalFieldMap = new HashMap<>();
            originalCu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).forEach(f ->
                    // Put each variable name individually, associated with the parent FieldDeclaration string
                    f.getVariables().forEach(v -> originalFieldMap.put(v.getNameAsString(), f.toString()))
            );

            // Map for Top-Level Types (Classes, Enums, Interfaces) (Key: typeName)
            Map<String, String> originalTypeMap = new HashMap<>();
            originalCu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class).forEach(t ->
                    originalTypeMap.put(t.getNameAsString(), t.toString())
            );


            // --- 2. CHECK FOR MODIFICATIONS AND ADDITIONS (Iterate Modified CU) ---

            // Sets to track members found in the modified version (used later to detect deletions)
            Set<String> processedModifiedMethods = new HashSet<>();
            Set<String> processedModifiedFields = new HashSet<>();
            Set<String> processedModifiedTypes = new HashSet<>();


            // 2a. Check Methods
            for (MethodDeclaration modifiedMethod : modifiedCu.findAll(MethodDeclaration.class)) {
                String methodName = modifiedMethod.getNameAsString();
                processedModifiedMethods.add(methodName);
                String modifiedMethodString = modifiedMethod.toString();
                String originalMethodString = originalMethodMap.get(methodName);

                if (originalMethodString == null) {
                    // ADDITION
                    semanticDiffs.add("// TYPE: METHOD_ADDED \n// MEMBER: " + methodName + " \n" + modifiedMethodString + "\n");
                } else if (!originalMethodString.equals(modifiedMethodString)) {
                    // MODIFICATION
                    Optional<MethodDeclaration> originalMethodOpt = originalCu.findAll(MethodDeclaration.class)
                            .stream().filter(m -> m.getNameAsString().equals(methodName)).findFirst();

                    String oldSignature = originalMethodOpt.map(m -> m.getDeclarationAsString(false, true, true)).orElse("N/A");
                    String newSignature = modifiedMethod.getDeclarationAsString(false, true, true);

                    semanticDiffs.add(
                            "// TYPE: METHOD_MODIFIED \n" +
                                    "// OLD Signature: " + oldSignature + " \n" +
                                    "// NEW Signature: " + newSignature + " \n" +
                                    modifiedMethodString + "\n"
                    );
                }
            }

            // 2b. Check Fields (Constants/Variables)
            for (com.github.javaparser.ast.body.FieldDeclaration modifiedField : modifiedCu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
                String modifiedFieldString = modifiedField.toString();

                modifiedField.getVariables().forEach(modifiedVariable -> {
                    String fieldName = modifiedVariable.getNameAsString();
                    processedModifiedFields.add(fieldName);
                    String originalFieldString = originalFieldMap.get(fieldName);

                    if (originalFieldString == null) {
                        // ADDITION
                        semanticDiffs.add("// TYPE: FIELD_ADDED \n// MEMBER: " + fieldName + " \n" + modifiedFieldString + "\n");
                    } else if (!originalFieldString.equals(modifiedFieldString)) {
                        // MODIFICATION (Value or declaration changed)
                        semanticDiffs.add(
                                "// TYPE: FIELD_MODIFIED \n" +
                                        "// FIELD: " + fieldName + " \n" +
                                        "// OLD DECLARATION: " + originalFieldString + " \n" +
                                        "// NEW DECLARATION: " + modifiedFieldString + "\n"
                        );
                    }
                });
            }

            // 2c. Check Top-Level Types (Classes/Enums/Interfaces)
            for (com.github.javaparser.ast.body.TypeDeclaration<?> modifiedType : modifiedCu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class)) {
                String typeName = modifiedType.getNameAsString();
                processedModifiedTypes.add(typeName);
                String modifiedTypeString = modifiedType.toString();
                String originalTypeString = originalTypeMap.get(typeName);

                if (originalTypeString == null) {
                    // ADDITION
                    semanticDiffs.add("// TYPE: TYPE_ADDED \n// MEMBER: " + typeName + " \n" + modifiedTypeString + "\n");
                } else if (!originalTypeString.equals(modifiedTypeString)) {
                    // MODIFICATION (Inheritance, interfaces, annotations, or inner members changed)
                    semanticDiffs.add(
                            "// TYPE: TYPE_MODIFIED \n" +
                                    "// MEMBER: " + typeName + " \n" +
                                    "// OLD DECLARATION: " + originalTypeString + " \n" +
                                    "// NEW DECLARATION: " + modifiedTypeString + "\n"
                    );
                }
            }


            // --- 3. CHECK FOR DELETIONS (Iterate Original Maps) ---

            // 3a. Check Deleted Methods
            for (String originalMethodName : originalMethodMap.keySet()) {
                if (!processedModifiedMethods.contains(originalMethodName)) {
                    semanticDiffs.add("// TYPE: METHOD_REMOVED \n// MEMBER: " + originalMethodName + " \n" + originalMethodMap.get(originalMethodName) + "\n");
                }
            }

            // 3b. Check Deleted Fields
            for (String originalFieldName : originalFieldMap.keySet()) {
                if (!processedModifiedFields.contains(originalFieldName)) {
                    semanticDiffs.add("// TYPE: FIELD_REMOVED \n// MEMBER: " + originalFieldName + " \n" + originalFieldMap.get(originalFieldName) + "\n");
                }
            }

            // 3c. Check Deleted Types
            for (String originalTypeName : originalTypeMap.keySet()) {
                if (!processedModifiedTypes.contains(originalTypeName)) {
                    semanticDiffs.add("// TYPE: TYPE_REMOVED \n// MEMBER: " + originalTypeName + " \n" + originalTypeMap.get(originalTypeName) + "\n");
                }
            }

            // --- 4. FALLBACK FOR STRUCTURAL/METADATA CHANGES ---
            if (semanticDiffs.isEmpty() && !originalContent.equals(modifiedContent)) {
                // This captures changes to imports, package name, or class-level Javadoc/comments
                semanticDiffs.add(
                        "// TYPE: STRUCTURAL_METADATA_CHANGE \n" +
                                "// DESCRIPTION: Changes detected outside of primary members (e.g., imports, package, file-level comments). \n"
                );
            }

            return semanticDiffs;
        } catch (Exception e) {
            throw new RuntimeException("AST Parsing failed for file fragment.", e);
        }
    }

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

    private static Optional<String> extractFQN(String code) {
        String packageName = "";
        String className = null;

        for (String line : code.split("[\r\n]")) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("package ")) {
                packageName = trimmedLine.substring("package ".length()).split(";")[0].trim() + ".";
                continue;
            }

            String[] parts = trimmedLine.split("\\s+");
            int typeKeywordIndex = -1;

            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals("class") || parts[i].equals("interface") || parts[i].equals("enum")) {
                    typeKeywordIndex = i;
                    break;
                }
            }

            if (typeKeywordIndex != -1 && className == null && parts.length > typeKeywordIndex + 1) {
                String potentialClassName = parts[typeKeywordIndex + 1].split("<|\\{|extends|implements")[0].trim();

                if (!potentialClassName.isEmpty() && !potentialClassName.contains("(")) {
                    className = potentialClassName;
                    break;
                }
            }
        }

        if (className != null) {
            return Optional.of(packageName + className);
        }
        return Optional.empty();
    }


}
