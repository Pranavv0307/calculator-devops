package com.example.calculator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CalculatorServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = startServer(8080);
        System.out.println("Calculator API listening on port 8080");
    }

    /** Package-private so tests can start the server on a throwaway port. */
    static HttpServer startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/v1/add", ex -> handleOp(ex, "add"));
        server.createContext("/api/v1/subtract", ex -> handleOp(ex, "subtract"));
        server.createContext("/api/v1/multiply", ex -> handleOp(ex, "multiply"));
        server.createContext("/api/v1/divide", ex -> handleOp(ex, "divide"));
        server.createContext("/actuator/health", CalculatorServer::handleHealth);

        server.setExecutor(null); // uses the JDK's default single-threaded executor
        server.start();
        return server;
    }

    private static void handleOp(HttpExchange exchange, String op) throws IOException {
        Map<String, String> params = queryToMap(exchange.getRequestURI());
        String aStr = params.get("a");
        String bStr = params.get("b");

        if (aStr == null || bStr == null) {
            sendJson(exchange, 400, errorJson("Missing required parameters a and/or b", op, aStr, bStr));
            return;
        }

        double a, b;
        try {
            a = Double.parseDouble(aStr);
            b = Double.parseDouble(bStr);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, errorJson("Parameters a and b must be numeric", op, aStr, bStr));
            return;
        }

        double result;
        try {
            result = switch (op) {
                case "add" -> Calculator.add(a, b);
                case "subtract" -> Calculator.subtract(a, b);
                case "multiply" -> Calculator.multiply(a, b);
                case "divide" -> Calculator.divide(a, b);
                default -> throw new IllegalStateException("Unknown operation: " + op);
            };
        } catch (ArithmeticException e) {
            sendJson(exchange, 400, errorJson(e.getMessage(), op, aStr, bStr));
            return;
        }

        String json = String.format(
                "{\"operation\":\"%s\",\"a\":%s,\"b\":%s,\"result\":%s}", op, a, b, result);
        sendJson(exchange, 200, json);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"UP\"}");
    }

    private static String errorJson(String message, String op, String a, String b) {
        return String.format(
                "{\"error\":\"%s\",\"operation\":\"%s\",\"a\":%s,\"b\":%s}",
                message, op, quoteOrNull(a), quoteOrNull(b));
    }

    private static String quoteOrNull(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static Map<String, String> queryToMap(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) result.put(kv[0], kv[1]);
        }
        return result;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
