package org.example.websocket;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@WebSocket
public class MarketDataWebSocketServer {
    // Thread-safe set to hold all connected sessions
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @OnWebSocketConnect
    public void onConnect(Session session) {
        sessions.add(session);
        int count = sessions.size();
        System.out.println("Client connected: " + session.getRemoteAddress() +
                " (Total clients: " + count + ")");
        try {
            // Send welcome message
            String welcomeMessage = "{\"type\":\"connection\",\"status\":\"connected\",\"message\":\"Connected to Market Data Feed\"}";
            session.getRemote().sendString(welcomeMessage);
        } catch (IOException e) {
            System.err.println("Error sending welcome message: " + e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        int count = sessions.size();
        System.out.println("Client disconnected: " + session.getRemoteAddress() +
                " Status: " + statusCode + " Reason: " + reason +
                " (Total clients: " + count + ")");
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        System.err.println("WebSocket error for session " +
                (session != null ? session.getRemoteAddress() : "unknown") +
                ": " + error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        System.out.println("Received message from " + session.getRemoteAddress() + ": " + message);

        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.get("type").asText();

            if ("get_metrics".equals(type)) {
                // Send metrics back to client
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("type", "metrics");
                metrics.put("connections", getConnectionCount());
                metrics.put("timestamp", System.currentTimeMillis());

                // Get performance metrics from order book if available
                // Note: You'll need to pass an OrderBook instance or access it statically
                try {
                    // Example placeholder - replace with actual order book access
                    // OrderBook orderBook = MarketDataService.getOrderBook();
                    // metrics.put("totalOrders", orderBook.getTotalOrders());
                    // metrics.put("totalTrades", orderBook.getTotalTrades());
                    // metrics.put("avgLatency", orderBook.getAverageLatency());

                    // Fallback metrics for demonstration
                    metrics.put("totalOrders", sessions.size() * 100);
                    metrics.put("totalTrades", sessions.size() * 50);
                    metrics.put("avgLatency", 85.5);
                } catch (Exception e) {
                    System.err.println("Error getting order book metrics: " + e.getMessage());
                    // Fallback metrics
                    metrics.put("totalOrders", sessions.size() * 100);
                    metrics.put("totalTrades", sessions.size() * 50);
                    metrics.put("avgLatency", 85.5);
                }

                String response = objectMapper.writeValueAsString(metrics);
                session.getRemote().sendString(response);
            } else {
                // Handle other message types if needed
                System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }

    public static void broadcastMarketData(MarketDataMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            for (Session session : sessions) {
                if (session.isOpen()) {
                    session.getRemote().sendString(json);
                }
            }
        } catch (IOException e) {
            System.err.println("Error broadcasting market data: " + e.getMessage());
        }
    }

    public static int getConnectionCount() {
        return sessions.size();
    }
}