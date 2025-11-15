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
}