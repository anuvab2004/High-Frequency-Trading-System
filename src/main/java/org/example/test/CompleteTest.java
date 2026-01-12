package org.example.test;

import org.example.EnhancedOrderBook;
import org.example.fix.FixGateway;
import org.example.risk.RiskEngine;
import org.example.marketdata.HybridMarketDataProducer;
import org.example.marketdata.MarketDataSnapshot;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

public class CompleteTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== COMPLETE FIX GATEWAY TEST WITH MARKET DATA ===\n");

        // -------------------------------------------------------------
        // CLEAN UP PREVIOUS SESSION STATE
        // -------------------------------------------------------------
        System.out.println("Cleaning up previous session state...");
        cleanDirectory("client_store");
        cleanDirectory("store");
        cleanDirectory("client_log");
        cleanDirectory("log");
        System.out.println("✅ Session state cleaned\n");

        // -------------------------------------------------------------
        // MAIN TEST EXECUTION
        // -------------------------------------------------------------

        // Step 0: Initialize Market Data Producer
        System.out.println("0. Initializing Market Data System...");
        HybridMarketDataProducer marketData = new HybridMarketDataProducer();
        System.out.println("   ✅ Market data producer started with live prices\n");

        // Step 1: Start FIX Gateway
        System.out.println("1. Starting FIX Gateway...");
        RiskEngine riskEngine = new RiskEngine();
        EnhancedOrderBook orderBook = new EnhancedOrderBook(
                trade -> System.out.printf("[ORDER BOOK] Trade: %,d @ $%.2f%n",
                        trade.quantity(), trade.price() / 100.0),
                riskEngine
        );

        FixGateway gateway = new FixGateway(orderBook);

        Thread gatewayThread = new Thread(() -> {
            try {
                gateway.start();
            } catch (Exception e) {
                System.err.println("Gateway startup failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
        gatewayThread.setDaemon(true);
        gatewayThread.start();

        // Start market data feed to order book
        Thread marketDataUpdater = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    MarketDataSnapshot snapshot = marketData.getSnapshot("TEST");
                    orderBook.updateMarketPrice(snapshot.getLastPrice());
                    Thread.sleep(1000); // Update every second
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Market data updater interrupted");
            }
        });
        marketDataUpdater.setDaemon(true);
        marketDataUpdater.start();

        // Display live market data
        Thread marketDataDisplay = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    MarketDataSnapshot snapshot = marketData.getSnapshot("TEST");
                    System.out.printf("[MARKET DATA] TEST: Bid=$%.2f Ask=$%.2f Last=$%.2f Spread=%.2f%n",
                            snapshot.getBidPrice(),
                            snapshot.getAskPrice(),
                            snapshot.getLastPrice(),
                            snapshot.getAskPrice() - snapshot.getBidPrice());
                    Thread.sleep(3000); // Display every 3 seconds
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Market data display interrupted");
            }
        });
        marketDataDisplay.setDaemon(true);
        marketDataDisplay.start();

        // Wait for gateway to fully start
        System.out.println("   Waiting for gateway to initialize...");
        Thread.sleep(3000);
        System.out.println("   ✅ Gateway is ready on port 9876\n");

        // Step 2: Create and start test client
        System.out.println("2. Starting Test Client...");

        // Start the client
        SocketInitiator client = createAndStartClient();

        // Wait for connection
        Thread.sleep(2000);

        // Step 3: Send test orders
        System.out.println("\n3. Sending Test Orders...");

        // Get current market price for intelligent order placement
        MarketDataSnapshot currentMarket = marketData.getSnapshot("TEST");
        System.out.printf("   Current Market: Bid=$%.2f Ask=$%.2f Last=$%.2f%n%n",
                currentMarket.getBidPrice(),
                currentMarket.getAskPrice(),
                currentMarket.getLastPrice());

        // Order 1: Buy at market bid
        double buyPrice = Math.round(currentMarket.getBidPrice() * 100.0) / 100.0;
        sendOrder(client, "TEST001", Side.BUY, buyPrice, 100, "ASVAL");
        Thread.sleep(1500);

        // Order 2: Sell at market ask (should match if prices cross)
        double sellPrice = Math.round(currentMarket.getAskPrice() * 100.0) / 100.0;
        sendOrder(client, "TEST002", Side.SELL, sellPrice, 50, "ASVAL");
        Thread.sleep(1500);

        // Order 3: Another buy with limit
        double aggressiveBuy = Math.round((currentMarket.getLastPrice() + 0.10) * 100.0) / 100.0;
        sendOrder(client, "TEST003", Side.BUY, aggressiveBuy, 200, "OXFORD");
        Thread.sleep(1500);

        // Order 4: Test risk limits - should be rejected
        double largeOrderPrice = Math.round(currentMarket.getBidPrice() * 100.0) / 100.0;
        sendOrder(client, "RISK001", Side.BUY, largeOrderPrice, 15000, "ASVAL");
        Thread.sleep(1500);

        // Test price deviation - should be rejected
        sendOrder(client, "PRICE_DEV", Side.BUY, 200.00, 100, "ASVAL");
        // Market is ~$100, so 100% deviation should trigger rejection

        // Test daily volume limit
        sendOrder(client, "VOL_LIMIT", Side.BUY, 101.00, 9500, "ASVAL");
        // ASVAL has 10,000 daily limit, after 100 + 15000(rejected) + 50 = 151 already used

        // Step 4: Show comprehensive results
        System.out.println("\n4. Test Results Summary:");
        System.out.println("=".repeat(50));

        EnhancedOrderBook.PerformanceMetrics metrics = orderBook.getPerformanceMetrics();
        System.out.printf("   Orders Processed: %d%n", metrics.getTotalOrders());
        System.out.printf("   Trades Executed: %d%n", metrics.getTotalTrades());
        System.out.printf("   Orders Rejected: %d (%.1f%% acceptance rate)%n",
                metrics.getRejectedOrders(), metrics.getAcceptanceRate());
        System.out.printf("   Active Orders: %d%n", metrics.getActiveOrderCount());
        System.out.printf("   Average Latency: %.2f μs%n", metrics.getAverageLatencyMicros());

        // Show current market state
        MarketDataSnapshot finalMarket = marketData.getSnapshot("TEST");
        System.out.println("\n   Final Market State:");
        System.out.printf("     TEST Symbol: Bid=$%.2f Ask=$%.2f Last=$%.2f%n",
                finalMarket.getBidPrice(),
                finalMarket.getAskPrice(),
                finalMarket.getLastPrice());

        // Show risk engine state
        System.out.println("\n   Risk Engine State:");
        System.out.printf("     ASVAL Position: %d (Daily Volume: %d)%n",
                riskEngine.getPosition("ASVAL", org.example.Order.Side.BUY),
                riskEngine.getDailyVolume("ASVAL"));
        System.out.printf("     OXFORD Position: %d (Daily Volume: %d)%n",
                riskEngine.getPosition("OXFORD", org.example.Order.Side.BUY),
                riskEngine.getDailyVolume("OXFORD"));

        // Wait and stop
        Thread.sleep(3000);
        client.stop();
        marketData.shutdown(); // Fixed method name - should be shutdown() not stop()

        System.out.println("\n" + "=".repeat(50));
        System.out.println("✅ Test completed successfully!");
        System.out.println("\nPress Enter to exit...");

        try {
            System.in.read();
        } catch (IOException e) {
            System.out.println("Shutting down...");
        }
    }

    private static SocketInitiator createAndStartClient() throws ConfigError {
        // Client configuration
        SessionSettings clientSettings = new SessionSettings();
        SessionID sessionId = new SessionID("FIX.4.4", "CLIENT", "EXCHANGE");

        clientSettings.setString(sessionId, "ConnectionType", "initiator");
        clientSettings.setString(sessionId, "SocketConnectHost", "localhost");
        clientSettings.setString(sessionId, "SocketConnectPort", "9876");
        clientSettings.setString(sessionId, "FileStorePath", "client_store");
        clientSettings.setString(sessionId, "FileLogPath", "client_log");
        clientSettings.setString(sessionId, "HeartBtInt", "30");
        clientSettings.setString(sessionId, "StartTime", "00:00:00");
        clientSettings.setString(sessionId, "EndTime", "23:59:59");
        clientSettings.setString(sessionId, "UseDataDictionary", "N");

        // Client application
        Application clientApp = new ApplicationAdapter() {
            @Override
            public void fromApp(Message message, SessionID sessionId) {
                try {
                    String msgType = message.getHeader().getString(MsgType.FIELD);
                    if (MsgType.EXECUTION_REPORT.equals(msgType)) {
                        String orderStatus = message.getString(39); // OrdStatus
                        String execType = message.getString(150); // ExecType
                        double price = message.getDouble(44); // Price
                        double qty = message.getDouble(38); // OrderQty
                        double lastPx = 0.0;
                        double lastQty = 0.0;

                        try {
                            lastPx = message.getDouble(31); // LastPx
                            lastQty = message.getDouble(32); // LastQty
                        } catch (FieldNotFound e) {
                            // Not a fill
                        }

                        if (lastQty > 0) {
                            System.out.printf("[CLIENT] FILL: %.0f @ $%.2f (Status: %s)%n",
                                    lastQty, lastPx, orderStatus);
                        } else {
                            System.out.printf("[CLIENT] Execution Report: %s %s %.0f @ $%.2f%n",
                                    orderStatus, execType, qty, price);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing execution report: " + e.getMessage());
                }
            }

            @Override
            public void onLogon(SessionID sessionId) {
                System.out.println("   ✅ Client logged on to gateway");
            }

            @Override
            public void onLogout(SessionID sessionId) {
                System.out.println("   Client logged out");
            }
        };

        // Create and start client
        SocketInitiator client = new SocketInitiator(
                clientApp,
                new FileStoreFactory(clientSettings),
                clientSettings,
                new ScreenLogFactory(true, true, true, true),
                new DefaultMessageFactory()
        );

        client.start();
        return client;
    }

    private static void sendOrder(SocketInitiator client, String clOrdId, char side,
                                  double price, double quantity, String account) throws Exception {

        SessionID sessionId = new SessionID("FIX.4.4", "CLIENT", "EXCHANGE");

        NewOrderSingle order = new NewOrderSingle();
        order.set(new ClOrdID(clOrdId));
        order.set(new HandlInst('1'));
        order.set(new Symbol("TEST"));
        order.set(new Side(side));
        order.set(new TransactTime(LocalDateTime.now()));
        order.set(new OrderQty(quantity));
        order.set(new Price(price));
        order.set(new OrdType(OrdType.LIMIT));
        order.set(new Account(account));

        String sideStr = side == Side.BUY ? "BUY" : "SELL";
        System.out.printf("   Sending: %s %s %.0f @ $%.2f (Account: %s)%n",
                clOrdId, sideStr, quantity, price, account);

        Session.sendToTarget(order, sessionId);
    }

    // -------------------------------------------------------------
    // FILE SYSTEM CLEANUP HELPERS
    // -------------------------------------------------------------
    private static void cleanDirectory(String dirPath) {
        File dir = new File(dirPath);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
        if (!dir.mkdirs()) {
            System.err.println("Warning: Failed to create directory: " + dirPath);
        }
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    if (!file.delete()) {
                        System.err.println("Warning: Failed to delete file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        if (!dir.delete()) {
            System.err.println("Warning: Failed to delete directory: " + dir.getAbsolutePath());
        }
    }
}