package com.example.calculator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorServerIntegrationTest {

    private static final int TEST_PORT = 8099;
    private static HttpServer server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        server = CalculatorServer.startServer(TEST_PORT);
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void add_returnsSumAndOk() throws Exception {
        HttpResponse<String> response = get("/api/v1/add?a=2&b=3");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"result\":5.0"));
    }

    @Test
    void divide_byZero_returns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/divide?a=4&b=0");
        assertEquals(400, response.statusCode());
    }

    @Test
    void health_returnsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void add_withPost_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/v1/add?a=2&b=3"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }
}
