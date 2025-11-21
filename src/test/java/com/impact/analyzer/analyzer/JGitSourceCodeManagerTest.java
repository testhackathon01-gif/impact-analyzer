package com.impact.analyzer.analyzer;

import com.impact.analyzer.service.GitHubRepoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JGitSourceCodeManagerTest {

    @Test
    @DisplayName("accessRemoteRepoAndGetTheFileContents populates metadata from checked-out repo")
    void testAccessRemoteRepoAndGetTheFileContents() throws Exception {
        // Mock GitHubRepoService to return one repo URL
        GitHubRepoService repoService = mock(GitHubRepoService.class);
        String repoUrl = "https://example.com/acme/repo.git";
        when(repoService.getCachedPublicRepoUrls()).thenReturn(List.of(repoUrl));

        // Create a fake working directory structure returned by fetchAndCheckoutSourceCode
        Path tempRepo = Files.createTempDirectory("jgit-test-work-");
        Path srcMainJava = tempRepo.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(srcMainJava.resolve("com").resolve("x"));
        Path javaFile = srcMainJava.resolve("com").resolve("x").resolve("A.java");
        Files.write(javaFile, "package com.x; public class A {}".getBytes(StandardCharsets.UTF_8));

        // Mock static method to return our temp directory
        try (MockedStatic<JGitSourceCodeManager> mocked = mockStatic(JGitSourceCodeManager.class, CALLS_REAL_METHODS)) {
            mocked.when(() -> JGitSourceCodeManager.fetchAndCheckoutSourceCode(anyString(), anyString()))
                    .thenReturn(tempRepo.toFile());

            JGitSourceCodeManager manager = new JGitSourceCodeManager(repoService);
            manager.accessRemoteRepoAndGetTheFileContents();

            Map<String, Map<String, String>> meta = manager.getRepoMetaData();
            assertThat(meta).containsKey(repoUrl);
            Map<String, String> classes = meta.get(repoUrl);
            assertThat(classes).containsKey("com.x.A");
            assertThat(classes.get("com.x.A")).contains("class A");
        } finally {
            // ensure test temp cleanup
            JGitSourceCodeManager.cleanupDirectory(tempRepo.toFile());
        }
    }

    @Test
    @DisplayName("cleanupDirectory removes nested files and folder")
    void testCleanupDirectoryRemovesAll() throws IOException {
        Path dir = Files.createTempDirectory("cleanup-test-");
        Path nested = dir.resolve("a").resolve("b");
        Files.createDirectories(nested);
        Files.write(nested.resolve("c.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        File asFile = dir.toFile();

        // Precondition
        assertThat(asFile.exists()).isTrue();

        JGitSourceCodeManager.cleanupDirectory(asFile);

        assertThat(asFile.exists()).isFalse();
    }
}
