package org.example.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class FinnhubDataService {
    private static final Logger LOGGER = Logger.getLogger(FinnhubDataService.class.getName());
    private static final String API_KEY = System.getenv("FINNHUB_API_KEY");
    private static final String BASE_URL = "https://finnhub.io/api/v1/quote";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;

    // Cache to avoid hitting rate limits
    private final ConcurrentHashMap<String, MarketDataSnapshot> cache;
    private final ConcurrentHashMap<String, Long> lastFetchTime;

    public FinnhubDataService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.cache = new ConcurrentHashMap<>();
        this.lastFetchTime = new ConcurrentHashMap<>();

     
        initializeSymbols();
    }

    private void initializeSymbols() {
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "TEST"};
        for (String symbol : symbols) {
            cache.put(symbol, createDefaultSnapshot(symbol));
        }
    }

    public MarketDataSnapshot getRealTimeData(String symbol) {
        long now = System.currentTimeMillis();
        Long lastFetch = lastFetchTime.get(symbol);

        // Rate limiting: Don't fetch more than once per 5 seconds per symbol (free tier limit)
        if (lastFetch != null && (now - lastFetch) < 5000) {
            return cache.getOrDefault(symbol, createDefaultSnapshot(symbol));
        }

        // For TEST symbol, always use simulation
        if ("TEST".equals(symbol)) {
            return createDefaultSnapshot(symbol);
        }

        try {
            MarketDataSnapshot snapshot = fetchFromFinnhub(symbol);
            if (snapshot != null) {
                cache.put(symbol, snapshot);
                lastFetchTime.put(symbol, now);
                return snapshot;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to fetch from Finnhub for " + symbol + ": " + e.getMessage());
        }

        // Fallback to cached or default
        return cache.getOrDefault(symbol, createDefaultSnapshot(symbol));
    }

    private MarketDataSnapshot fetchFromFinnhub(String symbol) throws Exception {
        // Don't fetch for TEST symbol (use simulation)
        if ("TEST".equals(symbol)) {
            return createDefaultSnapshot(symbol);
        }

        String url = String.format("%s?symbol=%s&token=%s", BASE_URL, symbol, API_KEY);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String json = response.body();
            return parseFinnhubResponse(symbol, json);
        } else {
            LOGGER.warning("Finnhub API error for " + symbol + ": " + response.statusCode());
            return null;
        }
    }

    private MarketDataSnapshot parseFinnhubResponse(String symbol, String json) throws Exception {
        var node = objectMapper.readTree(json);

        double currentPrice = node.get("c").asDouble();
        double previousClose = node.get("pc").asDouble();
        double high = node.get("h").asDouble();
        double low = node.get("l").asDouble();
        double open = node.get("o").asDouble();

        // Finnhub doesn't provide bid/ask in quote endpoint
        // So we'll simulate a small spread
        double spread = currentPrice * 0.001; // 0.1% spread
        double bid = currentPrice - (spread / 2);
        double ask = currentPrice + (spread / 2);

        MarketDataSnapshot snapshot = new MarketDataSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setBidPrice(bid);
        snapshot.setAskPrice(ask);
        snapshot.setLastPrice(currentPrice);
        snapshot.setLastSize(100); // Default size
        snapshot.setTimestamp(System.currentTimeMillis());

        return snapshot;
    }

    private MarketDataSnapshot createDefaultSnapshot(String symbol) {
        MarketDataSnapshot snapshot = new MarketDataSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setBidPrice(100.0);
        snapshot.setAskPrice(100.1);
        snapshot.setLastPrice(100.05);
        snapshot.setLastSize(100);
        snapshot.setTimestamp(System.currentTimeMillis());
        return snapshot;
    }

    public void startBackgroundUpdates() {
        // Update all symbols every 5 seconds (respects rate limits)
        scheduler.scheduleAtFixedRate(() -> {
            String[] symbols = {"AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"};
            for (String symbol : symbols) {
                try {
                    MarketDataSnapshot snapshot = getRealTimeData(symbol);
                    cache.put(symbol, snapshot);
                } catch (Exception e) {
                    LOGGER.warning("Background update failed for " + symbol + ": " + e.getMessage());
                }
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
