package com.impact.analyzer.analyzer;

import com.impact.analyzer.analyzer.JGitSourceCodeManager;
import com.impact.analyzer.api.model.AggregatedChangeReport;
import com.impact.analyzer.service.GitHubRepoService;
import com.impact.analyzer.service.ImpactAnalysisServiceImpl;
import com.impact.analyzer.service.RepoFileMetaDataAccessor;
import com.impact.analyzer.util.AnalysisOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceImplTest {

    @Mock private AnalysisOrchestrator analysisOrchestrator;
    @Mock private GitHubRepoService gitHubRepoService;
    @Mock private JGitSourceCodeManager sourceCodeManager;
    @Mock private RepoFileMetaDataAccessor metaDataAccessor;

    private ImpactAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImpactAnalysisServiceImpl(
                analysisOrchestrator,
                gitHubRepoService,
                sourceCodeManager,
                metaDataAccessor
        );
    }

    @Test
    @DisplayName("runAnalysis combines repos, pulls metadata, and delegates to orchestrator")
    void testRunAnalysisHappyPath() throws Exception {
        String selectedRepo = "repoA";
        List<String> compareRepos = new ArrayList<>(List.of("repoB")); // doesn't include selectedRepo
        String changedCode = "class X {}";
        String targetFile = "com.a.X";

        Map<String, Map<String, String>> cache = new HashMap<>();
        when(sourceCodeManager.getRepoMetaData()).thenReturn(cache);

        List<String> fqcnList = List.of("com.a.Foo", "com.b.Bar");
        Map<String, String> flatMap = Map.of("com.a.Foo", "FOO", "com.b.Bar", "BAR");
        when(metaDataAccessor.getFileFQCNSForSelectedRepos(eq(cache), anyList())).thenReturn(fqcnList);
        when(metaDataAccessor.getFileMetaDataForRepos(eq(cache), anyList())).thenReturn(flatMap);

        List<AggregatedChangeReport> expected = List.of(new AggregatedChangeReport());
        when(analysisOrchestrator.getImpactAnalysisReport(fqcnList, flatMap, changedCode, targetFile))
                .thenReturn(expected);

        List<AggregatedChangeReport> actual = service.runAnalysis(selectedRepo, compareRepos, changedCode, targetFile);

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<List<String>> reposCaptor = ArgumentCaptor.forClass(List.class);
        verify(metaDataAccessor).getFileFQCNSForSelectedRepos(eq(cache), reposCaptor.capture());
        List<String> passedRepos = reposCaptor.getValue();
        assertThat(passedRepos).containsExactlyInAnyOrder("repoA", "repoB");

        // Ensure orchestrator called with our prepared data
        verify(analysisOrchestrator).getImpactAnalysisReport(fqcnList, flatMap, changedCode, targetFile);
    }

    @Test
    @DisplayName("runAnalysis throws on missing selectedRepo or changedCode")
    void testRunAnalysisInvalidArgs() {
        assertThatThrownBy(() -> service.runAnalysis(" ", List.of(), "code", "f"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.runAnalysis("repo", List.of(), " ", "f"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getAvailableRepositories delegates to sourceCodeManager")
    void testGetAvailableRepositories() {
        Map<String, Map<String, String>> cache = Map.of();
        when(sourceCodeManager.getRepoMetaData()).thenReturn(cache);
        Map<String, Map<String, String>> result = service.getAvailableRepositories();
        assertThat(result).isSameAs(cache);
        verify(sourceCodeManager).getRepoMetaData();
    }

    @Test
    @DisplayName("getClassCode returns code when accessor finds value")
    void testGetClassCodeFound() {
        Map<String, Map<String, String>> cache = Map.of();
        when(sourceCodeManager.getRepoMetaData()).thenReturn(cache);
        when(metaDataAccessor.getSubMapValue(cache, "repoA", "fileX")).thenReturn("CODE");

        String code = service.getClassCode("repoA", "fileX");
        assertThat(code).isEqualTo("CODE");
        verify(metaDataAccessor).getSubMapValue(cache, "repoA", "fileX");
    }

    @Test
    @DisplayName("getClassCode returns null when accessor returns null")
    void testGetClassCodeMissing() {
        Map<String, Map<String, String>> cache = Map.of();
        when(sourceCodeManager.getRepoMetaData()).thenReturn(cache);
        when(metaDataAccessor.getSubMapValue(cache, "repoA", "fileX")).thenReturn(null);

        String code = service.getClassCode("repoA", "fileX");
        assertThat(code).isNull();
    }
}