package org.example.test;

import org.example.marketdata.HybridMarketDataProducer;
import org.example.marketdata.MarketDataSnapshot;

public class TestFinnhubIntegration {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Testing Finnhub Integration...\n");

        HybridMarketDataProducer producer = new HybridMarketDataProducer();

        // Test a few symbols
        String[] symbols = {"AAPL", "GOOGL", "MSFT", "INVALID"};

        for (String symbol : symbols) {
            MarketDataSnapshot snapshot = producer.getSnapshot(symbol);
            System.out.printf("%s: $%.2f (Bid: $%.2f, Ask: $%.2f)%n",
                    symbol, snapshot.getLastPrice(),
                    snapshot.getBidPrice(), snapshot.getAskPrice());

            Thread.sleep(1000); // Respect rate limits
        }

        producer.shutdown();
        System.out.println("\n✅ Test completed!");
    }
}