package com.citi.intelli.diff.analyzer;

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
     * STUB: Finds ALL callers of the target method across the entire codebase.
     * This logic is now fixed to dynamically search for the exact method name.
     */
    public Map<String, List<MethodDeclaration>> findAllCallersOfMethod(
            String targetFQN,
            String targetMethodSignature, // This will be "generateData" or "otherMethod"
            Map<String, String> allModuleCodes
    ) {
        System.out.println("Resolver Stub: Searching for callers of " + targetMethodSignature + "...");
        Map<String, List<MethodDeclaration>> callers = new java.util.HashMap<>();

        // 🎯 FIX: Dynamically create the search string based on the target method name.
        // It looks for a call like "a.generateData()" or "a.otherMethod()".
        String searchCallPattern = "a." + targetMethodSignature + "(";

        // Iterate through ALL modules (C, D, E, etc.)
        for (Map.Entry<String, String> entry : allModuleCodes.entrySet()) {
            String fqn = entry.getKey();
            String code = entry.getValue();

            // Ignore the module that was changed (Module A)
            if (fqn.equals(targetFQN)) {
                continue;
            }

            try {
                // Parse the Compilation Unit of the potential calling module
                CompilationUnit cu = StaticJavaParser.parse(code);

                // FIXED LOGIC: Use the dynamically created search pattern
                List<MethodDeclaration> callingMethods = cu.findAll(MethodDeclaration.class)
                        .stream()
                        .filter(m -> m.toString().contains(searchCallPattern))
                        .toList();

                if (!callingMethods.isEmpty()) {
                    System.out.println("-> DISCOVERED IMPACT in module: " + fqn + " (" + callingMethods.size() + " method(s))");
                    callers.put(fqn, callingMethods);
                }

            } catch (Exception e) {
                System.err.println("Stub Error: Could not parse module for dependency check: " + fqn + ". Error: " + e.getMessage());
            }
        }

        System.out.println("Resolver Stub: Found " + callers.size() + " impacted module(s).");
        return callers;
    }
}