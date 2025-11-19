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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class ImpactAnalyzer {


    // Use System.lineSeparator() for cross-platform compatibility
    private static final String NL = System.lineSeparator();

    // --- JSON Schema Definition (for the LLM output) ---
    // Inside ImpactAnalyzer.java

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
            "testStrategy": {
              "type": "object",
              "description": "The testing strategy generated in Step 6 of the reasoning process.",
              "properties": {
                "scope": {
                  "type": "string",
                  "description": "The overall testing scope (e.g., 'Modules requiring syntactic fixes and semantic validation')."
                },
                "priority": {
                  "type": "string",
                  "enum": ["HIGH", "MEDIUM", "LOW"],
                  "description": "The priority of the testing effort based on riskScore (e.g., HIGH for risk >= 8)."
                },
                "testCasesRequired": {
                  "type": "array",
                  "description": "A list of required test cases focused on specific impacts.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "moduleName": {"type": "string"},
                      "testType": {"type": "string"},
                      "focus": {"type": "string"}
                    },
                    "required": ["moduleName", "testType", "focus"]
                  }
                }
              },
              "required": ["scope", "priority", "testCasesRequired"]
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
                    "enum": ["SYNTACTIC_BREAK", "SEMANTIC_BREAK", "PERFORMANCE_RISK", "RUNTIME_RISK", "NO_IMPACT"],
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
          "required": ["analysisId", "riskScore", "reasoning", "testStrategy", "impactedModules"]
        }
        """;
    // --- End of Schema ---

    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final WebClient webClient;
    private final RestTemplate restTemplate = new RestTemplate();
    // Using a more stable model for complex reasoning
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // Constructor remains the same
    public ImpactAnalyzer(WebClient webClient) {
        this.webClient = webClient;
        this.apiKey = "AIzaSyBkRhbKdRYcXB8yJgOr4_AkZS1HEIZcag0";
        if (this.apiKey == null || this.apiKey.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is not set");
        }
    }

    // Method now returns a CompletableFuture for asynchronous execution
    public CompletableFuture<ImpactReport> analyze(String changedModuleDiff, Map<String, CompilationUnit> relevantContextASTs, String targetMethodSignature) {

        String contextualSnippets = extractContextualCode(relevantContextASTs, targetMethodSignature);
        String prompt = generateCoTPromptWithSchema(changedModuleDiff, contextualSnippets);

        // Build request body (remains the same)
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("responseMimeType", "application/json");
        try {
            generationConfig.put("responseSchema", mapper.readValue(IMPACT_REPORT_SCHEMA, Map.class));
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e); // Handle IO exception during schema read
        }
        requestBody.put("generationConfig", generationConfig);

        Map<String, Object> content = new HashMap<>();
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        content.put("parts", List.of(part));
        requestBody.put("contents", List.of(content));


        // --- ASYNCHRONOUS WEBCLIENT CALL ---

        // 1. Configure the WebClient request
        Mono<Map> responseMono = webClient.post()
                .uri(API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                // 2. Specify the expected response type (Map)
                .bodyToMono(Map.class);

        // 3. Convert the WebClient Mono into a CompletableFuture
        return responseMono.toFuture()
                // 4. Use thenApply to process the response map when the call completes
                .thenApply(response -> {
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> contentResponse = (Map<String, Object>) candidates.get(0).get("content");
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) contentResponse.get("parts");
                        String jsonOutput = (String) parts.get(0).get("text");

                        // System.out.print(jsonOutput); // Debugging

                        return mapper.readValue(cleanJsonResponse(jsonOutput), ImpactReport.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process LLM response", e);
                    }
                });
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

        final String NL = "\n";

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert Software Architect for Java. Your task is to perform a detailed cascading impact analysis.")
                .append(NL).append(NL);

        prompt.append("Analyze the CODE CHANGE (Module A Diff) against the CONTEXTUAL MODULES (Modules B, C, etc.) to predict cascading impact.")
                .append(NL);

        // --- Chain of Thought (CoT) Instructions ---
        prompt.append(NL).append("### REASONING PROCESS (Chain of Thought)")
                .append(NL).append("You MUST first detail your reasoning step-by-step. The steps are:")
                .append(NL).append("1. **Analyze Contractual Change in Module A:** Identify the exact change in public signature, visibility, input expectations, output type, or constant values from the provided diff snippets.")
                .append(NL).append("2. **Trace Syntactic Dependencies (Immediate Break Check):**")
                .append(NL).append("   - **Method/Type Removed:** Check if the removed member (`METHOD_REMOVED`, `TYPE_REMOVED`) is called/used by any CONTEXTUAL MODULE. This results in a **SYNTACTIC_BREAK**.")
                .append(NL).append("   - **Method/Type Modified:** Check if the modified member (`METHOD_MODIFIED`, `TYPE_MODIFIED`) has changes in its signature (return type, parameters, visibility) that cause a **SYNTACTIC_BREAK** (e.g., compile error).")
                .append(NL).append("3. **Trace Semantic Dependencies (Logic/Runtime Check):**")
                .append(NL).append("   - **Logic Changed:** If `METHOD_MODIFIED` shows a body change, analyze how the new algorithm affects downstream assumptions (e.g., unexpected rounding, different output).")
                .append(NL).append("   - **Constant Changed:** If `FIELD_MODIFIED` shows a value change (e.g., TAX\\_RATE from 0.05 to 0.08), trace all usages in CONTEXTUAL MODULES that rely on the old value to flag a **SEMANTIC_BREAK**.")
                .append(NL).append("   - **Type Change Risk:** Analyze if a change from primitive to object (`double` to `BigDecimal`) introduces new runtime risks like **NullPointerExceptions**.")
                .append(NL).append("4. **Validate Code Removals (Dead Code Check):** For every member flagged as `METHOD_REMOVED`, confirm whether zero callers are found across the project. If zero callers are confirmed, classify the impact as **NO_IMPACT** (Safe Cleanup).")
                .append(NL).append("5. **Determine Risk Score:** Conclude with a Risk Score (1-10) based on the severity and scope of the identified impacts.")
                .append(NL).append("6. **Generate Test Strategy:** Based on all identified impacts (SYNTACTIC, SEMANTIC, RUNTIME), create a focused testing strategy.")
                .append(NL).append("   - **Prioritize:** Modules with SYNTACTIC_BREAKs must be tested first.")
                .append(NL).append("   - **Focus:** For SEMANTIC_BREAKs, define the specific business logic assumption that needs re-validation (e.g., 'Verify tax rate is now 8.0% instead of 5.0%').")
                .append(NL).append("   - **Removals:** For NO_IMPACT (safe removals), recommend running existing integration tests to ensure no hidden dependencies were broken.")
                .append(NL).append("7. **Format Final Output:** Generate the final analysis report strictly as a JSON object.")
                .append(NL).append(NL);

        // --- Input Code ---
        prompt.append("### 1. CODE CHANGE (Module A Diff)")
                .append(NL).append("The diff snippets below include specific markers (e.g., METHOD_REMOVED, FIELD_MODIFIED, TYPE_ADDED) to help your analysis.")
                .append(NL).append("```java").append(NL).append(moduleADiff).append(NL).append("```")
                .append(NL).append(NL);

        prompt.append("### 2. CONTEXTUAL MODULES (Relevant Dependencies)")
                .append(NL).append("```java").append(NL).append(contextualModules).append(NL).append("```")
                .append(NL).append(NL);

        prompt.append("### 3. FINAL OUTPUT")
                .append(NL).append("**CRITICAL REQUIREMENT:** Based on your reasoning, you **MUST** populate the `impactedModules` array with a separate entry for every module identified as having a SYNTACTIC_BREAK, SEMANTIC_BREAK, RUNTIME_RISK, or NO_IMPACT (for validated safe removals).")
                .append(NL).append("**MANDATORY FIELD (testStrategy):** The `testStrategy` field **MUST be fully populated** and **cannot be null or empty**. Synthesize the findings from Step 6 into this object. If the overall risk is low, the `priority` should be 'Low' and the `scope` limited, but the object must be present in the JSON.")
                .append(NL).append("The `reasoning` field should contain the full Chain of Thought. DO NOT put the module-specific findings in the `reasoning` field; put them in the `impactedModules` list.")
                .append(NL).append("Return ONLY a valid JSON object that strictly adheres to the provided schema.");

        return prompt.toString();
    }
}