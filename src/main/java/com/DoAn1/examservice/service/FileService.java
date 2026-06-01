package com.DoAn1.examservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.DoAn1.examservice.exception.StorageException;

@Service
public class FileService {

    @Value("${examservice.storage.root-path:D:/DoAn/DoAn1_storage}")
    private String baseURI;

    public void createUploadFolder(String folder) throws IOException {
        Files.createDirectories(resolveFolder(folder));
    }

    public String store(MultipartFile file, String folder) throws IOException {
        String originalFileName = StringUtils.cleanPath(String.valueOf(file.getOriginalFilename()));
        if (!StringUtils.hasText(originalFileName) || originalFileName.contains("..")) {
            throw new StorageException("Invalid file name");
        }

        String finalName = System.currentTimeMillis() + "-" + originalFileName;
        Path folderPath = resolveFolder(folder);
        Files.createDirectories(folderPath);

        Path targetPath = folderPath.resolve(finalName).normalize();
        if (!targetPath.startsWith(folderPath)) {
            throw new StorageException("Cannot store file outside upload folder");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return finalName;
    }

    public String buildStorageUrl(String folder, String fileName) {
        String cleanFolder = StringUtils.cleanPath(folder.trim()).replace("\\", "/");
        String cleanFileName = StringUtils.cleanPath(fileName);
        if (cleanFolder.startsWith("/") || cleanFolder.contains("..")
                || cleanFileName.contains("..") || !StringUtils.hasText(cleanFileName)) {
            throw new StorageException("Invalid storage path");
        }
        return "/storage/" + cleanFolder + "/" + cleanFileName;
    }

    private Path resolveFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            throw new StorageException("Folder is required");
        }

        String cleanFolder = StringUtils.cleanPath(folder.trim()).replace("\\", "/");
        if (cleanFolder.startsWith("/") || cleanFolder.contains("..")) {
            throw new StorageException("Invalid upload folder");
        }

        Path rootPath = Path.of(baseURI).toAbsolutePath().normalize();
        Path folderPath = rootPath.resolve(cleanFolder).normalize();
        if (!folderPath.startsWith(rootPath)) {
            throw new StorageException("Cannot access folder outside storage root");
        }
        return folderPath;
    }
}
