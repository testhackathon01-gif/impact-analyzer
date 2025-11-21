package com.impact.analyzer.analyzer;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DependencyResolverTest {

    @Test
    @DisplayName("findPotentialCallers: finds callers in other modules and skips the target file itself")
    void testFindPotentialCallers_findsCallers() {
        // Arrange
        DependencyResolver resolver = new DependencyResolver(Collections.emptyList());

        String targetFqn = "com.example.A";
        String targetMethod = "foo";

        // Target file code (should be skipped by resolver)
        String codeA = "class A {" +
                "  void foo() { }" +
                "  void selfCaller() { foo(); }" +
                "}";

        // Another file that calls A#foo()
        String codeB = "class B {" +
                "  void bar() { new A().foo(); }" +
                "  void baz() { System.out.println(\"x\"); }" +
                "}";

        Map<String, String> allModules = new HashMap<>();
        allModules.put(targetFqn, codeA);
        allModules.put("com.example.B", codeB);

        // Act
        Map<String, List<MethodDeclaration>> result = resolver.findPotentialCallers(targetFqn, targetMethod, allModules);

        // Assert
        assertFalse(result.containsKey(targetFqn), "Target file must be skipped");
        assertTrue(result.containsKey("com.example.B"), "B should be detected as impacted");
        List<MethodDeclaration> methods = result.get("com.example.B");
        assertNotNull(methods);
        assertEquals(1, methods.size(), "Only one method should call foo()");
        assertEquals("bar", methods.get(0).getNameAsString());
    }

    @Test
    @DisplayName("findPotentialCallers: returns empty map when no callers exist")
    void testFindPotentialCallers_noCallers() {
        // Arrange
        DependencyResolver resolver = new DependencyResolver(Collections.emptyList());
        String targetFqn = "com.example.A";
        String targetMethod = "foo";

        String codeB = "class B { void bar() { System.out.println(\"no calls\"); } }";
        Map<String, String> allModules = Map.of(
                targetFqn, "class A { void foo() {} }",
                "com.example.B", codeB
        );

        // Act
        Map<String, List<MethodDeclaration>> result = resolver.findPotentialCallers(targetFqn, targetMethod, allModules);

        // Assert
        assertTrue(result.isEmpty(), "No modules should be reported when there are no callers");
    }

    @Test
    @DisplayName("findPotentialCallers: gracefully handles parse errors and continues")
    void testFindPotentialCallers_handlesParseError() {
        // Arrange
        DependencyResolver resolver = new DependencyResolver(Collections.emptyList());
        String targetFqn = "com.example.A";
        String targetMethod = "foo";

        // Invalid java to force JavaParser exception
        String invalidCode = "class C { void broken( ";

        String validCaller = "class D { void call() { new A().foo(); } }";

        Map<String, String> allModules = new HashMap<>();
        allModules.put(targetFqn, "class A { void foo() {} }");
        allModules.put("com.example.C", invalidCode);
        allModules.put("com.example.D", validCaller);

        // Act
        Map<String, List<MethodDeclaration>> result = resolver.findPotentialCallers(targetFqn, targetMethod, allModules);

        // Assert
        // Should not throw; the invalid module should simply be ignored
        assertFalse(result.containsKey("com.example.C"), "Invalid/Unparseable module should be ignored");
        assertTrue(result.containsKey("com.example.D"), "Valid caller module should be reported");
        List<MethodDeclaration> methods = result.get("com.example.D");
        assertNotNull(methods);
        assertEquals(1, methods.size());
        assertEquals("call", methods.get(0).getNameAsString());
    }
}
