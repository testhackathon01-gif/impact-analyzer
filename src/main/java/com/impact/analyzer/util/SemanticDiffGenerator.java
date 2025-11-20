package com.impact.analyzer.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Component responsible for comparing the Abstract Syntax Trees (ASTs) of two versions
 * of a Java file to generate a list of semantic change markers (METHOD_MODIFIED, FIELD_ADDED, etc.).
 */
@Component
@Slf4j
public class SemanticDiffGenerator {

    public static final String FILE_SPLIT_MARKER = "\n// --- FILE SPLIT --- \n";

    /**
     * Generates a map of Fully Qualified Names (FQN) to a list of semantic diff snippets.
     * @param originalCodes Map of FQN -> original code content.
     * @param modifiedCodes Map of FQN -> modified code content.
     * @param targetFQN The FQN of the file that was changed.
     * @return Map<FQN, List<String>> where the list contains the semantic diff markers.
     */
    public Map<String, List<String>> generateSemanticDiffs(
            Map<String, String> originalCodes,
            Map<String, String> modifiedCodes,
            String targetFQN) {

        String originalContent = originalCodes.get(targetFQN);
        String modifiedContent = modifiedCodes.get(targetFQN);

        if (originalContent == null || modifiedContent == null) {
            log.error("Error: Target file {} not found in code maps.", targetFQN);
            return Collections.emptyMap();
        }
        if (originalContent.equals(modifiedContent)) {
            log.info("No content change detected for {}.", targetFQN);
            return Collections.emptyMap();
        }

        try {
            List<String> diffsForFile = generateSemanticDiffForFile(originalContent, modifiedContent);

            if (!diffsForFile.isEmpty()) {
                return Map.of(targetFQN, diffsForFile);
            }
        } catch (Exception e) {
            log.error("Error generating semantic diff for file: {}.", targetFQN, e);
        }

        return Collections.emptyMap();
    }

    // Renamed and made private (as it's a helper for the public method)
    private List<String> generateSemanticDiffForFile(String originalContent, String modifiedContent) {
        try {
            // Use JavaParser to parse the Abstract Syntax Trees (AST)
            CompilationUnit originalCu = StaticJavaParser.parse(originalContent);
            CompilationUnit modifiedCu = StaticJavaParser.parse(modifiedContent);

            List<String> semanticDiffs = new ArrayList<>();

            // 1. COLLECT ALL MEMBERS FROM ORIGINAL VERSION
            Map<String, String> originalMethodMap = new HashMap<>();
            originalCu.findAll(MethodDeclaration.class).forEach(m -> originalMethodMap.put(m.getNameAsString(), m.toString()));

            Map<String, String> originalFieldMap = new HashMap<>();
            originalCu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class).forEach(f ->
                    f.getVariables().forEach(v -> originalFieldMap.put(v.getNameAsString(), f.toString()))
            );

            Map<String, String> originalTypeMap = new HashMap<>();
            originalCu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class).forEach(t ->
                    originalTypeMap.put(t.getNameAsString(), t.toString())
            );

            // 2. CHECK FOR MODIFICATIONS AND ADDITIONS (Iterate Modified CU)
            Set<String> processedModifiedMethods = new HashSet<>();
            Set<String> processedModifiedFields = new HashSet<>();
            Set<String> processedModifiedTypes = new HashSet<>();
            final String NL = "\n"; // Use NL constant

            // ... (Method, Field, Type comparison logic is largely retained but uses NL constant) ...
            // Simplified and logged addition/modification checks

            // 2a. Check Methods
            for (MethodDeclaration modifiedMethod : modifiedCu.findAll(MethodDeclaration.class)) {
                String methodName = modifiedMethod.getNameAsString();
                processedModifiedMethods.add(methodName);
                String modifiedMethodString = modifiedMethod.toString();
                String originalMethodString = originalMethodMap.get(methodName);

                if (originalMethodString == null) {
                    semanticDiffs.add("// TYPE: METHOD_ADDED " + NL + "// MEMBER: " + methodName + " " + NL + modifiedMethodString + NL);
                    log.debug("Found METHOD_ADDED: {}", methodName);
                } else if (!originalMethodString.equals(modifiedMethodString)) {
                    Optional<MethodDeclaration> originalMethodOpt = originalCu.findAll(MethodDeclaration.class)
                            .stream().filter(m -> m.getNameAsString().equals(methodName)).findFirst();

                    String oldSignature = originalMethodOpt.map(m -> m.getDeclarationAsString(false, true, true)).orElse("N/A");
                    String newSignature = modifiedMethod.getDeclarationAsString(false, true, true);

                    semanticDiffs.add(
                            "// TYPE: METHOD_MODIFIED " + NL +
                                    "// OLD Signature: " + oldSignature + " " + NL +
                                    "// NEW Signature: " + newSignature + " " + NL +
                                    modifiedMethodString + NL
                    );
                    log.debug("Found METHOD_MODIFIED: {}", methodName);
                }
            }

