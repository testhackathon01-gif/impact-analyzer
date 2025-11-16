package com.citi.intelli.diff.analyzer;

import com.citi.intelli.diff.api.model.ImpactReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ImpactAnalyzer {


    // Use System.lineSeparator() for cross-platform compatibility
    private static final String NL = System.lineSeparator();

    // --- JSON Schema Definition (for the LLM output) ---
    private static final String IMPACT_REPORT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "analysisId": {
              "type": "string",
              "description": "A unique ID for this analysis run."
            },
            "riskScore": {
              "type": "integer",
              "minimum": 1,
              "maximum": 10,
              "description": "A score from 1 (low risk) to 10 (high risk/API break)."
            },
            "reasoning": {
              "type": "string",
              "description": "The detailed step-by-step reasoning (Chain of Thought)."
            },
            "impactedModules": {
              "type": "array",
              "description": "A list of modules and the specific impacts found.",
              "items": {
                "type": "object",
                "properties": {
                  "moduleName": {
                    "type": "string",
                    "description": "The fully qualified class name of the impacted module (e.g., com.app.modulec.FinalReporter)."
                  },
                  "impactType": {
                    "type": "string",
                    "enum": ["SYNTACTIC_BREAK", "SEMANTIC_BREAK", "PERFORMANCE_RISK", "NO_IMPACT"],
                    "description": "The type of impact observed."
                  },
                  "description": {
                    "type": "string",
                    "description": "A detailed explanation of the impact and suggested fix."
                  }
                },
                "required": ["moduleName", "impactType", "description"]
              }
            }
          },
          "required": ["analysisId", "riskScore", "reasoning", "impactedModules"]
        }
        """;
    // --- End of Schema ---

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final RestTemplate restTemplate = new RestTemplate();
    // Using a more stable model for complex reasoning
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // Constructor remains the same
    public ImpactAnalyzer() {
        // NOTE: In a real application, remove the hardcoded key!
        this.apiKey = "AIzaSyBkRhbKdRYcXB8yJgOr4_AkZS1HEIZcag0";
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is not set");
        }
    }

    public ImpactAnalyzer(String apiKey) {
        this.apiKey = apiKey;
    }

    // analyze method remains the same (truncated for brevity)
    public ImpactReport analyze(String changedModuleDiff, Map<String, CompilationUnit> relevantContextASTs, String targetMethodSignature) throws Exception {

        String contextualSnippets = extractContextualCode(relevantContextASTs, targetMethodSignature);
        String prompt = generateCoTPromptWithSchema(changedModuleDiff, contextualSnippets);

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();

        // IMPORTANT: Set the responseMimeType and responseSchema for structured output
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", mapper.readValue(IMPACT_REPORT_SCHEMA, Map.class));
        requestBody.put("generationConfig", generationConfig);

        // Build contents (prompt)
        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));


        // Make HTTP request (rest of the analysis method)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = API_URL + "?key=" + apiKey;
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);

        // The response will be a clean JSON object because of responseMimeType/responseSchema
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        @SuppressWarnings("unchecked")
        Map<String, Object> contentResponse = (Map<String, Object>) candidates.get(0).get("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentResponse.get("parts");
        String jsonOutput = (String) parts.get(0).get("text");

        // No need for a cleaning step if responseSchema is used, but we keep a simplified one as fallback
        return mapper.readValue(cleanJsonResponse(jsonOutput), ImpactReport.class);
    }

    // --- Utility Methods (same as original, but updated line separators) ---

    private String cleanJsonResponse(String jsonOutput) {
        jsonOutput = jsonOutput.trim();
        if (jsonOutput.startsWith("```json")) {
            jsonOutput = jsonOutput.substring(7).trim();
        } else if (jsonOutput.startsWith("```")) {
            jsonOutput = jsonOutput.substring(3).trim();
        }
        if (jsonOutput.endsWith("```")) {
            jsonOutput = jsonOutput.substring(0, jsonOutput.length() - 3).trim();
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
                            .map(method -> String.format("// Module: %s - Method: %s" + NL + "%s" + NL,
                                    moduleName, method.getNameAsString(), method.toString()))
                            .collect(Collectors.joining(NL));
                    return snippets;
                })
                .collect(Collectors.joining(NL + NL));
    }

    // --- The Core Prompt Generation Method ---
    public String generateCoTPromptWithSchema(String moduleADiff, String contextualModules) {

        // Define the newline constant for cleaner code
        final String NL = "\n";

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert Software Architect for Java. Your task is to perform a detailed cascading impact analysis.")
                .append(NL).append(NL);

        prompt.append("Analyze the CODE CHANGE (Module A Diff) against the CONTEXTUAL MODULES (Modules B, C, etc.) to predict cascading impact.")
                .append(NL);

        // --- Chain of Thought (CoT) Instructions ---
        prompt.append(NL).append("### REASONING PROCESS (Chain of Thought)")
                .append(NL).append("You MUST first detail your reasoning step-by-step. The steps are:")
                .append(NL).append("1. **Analyze Contractual Change in Module A:** Identify the exact change in public signature, input expectations, and output type/format.")
                .append(NL).append("2. **Trace Direct Dependencies (Syntactic Check):** Examine the CONTEXTUAL MODULES to see if they directly call the changed method. Identify immediate compilation/runtime errors (API breaks).")
                .append(NL).append("3. **Trace Semantic Dependencies (Logic Check):** For the directly impacted modules, analyze how the *new business logic or data values* (e.g., changing 'TypeX' to 'CLEAN') will flow to downstream logic and potentially cause subtle bugs or incorrect processing.")
                .append(NL).append("4. **Validate Code Removals (Dead Code Check):** If any methods were removed from Module A (Source of Change), confirm whether those methods were called by any CONTEXTUAL MODULES. If zero callers are found, conclude the removal is safe (NO_IMPACT).")
                .append(NL).append("5. **Determine Risk Score:** Conclude with a Risk Score (1-10) based on the severity and scope of the identified impacts.")
                .append(NL).append("6. **Format Final Output:** Generate the final analysis report strictly as a JSON object.") // Renumbered to 6
                .append(NL).append(NL);

        // --- Input Code ---
        prompt.append("### 1. CODE CHANGE (Module A Diff)")
                .append(NL).append("```java").append(NL).append(moduleADiff).append(NL).append("```")
                .append(NL).append(NL);

        prompt.append("### 2. CONTEXTUAL MODULES (Relevant Dependencies)")
                .append(NL).append("```java").append(NL).append(contextualModules).append(NL).append("```")
                .append(NL).append(NL);

        // --- Output Instructions (CRITICAL MODIFICATION HERE) ---
        prompt.append("### 3. FINAL OUTPUT")
                .append(NL).append("**CRITICAL REQUIREMENT:** Based on your reasoning, you **MUST** populate the `impactedModules` array with a separate entry for every module identified as having a SYNTACTIC_BREAK, SEMANTIC_BREAK, or NO_IMPACT (for safe removals).")
                .append(NL).append("The `reasoning` field should contain the full Chain of Thought. DO NOT put the module-specific findings in the `reasoning` field; put them in the `impactedModules` list.")
                .append(NL).append("Return ONLY a valid JSON object that strictly adheres to the provided schema.");

        return prompt.toString();
    }
}