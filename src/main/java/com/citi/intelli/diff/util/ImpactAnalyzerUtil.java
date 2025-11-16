package com.citi.intelli.diff.util;

import com.citi.intelli.diff.analyzer.DependencyResolver;
import com.citi.intelli.diff.analyzer.ImpactAnalyzer;
import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import com.citi.intelli.diff.api.model.ImpactReport;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
public class ImpactAnalyzerUtil {

    final static String FILE_SPLIT_MARKER = "\n// --- FILE SPLIT --- \n";

    public static final String ORIGINAL_FOLDER_PATH = "C:\\Users\\DELL\\Downloads\\impact-analyzer\\original\\";
    public static final String MODIFIED_FOLDER_PATH = "C:\\Users\\DELL\\Downloads\\impact-analyzer\\modified\\";


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

        return "unknownMethod";
    }

    public static List<AggregatedChangeReport> executeAnalysisLoop(
            Map<String, List<String>> allModuleADiffs,
            Map<String, String> originalDependentCodes,
            Map<String, String> modifiedDependentCodes) throws Exception {

        if (allModuleADiffs.isEmpty()) {
            System.out.println("No structural changes found. Analysis complete.");
            return List.of();
        }

        setupMockFiles(modifiedDependentCodes);
        DependencyResolver resolver = new DependencyResolver(
                List.of(System.getProperty("PROJECT_ROOT"))
        );

        List<AggregatedChangeReport> masterReportList = new ArrayList<>();

        for (Map.Entry<String, List<String>> fileChangeEntry : allModuleADiffs.entrySet()) {
            String changedFileFQN = fileChangeEntry.getKey();
            List<String> diffSnippets = fileChangeEntry.getValue();

            for (String changeDiffSnippet : diffSnippets) {
                String targetMethodName = extractTargetMethodName(changeDiffSnippet);

                System.out.println("\n############################################################");
                System.out.println("# ANALYZING CHANGE: " + changedFileFQN + "." + targetMethodName + "()");
                System.out.println("############################################################");

                // D. Find ALL callers
                Map<String, List<MethodDeclaration>> allCallersMap =
                        resolver.findAllCallersOfMethod(changedFileFQN, targetMethodName, originalDependentCodes);

                // E. Prepare LLM Context
                Map<String, CompilationUnit> llmContextMap = new HashMap<>();
                for (String callingModuleFQN : allCallersMap.keySet()) {
                    String moduleCode = originalDependentCodes.get(callingModuleFQN);
                    if (moduleCode != null) {
                        llmContextMap.put(callingModuleFQN, StaticJavaParser.parse(moduleCode));
                    }
                }

                // --- 4. RUN LLM ANALYZER ---
                ImpactAnalyzer analyzer = new ImpactAnalyzer();
                // Adding a debug print here to confirm the call
                // System.out.println(">>> IMPACT ANALYZER CALLED for method: " + targetMethodName);
                ImpactReport report = analyzer.analyze(
                        changeDiffSnippet,
                        llmContextMap,
                        targetMethodName
                );

                // --- 5. AGGREGATION STEP ---
                AggregatedChangeReport aggReport = new AggregatedChangeReport();
                aggReport.changedMethod = targetMethodName;
                aggReport.llmReport = report;
                masterReportList.add(aggReport);
            }
        }
        return masterReportList;
    }

    public static List<AggregatedChangeReport> getImpactAnalysisReport(List<String> totalFileList) throws Exception {

        Map<String, Map<String, String>> data = ImpactAnalyzerUtil.loadDynamicCodeMaps(totalFileList);
        // Call helper method directly
        Map<String, String> originalDependentCodes = data.get("original");
        Map<String, String> modifiedDependentCodes = data.get("modified");

        // 3. DYNAMIC DIFF & METADATA EXTRACTION
        Map<String, List<String>> allModuleADiffs = generateDiffs(originalDependentCodes, modifiedDependentCodes, "com.app.modulea.A_Helper"); // Call helper method directly
        System.out.println("--- Found structural changes in " + allModuleADiffs.size() + " file(s) within Module A ---");

        // 4. EXECUTE ANALYSIS LOOP
        List<AggregatedChangeReport> masterReportList = executeAnalysisLoop( // Call helper method directly
                allModuleADiffs,
                originalDependentCodes,
                modifiedDependentCodes
        );
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

    public static Map<String, Map<String, String>> loadDynamicCodeMaps(List<String> data) {

        // 1. Create the two maps to hold the final FQN -> Code content
        Map<String, String> originalDependentCodes = new HashMap<>();
        Map<String, String> modifiedDependentCodes = new HashMap<>();

        // 2. Iterate over every FQN defined in the TestData
        for (String fqn : data) {

            // Convert FQN to a relative file path (e.g., com.app.modulea.A_Helper -> com/app/modulea/A_Helper.java)
            String relativeFilePath = fqn.replace('.', File.separatorChar) + ".java";

            try {
                // A. Load Original (Baseline) Code
                String originalPath = ORIGINAL_FOLDER_PATH + File.separator + relativeFilePath;
                String originalContent = readFileContent(originalPath);
                originalDependentCodes.put(fqn, originalContent);

                // B. Load Modified (Target) Code
                String modifiedPath = MODIFIED_FOLDER_PATH + File.separator + relativeFilePath;
                String modifiedContent = readFileContent(modifiedPath);
                modifiedDependentCodes.put(fqn, modifiedContent);

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
            CompilationUnit originalCu = StaticJavaParser.parse(originalContent);
            CompilationUnit modifiedCu = StaticJavaParser.parse(modifiedContent);

            List<String> semanticDiffs = new ArrayList<>();

            for (MethodDeclaration modifiedMethod : modifiedCu.findAll(MethodDeclaration.class)) {
                String methodName = modifiedMethod.getNameAsString();

                Optional<MethodDeclaration> originalMethodOpt = originalCu.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(m -> m.getNameAsString().equals(methodName))
                        .findFirst();

                if (originalMethodOpt.isPresent()) {
                    MethodDeclaration originalMethod = originalMethodOpt.get();

                    boolean isSignatureChanged = !originalMethod.getType().equals(modifiedMethod.getType()) ||
                            !originalMethod.getParameters().equals(modifiedMethod.getParameters());

                    boolean isBodyChanged = false;
                    if (originalMethod.getBody().isPresent() && modifiedMethod.getBody().isPresent()) {
                        isBodyChanged = !originalMethod.getBody().get().equals(modifiedMethod.getBody().get());
                    } else if (originalMethod.getBody().isPresent() != modifiedMethod.getBody().isPresent()) {
                        isBodyChanged = true;
                    }

                    if (isSignatureChanged || isBodyChanged) {
                        StringBuilder semanticDiff = new StringBuilder();
                        String oldSignature = originalMethod.getDeclarationAsString(false, true, true);
                        String newSignature = modifiedMethod.getDeclarationAsString(false, true, true);

                        semanticDiff.append("// OLD Signature: ").append(oldSignature).append(" \n");
                        semanticDiff.append("// NEW Signature: ").append(newSignature).append(" \n");
                        semanticDiff.append(modifiedMethod.toString()).append("\n");

                        semanticDiffs.add(semanticDiff.toString());
                    }
                }
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
