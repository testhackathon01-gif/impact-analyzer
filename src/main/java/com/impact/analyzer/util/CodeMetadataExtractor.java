package com.impact.analyzer.util;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for extracting specific metadata (like FQN or member names)
 * from raw code or semantic diff snippets using heuristics and JavaParser.
 */
@Component
@Slf4j
public class CodeMetadataExtractor {

    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum");

    /**
     * Extracts the Fully Qualified Name (FQN) from a Java code string.
     */
    public Optional<String> extractFQN(String code) {
        String packageName = "";
        String className = null;

        for (String line : code.split("[\r\n]")) {
            String trimmedLine = line.trim();

            if (trimmedLine.startsWith("package ")) {
                packageName = trimmedLine.substring("package ".length()).split(";")[0].trim() + ".";
                continue;
            }

            // Look for class, interface, or enum declaration
            if (className == null) {
                String[] parts = trimmedLine.split("\\s+");
                int typeKeywordIndex = -1;

                for (int i = 0; i < parts.length; i++) {
                    if (TYPE_KEYWORDS.contains(parts[i])) {
                        typeKeywordIndex = i;
                        break;
                    }
                }

                if (typeKeywordIndex != -1 && parts.length > typeKeywordIndex + 1) {
                    // Extract the class name, stripping generics, braces, inheritance, etc.
                    String potentialClassName = parts[typeKeywordIndex + 1].split("<|\\{|extends|implements")[0].trim();

                    if (!potentialClassName.isEmpty() && !potentialClassName.contains("(")) {
                        className = potentialClassName;
                        break;
                    }
                }
            }
        }

        if (className != null) {
            log.trace("Extracted FQN: {}{}", packageName, className);
            return Optional.of(packageName + className);
        }
        log.warn("Could not extract FQN from code snippet.");
        return Optional.empty();
    }

    /**
     * Extracts the member name (method, field, or type) from a semantic diff snippet.
     */
    public String extractTargetMemberName(String diffSnippet) {
        // 1. Check for specific MEMBER or FIELD markers
        List<String> markers = List.of("// FIELD:", "// MEMBER:");

        for(String marker : markers) {
            int startIndex = diffSnippet.indexOf(marker);
            if (startIndex != -1) {
                int nameStart = startIndex + marker.length();
                int nameEnd = diffSnippet.indexOf('\n', nameStart);

                return diffSnippet.substring(nameStart, nameEnd).trim();
            }
        }

        // 2. Fallback to METHOD signature parsing
        int methodStartIndex = diffSnippet.indexOf("// NEW Signature:");
        if (methodStartIndex != -1) {
            // Find the start of the actual method code block
            int headerEndIndex = diffSnippet.indexOf('\n', methodStartIndex);
            if (headerEndIndex == -1) return "unknownMethod";

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
                log.warn("Failed to parse method code for name extraction: {}", e.getMessage());
            }
        }

        log.warn("Could not extract target member name from diff snippet.");
        return "unknownMember";
    }

    // Original's getSubMapValue is moved to the Service layer's RepoFileMetaDataAccessorImpl
}