package org.example.marketdata;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SimulatedDataProducer {
    private final Map<String, Double> basePrices;
    private final Random random;

    public SimulatedDataProducer() {
        this.random = new Random();
        this.basePrices = new HashMap<>();

        // Set realistic base prices
        basePrices.put("TEST", 100.0);
        basePrices.put("AAPL", 175.0);
        basePrices.put("GOOGL", 142.0);
        basePrices.put("MSFT", 415.0);
        basePrices.put("AMZN", 178.0);
        basePrices.put("TSLA", 245.0);
    }

    public MarketDataSnapshot getSnapshot(String symbol) {
        double basePrice = basePrices.getOrDefault(symbol, 100.0);

        // Realistic price movement (0.5% max change)
        double changePercent = (random.nextDouble() - 0.5) * 0.01; // -0.5% to +0.5%
        double newPrice = basePrice * (1 + changePercent);

        // Update base price for next call
        basePrices.put(symbol, newPrice);

        // Realistic spread (0.1%)
        double spread = newPrice * 0.001;

        MarketDataSnapshot snapshot = new MarketDataSnapshot();
        snapshot.setSymbol(symbol);
        snapshot.setBidPrice(newPrice - (spread / 2));
        snapshot.setAskPrice(newPrice + (spread / 2));
        snapshot.setLastPrice(newPrice);
        snapshot.setLastSize(random.nextInt(1000) + 100);
        snapshot.setTimestamp(System.currentTimeMillis());

        return snapshot;
    }
}