            // 2b. Check Fields (Constants/Variables) - Logic retained and logged
            for (com.github.javaparser.ast.body.FieldDeclaration modifiedField : modifiedCu.findAll(com.github.javaparser.ast.body.FieldDeclaration.class)) {
                String modifiedFieldString = modifiedField.toString();
                modifiedField.getVariables().forEach(modifiedVariable -> {
                    String fieldName = modifiedVariable.getNameAsString();
                    processedModifiedFields.add(fieldName);
                    String originalFieldString = originalFieldMap.get(fieldName);

                    if (originalFieldString == null) {
                        semanticDiffs.add("// TYPE: FIELD_ADDED " + NL + "// MEMBER: " + fieldName + " " + NL + modifiedFieldString + NL);
                        log.debug("Found FIELD_ADDED: {}", fieldName);
                    } else if (!originalFieldString.equals(modifiedFieldString)) {
                        semanticDiffs.add(
                                "// TYPE: FIELD_MODIFIED " + NL +
                                        "// FIELD: " + fieldName + " " + NL +
                                        "// OLD DECLARATION: " + originalFieldString + " " + NL +
                                        "// NEW DECLARATION: " + modifiedFieldString + NL
                        );
                        log.debug("Found FIELD_MODIFIED: {}", fieldName);
                    }
                });
            }

            // 2c. Check Top-Level Types (Classes/Enums/Interfaces) - Logic retained and logged
            for (com.github.javaparser.ast.body.TypeDeclaration<?> modifiedType : modifiedCu.findAll(com.github.javaparser.ast.body.TypeDeclaration.class)) {
                String typeName = modifiedType.getNameAsString();
                processedModifiedTypes.add(typeName);
                String modifiedTypeString = modifiedType.toString();
                String originalTypeString = originalTypeMap.get(typeName);

                if (originalTypeString == null) {
                    semanticDiffs.add("// TYPE: TYPE_ADDED " + NL + "// MEMBER: " + typeName + " " + NL + modifiedTypeString + NL);
                    log.debug("Found TYPE_ADDED: {}", typeName);
                } else if (!originalTypeString.equals(modifiedTypeString)) {
                    semanticDiffs.add(
                            "// TYPE: TYPE_MODIFIED " + NL +
                                    "// MEMBER: " + typeName + " " + NL +
                                    "// OLD DECLARATION: " + originalTypeString + " " + NL +
                                    "// NEW DECLARATION: " + modifiedTypeString + NL
                    );
                    log.debug("Found TYPE_MODIFIED: {}", typeName);
                }
            }


            // 3. CHECK FOR DELETIONS (Iterate Original Maps) - Logic retained and logged
            originalMethodMap.keySet().stream()
                    .filter(name -> !processedModifiedMethods.contains(name))
                    .forEach(name -> {
                        semanticDiffs.add("// TYPE: METHOD_REMOVED " + NL + "// MEMBER: " + name + " " + NL + originalMethodMap.get(name) + NL);
                        log.debug("Found METHOD_REMOVED: {}", name);
                    });

            originalFieldMap.keySet().stream()
                    .filter(name -> !processedModifiedFields.contains(name))
                    .forEach(name -> {
                        semanticDiffs.add("// TYPE: FIELD_REMOVED " + NL + "// MEMBER: " + name + " " + NL + originalFieldMap.get(name) + NL);
                        log.debug("Found FIELD_REMOVED: {}", name);
                    });

            originalTypeMap.keySet().stream()
                    .filter(name -> !processedModifiedTypes.contains(name))
                    .forEach(name -> {
                        semanticDiffs.add("// TYPE: TYPE_REMOVED " + NL + "// MEMBER: " + name + " " + NL + originalTypeMap.get(name) + NL);
                        log.debug("Found TYPE_REMOVED: {}", name);
                    });


            // 4. FALLBACK FOR STRUCTURAL/METADATA CHANGES
            if (semanticDiffs.isEmpty() && !originalContent.equals(modifiedContent)) {
                semanticDiffs.add(
                        "// TYPE: STRUCTURAL_METADATA_CHANGE " + NL +
                                "// DESCRIPTION: Changes detected outside of primary members (e.g., imports, package, file-level comments). " + NL
                );
            }

            return semanticDiffs;
        } catch (Exception e) {
            log.error("AST Parsing failed for file fragment.", e);
            throw new RuntimeException("AST Parsing failed for file fragment.", e);
        }
    }
}
