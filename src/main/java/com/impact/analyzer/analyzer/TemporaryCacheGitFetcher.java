package com.impact.analyzer.analyzer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class TemporaryCacheGitFetcher {

    private Map<String, Map<String, String>> classMetadataMap = new HashMap<>();

    @Autowired
    GitRepoLister gitRepoList;

    public static File fetchAndCheckoutSourceCode(String repoUrl, String branch) throws Exception {
        Repository bareRepo = null;
        File workingDir = null;
        Path bareDir = null;

        try {
            // 1. Create a temporary directory for the BARE repository (the cache)
            bareDir = Files.createTempDirectory("jgit-bare-cache-");
            System.out.println("Caching bare repository history: " + bareDir.toAbsolutePath());

            try (Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(bareDir.toFile())
                    .setBare(true)
                    .setNoCheckout(true) // Ensure it doesn't try to checkout anything immediately
                    .call()) {
                // Close the Git object
            }

            // Open the cached repository
            bareRepo = new FileRepositoryBuilder()
                    .setGitDir(bareDir.toFile())
                    .build();

            // 2. Set the configuration (the refspec)
            StoredConfig config = bareRepo.getConfig();
            // This tells JGit to map all remote heads to refs/remotes/origin/*
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();

            // 3. Run the Fetch command using the new configuration
            try (Git git = new Git(bareRepo)) {
                git.fetch()
                        .setRemote("origin")
                        .call();
            }

            // 4. Create a SEPARATE temporary directory for the WORKING COPY (the source code)
            workingDir = Files.createTempDirectory("jgit-source-code-").toFile();
            System.out.println("Checking out source code to: " + workingDir.getAbsolutePath());
            // 3. Link the working directory to the bare cache by creating a .git FILE
            File gitFileInWorkDir = new File(workingDir, ".git");
            String gitDirContent = "gitdir: " + bareRepo.getDirectory().getAbsolutePath();

            // Use the Java 8-compatible Files.write()
            Files.write(
                    gitFileInWorkDir.toPath(),
                    gitDirContent.getBytes(StandardCharsets.UTF_8)
            );

            // 4. Open this new "linked" repository and check out the branch
            try (Repository linkedRepo = new FileRepositoryBuilder()
                    .setWorkTree(workingDir) // Tell JGit where the source files are
                    .setGitDir(bareRepo.getDirectory()) // Tell JGit where the .git data is
                    .build();
                 Git git = new Git(linkedRepo)) {

                // Perform the checkout of the desired branch
                git.checkout()
                        .setName("refs/remotes/origin/" + branch)
                        .setForce(true)
                        .setStartPoint("refs/remotes/origin/" + branch)
                        .setAllPaths(true)
                        .call();
            }

            return workingDir;

        } catch (Exception e) {
            if (bareRepo != null) cleanupDirectory(bareRepo.getDirectory());
            if (workingDir != null) cleanupDirectory(workingDir);
            throw e;
        } finally {
            if (bareRepo != null) bareRepo.close();
        }
    }

    public static void cleanupDirectory(File dir) {
        if (dir == null || !dir.exists()) return;

        System.out.println("Cleaning up temporary directory: " + dir.getAbsolutePath());

        try {
            Files.walk(dir.toPath())
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2)) // Delete files before directories
                    .forEach(File::delete);
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    @PostConstruct
    public void  accessRemoteRepoAndGetTheFileContents() {
        List<String> repoList = new ArrayList<>();
        repoList.addAll(gitRepoList.getPublicRepoList());
        String targetBranch = "master";
        File sourceCodeDir = null;
        Map<String, Map<String, String>> metaDataMap = new HashMap<>();

        for (String repoUrl : repoList) {
            Map<String, String> attributes = new HashMap<>();
            System.out.println("Repo Name:"+repoUrl);
            try {
                sourceCodeDir = fetchAndCheckoutSourceCode(repoUrl, targetBranch);

                System.out.println("\n--- Source Code Files Successfully Accessed ---");

                Path sourceRoot = sourceCodeDir.toPath().resolve("src/main/java");
                System.out.println("Scanning for Java classes in: " + sourceRoot.toAbsolutePath());
// Create a map for the specific class's metadata

                if (Files.exists(sourceRoot)) {
                    try (Stream<Path> paths = Files.walk(sourceRoot)) {
                        paths.filter(Files::isRegularFile)
                                .filter(p -> p.toString().endsWith(".java"))
                                .forEach(javaFilePath -> {
                                    String relativePath = sourceRoot.relativize(javaFilePath).toString();

                                    // Convert file path to Fully Qualified Class Name (FQCN)
                                    String fqcn = relativePath
                                            .replace(File.separator, ".")
                                            .replace(".java", "");

                                    String packageName = "";
                                    int lastDot = fqcn.lastIndexOf('.');
                                    if (lastDot != -1) {
                                        packageName = fqcn.substring(0, lastDot) + "." + fqcn.substring(lastDot + 1);
                                        System.out.println("packageName: " + packageName);
                                    }

                                    // --- New Logic: Read Raw File Content ---
                                    String rawContent = "Error reading file content.";
                                    try {
                                        // Java 8-compatible way to read the whole file into a string
                                        rawContent = new String(Files.readAllBytes(javaFilePath), StandardCharsets.UTF_8);
                                        System.out.println("rawContent: " + rawContent);
                                    } catch (IOException e) {
                                        System.err.println("Failed to read raw content for " + fqcn + ": " + e.getMessage());
                                    }
                                    attributes.put(packageName, rawContent);
                                    metaDataMap.put(repoUrl, attributes);

                                });
                    } catch (IOException e) {
                        System.err.println("Error walking file tree: " + e.getMessage());
                    }
                } else {
                    System.out.println("Could not find the standard source directory: src/main/java");
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (sourceCodeDir != null) {
                    cleanupDirectory(sourceCodeDir);
                }
            }
        }
        this.classMetadataMap = metaDataMap;
    }
    public Map<String, Map<String,String>> getRepoMetaData(){
        return this.classMetadataMap;
    }
}