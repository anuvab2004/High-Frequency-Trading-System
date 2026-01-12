package org.example.marketdata;

public class MarketDataSnapshot {
    private String symbol;
    private double bidPrice;
    private double askPrice;
    private long bidSize;
    private long askSize;
    private double lastPrice;
    private long lastSize;
    private long timestamp;

    // Constructor 1: Full constructor
    public MarketDataSnapshot(String symbol, double bidPrice, double askPrice,
                              long bidSize, long askSize, double lastPrice,
                              long lastSize, long timestamp) {
        this.symbol = symbol;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.bidSize = bidSize;
        this.askSize = askSize;
        this.lastPrice = lastPrice;
        this.lastSize = lastSize;
        this.timestamp = timestamp;
    }

    // Constructor 2: Simple constructor
    public MarketDataSnapshot(String symbol, double bid, double ask, long timestamp) {
        this(symbol, bid, ask, 1000, 1000, (bid + ask) / 2, 100, timestamp);
    }

    // Default constructor (needed for setters)
    public MarketDataSnapshot() {
    }

    // Getters
    public String getSymbol() { return symbol; }
    public double getBidPrice() { return bidPrice; }
    public double getAskPrice() { return askPrice; }
    public long getBidSize() { return bidSize; }
    public long getAskSize() { return askSize; }
    public double getLastPrice() { return lastPrice; }
    public long getLastSize() { return lastSize; }
    public long getTimestamp() { return timestamp; }

    // Setters (needed for SimulatedDataProducer)
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setBidPrice(double bidPrice) { this.bidPrice = bidPrice; }
    public void setAskPrice(double askPrice) { this.askPrice = askPrice; }
    public void setBidSize(long bidSize) { this.bidSize = bidSize; }
    public void setAskSize(long askSize) { this.askSize = askSize; }
    public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }
    public void setLastSize(long lastSize) { this.lastSize = lastSize; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}