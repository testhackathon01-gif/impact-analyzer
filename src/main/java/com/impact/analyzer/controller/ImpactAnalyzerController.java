package com.impact.analyzer.controller;

import com.impact.analyzer.api.model.AnalysisRequest;
import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.api.model.ConciseAnalysisReport;
import com.impact.analyzer.service.ImpactAnalysisService;
import com.impact.analyzer.util.ImpactPostProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for triggering impact analysis and fetching related metadata.
 * Implements the API layer of the application.
 */
@Slf4j
@CrossOrigin
@RestController
@RequestMapping("/api/v1/impact")
public class ImpactAnalyzerController { // 💡 Renaming convention maintained

    private final ImpactAnalysisService analyzerService;

    // Constructor Injection is correctly used
    public ImpactAnalyzerController(ImpactAnalysisService analyzerService) {
        this.analyzerService = analyzerService;
    }

    // --- 1. CORE ANALYSIS ENDPOINT ---

    /**
     * API Endpoint to trigger the code impact analysis.
     * URL: POST /api/v1/impact/analyze
     */
    @PostMapping(value = "/analyze", consumes = "application/json")
    public ResponseEntity<List<ConciseAnalysisReport>> analyze(@RequestBody AnalysisRequest request) {

        log.info("Received analysis request for target file: {}", request.getTargetFilename());

        // 1. Robust Validation
        if (request.getChangedCode() == null || request.getChangedCode().isBlank() ||
                request.getTargetFilename() == null || request.getTargetFilename().isBlank() ||
                request.getSelectedRepository() == null || request.getSelectedRepository().isBlank()) {

            log.warn("Bad Request: Missing required fields (changedCode, targetFilename, or selectedRepository).");
            return ResponseEntity.badRequest().build();
        }

        try {
            // 2. Delegate core analysis logic
            List<AggregatedChangeReport> rawReport = analyzerService.runAnalysis(
                    request.getSelectedRepository(),
                    request.getCompareRepositoryUrls(),
                    request.getChangedCode(),
                    request.getTargetFilename()
            );

            // 3. Post-process the raw LLM report into a concise format
            List<ConciseAnalysisReport> finalReport = ImpactPostProcessor.processReports(rawReport, request.getTargetFilename());

            log.info("Analysis completed successfully. Generated {} reports.", finalReport.size());

            // 4. Return 200 OK
            return ResponseEntity.ok(finalReport);

        } catch (IllegalArgumentException e) {
            // Catch specific validation errors from the service layer
            log.error("Analysis failed due to invalid arguments: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            // Log the error and return a 500 Internal Server Error
            log.error("Analysis failed due to internal server error.", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- 2. METADATA ENDPOINT: REPOSITORIES ---

    /**
     * API Endpoint to retrieve a map of available repositories and their file metadata.
     * URL: GET /api/v1/impact/metadata/repos
     */
    @GetMapping("/metadata/repos") // 💡 Renamed to improve clarity on the data being fetched
    public ResponseEntity<Map<String, Map<String, String>>> getRepositoriesMetadata() {

        log.debug("Fetching available repository metadata.");

        // The service layer handles caching/fetching, returning the full structure
        Map<String, Map<String, String>> reposMetadata = analyzerService.getAvailableRepositories();

        // Returns 200 OK
        return ResponseEntity.ok(reposMetadata);
    }

    // --- 3. METADATA ENDPOINT: RAW CODE ---

    /**
     * API Endpoint to retrieve the raw code content of a specific class file.
     * URL: GET /api/v1/impact/code?repo={repoName}&file={fileName}
     *
     * @param repoIdentifier The repository identifier (e.g., URL or name).
     * @param filename The Fully Qualified Class Name (FQCN) or simple file name.
     * @return Raw code content as a String.
     */
    @GetMapping(value = "/code", produces = "text/plain") // Explicitly set content type to text/plain
    public ResponseEntity<String> getClassCode(
            @RequestParam("repo") String repoIdentifier,
            @RequestParam("file") String filename) {

        log.debug("Request to fetch code for file {} in repository {}.", filename, repoIdentifier);

        if (repoIdentifier == null || repoIdentifier.isBlank() || filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest().body("Repository and file identifiers are required.");
        }

        try {
            // Delegate the logic to the service layer
            String codeContent = analyzerService.getClassCode(repoIdentifier, filename);

            if (codeContent == null || codeContent.isEmpty()) {
                // If the service returns null, treat it as "Not Found"
                log.warn("Code not found for repo: {} file: {}", repoIdentifier, filename);
                return new ResponseEntity<>("File not found in cache.", HttpStatus.NOT_FOUND);
            }

            // Return the raw String content with 200 OK
            return ResponseEntity.ok(codeContent);

        } catch (Exception e) {
            log.error("Error retrieving code for {} in {}.", filename, repoIdentifier, e);
            // Return 500 Internal Server Error
            return ResponseEntity.internalServerError().body("Error retrieving code.");
        }
    }
}