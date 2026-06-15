package com.DoAn1.examservice.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.DoAn1.examservice.service.FileService;

@RestController
public class StorageController {

    private final FileService fileService;

    public StorageController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping({ "/storage/download", "/storage/download/path" })
    public ResponseEntity<Resource> downloadByRelativePath(
            @RequestParam(name = "path") String relativePath) {
        Path filePath = fileService.resolveStorageRelativePath(relativePath);
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
        }

        Resource resource = new FileSystemResource(filePath);
        String filename = filePath.getFileName().toString();

        return ResponseEntity.ok()
                .contentType(resolveContentType(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private MediaType resolveContentType(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            return contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;
        } catch (IOException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
