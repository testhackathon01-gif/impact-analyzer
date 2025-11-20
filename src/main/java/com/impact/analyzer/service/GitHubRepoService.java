package com.impact.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impact.analyzer.exception.GitRepoFetchException;
import lombok.extern.slf4j.Slf4j; // 💡 NEW IMPORT: Lombok's @Slf4j annotation
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service responsible for interacting with the GitHub REST API to list repositories.
 * Uses HttpClient for clean, production-ready non-blocking I/O (though here, it's used synchronously).
 */
@Service
@Slf4j // 💡 Inject logger instance named 'log'
public class GitHubRepoService {

    // Use constants for base URL and pagination
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final int REPOS_PER_PAGE = 100;

    // Injected Dependencies (Testable)
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Configuration Values
    @Value("${github.api.token:}")
    private String githubToken;

    // Cache for public repos (populated once via @PostConstruct)
    private List<String> cachedPublicRepoUrls = Collections.emptyList();


    public GitHubRepoService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        // log.info will be available here due to @Slf4j
        log.info("GitHubRepoService initialized with injected HttpClient and ObjectMapper.");
    }

    // --- Public Methods ---

    /**
     * Fetches all repositories (full HTML URL) for a given user or the authenticated user.
     * This method handles pagination automatically.
     * @param username The GitHub username. If null or blank, fetches repos for the authenticated user.
     * @return A list of repository HTML URLs.
     * @throws GitRepoFetchException if the API call fails due to I/O, networking, or authentication issues.
     */
    public List<String> fetchAllRepoUrls(String username) throws GitRepoFetchException {
        int page = 1;
        List<String> repoList = new ArrayList<>();
        boolean hasMorePages = true;

        log.info("Starting repository fetch for user: {}", username != null && !username.isBlank() ? username : "authenticated user");

        try {
            while (hasMorePages) {
                log.debug("Fetching page {} of repositories.", page);

                HttpRequest request = buildRepoRequest(username, page);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                List<String> pageUrls = processResponse(response);

                repoList.addAll(pageUrls);

                if (pageUrls.size() < REPOS_PER_PAGE) {
                    hasMorePages = false;
                    log.debug("End of pagination reached. Last page size: {}", pageUrls.size());
                } else {
                    page++;
                }
            }
        } catch (IOException | InterruptedException e) {
            // Log low-level networking/IO issues at ERROR level
            Thread.currentThread().interrupt();
            log.error("Failed to fetch GitHub repositories due to network/IO error.", e);
            throw new GitRepoFetchException("Failed to fetch GitHub repositories due to network/IO error.", e);
        }

        log.info("Successfully fetched {} repository URLs.", repoList.size());
        return repoList;
    }

    // --- (getCachedPublicRepoUrls remains the same) ---
    public List<String> getCachedPublicRepoUrls(){
        return Collections.unmodifiableList(this.cachedPublicRepoUrls);
    }

    // --- Private Helper Methods ---

    /**
     * Builds the specific HttpRequest for fetching a page of repositories.
     */
    private HttpRequest buildRepoRequest(String username, int page) {
        String path;
        String userDisplay = username == null || username.isBlank() ? "authenticated user" : username;

        if (username == null || username.isBlank()) {
            path = String.format("/user/repos?per_page=%d&page=%d", REPOS_PER_PAGE, page);
        } else {
            path = String.format("/users/%s/repos?per_page=%d&page=%d", username, REPOS_PER_PAGE, page);
        }

        log.debug("Building request for {} on path: {}", userDisplay, path);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_BASE + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ImpactAnalyzer-Service");

        if (githubToken != null && !githubToken.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + githubToken);
            log.trace("Authorization header added to request."); // TRACE for sensitive info check
        }

        return reqBuilder.GET().build();
    }

    /**
     * Processes the HTTP response, handles errors, and extracts repo URLs.
     */
    private List<String> processResponse(HttpResponse<String> response) throws GitRepoFetchException, IOException {
        int status = response.statusCode();

        if (status >= 400) {
            // Log the failure at WARN/ERROR level before throwing exception
            String errorMsg = extractErrorMessage(response.body(), status);
            log.warn("GitHub API call failed with status {}. Error: {}", status, errorMsg);
            throw new GitRepoFetchException(errorMsg, status);
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) {
            log.error("Unexpected API response: expected JSON array but got {}", root.getNodeType().name());
            throw new GitRepoFetchException("Unexpected API response: expected JSON array but got " + root.getNodeType().name(), status);
        }

        return StreamSupport.stream(root.spliterator(), false)
                .map(repo -> safeText(repo, "html_url"))
                .filter(url -> url != null && !url.isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Attempts to extract a meaningful error message from the GitHub API response body.
     */
    private String extractErrorMessage(String responseBody, int status) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("message").asText(null);

            if (message != null) {
                return String.format("GitHub API Error (HTTP %d): %s", status, message);
            }
        } catch (IOException ignored) {
            // Log that we couldn't parse the error body at DEBUG level
            log.debug("Failed to parse error response body as JSON: {}", responseBody);
        }

        if (status == 401 || status == 403) {
            return String.format("Authentication/Permission Error (HTTP %d). Check github.api.token.", status);
        }
        return String.format("Unknown API Error (HTTP %d).", status);
    }

    // --- (safeText remains the same) ---
    private String safeText(JsonNode n, String field) {
        JsonNode f = n.get(field);
        if (f == null || f.isNull()) return null;
        return f.asText();
    }

    // --- Lifecycle Method ---

    /**
     * Populates the cache after the service is initialized by Spring.
     * Note: This is an expensive operation and should only run once.
     */
    @PostConstruct
    public void cachePublicRepos() {
        String usernameToFetch = "testhackathon01-gif";
        log.info("Starting initial cache population for public repos of user: {}", usernameToFetch);
        try {
            this.cachedPublicRepoUrls = fetchAllRepoUrls(usernameToFetch);
            // Replaced System.out with log.info
            log.info("Successfully cached {} public repo URLs for user {}.", this.cachedPublicRepoUrls.size(), usernameToFetch);
        } catch (GitRepoFetchException e) {
            // Replaced System.err with log.error
            log.error("CRITICAL: Failed to cache initial public repos list.", e);
        }
    }
}