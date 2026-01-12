package org.example;

public record OrderBookEntry(long price, long totalQuantity) {
    // Helper method to format price
    public String getFormattedPrice() {
        return String.format("₹%.2f", price / 100.0);
    }

    @Override
    public String toString() {
        return String.format("%s | Qty: %,d", getFormattedPrice(), totalQuantity);
    }
}