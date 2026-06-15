package com.DoAn1.examservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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

    private static final String STORAGE_PATH_PREFIX = "/storage/";

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
        return STORAGE_PATH_PREFIX + buildStorageRelativePath(folder, fileName);
    }

    public String buildStorageRelativePath(String folder, String fileName) {
        String cleanFolder = cleanRelativeFolder(folder);
        String cleanFileName = StringUtils.cleanPath(fileName).replace("\\", "/");
        if (cleanFileName.startsWith("/") || cleanFileName.contains("/") || cleanFileName.contains("..")
                || !StringUtils.hasText(cleanFileName)) {
            throw new StorageException("Invalid storage path");
        }
        return cleanFolder + "/" + cleanFileName;
    }

    public String storeBytes(byte[] content, String folder, String fileName) throws IOException {
        String cleanFileName = StringUtils.cleanPath(fileName);
        if (!StringUtils.hasText(cleanFileName) || cleanFileName.contains("..")) {
            throw new StorageException("Invalid file name");
        }

        Path folderPath = resolveFolder(folder);
        Files.createDirectories(folderPath);
        Path targetPath = folderPath.resolve(cleanFileName).normalize();
        if (!targetPath.startsWith(folderPath)) {
            throw new StorageException("Cannot store file outside upload folder");
        }

        Files.write(targetPath, content);
        return buildStorageUrl(folder, cleanFileName);
    }

    public Path resolveStorageUrl(String storageUrl) {
        if (!StringUtils.hasText(storageUrl) || !storageUrl.startsWith("/storage/")) {
            throw new StorageException("Invalid storage URL");
        }

        String relativePath = storageUrl.substring("/storage/".length());
        return resolveStorageRelativePath(relativePath);
    }

    public Path resolveStorageRelativePath(String path) {
        String relativePath = normalizeStorageRelativePath(path);
        Path rootPath = storageRootPath();
        Path resolvedPath = rootPath.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new StorageException("Cannot access file outside storage root");
        }
        return resolvedPath;
    }

    public String extractStorageRelativePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String trimmedValue = value.trim();
        if (trimmedValue.startsWith(STORAGE_PATH_PREFIX)) {
            return normalizeStorageRelativePath(trimmedValue.substring(STORAGE_PATH_PREFIX.length()));
        }
        if (trimmedValue.startsWith("http://") || trimmedValue.startsWith("https://")) {
            try {
                String path = new URI(trimmedValue).getPath();
                if (path != null && path.startsWith(STORAGE_PATH_PREFIX)) {
                    return normalizeStorageRelativePath(path.substring(STORAGE_PATH_PREFIX.length()));
                }
            } catch (URISyntaxException ex) {
                return null;
            }
            return null;
        }

        return normalizeStorageRelativePath(trimmedValue);
    }

    private Path resolveFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            throw new StorageException("Folder is required");
        }

        String cleanFolder = cleanRelativeFolder(folder);
        Path rootPath = storageRootPath();
        Path folderPath = rootPath.resolve(cleanFolder).normalize();
        if (!folderPath.startsWith(rootPath)) {
            throw new StorageException("Cannot access folder outside storage root");
        }
        return folderPath;
    }

    private Path storageRootPath() {
        return Path.of(baseURI).toAbsolutePath().normalize();
    }

    private String cleanRelativeFolder(String folder) {
        if (!StringUtils.hasText(folder)) {
            throw new StorageException("Folder is required");
        }

        String cleanFolder = StringUtils.cleanPath(folder.trim()).replace("\\", "/");
        if (cleanFolder.startsWith("/") || cleanFolder.contains("..")) {
            throw new StorageException("Invalid upload folder");
        }
        return cleanFolder;
    }

    private String normalizeStorageRelativePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new StorageException("Storage path is required");
        }

        String cleanPath = StringUtils.cleanPath(path.trim()).replace("\\", "/");
        if (cleanPath.startsWith("/") || cleanPath.contains("..")) {
            throw new StorageException("Invalid storage path");
        }
        return cleanPath;
    }
}
