package com.DoAn1.examservice.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.DoAn1.examservice.domain.responseDTO.file.UploadFileResDTO;
import com.DoAn1.examservice.exception.StorageException;
import com.DoAn1.examservice.service.FileService;
import com.DoAn1.examservice.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/files")
    @ApiMessage("Upload single file to server")
    public ResponseEntity<UploadFileResDTO> uploadFile(
            @RequestParam(name = "file", required = false) MultipartFile file,
            @RequestParam(name = "folder") String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File is required");
        }

        String fileName = file.getOriginalFilename();
        List<String> allowedExtensions = List.of("png", "jpg", "jpeg", "gif", "pdf", "doc", "docx");
        List<String> allowedMimeTypes = Arrays.asList(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "image/gif",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        boolean isValidExtension = allowedExtensions.stream()
                .anyMatch(extension -> fileName != null && fileName.toLowerCase().endsWith("." + extension));
        if (!isValidExtension) {
            throw new StorageException("File type is not allowed. Only allow: " + String.join(", ", allowedExtensions));
        }

        String contentType = file.getContentType();
        if (!allowedMimeTypes.contains(contentType)) {
            throw new StorageException("Invalid file type based on MIME type.");
        }

        fileService.createUploadFolder(folder);
        String uploadedFile = fileService.store(file, folder);
        String relativePath = fileService.buildStorageRelativePath(folder, uploadedFile);
        return ResponseEntity.ok(new UploadFileResDTO(uploadedFile, relativePath, java.time.Instant.now()));
    }
}
