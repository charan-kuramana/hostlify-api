package com.hostlify.shared.storage;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Serves locally stored files (dev mode only) for links signed by AzureBlobService. */
@RestController
@RequestMapping("/api/v1/public/files")
public class LocalFileController {

    private final AzureBlobService storage;

    public LocalFileController(AzureBlobService storage) {
        this.storage = storage;
    }

    @GetMapping
    public ResponseEntity<byte[]> get(@RequestParam String path, @RequestParam long exp, @RequestParam String sig) throws Exception {
        Path file = storage.resolveSignedLocal(path, exp, sig);
        if (file == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        MediaType type = MediaTypeFactory.getMediaType(file.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(Files.readAllBytes(file));
    }
}
