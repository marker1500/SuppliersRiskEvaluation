package by.bsuir.coursework.server.transport;

import by.bsuir.coursework.common.ApiRequest;
import by.bsuir.coursework.common.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {
    private final int port;
    private final int threads;
    private final RequestHandler handler = new RequestHandler();
    private final ObjectMapper mapper = JsonMapper.get();
    private volatile boolean running = false;

    public TcpServer(int port, int threads) {
        this.port = port;
        this.threads = threads;
    }

    public void start() throws Exception {
        running = true;
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Server started on port " + port);
            System.out.println("Using " + threads + " threads");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                pool.submit(() -> handleClient(clientSocket));
            }
        } finally {
            pool.shutdown();
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            String line = reader.readLine();
            if (line == null || line.isBlank()) {
                return;
            }

            ApiRequest request = mapper.readValue(line, ApiRequest.class);
            var response = handler.handle(request);
            String responseJson = mapper.writeValueAsString(response);

            writer.write(responseJson);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }
}