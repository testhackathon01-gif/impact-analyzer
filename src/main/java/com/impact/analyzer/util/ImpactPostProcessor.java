package com.impact.analyzer.util;

import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.api.model.ConciseAnalysisReport;
import com.impact.analyzer.api.model.ImpactReport.ImpactedModule;
import com.impact.analyzer.api.model.TestStrategy;
import com.impact.analyzer.api.model.TestCaseRequired;
import com.impact.analyzer.api.model.ActionableImpact;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class responsible for post-processing the raw LLM analysis reports (AggregatedChangeReport)
 * into a concise, easily consumable format (ConciseAnalysisReport).
 *
 * It handles name normalization, reasoning summarization, and fallback strategy creation.
 */
public final class ImpactPostProcessor {

    // Prevent instantiation of utility class
    private ImpactPostProcessor() {}

    private final static Pattern SENTENCE_END_PATTERN = Pattern.compile("([^\\.])\\.\\s+");
    private final static List<String> LOCAL_MODULE_PLACEHOLDERS = List.of("Module A", "ModuleA");


    // --- Public Processing Method ---

    /**
     * Processes a list of AggregatedChangeReports into a list of concise ConciseAnalysisReports.
     */
    public static List<ConciseAnalysisReport> processReports(List<AggregatedChangeReport> aggReports, String changedFileFQN) {
        if (aggReports == null || aggReports.isEmpty()) {
            return Collections.emptyList();
        }
        return aggReports.stream()
                .filter(aggReport -> aggReport != null && aggReport.llmReport != null)
                .map(aggReport -> toConciseReport(aggReport, changedFileFQN))
                .collect(Collectors.toList());
    }

    // --- Core Transformation Logic ---

    /**
     * Helper method to process a single AggregatedChangeReport.
     */
    private static ConciseAnalysisReport toConciseReport(AggregatedChangeReport aggReport, String changedFileFQN) {
        // 1. Process Impacts (required for strategy fallback)
        List<ActionableImpact> conciseImpacts = aggReport.llmReport.getImpactedModules().stream()
                .map(module -> toActionableImpact(module, changedFileFQN))
                .collect(Collectors.toList());

        ConciseAnalysisReport conciseReport = new ConciseAnalysisReport();

        // 2. SET CORE FIELDS and Normalize Member Name
        String simpleClassName = getSimpleClassName(changedFileFQN);
        String finalChangedMemberName = normalizeChangedMemberName(aggReport.changedMethod, changedFileFQN, simpleClassName);

        conciseReport.changedMember = finalChangedMemberName;
        conciseReport.riskScore = aggReport.llmReport.getRiskScore();
        conciseReport.summaryReasoning = summarizeReasoning(aggReport.llmReport.getReasoning());
        // Deduce type based on normalized name and summary
        conciseReport.memberType = deduceMemberType(finalChangedMemberName, changedFileFQN, conciseReport.summaryReasoning);


        // 3. SET TEST STRATEGY (Rely on DTO first, then fallback)
        TestStrategy llmStrategy = aggReport.llmReport.getTestStrategy();

        if (llmStrategy != null) {
            // Trust the LLM's structured output
            conciseReport.testStrategy = llmStrategy;
        } else {
            // Fallback for extreme failure case where DTO is null
            conciseReport.testStrategy = createMinimalFallbackStrategy(conciseImpacts, conciseReport.riskScore);
        }

        // 4. Final Assignment
        conciseReport.actionableImpacts = conciseImpacts;

        return conciseReport;
    }

    // --- Helper Methods ---

    /**
     * Normalizes the changed member name from the raw LLM output using heuristics.
     */
    private static String normalizeChangedMemberName(String memberName, String changedFileFQN, String simpleClassName) {
        if ("unknownMethod".equals(memberName) || "unknownMember".equals(memberName) || "Module A".equals(memberName) || simpleClassName.equals(memberName)) {
            return changedFileFQN; // Return FQN if name is generic/unknown
        }
        return memberName;
    }

