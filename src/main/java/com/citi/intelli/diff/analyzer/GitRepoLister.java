package com.citi.intelli.diff.analyzer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value; // Import for @Value
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class GitRepoLister {

    private static final String API_BASE = "https://api.github.com";
    private static final int PER_PAGE = 100;

    private final HttpClient http;

    // 1. Token injected from application.properties
    @Value("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")
    private String token;

    private final ObjectMapper mapper = new ObjectMapper();
    private List<String> publicRepoList = Collections.emptyList();


    public GitRepoLister() {
        // 2. Removed hardcoded token
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Fetch and print repositories. If username is null, fetch authenticated user's repos (/user/repos).
     * Otherwise fetch /users/{username}/repos (public repos).
     */
    public List<String> listRepos(String username) throws IOException, InterruptedException {
        int page = 1;
        boolean more = true;
        List<String> repoList = new ArrayList<>();

        while (more) {
            String path;
            if (username == null || username.isBlank()) {
                // Endpoint for authenticated user's repos
                path = String.format("/user/repos?per_page=%d&page=%d", PER_PAGE, page);
            } else {
                // Endpoint for a specific user's public repos
                path = String.format("/users/%s/repos?per_page=%d&page=%d", username, PER_PAGE, page);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "GitRepoLister-Java");

            if (token != null && !token.isBlank()) {
                // 3. FIXED: Use "Bearer" scheme for modern GitHub PATs
                reqBuilder.header("Authorization", "Bearer " + token);
            }

            HttpRequest req = reqBuilder.GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            int status = resp.statusCode();
            if (status == 401 || status == 403) {
                // These errors often indicate an invalid/expired token or incorrect permissions (scopes).
                System.err.println("Authentication or permission error (HTTP " + status + "). Check your token.");
                System.err.println("Response body: " + resp.body());
                return repoList;
            } else if (status >= 400) {
                // Includes the original 400 Bad Request error
                System.err.println("HTTP error " + status + ". Response: " + resp.body());
                return repoList;
            }

            JsonNode root = mapper.readTree(resp.body());
            if (!root.isArray()) {
                // Handle rate limiting (status 403, but body might contain "message": "rate limit exceeded")
                if (root.has("message")) {
                    System.err.println("GitHub API Error: " + root.path("message").asText());
                } else {
                    System.err.println("Unexpected response (not an array): " + root.toString());
                }
                return repoList;
            }

            int count = 0;
            for (JsonNode repo : root) {
                count++;
                String name = safeText(repo, "name");
                String fullName = safeText(repo, "full_name");
                String htmlUrl = safeText(repo, "html_url");
                boolean isPrivate = repo.path("private").asBoolean(false);
                String desc = safeText(repo, "description");

                repoList.add(htmlUrl);

                System.out.printf("%d. %s (%s) %s %s%n", (page - 1) * PER_PAGE + count,
                        fullName != null ? fullName : name,
                        isPrivate ? "private" : "public",
                        htmlUrl != null ? htmlUrl : "",
                        desc != null ? "- " + desc : "");
            }

            System.out.println("htmlUrl====="+repoList.toString());

            if (count < PER_PAGE) {
                more = false;
            } else {
                page++;
            }
        }
        return repoList;
    }

    private static String safeText(JsonNode n, String field) {
        JsonNode f = n.get(field);
        if (f == null || f.isNull()) return null;
        return f.asText();
    }

    @PostConstruct
    public void setPublicRepoList() {
        String username = ""; // Use null or blank string to fetch authenticated user's repos

        // The check for token is now implicitly handled by Spring trying to load the @Value
        // If the token is missing from properties, Spring will fail to start.

        // The token is now a class member, not a local variable.
        GitRepoLister lister = this; // Since we are in a @Service, we can use 'this' or inject.

        try {
            // Note: If you don't provide a username, the listRepos will use /user/repos
            // which requires the token and appropriate scope (e.g., 'repo' scope).
            this.publicRepoList = lister.listRepos(username);
        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching repos: " + e.getMessage());
            e.printStackTrace();
            // Depending on your application's needs, you might throw a RuntimeException instead of System.exit(2);
        }
    }

    public List<String> getPublicRepoList(){
        return this.publicRepoList;
    }

}