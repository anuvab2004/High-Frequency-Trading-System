package org.example.marketdata;

import java.util.HashMap;
import java.util.Map;

public class HybridMarketDataProducer {
    private final FinnhubDataService finnhubService;
    private final SimulatedDataProducer simulatedProducer;
    private final Map<String, String> symbolSourceMap;

    public HybridMarketDataProducer() {
        this.finnhubService = new FinnhubDataService();
        this.simulatedProducer = new SimulatedDataProducer();
        this.symbolSourceMap = new HashMap<>();

        // Configure which symbols use which source
        symbolSourceMap.put("TEST", "SIMULATION");
        symbolSourceMap.put("AAPL", "FINNHUB");
        symbolSourceMap.put("GOOGL", "FINNHUB");
        symbolSourceMap.put("MSFT", "FINNHUB");
        symbolSourceMap.put("AMZN", "FINNHUB");
        symbolSourceMap.put("TSLA", "FINNHUB");

        // Start background updates
        finnhubService.startBackgroundUpdates();
    }

    public MarketDataSnapshot getSnapshot(String symbol) {
        String source = symbolSourceMap.getOrDefault(symbol, "SIMULATION");

        if ("FINNHUB".equals(source)) {
            return finnhubService.getRealTimeData(symbol);
        } else {
            return simulatedProducer.getSnapshot(symbol);
        }
    }

    public void shutdown() {
        finnhubService.shutdown();
    }
}