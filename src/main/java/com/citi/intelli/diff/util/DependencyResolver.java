package com.citi.intelli.diff.util;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.util.List;
import java.util.Map;

// Simulates the class that uses the JavaParser Symbol Solver
public class DependencyResolver {

    private final List<String> sourcePaths;

    public DependencyResolver(List<String> sourcePaths) {
        this.sourcePaths = sourcePaths;
        System.out.println("DependencyResolver initialized with source paths: " + sourcePaths);
    }

    /**
     * STUB: Finds ALL callers of the target method across the entire codebase (Intra-module and Inter-module).
     */
    public Map<String, List<MethodDeclaration>> findAllCallersOfMethod(
            String targetFQN,
            String targetMethodSignature,
            Map<String, String> allModuleCodes
    ) {
        System.out.println("Resolver Stub: Searching for callers of " + targetFQN + "." + targetMethodSignature + "...");
        Map<String, List<MethodDeclaration>> callers = new java.util.HashMap<>();

        // Dynamically create the search string based on the target method name (e.g., "h.getTimestamp(")
        String searchCallPattern = targetMethodSignature + "(";

        // Iterate through ALL modules/files in the project
        for (Map.Entry<String, String> entry : allModuleCodes.entrySet()) {
            String fqn = entry.getKey();
            String code = entry.getValue();

            // Ignore the source file that was changed (A_Helper)
            if (fqn.equals(targetFQN)) {
                continue;
            }

            try {
                // Parse the Compilation Unit of the potential calling module/class
                CompilationUnit cu = StaticJavaParser.parse(code);

                // STUB LOGIC: Check for any method that contains the call pattern.
                List<MethodDeclaration> callingMethods = cu.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(m -> m.toString().contains(searchCallPattern))
                        .toList();

                if (!callingMethods.isEmpty()) {
                    System.out.println("-> DISCOVERED IMPACT in file: " + fqn + " (" + callingMethods.size() + " method(s))");
                    callers.put(fqn, callingMethods);
                }

            } catch (Exception e) {
                System.err.println("Stub Error: Could not parse module for dependency check: " + fqn + ". Error: " + e.getMessage());
            }
        }

        System.out.println("Resolver Stub: Found " + callers.size() + " impacted module/file(s).");
        return callers;
    }
}