package org.example.websocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.example.marketdata.HybridMarketDataProducer;
import org.example.marketdata.MarketDataSnapshot;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleWebSocketStarter {
    private static final Logger LOGGER = Logger.getLogger(SimpleWebSocketStarter.class.getName());
    private static HybridMarketDataProducer marketData;
    private static ScheduledExecutorService marketDataScheduler;
    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    // Define symbols to track
    private static final String[] SYMBOLS = {"TEST", "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
    private static final long BROADCAST_INTERVAL_MS = 2000; // 2 seconds
    private static final long SYMBOL_DELAY_MS = 100; // 100ms between symbols

    public static void main(String[] args) {
        System.out.println("=== Starting WebSocket Dashboard Server ===\n");

        try {
            // 1. Initialize Market Data Producer
            initializeMarketData();

            // 2. Register shutdown hook
            registerShutdownHook();

            // 3. Start broadcasting market data (non-blocking)
            startMarketDataBroadcast();

            // 4. Start WebSocket server (blocking)
            startWebSocketServer();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start WebSocket server", e);
            shutdown();
            System.exit(1);
        }
    }

    private static void initializeMarketData() {
        System.out.println("Initializing Market Data Producer...");
        marketData = new HybridMarketDataProducer();
        System.out.println("✅ Market Data Producer Started (Finnhub + Simulation)");

        // Initialize scheduler
        marketDataScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MarketData-Broadcaster");
            t.setDaemon(true);
            return t;
        });
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n=== Shutdown signal received ===");
            isRunning.set(false);
            shutdown();
        }));
    }

    private static void startMarketDataBroadcast() {
        System.out.println("Starting Market Data Broadcast...");

        marketDataScheduler.scheduleAtFixedRate(() -> {
            if (!isRunning.get()) {
                return;
            }

            try {
                for (String symbol : SYMBOLS) {
                    if (!isRunning.get()) {
                        break;
                    }

                    try {
                        broadcastSymbolData(symbol);

                        // Add small delay between symbols to avoid rate limiting
                        if (!"TEST".equals(symbol)) {
                            Thread.sleep(SYMBOL_DELAY_MS);
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error processing symbol " + symbol, e);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error in market data broadcast loop", e);
            }
        }, 1000, BROADCAST_INTERVAL_MS, TimeUnit.MILLISECONDS);

        System.out.println("✅ Market Data Broadcast started (" + (BROADCAST_INTERVAL_MS/1000) + " second updates)");
    }

    private static void broadcastSymbolData(String symbol) {
        try {
            MarketDataSnapshot snapshot = marketData.getSnapshot(symbol);

            MarketDataMessage message = new MarketDataMessage("market_data", symbol);
            message.setBid(snapshot.getBidPrice());
            message.setAsk(snapshot.getAskPrice());
            message.setLast(snapshot.getLastPrice());
            message.setVolume(snapshot.getLastSize());
            message.setTimestamp(snapshot.getTimestamp());

            // Broadcast to all connected clients
            MarketDataWebSocketServer.broadcastMarketData(message);

            // Log periodically (every 10 seconds) to avoid console spam
            long currentTime = System.currentTimeMillis();
            if (currentTime % 10000 < BROADCAST_INTERVAL_MS) {
                String source = "TEST".equals(symbol) ? "Simulation" : "Finnhub";
                System.out.printf("[%6s] %.2f (Source: %s, Clients: %d)%n",
                        symbol, snapshot.getLastPrice(), source,
                        MarketDataWebSocketServer.getConnectionCount());
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to broadcast data for " + symbol, e);
        }
    }

    private static void startWebSocketServer() {
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(8080);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Configure static file serving
        configureStaticResources(context);

        // Configure WebSocket support
        configureWebSocket(context);

        // Add default servlet for static files
        ServletHolder staticHolder = new ServletHolder("default", DefaultServlet.class);
        staticHolder.setInitParameter("dirAllowed", "true");
        context.addServlet(staticHolder, "/");

        try {
            server.start();
            printServerInfo();

            // Block until server stops
            server.join();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start WebSocket server", e);
            try {
                server.stop();
            } catch (Exception stopEx) {
                LOGGER.log(Level.WARNING, "Failed to stop server", stopEx);
            }
            throw new RuntimeException("WebSocket server failed to start", e);
        }
    }

    private static void configureStaticResources(ServletContextHandler context) {
        URL staticResource = SimpleWebSocketStarter.class.getClassLoader().getResource("static");
        if (staticResource != null) {
            String staticPath = staticResource.toExternalForm();
            context.setResourceBase(staticPath);
            System.out.println("✅ Static resources found: " + staticPath);
        } else {
            System.out.println("⚠️  No static resources found, using temp directory");
            context.setResourceBase(System.getProperty("java.io.tmpdir"));
        }
    }

    private static void configureWebSocket(ServletContextHandler context) {
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, container) -> {
            container.addMapping("/market-data", MarketDataWebSocketServer.class);
            // Optional: Configure WebSocket settings
            container.setIdleTimeout(Duration.ofDays(TimeUnit.MINUTES.toMillis(5)));
            container.setMaxTextMessageSize(65536); // 64KB
        });
    }

    private static void printServerInfo() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("✅ WebSocket Server Started Successfully");
        System.out.println("=".repeat(50));
        System.out.println("Server URL:     http://localhost:8080");
        System.out.println("WebSocket URL:  ws://localhost:8080/market-data");
        System.out.println("Symbols:        " + String.join(", ", SYMBOLS));
        System.out.println("Broadcast Rate: " + (BROADCAST_INTERVAL_MS/1000) + " seconds");
        System.out.println("=".repeat(50));
        System.out.println("\nPress Ctrl+C to stop the server\n");
    }

    private static void shutdown() {
        System.out.println("\n=== Shutting down gracefully ===");

        isRunning.set(false);

        // Shutdown market data scheduler
        if (marketDataScheduler != null) {
            try {
                System.out.println("Stopping market data broadcaster...");
                marketDataScheduler.shutdown();
                if (!marketDataScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    marketDataScheduler.shutdownNow();
                }
                System.out.println("✅ Market data broadcaster stopped");
            } catch (InterruptedException e) {
                marketDataScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown market data producer
        if (marketData != null) {
            try {
                System.out.println("Stopping market data producer...");
                marketData.shutdown();
                System.out.println("✅ Market data producer stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error stopping market data producer", e);
            }
        }

        System.out.println("✅ Shutdown complete");
    }
}