    /**
     * Extracts a concise summary from the LLM's verbose Chain of Thought reasoning.
     */
    private static String summarizeReasoning(String fullReasoning) {
        if (fullReasoning == null || fullReasoning.trim().isEmpty()) {
            return "No detailed reasoning available.";
        }

        // 1. Locate the start of the "1. Analyze Contractual Change" step
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

        // 4. Limit to the first one or two coherent sentences.
        if (summary.length() < 100) {
            return summary;
        }

        Matcher matcher = SENTENCE_END_PATTERN.matcher(summary);

        // Find the first sentence end
        if (matcher.find()) {
            int firstPeriodIndex = matcher.start() + 1; // +1 to include the period

            // Try to find the second sentence end to ensure enough context is provided
            if (matcher.find(firstPeriodIndex + 2)) {
                int secondPeriodIndex = matcher.start() + 1;
                // Only return the second sentence if it's not excessively long
                if (secondPeriodIndex < firstPeriodIndex + 100) {
                    return summary.substring(0, secondPeriodIndex).trim();
                }
            }
            // Fallback to the first sentence
            return summary.substring(0, firstPeriodIndex).trim();
        }

        return summary;
    }

    /**
     * Creates a minimal strategy if the LLM's testStrategy object is null.
     */
    private static TestStrategy createMinimalFallbackStrategy(List<ActionableImpact> impacts, int riskScore) {
        TestStrategy strategy = new TestStrategy();
        strategy.scope = "Test strategy was missing from LLM output. Auto-generated minimal regression scope.";

        // Set priority based on risk score
        strategy.priority = switch (riskScore) {
            case 8, 9, 10 -> "HIGH";
            case 5, 6, 7 -> "MEDIUM";
            default -> "LOW";
        };

        // Generate required test cases based on impacts
        strategy.testCasesRequired = impacts.stream()
                .map(i -> {
                    TestCaseRequired tc = new TestCaseRequired();
                    tc.moduleName = i.moduleName;
                    tc.testType = (i.impactType.contains("SYNTACTIC")) ? "Unit Test (Compile Fix)" : "Integration Test";
                    tc.focus = "Validate the fix related to: " + i.issue;
                    return tc;
                })
                .collect(Collectors.toList());
        return strategy;
    }

    /**
     * Converts a raw ImpactedModule DTO from the LLM into a concise ActionableImpact DTO.
     */
    private static ActionableImpact toActionableImpact(ImpactedModule module, String changedFileFQN) {
        ActionableImpact impact = new ActionableImpact();
        String simpleClassName = getSimpleClassName(changedFileFQN);

        // Naming Logic: Replace module A placeholder with FQN
        if (LOCAL_MODULE_PLACEHOLDERS.contains(module.getModuleName()) || module.getModuleName().equals(simpleClassName)) {
            impact.moduleName = changedFileFQN;
        } else {
            impact.moduleName = module.getModuleName();
        }

        impact.impactType = module.getImpactType();

        // Conciseness Logic: Truncate description to the first sentence
        String fullDescription = module.getDescription();
        String conciseIssue = fullDescription.trim();
        int endOfSentence = fullDescription.indexOf(". ");

        if (endOfSentence != -1) {
            conciseIssue = fullDescription.substring(0, endOfSentence + 1);
        } else if (fullDescription.contains(".")) {
            // Handle case where it's the last sentence in the block
            conciseIssue = fullDescription.substring(0, fullDescription.lastIndexOf(".") + 1);
        }
        impact.issue = conciseIssue.replace("\n", " ").trim();

        return impact;
    }

    /**
     * Simple utility to get the class name from a Fully Qualified Name (FQN).
     */
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

    /**
     * Uses heuristics based on the member name and summary reasoning to deduce the member type.
     */
    static String deduceMemberType(String memberName, String changedFileFQN, String summaryReasoning) {
        if (memberName == null || memberName.isEmpty()) {
            return "UNKNOWN";
        }
        String lowerReasoning = summaryReasoning.toLowerCase();
        String simpleClassName = getSimpleClassName(changedFileFQN);

        // 1. Based on Reasoning (most reliable)
        if (lowerReasoning.contains("field") || lowerReasoning.contains("constant")) return "FIELD/CONSTANT";
        if (lowerReasoning.contains("method") || lowerReasoning.contains("signature")) return "METHOD";
        if (lowerReasoning.contains("class") || lowerReasoning.contains("type") || lowerReasoning.contains("interface") || lowerReasoning.contains("enum")) return "CLASS/TYPE";

        // 2. Based on Naming Convention (fallback)
        if (memberName.matches("^[A-Z0-9_]+$") && memberName.equals(memberName.toUpperCase())) return "FIELD/CONSTANT";
        if (memberName.equals(simpleClassName) || memberName.equals(changedFileFQN)) return "CLASS/TYPE";
        if (Character.isLowerCase(memberName.charAt(0))) return "METHOD";
        if (Character.isUpperCase(memberName.charAt(0))) return "CLASS/TYPE";

        return "MEMBER";
    }
}