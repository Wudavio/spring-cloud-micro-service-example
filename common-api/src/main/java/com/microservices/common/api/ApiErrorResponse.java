package com.microservices.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

public record ApiErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        Map<String, String> details) {

    public static ApiErrorResponse of(int status, String code, String message, String path,
                                      String traceId, Map<String, String> details) {
        return new ApiErrorResponse(OffsetDateTime.now(ZoneOffset.UTC), status, code, message,
                path, traceId, details == null ? Map.of() : details);
    }
}
