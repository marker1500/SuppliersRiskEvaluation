package by.bsuir.coursework.client;

import by.bsuir.coursework.common.ApiRequest;
import by.bsuir.coursework.common.ApiResponse;
import by.bsuir.coursework.common.ChecksumUtil;
import by.bsuir.coursework.common.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpClient implements AutoCloseable {
    private final String host;
    private final int port;
    private final ObjectMapper mapper = JsonMapper.get();
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final Duration connectTimeout = Duration.ofSeconds(3);
    private final Duration readTimeout = Duration.ofSeconds(8);
    private final int maxAttempts = 3;

    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public CompletableFuture<ApiResponse> send(ApiRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (request.getChecksum() == null || request.getChecksum().isBlank()) {
                request.setChecksum(ChecksumUtil.checksum(request));
            }

            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), (int) connectTimeout.toMillis());
                    socket.setSoTimeout((int) readTimeout.toMillis());
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                        writer.write(mapper.writeValueAsString(request));
                        writer.newLine();
                        writer.flush();
                        String line = reader.readLine();
                        if (line == null || line.isBlank()) {
                            throw new SocketTimeoutException("Connection closed by server");
                        }
                        return mapper.readValue(line, ApiResponse.class);
                    }
                } catch (Exception e) {
                    last = e;
                    // backoff: 150ms, 450ms
                    sleepQuietly(150L * attempt * attempt);
                }
            }
            return ApiResponse.error(request.getRequestId(), "Network error: " + (last == null ? "unknown" : last.getMessage()));
        }, ioPool);
    }

    @Override
    public void close() {
        ioPool.shutdownNow();
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
