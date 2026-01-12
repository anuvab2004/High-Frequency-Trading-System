package org.example;

public class Order {

    public enum Side { BUY, SELL }

    private final long id;
    private final String userId;
    private final Side side;
    private final long price;
    private long quantity;
    private final long timestamp;

    public Order(long id, String userId, Side side, long price, long quantity) {
        this.id = id;
        this.userId = userId;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = System.nanoTime();
    }

    public long getId() { return id; }
    public String getUserId() { return userId; }
    public Side getSide() { return side; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long q) { this.quantity = q; }
    public long getTimestamp() { return timestamp; }
}
