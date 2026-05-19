package by.bsuir.coursework.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

public final class ChecksumUtil {
    private static final ObjectMapper MAPPER = JsonMapper.get();

    private ChecksumUtil() {
    }

    public static String checksum(ApiRequest request) {
        String cmd = request.getCommandType() == null ? "" : request.getCommandType().name();
        String token = request.getAuthToken() == null ? "" : request.getAuthToken();
        Map<String, Object> payload = request.getPayload() == null ? Map.of() : request.getPayload();
        String payloadJson = toCanonicalJson(payload);
        String material = cmd + "|" + token + "|" + payloadJson;
        return sha256Base64(material);
    }

    private static String toCanonicalJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }

    private static String sha256Base64(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute checksum", e);
        }
    }
}

