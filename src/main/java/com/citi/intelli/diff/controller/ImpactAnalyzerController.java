package com.citi.intelli.diff.controller;

import com.citi.intelli.diff.api.model.AnalysisRequest;
import com.citi.intelli.diff.api.model.AggregatedChangeReport;
import com.citi.intelli.diff.service.ImpactAnalyzerService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController // Marks this class as a Spring REST Controller
@RequestMapping("/api/v1/impact") // Base URL path for all APIs in this controller
public class ImpactAnalyzerController {

    private final ImpactAnalyzerService analyzerService;

    // Dependency Injection: Spring automatically injects the service implementation
    public ImpactAnalyzerController(ImpactAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    /**
     * API Endpoint to trigger the code impact analysis.
     * * URL: POST /api/v1/impact/analyze
     * Request Body: AnalysisRequest JSON
     * Response Body: List<AggregatedChangeReport> JSON
     */
    @PostMapping(value = "/analyze", consumes = "application/json")
    public ResponseEntity<List<AggregatedChangeReport>> analyze(@RequestBody AnalysisRequest request) {

        // 1. Validate the request (basic check)
        if (request.getLocalFilePath() == null || request.getTargetFilename() == null) {
            // Handle bad request
            return ResponseEntity.badRequest().build();
        }
        try {
            // 2. Delegate the core business logic to the service layer
            List<AggregatedChangeReport> report = analyzerService.runAnalysis(
                    request.getRepositoryUrls(),
                    request.getLocalFilePath(),
                    request.getTargetFilename()
            );

            // 3. Return the 200 OK status with the analysis report
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            // Log the error and return a 500 Internal Server Error
            System.err.println("Analysis failed: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * API Endpoint to retrieve a list of available repositories for the frontend.
     * * URL: GET /api/v1/impact/repositories
     * Response Body: List<RepositoryInfo> JSON
     */
    @GetMapping("/repositories")
    public ResponseEntity<List<String>> getRepositories() {
        // 1. Call the service to get the data
        List<String> repos = analyzerService.getAvailableRepositories();

        // 2. Return the list with 200 OK status
        return ResponseEntity.ok(repos);
    }

    /**
     * API Endpoint to retrieve the raw code content of a specific class file.
     * * URL: GET /api/v1/impact/code?repo={repoName}&file={fileName}
     * * @param repoIdentifier The repository name (e.g., "CoreBankingModuleA").
     *
     * @param filename The file name (e.g., "A_Helper.java").
     * @return Raw code content as a String.
     */
    @GetMapping("/code")
    public ResponseEntity<String> getClassCode(
            @RequestParam("repo") String repoIdentifier,
            @RequestParam("file") String filename) {
        try {
            // Delegate the logic to the service layer
            String codeContent = analyzerService.getClassCode(repoIdentifier, filename);

            // Return the raw String content with 200 OK
            return ResponseEntity.ok(codeContent);

        } catch (Exception e) {
            // Return 500 for any other server-side error
            return ResponseEntity.internalServerError().body("Error retrieving code: " + e.getMessage());
        }
    }
}