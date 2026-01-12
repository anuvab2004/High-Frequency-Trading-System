package org.example.test;

import org.example.marketdata.HybridMarketDataProducer;
import org.example.marketdata.MarketDataSnapshot;

public class MarketDataTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Testing Market Data Producer ===\n");

        HybridMarketDataProducer producer = new HybridMarketDataProducer();

        // Show initial prices
        System.out.println("Initial Market Data:");
        System.out.println("Symbol    Bid       Ask       Last      BidSize   AskSize");
        System.out.println("----------------------------------------------------------");

        String[] symbols = {"AAPL", "GOOGL", "TSLA", "MSFT", "TEST"};
        for (String symbol : symbols) {
            MarketDataSnapshot snapshot = producer.getSnapshot(symbol);
            System.out.printf("%-6s  %8.2f  %8.2f  %8.2f  %8d  %8d%n",
                    snapshot.getSymbol(),
                    snapshot.getBidPrice(),
                    snapshot.getAskPrice(),
                    snapshot.getLastPrice(),
                    snapshot.getBidSize(),
                    snapshot.getAskSize()
            );
        }

        // Simulate 10 seconds of trading
        System.out.println("\nSimulating 10 seconds of price movement...");
        for (int i = 1; i <= 10; i++) {
            Thread.sleep(1000);
            MarketDataSnapshot snapshot = producer.getSnapshot("TEST");
            System.out.printf("Second %2d: TEST Last=%.2f (Bid=%.2f, Ask=%.2f)%n",
                    i, snapshot.getLastPrice(),
                    snapshot.getBidPrice(), snapshot.getAskPrice());
        }

        // Changed from stop() to shutdown()
        producer.shutdown();
        System.out.println("\n✅ Market Data Test Complete");
    }
}