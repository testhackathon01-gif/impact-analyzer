package com.impact.analyzer.analyzer;

import com.impact.analyzer.service.RepoFileMetaDataAccessorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RepoFileMetaDataAccessorImplTest {

    private RepoFileMetaDataAccessorImpl accessor;

    @BeforeEach
    void setUp() {
        accessor = new RepoFileMetaDataAccessorImpl();
    }

    @Test
    @DisplayName("getFileFQCNSForSelectedRepos returns combined keys for matching repos and skips misses")
    void testGetFileFQCNSForSelectedRepos() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        cache.put("repoA", new HashMap<>(Map.of(
                "com.a.Foo", "class Foo {}",
                "com.a.Bar", "class Bar {}"
        )));
        cache.put("repoB", new HashMap<>(Map.of(
                "com.b.Baz", "class Baz {}"
        )));

        List<String> targets = Arrays.asList("repoA", "repoB", "repoC"); // repoC missing

        List<String> result = accessor.getFileFQCNSForSelectedRepos(cache, targets);

        assertThat(result)
                .containsExactlyInAnyOrder("com.a.Foo", "com.a.Bar", "com.b.Baz");
    }

    @Test
    @DisplayName("getFileMetaDataForRepos flattens maps for selected repos only")
    void testGetFileMetaDataForRepos() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        cache.put("repoA", new HashMap<>(Map.of(
                "com.a.Foo", "A-Foo",
                "com.a.Bar", "A-Bar"
        )));
        cache.put("repoB", new HashMap<>(Map.of(
                "com.b.Baz", "B-Baz"
        )));
        cache.put("repoC", new HashMap<>(Map.of(
                "com.c.Qux", "C-Qux"
        )));

        List<String> targets = Arrays.asList("repoA", "repoB");

        Map<String, String> result = accessor.getFileMetaDataForRepos(cache, targets);

        assertThat(result)
                .hasSize(3)
                .containsEntry("com.a.Foo", "A-Foo")
                .containsEntry("com.a.Bar", "A-Bar")
                .containsEntry("com.b.Baz", "B-Baz")
                .doesNotContainKey("com.c.Qux");
    }

    @Test
    @DisplayName("getSubMapValue returns value when repo and file exist")
    void testGetSubMapValuePresent() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        cache.put("repoA", new HashMap<>(Map.of(
                "com.a.Foo", "A-Foo"
        )));

        String val = accessor.getSubMapValue(cache, "repoA", "com.a.Foo");

        assertThat(val).isEqualTo("A-Foo");
    }

    @Test
    @DisplayName("getSubMapValue returns null when repo missing")
    void testGetSubMapValueRepoMissing() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        String val = accessor.getSubMapValue(cache, "nope", "com.a.Foo");
        assertThat(val).isNull();
    }

    @Test
    @DisplayName("getSubMapValue returns null when file missing in existing repo")
    void testGetSubMapValueFileMissing() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        cache.put("repoA", new HashMap<>());
        String val = accessor.getSubMapValue(cache, "repoA", "nope");
        assertThat(val).isNull();
    }
}