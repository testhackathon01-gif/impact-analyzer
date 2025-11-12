package com.citi.intelli.diff.analyzer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ImpactAnalyzer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-001:generateContent";

    public ImpactAnalyzer() {
        this.apiKey = "AIzaSyBkRhbKdRYcXB8yJgOr4_AkZS1HEIZcag0";//System.getenv("GEMINI_API_KEY");
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is not set");
        }
    }

    public ImpactAnalyzer(String apiKey) {
        this.apiKey = apiKey;
    }

    public ImpactReport analyze(String changedModuleDiff, Map<String, CompilationUnit> relevantContextASTs, String targetMethodSignature) throws Exception {

        String contextualSnippets = extractContextualCode(relevantContextASTs, targetMethodSignature);
        String prompt = generateCoTPromptWithSchema(changedModuleDiff, contextualSnippets);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));

        // Set generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        // Make HTTP request
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = API_URL + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

        // Extract text from response
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        @SuppressWarnings("unchecked")
        Map<String, Object> contentResponse = (Map<String, Object>) candidates.get(0).get("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentResponse.get("parts");
        String jsonOutput = (String) parts.get(0).get("text");

        return mapper.readValue(cleanJsonResponse(jsonOutput), ImpactReport.class);
    }

    private String cleanJsonResponse(String jsonOutput) {
        jsonOutput = jsonOutput.trim();
        if (jsonOutput.startsWith("```json")) {
            jsonOutput = jsonOutput.substring(7);
        } else if (jsonOutput.startsWith("```")) {
            jsonOutput = jsonOutput.substring(3);
        }
        if (jsonOutput.endsWith("```")) {
            jsonOutput = jsonOutput.substring(0, jsonOutput.length() - 3);
        }
        return jsonOutput.trim();
    }

    private String extractContextualCode(Map<String, CompilationUnit> relevantContextASTs, String targetMethodSignature) {
        return relevantContextASTs.entrySet().stream()
                .map(entry -> {
                    String moduleName = entry.getKey();
                    CompilationUnit cu = entry.getValue();
                    String snippets = cu.findAll(MethodDeclaration.class).stream()
                            .filter(method -> method.toString().contains(targetMethodSignature))
                            .map(method -> String.format("// Module: %s - Method: %s\n%s\n",
                                    moduleName, method.getNameAsString(), method.toString()))
                            .collect(Collectors.joining("\n"));
                    return snippets;
                })
                .collect(Collectors.joining("\n\n"));
    }

    private String generateCoTPromptWithSchema(String moduleADiff, String contextualModules) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert Software Architect for Java. ");
        prompt.append("Analyze the diff and context for cascading impacts.\n\n");
        prompt.append("Analyze the change (Module A Diff) against the CONTEXTUAL MODULES (Modules B/C) to predict cascading impact.");
        prompt.append("\n\n### REASONING PROCESS (Chain of Thought)");
        prompt.append("\n\nYou MUST first detail your reasoning step-by-step. The steps are:");
        prompt.append("\n1. Analyze the functional and contractual change in Module A's output.");
        prompt.append("\n2. Trace all direct dependencies and identify API breaks.");
        prompt.append("\n3. Trace all indirect data dependencies (shared logic/tables) and identify potential subtle logic breaks.");
        prompt.append("\n4. Conclude with a Risk Score (1-10).");
        prompt.append("\n\n### 1. CODE CHANGE (Module A Diff)");
        prompt.append("\n```java\n").append(moduleADiff).append("\n```");
        prompt.append("\n\n### 2. CONTEXTUAL MODULES (Dependencies for Analysis)");
        prompt.append("\n```java\n").append(contextualModules).append("\n```");
        prompt.append("\n\n### 3. FINAL OUTPUT");
        prompt.append("\nReturn ONLY a valid JSON in this exact format:");
        prompt.append("\n{\"analysisId\":\"string\",\"riskScore\":1-10,\"reasoning\":\"string\",\"impactedModules\":[{\"moduleName\":\"string\",\"impactType\":\"string\",\"description\":\"string\"}]}");
        return prompt.toString();
    }
}