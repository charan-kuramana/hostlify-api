package com.hostlify.shared.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * File storage. Production: Azure Blob Storage with short-lived SAS read links.
 * Local dev (app.storage.local-dir set): files on disk, served by /api/v1/public/files with an
 * HMAC-signed, expiring link — same "private file, temporary link" model as SAS.
 */
@Service
public class AzureBlobService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobService.class);

    private final BlobServiceClient blobServiceClient;
    private final String containerName;
    private final int sasExpiryMinutes;

    @Value("${app.storage.local-dir:}")
    private String localDir;

    @Value("${app.api-base-url:http://localhost:8090}")
    private String apiBaseUrl;

    @Value("${app.jwt.secret}")
    private String signingSecret;

    public AzureBlobService(BlobServiceClient blobServiceClient,
                             @Value("${app.azure.blob.container-name}") String containerName,
                             @Value("${app.azure.blob.sas-expiry-minutes:15}") int sasExpiryMinutes) {
        this.blobServiceClient = blobServiceClient;
        this.containerName = containerName;
        this.sasExpiryMinutes = sasExpiryMinutes;
    }

    public boolean isLocal() {
        return localDir != null && !localDir.isBlank();
    }

    /**
     * Uploads a file under the given folder prefix.
     * Returns the blob path (folder/uuid-filename).
     */
    public String uploadFile(MultipartFile file, String folder) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String extension = originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase().replaceAll("[^a-z0-9.]", "")
                : "";
        String blobName = folder + "/" + UUID.randomUUID() + extension;

        if (isLocal()) {
            try (InputStream in = file.getInputStream()) {
                Path target = localPath(blobName);
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                return blobName;
            } catch (IOException e) {
                throw new AppException("Failed to store file: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

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
            log.warn("Blob upload failed: {}", e.getMessage());
            throw new AppException("File storage is unavailable right now. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return blobName;
    }

    /** Generates a time-limited read URL for the given blob path. */
    public String generateSasUrl(String blobPath) {
        return generateSasUrl(blobPath, sasExpiryMinutes);
    }

    /** Time-limited read URL with a custom lifetime (public listing photos use a longer one so pages can be cached). */
    public String generateSasUrl(String blobPath, int minutes) {
        if (isLocal()) {
            long exp = Instant.now().plusSeconds(minutes * 60L).getEpochSecond();
            return apiBaseUrl + "/api/v1/public/files?path=" + URLEncoder.encode(blobPath, StandardCharsets.UTF_8)
                    + "&exp=" + exp + "&sig=" + sign(blobPath, exp);
        }
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobPath);

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plusMinutes(minutes), permission);

        String sasToken = blobClient.generateSas(values);
        return blobClient.getBlobUrl() + "?" + sasToken;
    }

    /** Deletes a blob by its path. */
    public void deleteFile(String blobPath) {
        if (isLocal()) {
            try {
                Files.deleteIfExists(localPath(blobPath));
            } catch (IOException e) {
                log.warn("Local file delete failed for {}: {}", blobPath, e.getMessage());
            }
            return;
        }
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        blobClient.deleteIfExists();
    }

    // ─── local mode helpers (used by LocalFileController) ─────────────────

    /** Validates a local signed link and returns the file, or null when invalid/expired/missing. */
    public Path resolveSignedLocal(String blobPath, long exp, String sig) {
        if (!isLocal() || blobPath == null || sig == null) return null;
        if (Instant.now().getEpochSecond() > exp) return null;
        if (!HmacUtils.verifyHmac(signingSecret, blobPath + "|" + exp, sig)) return null;
        Path p = localPath(blobPath);
        return Files.isRegularFile(p) ? p : null;
    }

    private String sign(String blobPath, long exp) {
        return HmacUtils.hmacSha256(signingSecret, blobPath + "|" + exp);
    }

    private Path localPath(String blobPath) {
        Path root = Path.of(localDir).toAbsolutePath().normalize();
        Path p = root.resolve(blobPath).normalize();
        if (!p.startsWith(root)) throw new AppException("Invalid file path", HttpStatus.BAD_REQUEST);
        return p;
    }
}
