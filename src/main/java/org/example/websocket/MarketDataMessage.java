package org.example.websocket;

import java.time.Instant;

public class MarketDataMessage {
    private String type;
    private String symbol;
    private double bid;
    private double ask;
    private double last;
    private long volume;
    private long timestamp;

    // Default constructor
    public MarketDataMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    // Constructor for market data
    public MarketDataMessage(String type, String symbol) {
        this.type = type;
        this.symbol = symbol;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }

    public double getLast() {
        return last;
    }

    public void setLast(double last) {
        this.last = last;
    }

    public long getVolume() {
        return volume;
    }

    public void setVolume(long volume) {
        this.volume = volume;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("MarketDataMessage{symbol='%s', bid=%.2f, ask=%.2f, last=%.2f, volume=%d}",
                symbol, bid, ask, last, volume);
    }
}