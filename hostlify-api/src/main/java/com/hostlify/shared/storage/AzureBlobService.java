package com.hostlify.shared.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.hostlify.shared.exception.AppException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AzureBlobService {

    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    private final int sasExpiryMinutes;

    public AzureBlobService(BlobServiceClient blobServiceClient,
                             @Value("${app.azure.blob.container-name}") String containerName,
                             @Value("${app.azure.blob.sas-expiry-minutes:15}") int sasExpiryMinutes) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
        this.sasExpiryMinutes = sasExpiryMinutes;
    }

    /**
     * Uploads a file to Azure Blob Storage under the given folder prefix.
     * Returns the blob path (folder/uuid-filename).
     */
    public String uploadFile(MultipartFile file, String folder) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                : "";
        String blobName = folder + "/" + UUID.randomUUID() + extension;

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        BlobHttpHeaders headers = new BlobHttpHeaders().setContentType(file.getContentType());
        try {
            blobClient.upload(file.getInputStream(), file.getSize(), true);
            blobClient.setHttpHeaders(headers);
        } catch (IOException e) {
            throw new AppException("Failed to upload file: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (RuntimeException e) {
            // Storage misconfigured or unreachable (e.g. placeholder credentials locally): clear message, not a bare 500
            org.slf4j.LoggerFactory.getLogger(AzureBlobService.class).warn("Blob upload failed: {}", e.getMessage());
            throw new AppException("File storage is unavailable right now. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return blobName;
    }

    /**
     * Generates a time-limited SAS URL for the given blob path.
     */
    public String generateSasUrl(String blobPath) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobPath);

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plusMinutes(sasExpiryMinutes), permission);

        String sasToken = blobClient.generateSas(values);
        return blobClient.getBlobUrl() + "?" + sasToken;
    }

    /**
     * Deletes a blob by its path.
     */
    public void deleteFile(String blobPath) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        blobClient.deleteIfExists();
    }
}
