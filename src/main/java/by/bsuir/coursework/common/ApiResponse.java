package by.bsuir.coursework.common;

import java.time.Instant;
import java.util.Map;

public class ApiResponse {
    private String requestId;
    private Instant timestamp = Instant.now();
    private boolean success;
    private String message;
    private Map<String, Object> data;

    public static ApiResponse ok(String requestId, String message, Map<String, Object> data) {
        ApiResponse response = new ApiResponse();
        response.requestId = requestId;
        response.success = true;
        response.message = message;
        response.data = data;
        return response;
    }

    public static ApiResponse error(String requestId, String message) {
        ApiResponse response = new ApiResponse();
        response.requestId = requestId;
        response.success = false;
        response.message = message;
        response.data = Map.of();
        return response;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
