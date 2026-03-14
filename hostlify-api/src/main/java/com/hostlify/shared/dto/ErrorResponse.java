package com.hostlify.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean success = false;
    private String error;
    private String message;
    private String path;
    private String requestId;
    private String timestamp;

    public ErrorResponse(String error, String message, String path, String requestId) {
        this.error = error;
        this.message = message;
        this.path = path;
        this.requestId = requestId;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
