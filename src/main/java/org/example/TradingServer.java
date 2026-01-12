package org.example;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;

public class TradingServer {

    private static final Logger logger = Logger.getLogger(TradingServer.class.getName());

    private final EnhancedOrderBook book;
    private final int port = 8080;
    private final ExecutorService executor = Executors.newFixedThreadPool(100);

    public TradingServer(EnhancedOrderBook book) {
        this.book = book;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("HFT Server started on port " + port);
            System.out.println("HFT Server started on port " + port);

            while (!executor.isShutdown()) {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleClient(socket));
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Server failure", e);
            System.err.println("Server failure: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             InputStream in = socket.getInputStream()) {

            OrderParser parser = OrderParser.getParser();

            while (true) {
                Order order = parser.parseWithReuse(in);
                if (order == null) break;

                EnhancedOrderBook.OrderResponse response = book.processOrder(order);

                String logMessage = String.format(
                        "Remote Order: %d from %s - %s",
                        order.getId(), order.getUserId(),
                        response.isAccepted() ? "ACCEPTED" : "REJECTED"
                );

                logger.info(logMessage);
                System.out.println(logMessage);

                if (!response.isAccepted()) {
                    System.out.println("  Reason: " + response.getRejectReason());
                }
            }

        } catch (Exception e) {
            logger.log(Level.INFO, "Client disconnected", e);
            System.out.println("Client disconnected: " + e.getMessage());
        }
    }
}