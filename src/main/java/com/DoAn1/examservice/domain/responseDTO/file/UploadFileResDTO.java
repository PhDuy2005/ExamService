package com.DoAn1.examservice.domain.responseDTO.file;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileResDTO {
    private String fileName;
    private String relativePath;
    private Instant uploadedAt;
}
