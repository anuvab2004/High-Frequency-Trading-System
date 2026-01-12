package org.example;

import org.example.risk.RiskEngine;
import org.example.fix.FixGateway;
import java.util.*;
import java.io.*;

public class Main {

    public static void main(String[] args) {

        // 1. Trade Listener (simplified lambda)
        TradeListener myPrinter = trade -> System.out.printf(
                "\n >> TRADE EXECUTED: %d units @ ₹%.2f (Buy ID: %d, Sell ID: %d)%n",
                trade.quantity(),
                trade.price() / 100.0,
                trade.buyOrderId(),
                trade.sellOrderId()
        );

        // 2. Initialize Engine with Risk Management
        System.out.println("-------------------------------------------");
        System.out.println("   ENHANCED HFT ENGINE WITH RISK (v2.0)    ");
        System.out.println("-------------------------------------------");

        RiskEngine riskEngine = new RiskEngine();
        EnhancedOrderBook book = new EnhancedOrderBook(myPrinter, riskEngine);
        FixGateway fixGateway = new FixGateway(book);
        new Thread(() -> {
            try {
                fixGateway.start();
            } catch (Exception e) {
                System.err.println("Failed to start FIX Gateway: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
        System.out.println("✓ FIX Gateway starting on port 9876...");

        // 3. Recovery
        book.recover();

        // 4. Start Server
        Thread serverThread = new Thread(() -> {
            TradingServer server = new TradingServer(book);
            server.start();
        });
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Startup interrupted: " + e.getMessage());
        }

        // 5. CLI
        Scanner scanner = new Scanner(System.in);

        printHelp();

        while (true) {
            System.out.print("\n[CMD] > ");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "view":
                    printMarketData(book);
                    break;
                case "stress":
                    runStressTest(book);
                    break;
                case "send":
                    sendManualOrder(scanner, book);
                    break;
                case "metrics":
                    printPerformanceMetrics(book);
                    break;
                case "risk":
                    printRiskInfo(scanner, riskEngine);
                    break;
                case "cancel":
                    cancelOrderCLI(scanner, book);
                    break;
                case "modify":
                    modifyOrderCLI(scanner, book);
                    break;
                case "help":
                    printHelp();
                    break;
                case "exit":
                    System.out.println("Shutting down engine...");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Unknown command. Type 'help'.");
            }
        }
    }

    // ================= STRESS TEST =================
    private static void runStressTest(EnhancedOrderBook book) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter number of orders per side (default 1000): ");
        String input = scanner.nextLine();

        int ordersPerSide = 1000;
        try {
            if (!input.isEmpty()) {
                ordersPerSide = Integer.parseInt(input);
            }
        } catch (NumberFormatException ignored) {}

        long start = System.nanoTime();

        for (int i = 0; i < ordersPerSide; i++) {
            book.processOrder(new Order(
                    System.nanoTime(), "STRESS_SELLER",
                    Order.Side.SELL, 10500 + i, 10));

            book.processOrder(new Order(
                    System.nanoTime(), "STRESS_BUYER",
                    Order.Side.BUY, 9500 - i, 10));
        }

        long end = System.nanoTime();
        double ms = (end - start) / 1_000_000.0;

        System.out.printf("Stress test completed in: %.2f ms%n", ms);
        System.out.printf("Throughput: %.0f orders/sec%n",
                (ordersPerSide * 2) / (ms / 1000.0));

        // Show metrics after stress test
        printPerformanceMetrics(book);
    }

    // ================= SEND MANUAL ORDER =================
    private static void sendManualOrder(Scanner scanner, EnhancedOrderBook book) {
        System.out.println("\n=== SEND MANUAL ORDER ===");

        System.out.print("Side (BUY/SELL): ");
        Order.Side side;
        try {
            side = Order.Side.valueOf(scanner.nextLine().toUpperCase());
        } catch (Exception e) {
            System.out.println("Invalid side.");
            return;
        }

        System.out.print("User ID: ");
        String userId = scanner.nextLine();

        System.out.print("Price (e.g. 100.50): ");
        double price;
        try {
            price = Double.parseDouble(scanner.nextLine());
        } catch (Exception e) {
            System.out.println("Invalid price.");
            return;
        }

        System.out.print("Quantity: ");
        long qty;
        try {
            qty = Long.parseLong(scanner.nextLine());
        } catch (Exception e) {
            System.out.println("Invalid quantity.");
            return;
        }

        Order order = new Order(
                System.nanoTime(), userId, side,
                (long) (price * 100), qty);

        EnhancedOrderBook.OrderResponse response = book.processOrder(order);
        System.out.println("Order Response: " + response);

        if (!response.isAccepted()) {
            System.out.println("❌ Order rejected: " + response.getRejectReason());
        } else {
            System.out.println("✅ Order " + response.getStatus());
            if (!response.getTrades().isEmpty()) {
                System.out.println("   Trades executed: " + response.getTrades().size());
            }
        }
    }

    // ================= CANCEL ORDER =================
    private static void cancelOrderCLI(Scanner scanner, EnhancedOrderBook book) {
        System.out.println("\n=== CANCEL ORDER ===");
        System.out.print("Order ID to cancel: ");
        try {
            long orderId = Long.parseLong(scanner.nextLine());
            boolean success = book.cancelOrder(orderId);
            if (success) {
                System.out.println("✅ Order " + orderId + " cancelled successfully");
            } else {
                System.out.println("❌ Order " + orderId + " not found or already filled");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid order ID");
        }
    }

    // ================= MODIFY ORDER =================
    private static void modifyOrderCLI(Scanner scanner, EnhancedOrderBook book) {
        System.out.println("\n=== MODIFY ORDER ===");
        System.out.print("Order ID to modify: ");
        try {
            long orderId = Long.parseLong(scanner.nextLine());

            System.out.print("New Price (e.g. 100.50): ");
            double newPrice = Double.parseDouble(scanner.nextLine());

            System.out.print("New Quantity: ");
            long newQty = Long.parseLong(scanner.nextLine());

            EnhancedOrderBook.OrderResponse response = book.modifyOrder(
                    orderId, (long)(newPrice * 100), newQty);

            System.out.println("Modify Response: " + response);
        } catch (Exception e) {
            System.out.println("Invalid input: " + e.getMessage());
        }
    }

    // ================= MARKET DATA =================
    private static void printMarketData(EnhancedOrderBook book) {
        System.out.println("\n--- MARKET DATA (Top 5) ---");

        List<OrderBookEntry> asks = book.getOrderBookSide(Order.Side.SELL, 5);
        System.out.println("ASKS:");
        if (asks.isEmpty()) {
            System.out.println("  No sell orders");
        } else {
            for (OrderBookEntry e : asks) {
                System.out.printf("  %s%n", e);
            }
        }

        List<OrderBookEntry> bids = book.getOrderBookSide(Order.Side.BUY, 5);
        System.out.println("BIDS:");
        if (bids.isEmpty()) {
            System.out.println("  No buy orders");
        } else {
            for (OrderBookEntry e : bids) {
                System.out.printf("  %s%n", e);
            }
        }

        if (!asks.isEmpty() && !bids.isEmpty()) {
            double spread = (asks.getFirst().price() - bids.getFirst().price()) / 100.0;
            System.out.printf("Spread: ₹%.2f%n", spread);
        }
    }

    // ================= PERFORMANCE METRICS =================
    private static void printPerformanceMetrics(EnhancedOrderBook book) {
        EnhancedOrderBook.PerformanceMetrics metrics = book.getPerformanceMetrics();
        System.out.println("\n--- PERFORMANCE METRICS ---");
        System.out.println(metrics);
    }

    // ================= RISK INFORMATION =================
    private static void printRiskInfo(Scanner scanner, RiskEngine riskEngine) {
        System.out.println("\n=== RISK MANAGEMENT ===");
        System.out.println("1. View trader positions");
        System.out.println("2. View daily volumes");
        System.out.print("Choice: ");

        String choice = scanner.nextLine();
        String traderId;
        switch (choice) {
            case "1":
                System.out.print("Trader ID: ");
                traderId = scanner.nextLine();
                long buyPos = riskEngine.getPosition(traderId, Order.Side.BUY);
                long sellPos = riskEngine.getPosition(traderId, Order.Side.SELL);
                System.out.printf("Positions for %s: BUY=%,d, SELL=%,d, NET=%,d%n",
                        traderId, buyPos, sellPos, buyPos - sellPos);
                break;
            case "2":
                System.out.print("Trader ID: ");
                traderId = scanner.nextLine();
                long dailyVol = riskEngine.getDailyVolume(traderId);
                System.out.printf("Daily volume for %s: %,d%n", traderId, dailyVol);
                break;
            default:
                System.out.println("Invalid choice");
        }
    }

    // ================= HELP =================
    private static void printHelp() {
        System.out.println("\nCommands:");
        System.out.println("view     - Show market data");
        System.out.println("stress   - Stress test");
        System.out.println("send     - Send manual order");
        System.out.println("cancel   - Cancel an order");
        System.out.println("modify   - Modify an order");
        System.out.println("metrics  - Show performance metrics");
        System.out.println("risk     - Risk management info");
        System.out.println("help     - Show help");
        System.out.println("exit     - Exit engine");
    }
}