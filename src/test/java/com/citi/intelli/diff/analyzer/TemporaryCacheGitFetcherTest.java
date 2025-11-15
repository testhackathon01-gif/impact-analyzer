package com.citi.intelli.diff.analyzer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryCacheGitFetcherTest {

    // JUnit 5 annotation to create a temporary directory for tests
    @TempDir
    Path tempDir;

    private static final String TEST_REPO_URL = "https://github.com/test/repo.git";
    private static final String FQCN_FILE1 = "com.app.util.Helper";
    private static final String FQCN_FILE2 = "com.app.service.MyService";
    private static final String RAW_CONTENT_1 = "package com.app.util; public class Helper {}";
    private static final String RAW_CONTENT_2 = "package com.app.service; public class MyService {}";

    // Mock the class under test to override the complex fetch method
    private TemporaryCacheGitFetcher fetcherSpy;

    @BeforeEach
    void setUp() throws Exception {
        // Create a spy to mock the results of the method that interacts with JGit
        fetcherSpy = Mockito.spy(new TemporaryCacheGitFetcher());

        // Setup the mock file structure
        File mockWorkingDir = createMockWorkingRepository(tempDir.toFile());

        // Mock the fetchAndCheckoutSourceCode to return the controlled mock directory
        Mockito.doReturn(mockWorkingDir)
                .when(fetcherSpy)
                .fetchAndCheckoutSourceCode(Mockito.eq(TEST_REPO_URL), Mockito.anyString());

        // Mock cleanupDirectory to prevent errors during cleanup calls after mocking
        Mockito.doNothing().when(fetcherSpy).cleanupDirectory(Mockito.any());
    }

    @AfterEach
    void tearDown() {
        // Since cleanup is mocked, we need to ensure the temporary files are cleared by JUnit's @TempDir
    }

    /**
     * Creates a mock directory structure that resembles a successful JGit checkout.
     * @param rootDir The temporary root directory.
     * @return The mock working directory (the "sourceCodeDir").
     */
    private File createMockWorkingRepository(File rootDir) throws IOException {
        Path sourceCodeDir = rootDir.toPath().resolve("mock-source-code");
        Files.createDirectories(sourceCodeDir.resolve("src/main/java"));

        // Create package directories
        Path pkgPath1 = sourceCodeDir.resolve("src/main/java/com/app/util");
        Path pkgPath2 = sourceCodeDir.resolve("src/main/java/com/app/service");
        Files.createDirectories(pkgPath1);
        Files.createDirectories(pkgPath2);

        // Create mock Java files
        Files.write(pkgPath1.resolve("Helper.java"), RAW_CONTENT_1.getBytes(StandardCharsets.UTF_8));
        Files.write(pkgPath2.resolve("MyService.java"), RAW_CONTENT_2.getBytes(StandardCharsets.UTF_8));

        return sourceCodeDir.toFile();
    }

    @Test
    void accessRemoteRepoAndGetTheFileContents_ShouldProcessFilesAndReturnMetadataMap() throws Exception {
        // Arrange
        List<String> repoList = new ArrayList<>(Arrays.asList(TEST_REPO_URL));

        // Act
        // Call the spied method which uses the mocked fetch method
        Map<String, Map<String, String>> result = fetcherSpy.accessRemoteRepoAndGetTheFileContents(repoList);

        // Assert
        // 1. Verify that the map is not empty and contains the expected entry (due to the bug in the original code)
        assertFalse(result.isEmpty(), "Result map should not be empty.");
        assertTrue(result.containsKey(TEST_REPO_URL), "Result map must be keyed by repoUrl.");

        // 2. Due to the bug in the original implementation (overwriting the classMetadataMap with repoUrl as key),
        // we only assert on the last processed class's metadata.
        Map<String, String> attributes = result.get(TEST_REPO_URL);
        assertNotNull(attributes, "Attributes map should not be null.");

        // 3. Verify the final (overwritten) entry's data (which is MyService's data)
        // NOTE: The original code uses the WRONG key for raw content, it uses the bugged package name:
        String buggedKey = "com.app.service.MyService"; // Calculated as: package + "." + className
        assertTrue(attributes.containsKey(buggedKey), "Attributes should contain the raw content keyed by the bugged FQCN.");
        assertEquals(RAW_CONTENT_2, attributes.get(buggedKey), "Raw content should match the second file's content.");

        // 4. Verify that the correct cleanup was attempted
        Mockito.verify(fetcherSpy, Mockito.times(1)).cleanupDirectory(Mockito.any(File.class));
    }

    @Test
    void accessRemoteRepoAndGetTheFileContents_ShouldHandleMultipleRepos(
    ) throws Exception {
        // Arrange
        String TEST_REPO_URL_2 = "https://github.com/another/repo.git";
        List<String> repoList = new ArrayList<>(Arrays.asList(TEST_REPO_URL, TEST_REPO_URL_2));

        // Mock the fetch method for the second URL as well
        File mockWorkingDir2 = createMockWorkingRepository(tempDir.toFile().toPath().resolve("mock-2").toFile());
        Mockito.doReturn(mockWorkingDir2)
                .when(fetcherSpy)
                .fetchAndCheckoutSourceCode(Mockito.eq(TEST_REPO_URL_2), Mockito.anyString());

        // Act
        Map<String, Map<String, String>> result = fetcherSpy.accessRemoteRepoAndGetTheFileContents(repoList);

        assertEquals(1, result.size(), "Due to the logic error, only the last processed repo's data should remain.");

        // Verify only the last URL is the key
        assertTrue(result.containsKey(TEST_REPO_URL_2), "The map should contain data for the last repo processed.");
        assertFalse(result.containsKey(TEST_REPO_URL), "The first repo's data was overwritten due to incorrect map key.");
    }
}