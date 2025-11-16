package com.citi.intelli.diff.analyzer;

import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import com.citi.intelli.diff.api.model.ImpactReport;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.citi.intelli.diff.util.ImpactAnalyzerUtil.*;

public class Test {

    public static final String ORIGINAL_FOLDER_PATH="C:\\Users\\DELL\\Downloads\\impact-analyzer\\original\\";
    public static final String MODIFIED_FOLDER_PATH="C:\\Users\\DELL\\Downloads\\impact-analyzer\\modified\\";

  /*  private static TotalFiles defineTestData() {
        TotalFiles data = new TotalFiles();

        data.originalAHelperCode =
                "package com.app.modulea;\n" +
                        "public class A_Helper {\n" +
                        "    public String getTimestamp() { return String.valueOf(System.currentTimeMillis()); }\n" +
                        "    public int calculateHash(int a, int b) { return a + b; }\n" +
                        "}";

        data.modifiedAHelperCode =
                "package com.app.modulea;\n" +
                        "public class A_Helper {\n" +
                        "    public long getTimestamp() { return System.currentTimeMillis(); }\n" +
                        "    public int calculateHash(int a, int b) { int product = a * b; return product / 2; }\n" +
                        "}";

        data.dataGeneratorCode =
                "package com.app.modulea;\n" +
                        "public class DataGenerator {\n" +
                        "    public String generateData() {\n" +
                        "        A_Helper h = new A_Helper();\n" +
                        "        String ts = h.getTimestamp();\n" +
                        "        return \"Data:\" + ts;\n" +
                        "    }\n" +
                        "}";

        data.moduleFinalReporterCode =
                "package com.app.modulec; \n" +
                        "import com.app.modulea.A_Helper; \n" +
                        "public class FinalReporter {\n" +
                        "    public void runReport() {\n" +
                        "        A_Helper h = new A_Helper();\n" +
                        "        String ts = h.getTimestamp(); \n" +
                        "        System.out.println(ts);\n" +
                        "    }\n" +
                        "}";

        data.hashConsumerCode =
                "package com.app.moduled; \n" +
                        "import com.app.modulea.A_Helper; \n" +
                        "public class HashConsumer {\n" +
                        "    public void run() {\n" +
                        "        A_Helper h = new A_Helper();\n" +
                        "        int result = h.calculateHash(10, 5);\n" +
                        "        System.out.println(\"Hash: \" + result);\n" +
                        "    }\n" +
                        "}";

        data.moduleEServiceCode =
                "package com.app.modulee; \n" +
                        "public class Service { public void init() {} }";

        return data;
    }*/
    // Inside Test.java or ImpactAnalyzerUtil.java (wherever this helper lives)

    /**
     * 🎯 Dynamically reads the entire content of a file into a String.
     * Uses the Path object from the java.nio.file API.
     */
    private static String readFileContent(String path) throws Exception {
        Path filePath = Path.of(path);

        if (!Files.exists(filePath)) {
            // This is important for robust error handling in a dynamic system
            throw new FileNotFoundException("File not found at expected path: " + path);
        }

        // 💡 Best practice for reading small-to-medium files in Java 11+
        // Reads all content into a String, assuming UTF-8 encoding.
        // Automatically handles opening and closing the file resource.
        return Files.readString(filePath);

        // Alternative for Java 7/8/9/10:
    /*
    byte[] bytes = Files.readAllBytes(filePath);
    return new String(bytes, StandardCharsets.UTF_8);
    */
    }

    /*private static Map<String, Map<String, String>> prepareCodeMaps(TotalFiles data) {
        Map<String, String> originalDependentCodes = Map.of(
                "com.app.modulea.A_Helper", data.originalAHelperCode,
                "com.app.modulea.DataGenerator", data.dataGeneratorCode,
                "com.app.modulec.FinalReporter", data.moduleFinalReporterCode,
                "com.app.moduled.HashConsumer", data.hashConsumerCode,
                "com.app.modulee.Service", data.moduleEServiceCode
        );

        Map<String, String> modifiedDependentCodes = Map.of(
                "com.app.modulea.A_Helper", data.modifiedAHelperCode,
                "com.app.modulea.DataGenerator", data.dataGeneratorCode,
                "com.app.modulec.FinalReporter", data.moduleFinalReporterCode,
                "com.app.moduled.HashConsumer", data.hashConsumerCode,
                "com.app.modulee.Service", data.moduleEServiceCode
        );

        Map<String, Map<String, String>> maps = new HashMap<>();
        maps.put("original", originalDependentCodes);
        maps.put("modified", modifiedDependentCodes);
        return maps;
    }*/





    private static void printConsolidatedReport(List<AggregatedChangeReport> masterReportList) {
        int totalRiskScore = masterReportList.stream().mapToInt(r -> r.llmReport.getRiskScore()).sum();

        System.out.println("\n\n==============================================================");
        System.out.println("====== 🎯 FINAL CONSOLIDATED IMPACT ANALYSIS REPORT 🎯 ======");
        System.out.println("==============================================================");

        double averageRisk = masterReportList.isEmpty() ? 0 : (double)totalRiskScore / masterReportList.size();
        System.out.printf("Overall Average Risk Score: **%.1f/10** (Based on %d analyzed changes)\n", averageRisk, masterReportList.size());
        System.out.println("--------------------------------------------------------------");

        System.out.println("## 🔍 Detailed Impact Breakdown by Changed Method");

        for (AggregatedChangeReport aggReport : masterReportList) {
            String method = aggReport.changedMethod;
            ImpactReport report = aggReport.llmReport;

            System.out.println("\n### ➡️ Change: **" + method + "()** (Risk: " + report.getRiskScore() + "/10)");
            System.out.println("> **LLM Reasoning:** " + report.getReasoning());

            if (report.getImpactedModules() != null && !report.getImpactedModules().isEmpty()) {
                System.out.println("\n* **Impacted Modules:**");
                report.getImpactedModules().stream()
                        .sorted(java.util.Comparator.comparing(ImpactReport.ImpactedModule::getModuleName))
                        .forEach(module -> {
                            System.out.println("  * **" + module.getModuleName() + "** (Type: " + module.getImpactType() + ")");
                            System.out.println("    > Description: " + module.getDescription());
                        });
            } else {
                System.out.println("* **No impacted modules found by dependency analysis.**");
            }
        }
        System.out.println("\n==============================================================");
    }

    // --- MAIN METHOD ---

    public static void main(String[] args) {
        try {

            List<String> totalFileList=new ArrayList<>();
            totalFileList.add("com.app.modulea.A_Helper");
            totalFileList.add("com.app.modulea.DataGenerator");
            totalFileList.add("com.app.moduleb.FinalReporter");
            totalFileList.add("com.app.modulec.HashConsumer");
            totalFileList.add("com.app.moduled.Service");

            // 4. EXECUTE ANALYSIS LOOP
            //List<AggregatedChangeReport> masterReportList =getImpactAnalysisReport(totalFileList);

            // 5. PRINT CONSOLIDATED OUTPUT
            //printConsolidatedReport(masterReportList); // Call helper method directly

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\n!!! A runtime error occurred. Check stack trace for detail. !!!");
        }
    }
}