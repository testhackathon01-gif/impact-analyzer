package com.impact.analyzer.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for finding callers (dependencies) of a changed method.
 * <p>
 * NOTE: This implementation uses a text-based STUB for dependency tracing.
 * In a production environment, this class should be integrated with the
 * JavaParser Symbol Solver for accurate semantic analysis.
 */
@Slf4j
@Component
public class DependencyResolver {

    private final List<String> sourcePaths;

    public DependencyResolver(List<String> sourcePaths) {
        this.sourcePaths = (sourcePaths != null) ? sourcePaths : Collections.emptyList();
        // 2. Log initialization details at INFO level
        log.info("DependencyResolver initialized with source paths: {}", this.sourcePaths);
    }

    // ... (findPotentialCallers method signature remains the same) ...
    public Map<String, List<MethodDeclaration>> findPotentialCallers(
            String targetFQN,
            String targetMethodName,
            Map<String, String> allModuleCodes
    ) {
        // 3. Log search start at DEBUG or INFO level
        log.info("Resolver Stub: Searching for callers of {}.{}()...", targetFQN, targetMethodName);
        Map<String, List<MethodDeclaration>> callers = new HashMap<>();

        final String searchCallPattern = targetMethodName + "(";

        for (Map.Entry<String, String> entry : allModuleCodes.entrySet()) {
            String fqn = entry.getKey();
            String code = entry.getValue();

            if (fqn.equals(targetFQN)) {
                log.debug("Skipping target file itself: {}", targetFQN);
                continue;
            }

            try {
                List<MethodDeclaration> callingMethods = findCallingMethodsInModule(code, searchCallPattern);

                if (!callingMethods.isEmpty()) {
                    // 4. Log discovered impact
                    log.info("-> DISCOVERED IMPACT in file: {} ({} method(s))", fqn, callingMethods.size());
                    // Log the specific methods at a lower level (DEBUG)
                    if (log.isDebugEnabled()) {
                        String methodNames = callingMethods.stream()
                                .map(MethodDeclaration::getNameAsString)
                                .collect(Collectors.joining(", "));
                        log.debug("   Calling methods found: {}", methodNames);
                    }
                    callers.put(fqn, callingMethods);
                } else {
                    log.debug("No call pattern '{}' found in module: {}", searchCallPattern, fqn);
                }

            } catch (RuntimeException e) {
                // 5. Log parser errors at WARN or ERROR level
                log.warn("Resolver Error: Failed to parse module {} for dependency check.", fqn, e);
            }
        }

        log.info("Resolver Stub: Finished search. Found {} impacted module/file(s).", callers.size());
        return callers;
    }

    /**
     * Helper method to parse a module's code and find methods containing the search pattern.
     * Separates the parsing logic for better testability and readability.
     */
    private List<MethodDeclaration> findCallingMethodsInModule(String moduleCode, String searchPattern) throws RuntimeException {
        try {
            CompilationUnit cu = StaticJavaParser.parse(moduleCode);

            // Filter for methods whose body (toString()) contains the search pattern
            List<MethodDeclaration> callingMethods = cu.findAll(MethodDeclaration.class).stream()
                    .filter(m -> m.toString().contains(searchPattern))
                    .collect(Collectors.toList());

            return callingMethods;

        } catch (Exception e) {
            // Log the parsing failure, but throw the RuntimeException to be caught by the calling loop
            log.error("JavaParser failed to parse Compilation Unit for code block.", e);
            throw new RuntimeException("JavaParser failed to parse Compilation Unit.", e);
        }
    }
}