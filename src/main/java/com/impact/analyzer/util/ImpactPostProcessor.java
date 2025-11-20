package com.impact.analyzer.util;

import com.citi.intelli.diff.api.model.*;
import com.impact.analyzer.api.model.*;
import com.impact.analyzer.api.model.ImpactReport.ImpactedModule;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ImpactPostProcessor {

    /**
     * Processes a list of AggregatedChangeReports into a list of concise ConciseAnalysisReports.
     */
    public static List<ConciseAnalysisReport> processReports(List<AggregatedChangeReport> aggReports, String changedFileFQN) {
        return aggReports.stream()
                .filter(aggReport -> aggReport != null && aggReport.llmReport != null)
                .map(aggReport -> toConciseReport(aggReport, changedFileFQN))
                .collect(Collectors.toList());
    }

    /**
     * Helper method to process a single AggregatedChangeReport.
     */
    // Inside ImpactPostProcessor.java

    private static ConciseAnalysisReport toConciseReport(AggregatedChangeReport aggReport, String changedFileFQN) {

        // 1. Process Impacts (required for strategy extraction)
        List<ActionableImpact> conciseImpacts = aggReport.llmReport.getImpactedModules().stream()
                .map(module -> toActionableImpact(module, changedFileFQN))
                .collect(Collectors.toList());

        ConciseAnalysisReport conciseReport = new ConciseAnalysisReport();

        // ... (Naming Logic to set finalChangedMemberName - REMAINS THE SAME) ...
        String memberName = aggReport.changedMethod;
        String simpleClassName = getSimpleClassName(changedFileFQN);
        String finalChangedMemberName;
        if ("unknownMethod".equals(memberName) || "unknownMember".equals(memberName) || "Module A".equals(memberName) || simpleClassName.equals(memberName)) {
            finalChangedMemberName = changedFileFQN;
        } else {
            finalChangedMemberName = memberName;
        }

        // 2. SET CORE FIELDS
        conciseReport.changedMember = finalChangedMemberName;
        conciseReport.riskScore = aggReport.llmReport.getRiskScore();
        conciseReport.summaryReasoning = summarizeReasoning(aggReport.llmReport.getReasoning());
        conciseReport.memberType = deduceMemberType(finalChangedMemberName, changedFileFQN, conciseReport.summaryReasoning);


        // 3. --- SIMPLIFIED TEST STRATEGY ASSIGNMENT ---
        // Now that the schema enforces the field, we rely on the LLM's direct DTO output.
        // The responsibility shifts from extraction to validation (if needed), but for now, we trust the DTO.

        // We retain the LLM's object directly. If the LLM somehow fails to populate it despite the schema,
        // this will be null, which is correct, though the LLM is expected to return an empty TestStrategy object.
        TestStrategy llmStrategy = aggReport.llmReport.getTestStrategy();

        if (llmStrategy != null) {
            conciseReport.testStrategy = llmStrategy;
        } else {
            // Fallback for extreme failure case where DTO is null despite schema enforcement
            // We create a minimal fallback strategy to avoid NulLPointerExceptions in downstream code.
            conciseReport.testStrategy = createMinimalFallbackStrategy(conciseImpacts, conciseReport.riskScore);
        }
        // ----------------------------------------------------

        // 4. Final Assignment
        conciseReport.actionableImpacts = conciseImpacts;

        return conciseReport;
    }

    // Inside ImpactPostProcessor.java (Add this new method)

    /**
     * Creates a minimal strategy if the LLM's testStrategy object is null.
     * This replaces the previous, more complex manual extraction.
     */
    private static TestStrategy createMinimalFallbackStrategy(List<ActionableImpact> impacts, int riskScore) {
        TestStrategy strategy = new TestStrategy();
        strategy.scope = "Test strategy was missing from LLM output. Auto-generated minimal regression scope.";

        if (riskScore >= 8) {
            strategy.priority = "HIGH";
        } else if (riskScore >= 5) {
            strategy.priority = "MEDIUM";
        } else {
            strategy.priority = "LOW";
        }

        strategy.testCasesRequired = impacts.stream()
                .map(i -> {
                    TestCaseRequired tc = new TestCaseRequired();
                    tc.moduleName = i.moduleName;
                    tc.testType = "Integration Test"; // Generic default
                    tc.focus = "Validate the fix related to: " + i.issue;
                    return tc;
                })
                .collect(Collectors.toList());
        return strategy;
    }
    /**
     * Extracts the Test Strategy from the verbose 'reasoning' field and constructs the TestStrategy DTO.
     */
    private static TestStrategy extractTestStrategyFromReasoning(String reasoning, List<ActionableImpact> impacts, int riskScore) {
        // Use the riskScore to determine priority even in the fallback case (Fix for Priority Mismatch)
        if (reasoning == null || !reasoning.contains("6. Generate Test Strategy")) {
            return createFallbackStrategy(impacts, riskScore);
        }

        TestStrategy strategy = new TestStrategy();

        // --- Step 1: Isolate the Strategy Block ---
        int start = reasoning.indexOf("6. Generate Test Strategy:");
        int end = reasoning.indexOf("7. Format Final Output:", start);

        if (start == -1) return createFallbackStrategy(impacts, riskScore);

        String strategyBlock = (end != -1) ? reasoning.substring(start, end) : reasoning.substring(start);
        strategyBlock = strategyBlock.replace("6. Generate Test Strategy:", "").trim();

        // --- Step 2: Extract Priority & Scope (FIXED LOGIC) ---
        // Use the riskScore to set priority, overriding heuristics.
        if (riskScore >= 8) {
            strategy.priority = "HIGH";
        } else if (riskScore >= 5) {
            strategy.priority = "MEDIUM";
        } else {
            strategy.priority = "LOW";
        }
        strategy.scope = "Focused on high-risk modules and all required syntactic and semantic fixes.";


        // --- Step 3: Parse Test Cases Required ---
        List<TestCaseRequired> testCases = new ArrayList<>();

        // Use the existing ActionableImpacts list to drive the test case generation
        for (ActionableImpact impact : impacts) {
            TestCaseRequired tc = new TestCaseRequired();
            tc.moduleName = impact.moduleName;

            // Deduce Test Type and Focus based on impact type
            tc.testType = switch (impact.impactType) {
                case "SYNTACTIC_BREAK" -> "Unit/Integration Test (Compile Fix)";
                case "SEMANTIC_BREAK" -> "Integration/Acceptance Test (Logic Validation)";
                case "NO_IMPACT" -> "End-to-End Regression Test (Safe Removal Check)";
                case "PERFORMANCE_RISK" -> "Performance/Load Test (Runtime Validation)";
                default -> "Integration Test";
            };

            // Try to find the LLM's explicit focus from the reasoning block
            String focusFromReasoning = getFocusFromReasoning(impact.moduleName, strategyBlock);

            // Fallback to a clear focus derived from the issue
            String focusFromIssue = "Validate fix for " + impact.impactType + ": " + impact.issue;

            tc.focus = (focusFromReasoning != null) ? focusFromReasoning : focusFromIssue;

            testCases.add(tc);
        }

        strategy.testCasesRequired = testCases;
        return strategy;
    }

    /**
     * Creates a basic strategy if the LLM output is non-compliant or strategy text is missing.
     */
    private static TestStrategy createFallbackStrategy(List<ActionableImpact> impacts, int riskScore) {
        TestStrategy strategy = new TestStrategy();
        strategy.scope = "LLM failed to format test strategy. Strategy auto-generated from impacts.";

        // Use the riskScore for accurate priority mapping (Fix for Priority Mismatch)
        if (riskScore >= 8) {
            strategy.priority = "HIGH";
        } else if (riskScore >= 5) {
            strategy.priority = "MEDIUM";
        } else {
            strategy.priority = "LOW";
        }

        strategy.testCasesRequired = impacts.stream()
                .map(i -> {
                    TestCaseRequired tc = new TestCaseRequired();
                    tc.moduleName = i.moduleName;

                    tc.testType = switch (i.impactType) {
                        case "SYNTACTIC_BREAK" -> "Unit Test (Compile Fix)";
                        case "SEMANTIC_BREAK" -> "Integration Test (Logic Check)";
                        case "NO_IMPACT" -> "Regression Test";
                        default -> "Integration Test";
                    };

                    tc.focus = "Validate the fix related to: " + i.issue;
                    return tc;
                })
                .collect(Collectors.toList());
        return strategy;
    }

    /**
     * Helper to pull a specific focus sentence from the LLM's raw strategy text.
     */
    private static String getFocusFromReasoning(String moduleName, String strategyBlock) {
        // Look for sentences that start with "Prioritize [module name] to..."
        // Note: The LLM output used PricingUtility in the reasoning but moduleName might be the FQN.
        // We look for the simple class name within the FQN for a more robust match.
        String simpleName = getSimpleClassName(moduleName);

        // Pattern: "Prioritize [ModuleName] to [rest of sentence] ."
        // We use simpleName in case the LLM used it in the reasoning block.
        Pattern pattern = Pattern.compile("Prioritize\\s+(?:" + Pattern.quote(moduleName) + "|" + Pattern.quote(simpleName) + ")\\s+to\\s+([^\\.]+)\\.", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(strategyBlock);

        if (matcher.find()) {
            return "Verify: " + matcher.group(1).trim();
        }
        return null;
    }

    // --- Helper Methods (Copied for completeness) ---

    private static String getSimpleClassName(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return "";
        }
        int lastDotIndex = fqn.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return fqn;
        }
        return fqn.substring(lastDotIndex + 1);
    }

    // Inside ImpactPostProcessor.java

    /**
     * Extracts a concise summary from the LLM's verbose Chain of Thought reasoning.
     * Focuses on the content of the first step (Analyze Contractual Change).
     */
    private static String summarizeReasoning(String fullReasoning) {
        if (fullReasoning == null || fullReasoning.trim().isEmpty()) {
            return "No detailed reasoning available.";
        }

        // 1. Locate the start of the "1. Analyze Contractual Change" step (robust search)
        int searchStart = fullReasoning.toUpperCase().indexOf("1. ANALYZE CONTRACTUAL CHANGE");
        int startIndex = (searchStart != -1) ? searchStart : 0;

        // 2. Find the end of the analysis step (where step 2 begins)
        int endIndex = fullReasoning.toUpperCase().indexOf("2. TRACE SYNTACTIC DEPENDENCIES", startIndex);

        String summaryBlock;
        if (endIndex != -1) {
            summaryBlock = fullReasoning.substring(startIndex, endIndex);
        } else {
            summaryBlock = fullReasoning.substring(startIndex);
        }

        // 3. Clean up the output: Remove step header case-insensitively
        String summary = summaryBlock.replaceAll("(?i)1\\. ANALYZE CONTRACTUAL CHANGE IN MODULE A:", "")
                .replace("\n", " ").trim();

        // 4. Limit to the first coherent sentence.
        // Find the first period followed by a space or the end of the string.
        int firstPeriod = summary.indexOf(". ");

        if (firstPeriod > 0) {
            // Find the end of the second sentence for cases where the first sentence is short/introductory.
            // We ensure we don't accidentally cut off the core change description.
            int secondPeriod = summary.indexOf(". ", firstPeriod + 2);

            // If the entire summary is short, return it all.
            if (summary.length() < 100) {
                return summary;
            }

            // Return up to the end of the second sentence if it exists and is not excessively long.
            if (secondPeriod > 0 && secondPeriod < firstPeriod + 100) {
                return summary.substring(0, secondPeriod + 1).trim();
            }

            // Otherwise, return just the first clear sentence.
            return summary.substring(0, firstPeriod + 1).trim();
        }

        // Fallback: If no clear period-space separator is found, return the trimmed block.
        return summary;
    }
    private static ActionableImpact toActionableImpact(ImpactedModule module, String changedFileFQN) {
        ActionableImpact impact = new ActionableImpact();
        String simpleClassName = getSimpleClassName(changedFileFQN);
        final List<String> LOCAL_MODULE_PLACEHOLDERS = List.of("Module A", "ModuleA", simpleClassName);

        // Naming Logic
        if (LOCAL_MODULE_PLACEHOLDERS.contains(module.getModuleName()) || module.getModuleName().equals(simpleClassName)) {
            impact.moduleName = changedFileFQN;
        } else {
            impact.moduleName = module.getModuleName();
        }

        impact.impactType = module.getImpactType();

        // Conciseness Logic
        String fullDescription = module.getDescription();
        String conciseIssue = fullDescription.trim();
        int endOfSentence = fullDescription.indexOf(". ");

        if (endOfSentence != -1) {
            conciseIssue = fullDescription.substring(0, endOfSentence + 1);
        } else if (fullDescription.contains(".")) {
            conciseIssue = fullDescription.substring(0, fullDescription.indexOf(".") + 1);
        }
        impact.issue = conciseIssue.replace("\n", " ").trim();

        return impact;
    }

    static String deduceMemberType(String memberName, String changedFileFQN, String summaryReasoning) {
        // ... (deduceMemberType implementation remains the same) ...
        if (memberName == null || memberName.isEmpty()) {
            return "UNKNOWN";
        }
        String lowerReasoning = summaryReasoning.toLowerCase();
        String simpleClassName = getSimpleClassName(changedFileFQN);

        if (lowerReasoning.contains("field") || lowerReasoning.contains("constant")) return "FIELD/CONSTANT";
        if (lowerReasoning.contains("method")) return "METHOD";
        if (lowerReasoning.contains("class") || lowerReasoning.contains("type") || lowerReasoning.contains("interface") || lowerReasoning.contains("enum")) return "CLASS/TYPE";

        if (memberName.matches("^[A-Z0-9_]+$") && memberName.equals(memberName.toUpperCase())) return "FIELD/CONSTANT";
        if (memberName.equals(simpleClassName) || memberName.equals(changedFileFQN)) return "CLASS/TYPE";
        if (Character.isLowerCase(memberName.charAt(0))) return "METHOD";
        if (Character.isUpperCase(memberName.charAt(0))) return "CLASS/TYPE";

        return "MEMBER";
    }
}