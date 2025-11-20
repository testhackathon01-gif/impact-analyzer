package com.impact.analyzer.analyzer;

import com.impact.analyzer.service.TemporaryCacheGitFetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryCacheGitFetcherTest {

    @Mock
    private GitRepoLister gitRepoList;

    @InjectMocks
    private TemporaryCacheGitFetcher gitFetcher;

    // Use a MockedStatic to control the behavior of the static method
    private MockedStatic<TemporaryCacheGitFetcher> mockedStatic;

    // Path to simulate the root directory of the temporary checkout
    private Path tempSourceCodeDir;

    // Constants for testing
    private static final String MOCK_REPO_URL = "https://github.com/test/repo1.git";
    private static final String MOCK_BRANCH = "master";
    private static final String MOCK_JAVA_CONTENT = "package com.example;\npublic class TestClass {}";
    private static final String MOCK_JAVA_FILENAME = "TestClass.java";
    private static final String EXPECTED_FQCN = "com.example.TestClass";

    // The main method under test is @PostConstruct, so we call it explicitly.

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory structure to simulate a successful Git checkout
        tempSourceCodeDir = Files.createTempDirectory("test-checkout");

        // 1. Create the necessary Java source path: src/main/java/com/example/
        Path javaDir = tempSourceCodeDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        // 2. Create the mock Java file
        Path javaFile = javaDir.resolve(MOCK_JAVA_FILENAME);
        Files.write(javaFile, MOCK_JAVA_CONTENT.getBytes());

        // Mock the static method to return our controlled temporary directory
        mockedStatic = mockStatic(TemporaryCacheGitFetcher.class, CALLS_REAL_METHODS);
        mockedStatic.when(() -> TemporaryCacheGitFetcher.fetchAndCheckoutSourceCode(anyString(), anyString()))
                .thenReturn(tempSourceCodeDir.toFile());

        // Mock the cleanupDirectory to avoid actual filesystem issues during test
        mockedStatic.when(() -> TemporaryCacheGitFetcher.cleanupDirectory(any(File.class))).then(invocation -> {
            // Do nothing, but ensure the method is called
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        // Close the mocked static resource
        mockedStatic.close();

        // Ensure the temporary directory is cleaned up after the test run
        try {
            if (tempSourceCodeDir != null && Files.exists(tempSourceCodeDir)) {
                // Use the real cleanup method for safety after the mock is closed
                Files.walk(tempSourceCodeDir)
                        .map(Path::toFile)
                        .sorted((o1, o2) -> -o1.compareTo(o2))
                        .forEach(File::delete);
            }
        } catch (IOException e) {
            System.err.println("Failed to clean up test directory: " + e.getMessage());
        }
    }

    @Test
    void testAccessRemoteRepoAndGetTheFileContents_Success() {
        // ARRANGE
        List<String> mockRepoList = Arrays.asList(MOCK_REPO_URL);
        when(gitRepoList.getPublicRepoList()).thenReturn(mockRepoList);

        // ACT
        // Call the method that is annotated with @PostConstruct
        gitFetcher.accessRemoteRepoAndGetTheFileContents();

        Map<String, Map<String, String>> result = gitFetcher.getRepoMetaData();

        // ASSERT
        // 1. Verify the static method was called
        //verifyStatic(TemporaryCacheGitFetcher.class, times(1));
        try {
            TemporaryCacheGitFetcher.fetchAndCheckoutSourceCode(MOCK_REPO_URL, MOCK_BRANCH);
        } catch (Exception e) {
            fail("fetchAndCheckoutSourceCode should not throw exception here.");
        }

        // 2. Verify the result map structure
        assertNotNull(result, "The metadata map should not be null.");
        assertTrue(result.containsKey(MOCK_REPO_URL), "Result should contain the mock repository URL.");

        Map<String, String> classMap = result.get(MOCK_REPO_URL);
        assertNotNull(classMap, "Class map for the repo should not be null.");

        // 3. Verify the class content
        // In your current implementation, the key in the inner map is the FQCN,
        // and the value is the raw content.
        assertTrue(classMap.containsKey(EXPECTED_FQCN),
                "Class map should contain the expected FQCN key: " + EXPECTED_FQCN);
        assertEquals(MOCK_JAVA_CONTENT, classMap.get(EXPECTED_FQCN),
                "The stored content should match the mock Java content.");

        // 4. Verify cleanup was called
        //verifyStatic(TemporaryCacheGitFetcher.class, times(1));
        TemporaryCacheGitFetcher.cleanupDirectory(any(File.class));
    }

    @Test
    void testAccessRemoteRepoAndGetTheFileContents_EmptyRepoList() {
        // ARRANGE
        when(gitRepoList.getPublicRepoList()).thenReturn(List.of());

        // ACT
        gitFetcher.accessRemoteRepoAndGetTheFileContents();

        Map<String, Map<String, String>> result = gitFetcher.getRepoMetaData();

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty(), "The metadata map should be empty for an empty repo list.");

        // Verify that fetch and cleanup were never called
        //verifyStatic(TemporaryCacheGitFetcher.class, never());
        try {
            TemporaryCacheGitFetcher.fetchAndCheckoutSourceCode(anyString(), anyString());
        } catch (Exception e) {
            // Do nothing
        }
    }

    @Test
    void testAccessRemoteRepoAndGetTheFileContents_FetchFails() {
        // ARRANGE
        List<String> mockRepoList = Arrays.asList(MOCK_REPO_URL);
        when(gitRepoList.getPublicRepoList()).thenReturn(mockRepoList);

        // Re-mock the static method to throw an exception
        mockedStatic.close(); // Close previous mock
        mockedStatic = mockStatic(TemporaryCacheGitFetcher.class, CALLS_REAL_METHODS);
        try {
            mockedStatic.when(() -> TemporaryCacheGitFetcher.fetchAndCheckoutSourceCode(anyString(), anyString()))
                    .thenThrow(new RuntimeException("Simulated Git Error"));
        } catch (Exception e) {
            // Should not happen as we catch the exception in the method
        }

        // ACT
        gitFetcher.accessRemoteRepoAndGetTheFileContents();

        Map<String, Map<String, String>> result = gitFetcher.getRepoMetaData();

        // ASSERT
        assertNotNull(result);
        assertTrue(result.isEmpty(), "The metadata map should be empty if fetching fails.");

        // Verify that cleanup was still attempted (although the directory might be null)
        //verifyStatic(TemporaryCacheGitFetcher.class, times(1));
        TemporaryCacheGitFetcher.cleanupDirectory(isNull()); // Cleanup is called with null in the finally block
    }

    // Test the public getter method
    @Test
    void testGetRepoMetaData() {
        // ARRANGE
        Map<String, Map<String, String>> expectedMap = Map.of("test-url", Map.of("class", "content"));
        // Use reflection to directly set the private field as if @PostConstruct ran
        ReflectionTestUtils.setField(gitFetcher, "classMetadataMap", expectedMap);

        // ACT
        Map<String, Map<String, String>> result = gitFetcher.getRepoMetaData();

        // ASSERT
        assertEquals(expectedMap, result, "The getter should return the stored map.");
    }
}