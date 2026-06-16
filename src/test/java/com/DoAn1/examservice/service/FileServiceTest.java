package com.DoAn1.examservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.DoAn1.examservice.exception.StorageException;
import com.DoAn1.examservice.util.StorageUrlPrefixResolver;

class FileServiceTest {

    private FileService fileService;
    private Path storageRoot;

    @BeforeEach
    void setUp() {
        storageRoot = Path.of("build/tmp/FileServiceTest/storage").toAbsolutePath().normalize();
        fileService = new FileService();
        ReflectionTestUtils.setField(fileService, "baseURI", storageRoot.toString());
        ReflectionTestUtils.setField(fileService, "storageUrlPrefixes", StorageUrlPrefixResolver.DEFAULT_STORAGE_PATH_PREFIX);
    }

    @Test
    void resolveStorageRelativePathReturnsPathInsideStorageRoot() {
        Path resolvedPath = fileService.resolveStorageRelativePath("questions/question-1.png");

        assertThat(resolvedPath).isEqualTo(storageRoot.resolve("questions/question-1.png").normalize());
    }

    @Test
    void resolveStorageRelativePathRejectsTraversal() {
        assertThatThrownBy(() -> fileService.resolveStorageRelativePath("../secret.txt"))
                .isInstanceOf(StorageException.class)
                .hasMessage("Invalid storage path");
    }

    @Test
    void extractStorageRelativePathReadsStorageUrl() {
        String relativePath = fileService.extractStorageRelativePath("/storage/omr/raw/file.pdf");

        assertThat(relativePath).isEqualTo("omr/raw/file.pdf");
    }

    @Test
    void extractStorageRelativePathReadsConfiguredUrlPrefix() {
        ReflectionTestUtils.setField(fileService, "storageUrlPrefixes", "http://scoring.local/files/");

        String relativePath = fileService.extractStorageRelativePath(
                "http://scoring.local/files/omr/scored/page-1.png?token=abc");

        assertThat(relativePath).isEqualTo("omr/scored/page-1.png");
    }
}
