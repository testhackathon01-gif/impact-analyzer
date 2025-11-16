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
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class GitRepoLister {
    private static final String API_BASE = "https://api.github.com";
    private static final int PER_PAGE = 100;
    private final HttpClient http;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();
    private List<String> publicRepoList = Collections.emptyList();

    public GitRepoLister(String token) {
        this.token = token;
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
        String htmlUrl = "";
        List<String> repoList = new ArrayList<>();

        while (more) {
            String path;
            if (username == null || username.isBlank()) {
                path = String.format("/user/repos?per_page=%d&page=%d", PER_PAGE, page);
            } else {
                path = String.format("/users/%s/repos?per_page=%d&page=%d", username, PER_PAGE, page);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "GitRepoLister-Java");

            if (token != null && !token.isBlank()) {
                reqBuilder.header("Authorization", "token " + token);
            }

            HttpRequest req = reqBuilder.GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            int status = resp.statusCode();
            if (status == 401 || status == 403) {
                System.err.println("Authentication or permission error (HTTP " + status + ").");
                System.err.println("Response body: " + resp.body());
                return repoList;
            } else if (status >= 400) {
                System.err.println("HTTP error " + status + ". Response: " + resp.body());
                return repoList;
            }

            JsonNode root = mapper.readTree(resp.body());
            if (!root.isArray()) {
                System.err.println("Unexpected response (not an array): " + root.toString());
                return repoList;
            }

            int count = 0;
            for (JsonNode repo : root) {
                count++;
                String name = safeText(repo, "name");
                String fullName = safeText(repo, "full_name");
                htmlUrl = safeText(repo, "html_url");
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
        String token = "";//"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";
        String username = "";
        /*if (args.length > 0) {
            username = args[0].trim();
        }*/

        if ((token == null || token.isBlank()) && (username == null || username.isBlank())) {
            System.out.println("No username provided and no GITHUB_TOKEN set.");
            System.out.println("Provide a username as the first argument to list public repos,");
            System.out.println("or set GITHUB_TOKEN environment variable to list the authenticated user's repos.");
            System.out.println("Usage examples:");
            System.out.println("  java GithubRepoLister octocat");
            System.out.println("  export GITHUB_TOKEN=ghp_xxx");
            System.out.println("  java GithubRepoLister");
            System.exit(1);
        }

        GitRepoLister lister = new GitRepoLister(token);
        try {
            this.publicRepoList = lister.listRepos(username);
        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching repos: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }
    public List<String> getPublicRepoList(){
        return this.publicRepoList;
    }

}
