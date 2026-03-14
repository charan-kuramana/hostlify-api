package com.hostlify.shared.storage;

import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "File Storage")
public class FileController {

    private final AzureBlobService blobService;

    public FileController(AzureBlobService blobService) {
        this.blobService = blobService;
    }

    @GetMapping("/secure")
    @Operation(summary = "Generate a time-limited SAS URL for a blob path")
    public ApiResponse<String> getSecureUrl(@RequestParam String path) {
        if (path == null || path.isBlank()) {
            throw new AppException("path is required", HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(blobService.generateSasUrl(path));
    }

    @PostMapping("/upload/photo")
    @Operation(summary = "Upload a profile photo; returns the blob path")
    public ApiResponse<String> uploadPhoto(@RequestParam("file") MultipartFile file) {
        validateImage(file);
        String blobPath = blobService.uploadFile(file, "photos");
        return ApiResponse.ok(blobPath, "Photo uploaded");
    }

    @PostMapping("/upload/id-proof")
    @Operation(summary = "Upload an ID proof document; returns the blob path")
    public ApiResponse<String> uploadIdProof(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("File is required", HttpStatus.BAD_REQUEST);
        }
        String blobPath = blobService.uploadFile(file, "id-proofs");
        return ApiResponse.ok(blobPath, "ID proof uploaded");
    }

    @DeleteMapping
    @Operation(summary = "Delete a blob by its path")
    public ApiResponse<Void> deleteFile(@RequestParam String path) {
        if (path == null || path.isBlank()) {
            throw new AppException("path is required", HttpStatus.BAD_REQUEST);
        }
        blobService.deleteFile(path);
        return ApiResponse.ok(null, "File deleted");
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("File is required", HttpStatus.BAD_REQUEST);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException("Only image files are allowed for photos", HttpStatus.BAD_REQUEST);
        }
    }
}
