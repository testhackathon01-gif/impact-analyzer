package com.impact.analyzer.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.impact.analyzer.api.model.ImpactReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImpactAnalyzerTest {

    @Test
    @DisplayName("generateCoTPromptWithSchema includes diff and context blocks")
    void testGeneratePromptContainsInputs() {
        WebClient webClient = mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);
        ImpactAnalyzer analyzer = new ImpactAnalyzer(webClient);

        String diff = "// METHOD_MODIFIED: public int add(int a, int b) -> add(int a, int b, int c)";
        String context = "class B { void use(){} }";

        String prompt = analyzer.generateCoTPromptWithSchema(diff, context);

        assertThat(prompt).contains(diff);
        assertThat(prompt).contains(context);
        assertThat(prompt).contains("FINAL OUTPUT");
    }

    @Test
    @DisplayName("analyze returns ImpactReport parsed from WebClient JSON response")
    void testAnalyzeParsesResponse() throws Exception {
        // Arrange WebClient mock chain
        // Use deep-stubbed WebClient to simplify fluent-chain mocking
        WebClient webClient = mock(WebClient.class, Mockito.RETURNS_DEEP_STUBS);

        // Build fake JSON string matching ImpactReport
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "analysisId", "A-123",
                "riskScore", 7,
                "reasoning", "Because...",
                "testStrategy", Map.of(
                        "scope", "Unit + Integration",
                        "priority", "HIGH",
                        "testCasesRequired", List.of(
                                Map.of("moduleName", "com.x.A", "testType", "UNIT", "focus", "Syntactic break")
                        )
                ),
                "impactedModules", List.of(
                        Map.of("moduleName", "com.x.A", "impactType", "SYNTACTIC_BREAK", "description", "Signature changed")
                )
        ));

        Map<String, Object> responseMap = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(Map.of("text", json)));
        responseMap.put("candidates", List.of(Map.of("content", content)));

        // Deep-stub the full WebClient chain to return our response map
        when(webClient.post()
                .uri(anyString())
                .contentType(any(MediaType.class))
                .bodyValue(any())
                .retrieve()
                .bodyToMono(Map.class))
                .thenReturn(Mono.just(responseMap));

        ImpactAnalyzer analyzer = new ImpactAnalyzer(webClient);

        // Prepare minimal AST context & inputs
        String codeA = "class A { int add(int a,int b){ return a+b; } }";
        CompilationUnit cuA = StaticJavaParser.parse(codeA);
        Map<String, CompilationUnit> ctx = Map.of("com.x.A", cuA);

        CompletableFuture<ImpactReport> future = analyzer.analyze("diff-contents", ctx, "add");
        ImpactReport report = future.join();

        assertThat(report.getAnalysisId()).isEqualTo("A-123");
        assertThat(report.getRiskScore()).isEqualTo(7);
        assertThat(report.getReasoning()).contains("Because");
        assertThat(report.getTestStrategy()).isNotNull();
        assertThat(report.getImpactedModules()).hasSize(1);
    }
}
