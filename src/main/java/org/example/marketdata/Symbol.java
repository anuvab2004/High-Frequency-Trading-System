package org.example.marketdata;

public class Symbol {
    private final String ticker;
    private final String name;
    private final String exchange;
    private final double tickSize; // Minimum price increment

    public Symbol(String ticker, String name, String exchange, double tickSize) {
        this.ticker = ticker;
        this.name = name;
        this.exchange = exchange;
        this.tickSize = tickSize;
    }

    // Getters
    public String getTicker() { return ticker; }
    public String getName() { return name; }
    public String getExchange() { return exchange; }
    public double getTickSize() { return tickSize; }
}