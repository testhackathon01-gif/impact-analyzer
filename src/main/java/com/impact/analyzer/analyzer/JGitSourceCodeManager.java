package com.impact.analyzer.analyzer;

import com.impact.analyzer.service.GitHubRepoService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service responsible for fetching Git repository source code and caching it locally
 * using JGit's bare/working directory split for efficient disk usage.
 */
@Service
@Slf4j
public class JGitSourceCodeManager {

    // Key: Repository URL, Value: Map<FQCN, Raw_File_Content>
    private Map<String, Map<String, String>> classMetadataMap = Collections.emptyMap();

    private final GitHubRepoService gitHubRepoService;

    // Use constructor injection instead of @Autowired field injection
    public JGitSourceCodeManager(GitHubRepoService gitHubRepoService) {
        this.gitHubRepoService = gitHubRepoService;
    }

    /**
     * Fetches a repository, caches its history (bare), creates a working copy (linked),
     * and checks out the specified branch.
     *
     * @param repoUrl The URL of the Git repository.
     * @param branch The branch/ref to checkout (e.g., "master" or "main").
     * @return The File object representing the temporary working directory containing the source code.
     * @throws Exception if Git operations or file system operations fail.
     */
    public static File fetchAndCheckoutSourceCode(String repoUrl, String branch) throws Exception {
        Repository bareRepo = null;
        File workingDir = null;
        Path bareDir = null;

        try {
            // 1. Create a temporary directory for the BARE repository (the cache)
            bareDir = Files.createTempDirectory("jgit-bare-cache-");
            log.info("Caching bare repository history for {}: {}", repoUrl, bareDir.toAbsolutePath());

            // Clone the bare repository
            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(bareDir.toFile())
                    .setBare(true)
                    .setNoCheckout(true)
                    .call()) {
                log.debug("Bare clone completed for {}", repoUrl);
            }

            bareRepo = new FileRepositoryBuilder()
                    .setGitDir(bareDir.toFile())
                    .build();

            // 2. Set the configuration (refspec) to fetch all remote branches
            StoredConfig config = bareRepo.getConfig();
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();

            // 3. Run the Fetch command
            try (Git git = new Git(bareRepo)) {
                log.debug("Fetching all remote heads for {}", repoUrl);
                git.fetch()
                        .setRemote("origin")
                        .call();
            }

            // 4. Create a SEPARATE temporary directory for the WORKING COPY
            workingDir = Files.createTempDirectory("jgit-source-code-").toFile();
            log.info("Checking out source code for {} branch {} to: {}", repoUrl, branch, workingDir.getAbsolutePath());

            // 5. Link the working directory to the bare cache
            File gitFileInWorkDir = new File(workingDir, ".git");
            String gitDirContent = "gitdir: " + bareRepo.getDirectory().getAbsolutePath();

            Files.write(
                    gitFileInWorkDir.toPath(),
                    gitDirContent.getBytes(StandardCharsets.UTF_8)
            );

            // 6. Checkout the branch
            String remoteBranchRef = "refs/remotes/origin/" + branch;
            try (Repository linkedRepo = new FileRepositoryBuilder()
                    .setWorkTree(workingDir)
                    .setGitDir(bareRepo.getDirectory())
                    .build();
                 Git git = new Git(linkedRepo)) {

                log.debug("Performing checkout of ref: {}", remoteBranchRef);
                git.checkout()
                        .setName(remoteBranchRef)
                        .setForce(true)
                        .setStartPoint(remoteBranchRef)
                        .setAllPaths(true)
                        .call();
                log.debug("Checkout successful for {} at {}", repoUrl, branch);
            }

            return workingDir;

        } catch (Exception e) {
            log.error("Failed Git operation for repo {}. Attempting cleanup.", repoUrl, e);
            if (bareRepo != null) cleanupDirectory(bareRepo.getDirectory());
            if (workingDir != null) cleanupDirectory(workingDir);
            throw e;
        } finally {
            if (bareRepo != null) bareRepo.close();
        }
    }

    /**
     * Deletes a temporary directory recursively.
     * Logs the cleanup process at INFO level.
     *
     * @param dir The directory to delete.
     */
    public static void cleanupDirectory(File dir) {
        if (dir == null || !dir.exists()) return;

        log.info("Cleaning up temporary directory: {}", dir.getAbsolutePath());

        try {
            // Java 8 compatible recursive delete: walk, map to File, sort reverse, delete
            Files.walk(dir.toPath())
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(file -> {
                        if (!file.delete()) {
                            log.warn("Failed to delete file/directory during cleanup: {}", file.getAbsolutePath());
                        } else {
                            log.trace("Deleted: {}", file.getAbsolutePath());
                        }
                    });
        } catch (IOException e) {
            log.error("Error during temporary directory cleanup of {}: {}", dir.getAbsolutePath(), e.getMessage(), e);
        }
    }

    // --- Lifecycle Method ---

    @PostConstruct
    public void accessRemoteRepoAndGetTheFileContents() {
        // Use the cached list from the injected service
        List<String> repoList = gitHubRepoService.getCachedPublicRepoUrls();
        String targetBranch = "master";

        // Ensure map is mutable for this method
        Map<String, Map<String, String>> metaDataMap = new HashMap<>();

        if (repoList.isEmpty()) {
            log.warn("Repository list is empty. Skipping repository fetching.");
            this.classMetadataMap = Collections.emptyMap();
            return;
        }

        log.info("Starting source code fetch for {} repositories on branch '{}'.", repoList.size(), targetBranch);

        for (String repoUrl : repoList) {
            File sourceCodeDir = null;
            // Map for FQCN -> Content for the current repository
            Map<String, String> attributes = new HashMap<>();
            log.info("Processing repository: {}", repoUrl);

            try {
                // 1. Fetch and checkout the code
                sourceCodeDir = fetchAndCheckoutSourceCode(repoUrl, targetBranch);
                log.info("Source code successfully checked out to {}", sourceCodeDir.getAbsolutePath());

                // 2. Define the path to scan
                Path sourceRoot = sourceCodeDir.toPath().resolve("src/main/java");
                log.debug("Scanning for Java classes in: {}", sourceRoot.toAbsolutePath());

                if (Files.exists(sourceRoot)) {
                    // 3. Walk the file tree and process Java files
                    try (Stream<Path> paths = Files.walk(sourceRoot)) {
                        paths.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java"))
                                .forEach(javaFilePath -> {
                                    String relativePath = sourceRoot.relativize(javaFilePath).toString();

                                    // Construct FQCN from relative path
                                    String fqcn = relativePath
                                            .replace(File.separator, ".")
                                            .replace(".java", "");

                                    String rawContent = null;
                                    try {
                                        rawContent = new String(Files.readAllBytes(javaFilePath), StandardCharsets.UTF_8);
                                        attributes.put(fqcn, rawContent);
                                        log.trace("Read content for FQCN: {}", fqcn);
                                    } catch (IOException e) {
                                        log.error("Failed to read raw content for FQCN {}: {}", fqcn, e.getMessage());
                                    }
                                });

                        metaDataMap.put(repoUrl, attributes);
                        log.info("Finished processing repository {}. Found {} Java files.", repoUrl, attributes.size());

                    } catch (IOException e) {
                        log.error("Error walking file tree for {}: {}", repoUrl, e.getMessage(), e);
                    }
                } else {
                    log.warn("Source directory 'src/main/java' not found in repository: {}", repoUrl);
                }

            } catch (Exception e) {
                // Catch all exceptions from fetchAndCheckoutSourceCode
                log.error("Critical error during Git fetch/checkout for repository {}.", repoUrl, e);
            } finally {
                // 4. Ensure cleanup runs
                if (sourceCodeDir != null) {
                    cleanupDirectory(sourceCodeDir);
                }
            }
        }
        this.classMetadataMap = metaDataMap;
        log.info("Source code fetching complete. Metadata map populated with {} repositories.", this.classMetadataMap.size());
    }

    public Map<String, Map<String,String>> getRepoMetaData(){
        return Collections.unmodifiableMap(this.classMetadataMap);
    }
}